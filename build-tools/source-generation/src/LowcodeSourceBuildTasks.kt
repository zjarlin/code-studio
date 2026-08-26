package site.addzero.toolchain.lowcode

import io.ktor.server.config.yaml.YamlConfig
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import site.addzero.ddlgenerator.core.dialect.AutoDdlDialects
import site.addzero.ddlgenerator.core.diff.SchemaDiffPlanner
import site.addzero.ddlgenerator.core.model.AutoDdlSchema
import site.addzero.ddlgenerator.core.options.AutoDdlDiffOptions
import site.addzero.ddlgenerator.core.options.AutoDdlOptions
import site.addzero.ddlgenerator.jdbc.JdbcAutoDdlSchemaAdapter
import site.addzero.util.DatabaseMetadataReader
import site.addzero.util.db.DatabaseType
import site.addzero.platform.lowcode.generator.LowcodeGeneratedFile
import site.addzero.platform.lowcode.generator.LowcodeGeneratedFileKind
import site.addzero.ddl.compiler.DdlCompiler
import site.addzero.platform.lowcode.generator.LowcodeMetadata
import site.addzero.platform.lowcode.generator.LowcodeMetadataDatabaseConfig
import site.addzero.platform.lowcode.generator.LowcodeMetadataDatabaseReader
import site.addzero.platform.lowcode.generator.LowcodeMetadataSnapshot
import site.addzero.platform.lowcode.generator.LowcodeMetadataSnapshots
import site.addzero.platform.lowcode.generator.LowcodeModelMeta
import site.addzero.platform.lowcode.generator.LowcodeModuleCompiler
import site.addzero.platform.lowcode.generator.entitySourceContributorId
import site.addzero.platform.lowcode.generator.LowcodeDtoSourceGenerator
import site.addzero.platform.lowcode.generator.LowcodeDictionaryEnumSourceGenerator
import site.addzero.platform.lowcode.generator.LowcodeConstantSourceGenerator
import site.addzero.platform.lowcode.generator.LowcodeFeatureControllerSourceGenerator
import site.addzero.platform.lowcode.generator.LowcodeFeatureServiceSourceGenerator
import site.addzero.platform.lowcode.generator.STUDIO_GENERATED_MARKER
import site.addzero.platform.lowcode.generator.generatedByStudio
import site.addzero.platform.lowcode.generator.restrictToContributors
import site.addzero.studio.runtime.GenerationTargetProfile
import site.addzero.studio.runtime.GENERATION_TARGET_PROFILE_RESOURCE
import site.addzero.studio.runtime.GenerationTargetProfiles
import site.addzero.studio.runtime.MetadataContributor
import site.addzero.studio.runtime.MetadataContributors
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText

/**
 * 把当前贡献者的元数据迁移打包到清单声明的 classpath 位置。
 */
@TaskAction
@OptIn(ExperimentalPathApi::class)
fun packageContributorMetadata(
    @Input contributorManifest: Path,
    @Input contributorMetadataMigrationDirectory: Path,
    @Input generationTargetProfile: Path,
    @Output generatedResourcesDirectory: Path,
) {
    val contributor = readContributor(contributorManifest)
    copyContributorMetadata(contributor, contributorMetadataMigrationDirectory, generatedResourcesDirectory)
    val profile = readGenerationTargetProfile(generationTargetProfile)
    val profileResource = generatedResourcesDirectory.resolve(GENERATION_TARGET_PROFILE_RESOURCE)
    profileResource.createParentDirectories()
    profileResource.writeText(GenerationTargetProfiles.encode(profile))
}

@OptIn(ExperimentalPathApi::class)
internal fun copyContributorMetadata(
    contributor: MetadataContributor,
    sourceDirectory: Path,
    generatedResourcesDirectory: Path,
) {
    val expectedLocation = "classpath:db/studio/metadata/${contributor.id}"
    require(contributor.migrationLocation == expectedLocation) {
        "元数据贡献迁移位置必须是 $expectedLocation"
    }
    val targetDirectory = generatedResourcesDirectory.resolve(expectedLocation.removePrefix("classpath:"))
    generatedResourcesDirectory.deleteRecursively()
    targetDirectory.createDirectories()
    sourceDirectory.walk()
        .filter { path -> path.toFile().isFile && path.fileName.toString().endsWith(".sql") }
        .sortedBy { path -> path.relativeTo(sourceDirectory).toString() }
        .forEach { source ->
            val target = targetDirectory.resolve(source.relativeTo(sourceDirectory))
            target.createParentDirectories()
            source.toFile().copyTo(target.toFile(), overwrite = true)
        }
}

@TaskAction
fun generateLowcodeMigration(
    @Input currentContributorManifest: Path,
    @Input currentContributorMigrationDirectory: Path,
    @Input contributorIndex: Path,
    @Input platformMigrationDirectory: Path,
    @Output generatedMigrationDirectory: Path,
) {
    val contributors = contributorClosure(
        currentContributorManifest,
        currentContributorMigrationDirectory,
        contributorIndex,
    )
    val contributorId = contributors.current.id
    val diff = lowcodeSchemaDiff(contributorId, platformMigrationDirectory, contributors)
    generatedMigrationDirectory.toFile().mkdirs()
    generatedMigrationDirectory.resolve("candidate.sql").writeText(
        if (diff.statements.isEmpty()) {
            "-- 数据库结构已与低代码元数据一致。\n"
        } else {
            "-- ddlgenerator 生成的候选迁移；审核后再分配不可变的 Flyway 版本。\n" +
                diff.statements.joinToString(separator = "\n", postfix = "\n")
        },
    )
}

@TaskAction
fun verifyLowcodeSchema(
    @Input currentContributorManifest: Path,
    @Input currentContributorMigrationDirectory: Path,
    @Input contributorIndex: Path,
    @Input platformMigrationDirectory: Path,
) {
    val contributors = contributorClosure(
        currentContributorManifest,
        currentContributorMigrationDirectory,
        contributorIndex,
    )
    val contributorId = contributors.current.id
    val diff = lowcodeSchemaDiff(contributorId, platformMigrationDirectory, contributors)
    check(diff.operations.isEmpty()) {
        "低代码元数据与数据库结构不一致，请运行 generateLowcodeMigration 并审核候选 SQL：\n${diff.statements.joinToString("\n")}"
    }
}

private data class LowcodeSchemaDiff(
    val operations: List<site.addzero.ddlgenerator.core.diff.AutoDdlOperation>,
    val statements: List<String>,
)

private fun lowcodeSchemaDiff(
    contributorId: String,
    platformMigrationDirectory: Path,
    contributors: ContributorClosure,
): LowcodeSchemaDiff {
    val metadataDatabaseConfig = databaseConfig()
    val metadataDataSource = metadataDatabaseConfig.toDataSource()
    migrateMetadata(metadataDataSource, metadataDatabaseConfig.schema.requiredStudioSchema(), platformMigrationDirectory, contributors)
    val metadata = metadataDataSource.connection.use(LowcodeMetadataDatabaseReader::read)
        .restrictToContributors(contributors.ids)
    val desired = DdlCompiler.compile(
        metadata.models.filter { model -> model.contributorId == contributorId },
    )
    val targetDatabase = autoDdlTargetDatabaseConfig()
    val metadataReader = DatabaseMetadataReader(
        url = targetDatabase.database.jdbcUrl,
        username = targetDatabase.database.username,
        password = targetDatabase.database.password,
    )
    val tables = metadataReader.getTableMetaData(schema = targetDatabase.schema)
    val foreignKeys = metadataReader.getForeignKeysMetadata(schema = targetDatabase.schema)
    val indexes = metadataReader.getIndexMetadata(schema = targetDatabase.schema)
    val actual = JdbcAutoDdlSchemaAdapter.from(tables, foreignKeys, indexes)
        .forDesiredSchema(desired)
    val operations = SchemaDiffPlanner.plan(
        desired,
        actual,
        AutoDdlDiffOptions(
            ddlOptions = AutoDdlOptions(
                includeForeignKeys = false,
                includeIndexes = false,
                includeComments = true,
                includeSequences = false,
            ),
            allowDestructiveChanges = false,
        ),
    )
    val dialect = AutoDdlDialects.require(DatabaseType.POSTGRESQL)
    return LowcodeSchemaDiff(operations, dialect.render(operations))
}

private fun AutoDdlSchema.forDesiredSchema(desired: AutoDdlSchema): AutoDdlSchema {
    val desiredTables = desired.tables.associateBy { table -> table.name.lowercase() }
    return copy(
        tables = tables.mapNotNull { actualTable ->
            val desiredTable = desiredTables[actualTable.name.lowercase()] ?: return@mapNotNull null
            val desiredColumns = desiredTable.columns.associateBy { column -> column.name.lowercase() }
            actualTable.copy(
                columns = actualTable.columns.map { actualColumn ->
                    val desiredColumn = desiredColumns[actualColumn.name.lowercase()] ?: return@map actualColumn
                    actualColumn.copy(
                        defaultValue = desiredColumn.defaultValue,
                        primaryKey = desiredColumn.primaryKey,
                        autoIncrement = desiredColumn.autoIncrement,
                        sequenceName = desiredColumn.sequenceName,
                        nativeTypeHint = desiredColumn.nativeTypeHint,
                    )
                },
                foreignKeys = emptyList(),
                indexes = emptyList(),
            )
        },
        sequences = emptyList(),
    )
}

/**
 * 把当前 contributor 编译为确定性源码和可编辑脚手架期望值。
 */
@TaskAction
@OptIn(ExperimentalPathApi::class)
fun compileLowcodeSources(
    @Input contributorManifest: Path,
    @Input generationTargetProfile: Path,
    @Input metadataSnapshot: Path,
    @Input sourceMetadataSnapshots: List<Path>,
    @Output compiledSourceDirectory: Path,
    @Output scaffoldSourceDirectory: Path,
) {
    val contributor = readContributor(contributorManifest)
    val contributorId = contributor.id
    val metadata = readCompilationMetadata(
        metadataSnapshot = metadataSnapshot,
        sourceMetadataSnapshots = sourceMetadataSnapshots,
        contributor = contributor,
    )
    val targetProfile = readGenerationTargetProfile(generationTargetProfile)
    val files = metadata.generatedFiles(contributorId, targetProfile)
    compiledSourceDirectory.deleteRecursively()
    scaffoldSourceDirectory.deleteRecursively()
    files.filter(LowcodeGeneratedFile::isCompiledSource)
        .forEach { file -> file.writeGeneratedSource(compiledSourceDirectory) }
    files.filter(LowcodeGeneratedFile::isEditableScaffold)
        .forEach { file -> file.writeGeneratedSource(scaffoldSourceDirectory) }
}

/** 显式重放迁移并更新版本化的 canonical metadata snapshot。 */
@TaskAction
fun refreshLowcodeMetadata(
    @Input currentContributorManifest: Path,
    @Input currentContributorMigrationDirectory: Path,
    @Input contributorIndex: Path,
    @Input platformMigrationDirectory: Path,
    @Input developmentDatabaseConfig: Path,
    @Input metadataSnapshot: Path,
) {
    val contributors = contributorClosure(
        currentContributorManifest,
        currentContributorMigrationDirectory,
        contributorIndex,
    )
    val databaseConfig = databaseConfig(developmentDatabaseConfig = developmentDatabaseConfig)
    val dataSource = databaseConfig.toDataSource()
    migrateMetadata(dataSource, databaseConfig.schema.requiredStudioSchema(), platformMigrationDirectory, contributors)
    val metadata = dataSource.connection.use(LowcodeMetadataDatabaseReader::read)
        .restrictToContributors(contributors.ids)
    val snapshot = LowcodeMetadataSnapshot(
        contributorId = contributors.current.id,
        contributorIds = contributors.ordered.map(MetadataContributor::id),
        metadata = metadata,
    )
    metadataSnapshot.createParentDirectories()
    metadataSnapshot.writeText(LowcodeMetadataSnapshots.encode(snapshot))
}

/**
 * 只读宿主源码校验脚手架签名；这一段不连接数据库，也不运行 Flyway。
 */
@TaskAction
@OptIn(ExperimentalPathApi::class)
fun validateLowcodeSources(
    @Input compiledSourceDirectory: Path,
    @Input scaffoldSourceDirectory: Path,
    @Input sourceDirectory: Path,
    @Output generatedSourceDirectory: Path,
) {
    validateMaterializedSources(
        sourceRoot = sourceDirectory,
        expected = readEditableScaffolds(scaffoldSourceDirectory),
    )
    copyGeneratedSources(compiledSourceDirectory, generatedSourceDirectory)
}

private fun LowcodeMetadata.generatedFiles(
    contributorId: String,
    targetProfile: GenerationTargetProfile,
): List<LowcodeGeneratedFile> {
    return LowcodeModuleCompiler.generate(this, contributorId, targetProfile = targetProfile)
}

/**
 * 显式同步当前 contributor 可编辑的 Controller 和 ServiceImpl。
 */
@TaskAction
fun materializeLowcodeSources(
    @Input contributorManifest: Path,
    @Input generationTargetProfile: Path,
    @Input metadataSnapshot: Path,
    @Input sourceDirectory: Path,
) {
    val contributor = readContributor(contributorManifest)
    val contributorId = contributor.id
    val metadata = readMetadataSnapshot(metadataSnapshot, contributor)
    val targetProfile = readGenerationTargetProfile(generationTargetProfile)
    val files = metadata.generatedFiles(contributorId, targetProfile)
    val sources = files.filter(LowcodeGeneratedFile::isEditableScaffold)
    synchronizeMaterializedSources(sourceDirectory, sources)
}

private fun readGenerationTargetProfile(path: Path): GenerationTargetProfile =
    configurationObjectMapper.readValue(path.toFile(), GenerationTargetProfile::class.java)

private fun readMetadataSnapshot(path: Path, contributor: MetadataContributor): LowcodeMetadata {
    val snapshot = LowcodeMetadataSnapshots.decode(path.readText())
    require(snapshot.contributorId == contributor.id) {
        "元数据快照归属 ${snapshot.contributorId} 与当前 manifest ${contributor.id} 不一致"
    }
    require(snapshot.contributorIds.containsAll(contributor.requires)) {
        "元数据快照 ${contributor.id} 缺少 manifest.requires: " +
            (contributor.requires - snapshot.contributorIds.toSet()).sorted().joinToString()
    }
    return snapshot.metadata
}

private fun readCompilationMetadata(
    metadataSnapshot: Path,
    sourceMetadataSnapshots: List<Path>,
    contributor: MetadataContributor,
): LowcodeMetadata {
    val metadata = readMetadataSnapshot(metadataSnapshot, contributor)
    if (sourceMetadataSnapshots.isEmpty()) {
        return metadata
    }
    val sourceModels = sourceMetadataSnapshots
        .sortedBy { path -> path.toString() }
        .flatMap { path -> LowcodeMetadataSnapshots.decode(path.readText()).metadata.models }
    val ownedSourceModels = sourceModels.filter { model ->
        model.entitySourceContributorId() == contributor.id
    }
    require(ownedSourceModels.isNotEmpty()) {
        "外部元数据快照未提供当前 contributor 的实体源码: ${contributor.id}"
    }
    return metadata.copy(
        models = mergeCompilationModelCatalog(listOf(metadata.models, sourceModels)),
    )
}

internal fun mergeCompilationModelCatalog(
    catalogs: List<List<LowcodeModelMeta>>,
): List<LowcodeModelMeta> = catalogs
    .flatten()
    .groupBy { model -> model.modelCode }
    .map { (modelCode, candidates) ->
        mergeCompilationModel(modelCode, candidates)
    }
    .sortedBy { model -> model.modelCode }

private fun mergeCompilationModel(
    modelCode: String,
    candidates: List<LowcodeModelMeta>,
): LowcodeModelMeta {
    val shells = candidates.map { model ->
        model.copy(
            dtoDefinitions = emptyList(),
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
    }.distinct()
    require(shells.size == 1) {
        "模型源快照存在冲突: $modelCode"
    }
    val dtoDefinitions = candidates
        .flatMap { model -> model.dtoDefinitions }
        .mergeExactBy({ dto -> dto.dtoCode }, "$modelCode DTO")
    val fields = candidates
        .flatMap { model -> model.fields }
        .mergeExactBy({ field -> field.id }, "$modelCode 字段")
        .sortedWith(compareBy({ field -> field.orderNo }, { field -> field.id }))
    val queries = candidates
        .flatMap { model -> model.queries }
        .groupBy { query -> query.id }
        .map { (queryId, queryCandidates) ->
            val queryShells = queryCandidates.map { query -> query.copy(items = emptyList()) }.distinct()
            require(queryShells.size == 1) {
                "模型 $modelCode 的查询快照存在冲突: $queryId"
            }
            queryShells.single().copy(
                items = queryCandidates
                    .flatMap { query -> query.items }
                    .mergeExactBy({ item -> item.id }, "$modelCode 查询条件")
                    .sortedWith(compareBy({ item -> item.orderNo }, { item -> item.id })),
            )
        }
        .sortedWith(compareBy({ query -> query.orderNo }, { query -> query.id }))
    val relations = candidates
        .flatMap { model -> model.relations }
        .mergeExactBy({ relation -> relation.id }, "$modelCode 关系")
        .sortedWith(compareBy({ relation -> relation.orderNo }, { relation -> relation.id }))
    return shells.single().copy(
        dtoDefinitions = dtoDefinitions,
        fields = fields,
        queries = queries,
        relations = relations,
    )
}

private fun <T, K> List<T>.mergeExactBy(
    key: (T) -> K,
    description: String,
): List<T> = groupBy(key).map { (value, candidates) ->
    require(candidates.distinct().size == 1) {
        "$description 快照存在冲突: $value"
    }
    candidates.first()
}

private fun readContributor(path: Path): MetadataContributor =
    configurationObjectMapper.readValue(path.toFile(), MetadataContributor::class.java)

private data class ContributorIndex(
    val formatVersion: Int = CONTRIBUTOR_INDEX_FORMAT_VERSION,
    val contributors: Map<String, String>,
) {
    init {
        require(formatVersion == CONTRIBUTOR_INDEX_FORMAT_VERSION) {
            "不支持的 contributor index 版本: $formatVersion"
        }
        require(contributors.keys.none(String::isBlank)) { "contributor index ID 不能为空" }
        require(contributors.values.none(String::isBlank)) { "contributor index 模块路径不能为空" }
    }
}

private data class ContributorSource(
    val root: Path,
    val manifest: MetadataContributor,
) {
    val migrationDirectory: Path = root.resolve(CONTRIBUTOR_MIGRATION_DIRECTORY)
}

private data class ContributorClosure(
    val current: MetadataContributor,
    val orderedSources: List<ContributorSource>,
) {
    val ordered: List<MetadataContributor> = orderedSources.map(ContributorSource::manifest)
    val ids: Set<String> = ordered.mapTo(linkedSetOf(), MetadataContributor::id)
}

private fun contributorClosure(
    currentContributorManifest: Path,
    currentContributorMigrationDirectory: Path,
    contributorIndex: Path,
): ContributorClosure {
    val manifestPath = currentContributorManifest.toAbsolutePath().normalize()
    val currentRoot = contributorRoot(manifestPath)
    val expectedMigrationDirectory = currentRoot.resolve(CONTRIBUTOR_MIGRATION_DIRECTORY).normalize()
    require(currentContributorMigrationDirectory.toAbsolutePath().normalize() == expectedMigrationDirectory) {
        "当前 contributor migration 目录必须是 $expectedMigrationDirectory"
    }
    val current = readContributorSource(currentRoot)
    val indexPath = contributorIndex.toAbsolutePath().normalize()
    require(indexPath.exists()) { "缺少 contributor index: $indexPath" }
    require(indexPath.fileName.toString() == CONTRIBUTOR_INDEX_FILE_NAME) {
        "contributor index 必须命名为 $CONTRIBUTOR_INDEX_FILE_NAME: $indexPath"
    }
    val indexDirectory = requireNotNull(indexPath.parent) { "contributor index 缺少父目录: $indexPath" }
    require(indexDirectory.fileName.toString() == CONTRIBUTOR_INDEX_DIRECTORY) {
        "contributor index 必须位于 $CONTRIBUTOR_INDEX_DIRECTORY/: $indexPath"
    }
    val repositoryRoot = requireNotNull(indexDirectory.parent) {
        "contributor index 无法解析仓库根目录: $indexPath"
    }.normalize()
    val index = configurationObjectMapper.readValue(indexPath.toFile(), ContributorIndex::class.java)
    index.contributors[current.manifest.id]?.let { relativePath ->
        val indexedCurrentRoot = resolveContributorRoot(repositoryRoot, current.manifest.id, relativePath)
        require(indexedCurrentRoot == currentRoot) {
            "contributor index 中 ${current.manifest.id} 指向 $indexedCurrentRoot，当前模块是 $currentRoot"
        }
    }

    val sourcesById = linkedMapOf(current.manifest.id to current)
    fun visit(id: String) {
        if (id in sourcesById) return
        val relativePath = index.contributors[id]
            ?: error("contributor ${current.manifest.id} 的依赖 $id 未登记到 $indexPath")
        val root = resolveContributorRoot(repositoryRoot, id, relativePath)
        val source = readContributorSource(root)
        require(source.manifest.id == id) {
            "contributor index 的 $id 指向 manifest ${source.manifest.id}: $root"
        }
        sourcesById[id] = source
        source.manifest.requires.forEach(::visit)
    }
    current.manifest.requires.forEach(::visit)
    val ordered = MetadataContributors.resolve(sourcesById.values.map(ContributorSource::manifest))
    return ContributorClosure(current.manifest, ordered.map { contributor -> sourcesById.getValue(contributor.id) })
}

private fun contributorRoot(manifestPath: Path): Path {
    val suffix = Paths.get(CONTRIBUTOR_MANIFEST)
    require(manifestPath.endsWith(suffix)) {
        "contributor manifest 必须位于 $CONTRIBUTOR_MANIFEST: $manifestPath"
    }
    return (0 until suffix.nameCount).fold(manifestPath) { path, _ ->
        requireNotNull(path.parent) { "无法从 manifest 解析 contributor root: $manifestPath" }
    }
}

private fun readContributorSource(root: Path): ContributorSource {
    val manifestPath = root.resolve(CONTRIBUTOR_MANIFEST)
    require(manifestPath.exists()) { "contributor root 缺少 manifest: $manifestPath" }
    val source = ContributorSource(root, readContributor(manifestPath))
    require(source.migrationDirectory.isDirectory()) {
        "contributor root 缺少自治元数据迁移目录: ${source.migrationDirectory}"
    }
    return source
}

private fun resolveContributorRoot(repositoryRoot: Path, id: String, relativePath: String): Path {
    val path = Paths.get(relativePath)
    require(!path.isAbsolute) { "contributor index 的 $id 必须使用仓库相对路径: $relativePath" }
    val root = repositoryRoot.resolve(path).normalize()
    require(root.startsWith(repositoryRoot)) {
        "contributor index 的 $id 越出仓库根目录: $relativePath"
    }
    return root
}

private fun migrateMetadata(
    dataSource: PGSimpleDataSource,
    schema: String,
    platformMigrationDirectory: Path,
    contributors: ContributorClosure,
) {
    val platformLocation = "filesystem:${platformMigrationDirectory.toAbsolutePath().normalize()}"
    dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT pg_advisory_lock(?)").use { statement ->
            statement.setLong(1, LOWCODE_MIGRATION_LOCK_ID)
            statement.execute()
        }
        try {
            migrateLocations(
                dataSource = dataSource,
                schema = schema,
                location = platformLocation,
                historyTable = CODE_STUDIO_CORE_HISTORY_TABLE,
                placeholders = mapOf("studioSchema" to schema),
            )
            registerContributors(connection, schema, contributors.ordered)
            contributors.orderedSources.forEach { source ->
                migrateLocations(
                    dataSource = dataSource,
                    schema = schema,
                    location = "filesystem:${source.migrationDirectory.toAbsolutePath().normalize()}",
                    historyTable = CODE_STUDIO_METADATA_HISTORY_TABLE,
                    placeholders = mapOf(
                        "studioSchema" to schema,
                        "contributorId" to source.manifest.id,
                    ),
                    tolerateOtherContributorMigrations = true,
                )
            }
        } finally {
            connection.prepareStatement("SELECT pg_advisory_unlock(?)").use { statement ->
                statement.setLong(1, LOWCODE_MIGRATION_LOCK_ID)
                statement.execute()
            }
        }
    }
}

private fun registerContributors(
    connection: Connection,
    schema: String,
    contributors: List<MetadataContributor>,
) {
    val sql = """
        INSERT INTO $schema.metadata_contributor (id, format_version, migration_location)
        VALUES (?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            format_version = EXCLUDED.format_version,
            migration_location = EXCLUDED.migration_location
    """.trimIndent()
    connection.prepareStatement(sql).use { statement ->
        contributors.forEach { contributor ->
            statement.setString(1, contributor.id)
            statement.setInt(2, contributor.formatVersion)
            statement.setString(3, contributor.migrationLocation)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun migrateLocations(
    dataSource: PGSimpleDataSource,
    schema: String,
    location: String,
    historyTable: String,
    placeholders: Map<String, String>,
    tolerateOtherContributorMigrations: Boolean = false,
) {
    val configuration = Flyway.configure()
        .dataSource(dataSource)
        .schemas(schema)
        .defaultSchema(schema)
        .locations(location)
        .table(historyTable)
        .placeholders(placeholders)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .outOfOrder(true)
        .validateOnMigrate(true)
        .validateMigrationNaming(true)
        .failOnMissingLocations(true)
        .cleanDisabled(true)
    if (tolerateOtherContributorMigrations) {
        configuration.ignoreMigrationPatterns("*:missing", "*:future")
    }
    configuration.load().migrate()
}
private const val CODE_STUDIO_CORE_HISTORY_TABLE = "code_studio_core_history"
private const val CODE_STUDIO_METADATA_HISTORY_TABLE = "code_studio_metadata_history"

internal fun databaseConfig(
    environment: Map<String, String> = System.getenv(),
    developmentDatabaseConfig: Path? = null,
): LowcodeMetadataDatabaseConfig {
    val requiredEnvironment = listOf(
        "CODE_STUDIO_DB_JDBC_URL",
        "CODE_STUDIO_DB_USERNAME",
        "CODE_STUDIO_DB_PASSWORD",
    )
    val hasAnyExplicitConfig = requiredEnvironment.any { name ->
        environment[name]?.trim()?.isNotEmpty() == true
    }
    if (hasAnyExplicitConfig || developmentDatabaseConfig == null) {
        val config = LowcodeMetadataDatabaseConfig.fromEnvironment(environment)
        return config.copy(schema = config.schema ?: DEFAULT_STUDIO_SCHEMA)
    }

    val config = requireNotNull(YamlConfig(developmentDatabaseConfig.toString())) {
        "无法读取低代码开发数据源配置: $developmentDatabaseConfig"
    }
    val databasePath = "database"
    val legacyDatabasePath = "app.studio.database"
    return LowcodeMetadataDatabaseConfig(
        jdbcUrl = config.propertyOrNull("$databasePath.url")?.getString()
            ?: config.property("$legacyDatabasePath.jdbcUrl").getString(),
        username = config.propertyOrNull("$databasePath.username")?.getString()
            ?: config.property("$legacyDatabasePath.username").getString(),
        password = config.propertyOrNull("$databasePath.password")?.getString()
            ?: config.property("$legacyDatabasePath.password").getString(),
        schema = environment["CODE_STUDIO_SCHEMA"]?.trim()?.takeIf(String::isNotEmpty)
            ?: config.propertyOrNull("$databasePath.schema")?.getString()
            ?: DEFAULT_STUDIO_SCHEMA,
    )
}

private fun String?.requiredStudioSchema(): String = requireNotNull(this) {
    "Studio metadata schema 未配置"
}

internal data class AutoDdlTargetDatabaseConfig(
    val database: LowcodeMetadataDatabaseConfig,
    val schema: String,
)

/** 解析 AutoDDL 要校验的宿主数据库，禁止隐式回落到 Studio 控制面。 */
internal fun autoDdlTargetDatabaseConfig(
    environment: Map<String, String> = System.getenv(),
): AutoDdlTargetDatabaseConfig {
    val schema = environment[LOWCODE_TARGET_DB_SCHEMA]
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: "public"
    require(schema.matches(DATABASE_SCHEMA_PATTERN)) {
        "AutoDDL 目标 Schema 不合法: $schema"
    }
    return AutoDdlTargetDatabaseConfig(
        database = LowcodeMetadataDatabaseConfig(
            jdbcUrl = environment.requiredAutoDdl(LOWCODE_TARGET_DB_JDBC_URL),
            username = environment.requiredAutoDdl(LOWCODE_TARGET_DB_USERNAME),
            password = environment.requiredAutoDdl(LOWCODE_TARGET_DB_PASSWORD),
        ),
        schema = schema,
    )
}

private fun Map<String, String>.requiredAutoDdl(name: String): String =
    get(name)?.trim()?.takeIf(String::isNotEmpty)
        ?: error("AutoDDL 需要宿主数据库环境变量 $name")

internal fun LowcodeMetadataDatabaseConfig.toDataSource(): PGSimpleDataSource {
    schema?.let(::createMetadataSchema)
    return PGSimpleDataSource().apply {
        setURL(jdbcUrl)
        user = username
        password = this@toDataSource.password
        currentSchema = schema
    }
}

private fun LowcodeMetadataDatabaseConfig.createMetadataSchema(schema: String) {
    val dataSource = PGSimpleDataSource().apply {
        setURL(jdbcUrl)
        user = username
        password = this@createMetadataSchema.password
    }
    dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT pg_advisory_lock(?)").use { statement ->
            statement.setLong(1, LOWCODE_MIGRATION_LOCK_ID)
            statement.execute()
        }
        try {
            connection.createStatement().use { statement ->
                statement.execute("CREATE SCHEMA IF NOT EXISTS $schema")
            }
        } finally {
            connection.prepareStatement("SELECT pg_advisory_unlock(?)").use { statement ->
                statement.setLong(1, LOWCODE_MIGRATION_LOCK_ID)
                statement.execute()
            }
        }
    }
}

private fun LowcodeGeneratedFile.writeGeneratedSource(sourceRoot: Path) {
    require(extensionName == "kt") {
        "生成源码任务不支持文件类型: $relativePath"
    }
    val packagePath = packageName.replace('.', '/')
    val outputFile = sourceRoot.resolve(packagePath).resolve("$fileName.$extensionName")
    outputFile.createParentDirectories()
    outputFile.writeText(content)
}

@OptIn(ExperimentalPathApi::class)
private fun readEditableScaffolds(sourceRoot: Path): List<LowcodeGeneratedFile> {
    if (!sourceRoot.exists()) {
        return emptyList()
    }
    return sourceRoot.walk()
        .filter { path -> path.toFile().isFile && path.fileName.toString().endsWith(".kt") }
        .sortedBy { path -> path.relativeTo(sourceRoot).toString() }
        .map { path ->
            val content = path.readText()
            val packageName = requireNotNull(PACKAGE_DECLARATION.find(content)?.groupValues?.get(1)) {
                "可编辑脚手架缺少 package 声明: $path"
            }
            val kind = when {
                content.controllerSignature().isNotEmpty() -> LowcodeGeneratedFileKind.CONTROLLER_SCAFFOLD
                content.serviceImplementationSignature().isNotEmpty() ->
                    LowcodeGeneratedFileKind.SERVICE_IMPLEMENTATION_SCAFFOLD
                else -> error("无法识别可编辑脚手架: $path")
            }
            LowcodeGeneratedFile(
                packageName = packageName,
                fileName = path.fileName.toString().removeSuffix(".kt"),
                relativePath = path.relativeTo(sourceRoot).toString(),
                content = content,
                kind = kind,
            )
        }
        .toList()
}

@OptIn(ExperimentalPathApi::class)
private fun copyGeneratedSources(sourceDirectory: Path, targetDirectory: Path) {
    targetDirectory.deleteRecursively()
    if (!sourceDirectory.exists()) {
        return
    }
    sourceDirectory.walk()
        .filter { path -> path.toFile().isFile }
        .sortedBy { path -> path.relativeTo(sourceDirectory).toString() }
        .forEach { source ->
            val target = targetDirectory.resolve(source.relativeTo(sourceDirectory))
            target.createParentDirectories()
            source.toFile().copyTo(target.toFile())
        }
}

private fun validateMaterializedSources(
    sourceRoot: Path,
    expected: List<LowcodeGeneratedFile>,
): Set<Path> {
    val expectedByPath = expected.associateBy { file -> file.sourcePath(sourceRoot) }
    val missing = expectedByPath.keys.filterNot(Path::exists)
    check(missing.isEmpty()) {
        "缺少可编辑的低代码脚手架，请运行 codeStudioSync：" +
            missing.sorted().joinToString(separator = "\n", prefix = "\n")
    }
    val stale = materializedLowcodeSourceFiles(sourceRoot)
        .filterNot(expectedByPath::containsKey)
        .toList()
    check(stale.isEmpty()) {
        "存在已失效的生成源码，请运行 codeStudioSync：" +
            stale.sorted().joinToString(separator = "\n", prefix = "\n")
    }
    val handwrittenControllerCollisions = expectedByPath.mapNotNull { (path, file) ->
        val generatesController = file.kind == LowcodeGeneratedFileKind.CONTROLLER_SCAFFOLD ||
            file.kind == LowcodeGeneratedFileKind.CONTRACT_CONTROLLER
        if (!generatesController || !path.exists()) {
            return@mapNotNull null
        }
        path.takeIf(Path::isHandwrittenLowcodeController)
    }
    check(handwrittenControllerCollisions.isEmpty()) {
        "元数据将生成同名 Controller，请先迁移或重命名人工文件：" +
            handwrittenControllerCollisions.sorted().joinToString(separator = "\n", prefix = "\n")
    }
    val controllerSignatureMismatches = expectedByPath.mapNotNull { (path, file) ->
        if (file.kind != LowcodeGeneratedFileKind.CONTROLLER_SCAFFOLD || !path.exists()) {
            return@mapNotNull null
        }
        controllerSignatureMismatch(path, file)
    }
    check(controllerSignatureMismatches.isEmpty()) {
        "Controller 元数据签名已变更，请运行 codeStudioSync 并人工合并：\n" +
            controllerSignatureMismatches.joinToString("\n")
    }
    val controllerScaffoldMismatches = expectedByPath.mapNotNull { (path, file) ->
        if (file.kind != LowcodeGeneratedFileKind.CONTROLLER_SCAFFOLD || !path.exists()) {
            return@mapNotNull null
        }
        val mismatches = LowcodeFeatureControllerSourceGenerator.editableControllerScaffoldMismatches(
            actualContent = path.readText(),
            expectedContent = file.content,
        )
        mismatches.takeIf(List<String>::isNotEmpty)?.let { values ->
            "$path\n${values.joinToString("\n") { value -> "- $value" }}"
        }
    }
    check(controllerScaffoldMismatches.isEmpty()) {
        "Controller 脚手架结构已被破坏，请保留 CRUD 主干：\n" +
            controllerScaffoldMismatches.joinToString("\n")
    }
    val serviceSignatureMismatches = expectedByPath.mapNotNull { (path, file) ->
        if (file.kind != LowcodeGeneratedFileKind.SERVICE_IMPLEMENTATION_SCAFFOLD || !path.exists()) {
            return@mapNotNull null
        }
        val expectedSignature = file.content.serviceImplementationSignature()
        val actualSignature = path.readText().serviceImplementationSignature()
        path.takeIf { actualSignature != expectedSignature }
    }
    check(serviceSignatureMismatches.isEmpty()) {
        "Service 实现元数据签名已变更，请运行 codeStudioSync 并人工合并：" +
            serviceSignatureMismatches.sorted().joinToString(separator = "\n", prefix = "\n")
    }
    return expectedByPath.keys
}

private fun synchronizeMaterializedSources(
    sourceRoot: Path,
    expected: List<LowcodeGeneratedFile>,
): Set<Path> {
    val expectedByPath = expected.associateBy { file -> file.sourcePath(sourceRoot) }
    val stale = materializedLowcodeSourceFiles(sourceRoot)
        .filterNot(expectedByPath::containsKey)
        .toList()
    val staleEditableControllers = stale.filter(Path::isEditableLowcodeController)
    check(staleEditableControllers.isEmpty()) {
        "存在已失效但可能含人工修改的低代码 Controller，请人工确认后删除：" +
            staleEditableControllers.sorted().joinToString(separator = "\n", prefix = "\n")
    }
    val staleGeneratedServiceImplementations = stale.filter { path ->
        path.isEditableLowcodeServiceImplementation() && path.hasStudioGeneratedMarker()
    }
    check(staleGeneratedServiceImplementations.isEmpty()) {
        "存在已失效但可能含人工修改的低代码 Service 实现，请人工迁移到新包或确认后删除：" +
            staleGeneratedServiceImplementations.sorted().joinToString(separator = "\n", prefix = "\n")
    }
    val handwrittenControllerCollisions = expectedByPath.mapNotNull { (path, file) ->
        val generatesController = file.kind == LowcodeGeneratedFileKind.CONTROLLER_SCAFFOLD ||
            file.kind == LowcodeGeneratedFileKind.CONTRACT_CONTROLLER
        if (!generatesController || !path.exists()) {
            return@mapNotNull null
        }
        path.takeIf(Path::isHandwrittenLowcodeController)
    }
    check(handwrittenControllerCollisions.isEmpty()) {
        "低代码元数据将生成同名 Controller，请先迁移或重命名人工文件：" +
            handwrittenControllerCollisions.sorted().joinToString(separator = "\n", prefix = "\n")
    }
    val controllerSignatureMismatches = expectedByPath.mapNotNull { (path, file) ->
        if (file.kind != LowcodeGeneratedFileKind.CONTROLLER_SCAFFOLD || !path.exists()) {
            return@mapNotNull null
        }
        controllerSignatureMismatch(path, file)
    }
    check(controllerSignatureMismatches.isEmpty()) {
        "低代码 Controller 元数据签名已变更，请人工合并后更新签名，或删除文件后重新生成：\n" +
            controllerSignatureMismatches.joinToString("\n")
    }
    val controllerScaffoldMismatches = expectedByPath.mapNotNull { (path, file) ->
        if (file.kind != LowcodeGeneratedFileKind.CONTROLLER_SCAFFOLD || !path.exists()) {
            return@mapNotNull null
        }
        val mismatches = LowcodeFeatureControllerSourceGenerator.editableControllerScaffoldMismatches(
            actualContent = path.readText(),
            expectedContent = file.content,
        )
        mismatches.takeIf(List<String>::isNotEmpty)?.let { values ->
            "$path\n${values.joinToString("\n") { value -> "- $value" }}"
        }
    }
    check(controllerScaffoldMismatches.isEmpty()) {
        "低代码 Controller 脚手架结构已被破坏，请保留 CRUD 主干：\n" +
            controllerScaffoldMismatches.joinToString("\n")
    }
    expectedByPath.forEach { (path, file) ->
        if (file.kind == LowcodeGeneratedFileKind.SERVICE_IMPLEMENTATION_SCAFFOLD && path.exists()) {
            synchronizeServiceImplementationSignature(path, file)
        }
    }
    expectedByPath.forEach { (path, file) ->
        if (file.kind == LowcodeGeneratedFileKind.CONTROLLER_SCAFFOLD && path.exists()) {
            val content = path.readText()
            if (!path.hasStudioGeneratedMarker()) {
                path.writeText(generatedByStudio(content))
            }
            return@forEach
        }
        if (file.kind == LowcodeGeneratedFileKind.SERVICE_IMPLEMENTATION_SCAFFOLD && path.exists()) {
            return@forEach
        }
        path.createParentDirectories()
        path.writeText(file.content)
    }
    return expectedByPath.keys
}

private fun materializedLowcodeSourceFiles(sourceRoot: Path): Sequence<Path> =
    editableLowcodeSourceFiles(sourceRoot) +
        legacyFeatureControllerScaffoldFiles(sourceRoot)

private fun controllerSignatureMismatch(path: Path, expected: LowcodeGeneratedFile): String? {
    val expectedSignature = expected.content.controllerSignature()
    val actualSignature = path.readText().controllerSignature()
    if (actualSignature == expectedSignature) {
        return null
    }
    return "$path\n当前签名：$actualSignature\n期望签名：$expectedSignature"
}

private fun String.controllerSignature(): String = lineSequence()
    .take(GENERATED_MARKER_LINE_LIMIT)
    .firstOrNull { line -> line.startsWith(LowcodeFeatureControllerSourceGenerator.CONTROLLER_SIGNATURE_PREFIX) }
    .orEmpty()

private fun synchronizeServiceImplementationSignature(path: Path, expected: LowcodeGeneratedFile) {
    val expectedSignature = expected.content.serviceImplementationSignature()
    val content = path.readText()
    val actualSignature = content.serviceImplementationSignature()
    check(actualSignature.isEmpty() || actualSignature == expectedSignature) {
        "低代码 Service 实现元数据签名已变更，请人工合并后更新签名，或删除文件后重新生成：$path"
    }
    if (actualSignature.isEmpty()) {
        path.writeText("$expectedSignature\n$content")
    }
}

private fun String.serviceImplementationSignature(): String = lineSequence()
    .take(GENERATED_MARKER_LINE_LIMIT)
    .firstOrNull { line ->
        line.startsWith(LowcodeFeatureServiceSourceGenerator.SERVICE_IMPLEMENTATION_SIGNATURE_PREFIX)
    }
    .orEmpty()

private fun Path.hasStudioGeneratedMarker(): Boolean =
    readText().lineSequence().take(GENERATED_MARKER_LINE_LIMIT).any { line ->
        STUDIO_GENERATED_MARKER in line
    }

private fun editableLowcodeSourceFiles(sourceRoot: Path): Sequence<Path> {
    if (!sourceRoot.exists()) {
        return emptySequence()
    }
    return sourceRoot.toFile().walkTopDown()
        .filter { file -> file.isFile && file.extension == "kt" }
        .map { file -> file.toPath() }
        .filter { path ->
            path.isEditableLowcodeController() || path.isEditableLowcodeServiceImplementation()
        }
}

private fun legacyFeatureControllerScaffoldFiles(sourceRoot: Path): Sequence<Path> {
    if (!sourceRoot.exists()) {
        return emptySequence()
    }
    return sourceRoot.toFile().walkTopDown()
        .filter { file -> file.isFile && file.extension == "kt" }
        .map { file -> file.toPath() }
        .filter(Path::isLegacyFeatureControllerScaffold)
}

private fun Path.isLegacyFeatureControllerScaffold(): Boolean =
    exists() && readText().let { source ->
        LEGACY_CONTROLLER_EMPTY_INSTALL in source ||
            LEGACY_CONTROLLER_GET_EXAMPLE in source && LEGACY_CONTROLLER_POST_EXAMPLE in source
    }

private fun Path.isEditableLowcodeController(): Boolean =
    readText().controllerSignature().isNotEmpty()

private fun Path.isHandwrittenLowcodeController(): Boolean =
    fileName.toString().endsWith("Controller.kt") &&
        relativeToParentGeneratedDirectory().getOrNull(1) == "controller" &&
        !hasStudioGeneratedMarker() &&
        !isLegacyFeatureControllerScaffold()

private fun Path.isEditableLowcodeServiceImplementation(): Boolean =
    readText().serviceImplementationSignature().isNotEmpty()

private fun Path.relativeToParentGeneratedDirectory(): List<String> {
    val segments = map(Path::toString)
    val generatedIndex = segments.indexOf("generated")
    return if (generatedIndex < 0) emptyList() else segments.drop(generatedIndex)
}

private fun LowcodeGeneratedFile.sourcePath(sourceRoot: Path): Path =
    sourceRoot.resolve(packageName.replace('.', '/')).resolve("$fileName.$extensionName")

private fun LowcodeGeneratedFile.isEditableScaffold(): Boolean =
    kind == LowcodeGeneratedFileKind.CONTROLLER_SCAFFOLD ||
        kind == LowcodeGeneratedFileKind.SERVICE_IMPLEMENTATION_SCAFFOLD

private fun LowcodeGeneratedFile.isCompiledSource(): Boolean =
    extensionName == "kt" && !isEditableScaffold()

private const val LEGACY_CONTROLLER_EMPTY_INSTALL = "override fun install(route: Route) = Unit"
private const val LEGACY_CONTROLLER_GET_EXAMPLE = "GET 示例（需导入 get、respond）"
private const val LEGACY_CONTROLLER_POST_EXAMPLE = "POST 示例（需导入 post、receive、respond）"
private const val GENERATED_MARKER_LINE_LIMIT = 5
private const val LOWCODE_MIGRATION_LOCK_ID = 6_592_470_708_894_310_258L
private const val LOWCODE_TARGET_DB_JDBC_URL = "LOWCODE_TARGET_DB_JDBC_URL"
private const val LOWCODE_TARGET_DB_USERNAME = "LOWCODE_TARGET_DB_USERNAME"
private const val LOWCODE_TARGET_DB_PASSWORD = "LOWCODE_TARGET_DB_PASSWORD"
private const val LOWCODE_TARGET_DB_SCHEMA = "LOWCODE_TARGET_DB_SCHEMA"
private const val DEFAULT_STUDIO_SCHEMA = "code_studio"
private const val CONTRIBUTOR_MANIFEST = "src/main/resources/META-INF/code-studio/contributor.json"
private const val CONTRIBUTOR_MIGRATION_DIRECTORY = "src/main/lowcode-metadata/db/studio/migration"
private const val CONTRIBUTOR_INDEX_DIRECTORY = ".code-studio"
private const val CONTRIBUTOR_INDEX_FILE_NAME = "contributors.json"
private const val CONTRIBUTOR_INDEX_FORMAT_VERSION = 1
private val DATABASE_SCHEMA_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val PACKAGE_DECLARATION = Regex("(?m)^package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*$")
private val configurationObjectMapper = jacksonObjectMapper()
