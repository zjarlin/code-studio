package site.addzero.platform.lowcode.generator

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import site.addzero.constant.compiler.LsiConstant
import site.addzero.constant.compiler.LsiConstantType
import site.addzero.constant.compiler.KotlinConstantSourceGenerator
import site.addzero.dto.compiler.LsiDtoVisibility

/**
 * 低代码编译所需的数据库连接配置。
 */
data class LowcodeMetadataDatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val schema: String? = null,
) {
    init {
        require(jdbcUrl.isNotBlank()) { "低代码元数据 JDBC URL 不能为空" }
        require(username.isNotBlank()) { "低代码元数据数据库用户名不能为空" }
        require(schema == null || DATABASE_SCHEMA_PATTERN.matches(schema)) {
            "低代码元数据数据库 Schema 不合法: $schema"
        }
    }

    companion object {
        fun fromEnvironment(environment: Map<String, String>): LowcodeMetadataDatabaseConfig =
            LowcodeMetadataDatabaseConfig(
                jdbcUrl = environment.required("CODE_STUDIO_DB_JDBC_URL"),
                username = environment.required("CODE_STUDIO_DB_USERNAME"),
                password = environment.required("CODE_STUDIO_DB_PASSWORD"),
                schema = environment["CODE_STUDIO_SCHEMA"]?.trim()?.takeIf(String::isNotEmpty),
            )

        private fun Map<String, String>.required(name: String): String =
            get(name)?.trim()?.takeIf(String::isNotEmpty)
                ?: error("低代码源码生成需要环境变量 $name")
    }
}

private val DATABASE_SCHEMA_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")

/**
 * 从数据库读取后的完整低代码编译输入。
 */
data class LowcodeMetadata(
    val models: List<LowcodeModelMeta>,
    val dtoDefinitions: List<LsiLowcodeDtoDefinition>,
    val routeBindings: List<LowcodeRouteBinding>,
    val contracts: List<LsiLowcodeContract>,
    val conventionFiles: List<LsiConventionFile> = emptyList(),
    val features: List<LsiLowcodeFeature> = emptyList(),
    val dictionaries: List<LowcodeDictionaryMeta> = emptyList(),
    val constantGroups: List<LowcodeConstantGroupMeta> = emptyList(),
) {
    init {
        require(models.map(LowcodeModelMeta::modelCode).distinct().size == models.size) {
            "低代码数据库包含重复模型编码"
        }
        require(contracts.map(LsiLowcodeContract::contractCode).distinct().size == contracts.size) {
            "低代码数据库包含重复业务契约编码"
        }
        require(conventionFiles.map { file ->
            Triple(file.contributorId, file.packageName, file.kind to file.fileCode)
        }.distinct().size == conventionFiles.size) {
            "低代码数据库包含重复约定文件编码"
        }
        require(conventionFiles.map { file ->
            Triple(file.contributorId, file.packageName, file.className)
        }.distinct().size == conventionFiles.size) {
            "低代码数据库包含重复约定文件类名"
        }
        require(dtoDefinitions.map(LsiLowcodeDtoDefinition::dtoCode).distinct().size == dtoDefinitions.size) {
            "低代码数据库包含重复 DTO 编码"
        }
        require(routeBindings.map(LowcodeRouteBinding::routeCode).distinct().size == routeBindings.size) {
            "低代码数据库包含重复路由绑定编码"
        }
        require(dictionaries.map(LowcodeDictionaryMeta::dictionaryCode).distinct().size == dictionaries.size) {
            "低代码数据库包含重复字典编码"
        }
        require(constantGroups.map(LowcodeConstantGroupMeta::groupCode).distinct().size == constantGroups.size) {
            "低代码数据库包含重复常量组编码"
        }
        requireNoGeneratedRouteConflicts()
        val modelCodes = models.map(LowcodeModelMeta::modelCode).toSet()
        val contractCodes = contracts.map(LsiLowcodeContract::contractCode).toSet()
        val dtoCodes = dtoDefinitions.map(LsiLowcodeDtoDefinition::dtoCode).toSet()
        val modelsByCode = models.associateBy(LowcodeModelMeta::modelCode)
        dictionaries.filter(LowcodeDictionaryMeta::generateEnum).forEach { dictionary ->
            val ownerModelCode = requireNotNull(dictionary.ownerModelCode?.takeIf(String::isNotBlank)) {
                "生成枚举的字典 ${dictionary.dictionaryCode} 必须配置归属模型"
            }
            require(modelsByCode.containsKey(ownerModelCode)) {
                "生成枚举的字典 ${dictionary.dictionaryCode} 引用了不存在的归属模型 $ownerModelCode"
            }
            val className = requireNotNull(dictionary.enumClassName?.takeIf(String::isNotBlank)) {
                "生成枚举的字典 ${dictionary.dictionaryCode} 必须配置枚举类名"
            }
            require(KOTLIN_TYPE_NAME_PATTERN.matches(className)) {
                "字典 ${dictionary.dictionaryCode} 的枚举类名不合法: $className"
            }
            require(dictionary.items.isNotEmpty()) {
                "生成枚举的字典 ${dictionary.dictionaryCode} 至少需要一个字典项"
            }
            require(dictionary.items.map(LowcodeDictionaryItemMeta::value).distinct().size == dictionary.items.size) {
                "字典 ${dictionary.dictionaryCode} 包含重复值"
            }
            val enumNames = dictionary.items.map { item ->
                requireNotNull(item.enumName?.takeIf(String::isNotBlank)) {
                    "生成枚举的字典 ${dictionary.dictionaryCode} 存在未配置枚举常量名的字典项"
                }
            }
            require(enumNames.all(KOTLIN_ENUM_NAME_PATTERN::matches)) {
                "字典 ${dictionary.dictionaryCode} 包含不合法的枚举常量名"
            }
            require(enumNames.distinct().size == enumNames.size) {
                "字典 ${dictionary.dictionaryCode} 包含重复枚举常量名"
            }
            if (dictionary.enumStorage == LowcodeEnumStorage.ORDINAL) {
                require(dictionary.items.all { item -> item.value.toIntOrNull()?.let { value -> value >= 0 } == true }) {
                    "ORDINAL 字典 ${dictionary.dictionaryCode} 的值必须是非负整数"
                }
            }
        }
        features.groupBy { feature -> feature.contributorId to feature.packageName }
            .values
            .forEach { definitions ->
                require(definitions.map { feature ->
                    Triple(feature.featureCode, feature.name, feature.description)
                }.distinct().size == 1) {
                    "同一源码目录的功能元数据不一致: ${definitions.first().packageName}"
                }
            }
        features.forEach { feature ->
            require(feature.modelCodes.all(modelCodes::contains)) {
                "功能目录 ${feature.featureCode} 引用了未启用或不存在的模型"
            }
            require(feature.contractCodes.all(contractCodes::contains)) {
                "功能目录 ${feature.featureCode} 引用了未启用或不存在的契约"
            }
            require(feature.dtoCodes.all(dtoCodes::contains)) {
                "功能目录 ${feature.featureCode} 引用了未启用或不存在的 DTO"
            }
            feature.modelCodes.forEach { modelCode ->
                val model = models.single { candidate -> candidate.modelCode == modelCode }
                require(model.featurePackageName == feature.packageName && model.contributorId == feature.contributorId) {
                    "模型 $modelCode 的源码归属与功能目录 ${feature.featureCode} 不一致"
                }
            }
            feature.dtoCodes.forEach { dtoCode ->
                val dto = dtoDefinitions.single { candidate -> candidate.dtoCode == dtoCode }
                require(dto.featurePackageName == feature.packageName && dto.contributorId == feature.contributorId) {
                    "DTO $dtoCode 的源码归属与功能目录 ${feature.featureCode} 不一致"
                }
            }
            feature.contractCodes.forEach { contractCode ->
                val contract = contracts.single { candidate -> candidate.contractCode == contractCode }
                require(contract.featurePackageName == feature.packageName && contract.contributorId == feature.contributorId) {
                    "契约 $contractCode 的源码归属与功能目录 ${feature.featureCode} 不一致"
                }
            }
        }
        conventionFiles.forEach { file ->
            require(features.any { feature ->
                feature.packageName == file.packageName && feature.contributorId == file.contributorId
            }) {
                "约定文件 ${file.fileCode} 没有归属到有效功能目录: ${file.packageName}"
            }
        }
        constantGroups.forEach { group ->
            require(features.any { feature ->
                feature.packageName == group.featurePackageName && feature.contributorId == group.contributorId
            }) {
                "常量组 ${group.groupCode} 没有归属到有效功能目录: ${group.featurePackageName}"
            }
            KotlinConstantSourceGenerator.validate(group.toLsiConstantGroup())
        }
    }
}

/**
 * 把已有宿主实体绑定到低代码路由配置。
 */
data class LowcodeRouteBinding(
    val routeCode: String,
    val contributorId: String,
    val route: LsiLowcodeRoute,
)

/**
 * 直接从已执行 Flyway 的数据库加载编译元数据。
 */
object LowcodeMetadataDatabaseReader {
    fun read(config: LowcodeMetadataDatabaseConfig): LowcodeMetadata =
        DriverManager.getConnection(config.jdbcUrl, config.username, config.password).use(::read)

    fun read(connection: Connection): LowcodeMetadata {
        connection.isReadOnly = true
        connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        connection.autoCommit = false
        return try {
            val modelRows = connection.readModels()
            val activeModels = modelRows.filter { model -> model.status == ENABLED_STATUS }
            val modelIds = activeModels.map(ModelRow::id).toSet()
            val fields = connection.readFields(modelIds).groupBy(LowcodeFieldMeta::modelId)
            val queries = connection.readQueries(modelIds)
            val conditions = connection.readQueryConditions(queries.map(QueryRow::id).toSet())
                .groupBy(LowcodeQueryConditionMeta::queryId)
            val relations = connection.readRelations(modelIds, modelRows.associateBy(ModelRow::id))
                .groupBy(LowcodeRelationMeta::modelId)
            val models = activeModels.map { model ->
                model.toMeta(
                    fields = fields[model.id].orEmpty(),
                    queries = queries.filter { query -> query.modelId == model.id }.map { query ->
                        query.toMeta(conditions[query.id].orEmpty())
                    },
                    relations = relations[model.id].orEmpty(),
                )
            }
            val contracts = connection.readContracts()
            val conventionFiles = connection.readConventionFiles()
            val dtoDefinitions = connection.readDtoDefinitions()
            val dictionaries = connection.readDictionaries()
            val constantGroups = connection.readConstantGroups()
            val features = connection.readFeatures()
            val reboundModels = models.rebindGeneratedTypeReferences()
            val modelRouteBindings = reboundModels.mapNotNull { model ->
                val route = model.routeConfig ?: return@mapNotNull null
                val contributorId = model.contributorId
                    ?: error("低代码模型 ${model.modelCode} 缺少 contributor")
                LowcodeRouteBinding(model.modelCode, contributorId, route)
            }
            val routeBindings = (modelRouteBindings + connection.readRouteBindings())
                .distinctBy(LowcodeRouteBinding::routeCode)
            connection.commit()
            LowcodeMetadata(
                models = reboundModels,
                dtoDefinitions = dtoDefinitions,
                routeBindings = routeBindings,
                contracts = contracts,
                conventionFiles = conventionFiles,
                features = features,
                dictionaries = dictionaries,
                constantGroups = constantGroups,
            )
        } catch (error: Exception) {
            connection.rollback()
            throw error
        }
    }

    private fun Connection.readModels(): List<ModelRow> = prepareStatement(MODELS_SQL).use { statement ->
        statement.executeQuery().use { rows ->
            rows.mapRows { row ->
                ModelRow(
                    id = row.getLong("id"),
                    modelCode = row.getString("model_code"),
                    name = row.getString("name"),
                    packageName = row.getString("package_name"),
                    className = row.getString("class_name"),
                    tableName = row.getString("table_name"),
                    kind = LowcodeModelKind.valueOf(row.getString("model_type")),
                    status = row.getInt("status"),
                    version = row.getInt("version"),
                    contributorId = row.getString("contributor_id"),
                    entityConfig = LowcodeMetadataJson.readEntityConfig(row.getString("entity_config")),
                    routeConfig = LowcodeMetadataJson.readRoute(row.getString("route_config")),
                )
            }
        }
    }

    private fun Connection.readFields(modelIds: Set<Long>): List<LowcodeFieldMeta> {
        if (modelIds.isEmpty()) {
            return emptyList()
        }
        return prepareStatement(FIELDS_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    LowcodeFieldMeta(
                        id = row.getLong("id"),
                        modelId = row.getLong("model_id"),
                        orderNo = row.getInt("order_no"),
                        fieldCode = row.getString("field_code"),
                        label = row.getString("label"),
                        kotlinType = row.getString("kotlin_type"),
                        dbColumn = row.getString("db_column"),
                        required = row.getBoolean("required"),
                        listVisible = row.getBoolean("list_visible"),
                        formVisible = row.getBoolean("form_visible"),
                        formControl = row.getString("form_control"),
                        dictCode = row.getString("dict_code"),
                        defaultValue = row.getString("default_value"),
                        remark = row.getString("remark"),
                        serialized = row.getBoolean("serialized"),
                        maxLength = row.getInt("max_length").takeUnless { row.wasNull() },
                        enumStorage = row.getString("enum_storage")?.let(LowcodeEnumStorage::valueOf),
                        key = row.getBoolean("natural_key"),
                        createWritable = row.getBoolean("create_writable"),
                        updateWritable = row.getBoolean("update_writable"),
                    )
                }
            }
        }
    }

    private fun Connection.readQueries(modelIds: Set<Long>): List<QueryRow> {
        if (modelIds.isEmpty()) {
            return emptyList()
        }
        return prepareStatement(QUERIES_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    QueryRow(
                        id = row.getLong("id"),
                        modelId = row.getLong("model_id"),
                        orderNo = row.getInt("order_no"),
                        queryCode = row.getString("query_code"),
                        label = row.getString("label"),
                        logic = LowcodeQueryLogic.valueOf(row.getString("query_logic")),
                    )
                }
            }
        }
    }

    private fun Connection.readQueryConditions(queryIds: Set<Long>): List<LowcodeQueryConditionMeta> {
        if (queryIds.isEmpty()) {
            return emptyList()
        }
        return prepareStatement(QUERY_CONDITIONS_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    LowcodeQueryConditionMeta(
                        id = row.getLong("id"),
                        queryId = row.getLong("query_id"),
                        orderNo = row.getInt("order_no"),
                        fieldCode = row.getString("field_code"),
                        operator = LowcodeQueryOperator.valueOf(row.getString("query_operator")),
                        valueType = LowcodeQueryValueType.valueOf(row.getString("value_type")),
                        paramName = row.getString("param_name"),
                    )
                }
            }
        }
    }

    private fun Connection.readRelations(
        modelIds: Set<Long>,
        models: Map<Long, ModelRow>,
    ): List<LowcodeRelationMeta> {
        if (modelIds.isEmpty()) {
            return emptyList()
        }
        return prepareStatement(RELATIONS_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    val targetModelId = row.requiredLong("target_model_id")
                    val target = models[targetModelId]
                        ?: error("低代码关联引用了未启用或不存在的目标模型: $targetModelId")
                    LowcodeRelationMeta(
                        id = row.getLong("id"),
                        modelId = row.getLong("model_id"),
                        orderNo = row.getInt("order_no"),
                        relationCode = row.getString("relation_code"),
                        label = row.getString("label"),
                        relationKind = LowcodeRelationKind.valueOf(row.getString("relation_type")),
                        targetModelId = targetModelId,
                        targetModelCode = target.modelCode,
                        targetPackageName = target.packageName,
                        targetClassName = target.className,
                        joinColumn = row.getString("join_column"),
                        mappedBy = row.getString("mapped_by"),
                        joinTable = row.getString("join_table"),
                        joinTableJoinColumn = row.getString("join_table_join_column"),
                        joinTableInverseColumn = row.getString("join_table_inverse_column"),
                        joinTableFilterColumn = row.getString("join_table_filter_column"),
                        joinTableFilterValues = LowcodeMetadataJson.readStringList(
                            row.getString("join_table_filter_values"),
                        ),
                        dissociateAction = LowcodeDissociateAction.valueOf(row.getString("dissociate_action")),
                        required = row.getBoolean("required"),
                        listVisible = row.getBoolean("list_visible"),
                        formVisible = row.getBoolean("form_visible"),
                        createWritable = row.getBoolean("create_writable"),
                        updateWritable = row.getBoolean("update_writable"),
                    )
                }
            }
        }
    }

    private fun Connection.readContracts(): List<LsiLowcodeContract> =
        prepareStatement(CONTRACTS_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    LsiLowcodeContract(
                        contractCode = row.getString("contract_code"),
                        name = row.getString("name"),
                        description = row.getString("description"),
                        packageName = row.getString("package_name"),
                        className = row.getString("class_name"),
                        path = row.getString("path"),
                        contributorId = row.getString("contributor_id"),
                        operations = LowcodeMetadataJson.readOperations(row.getString("operations")),
                        agentExposure = LowcodeMetadataJson.readAgentExposure(row.getString("agent_exposure")),
                    )
                }
            }
        }

    private fun Connection.readConventionFiles(): List<LsiConventionFile> =
        prepareStatement(CONVENTION_FILES_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    LsiConventionFile(
                        fileCode = row.getString("file_code"),
                        name = row.getString("name"),
                        className = row.getString("class_name"),
                        kind = LsiConventionFileKind.valueOf(row.getString("kind")),
                        packageName = row.getString("package_name"),
                        contributorId = row.getString("contributor_id"),
                        description = row.getString("description"),
                    )
                }
            }
        }

    private fun Connection.readDtoDefinitions(): List<LsiLowcodeDtoDefinition> =
        prepareStatement(
            DTOS_SQL.replace(
                DTO_SUPER_TYPES_PROJECTION,
                if (hasColumn("lowcode_dto", "super_types")) {
                    "dto.super_types"
                } else {
                    "'[]'::jsonb AS super_types"
                },
            ),
        ).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    LsiLowcodeDtoDefinition(
                        dtoCode = row.getString("dto_code"),
                        name = row.getString("name"),
                        packageName = row.getString("package_name"),
                        className = row.getString("class_name"),
                        kind = LowcodeDtoKind.valueOf(row.getString("kind")),
                        visibility = LsiDtoVisibility.valueOf(row.getString("visibility")),
                        contributorId = row.getString("contributor_id"),
                        status = row.getInt("status"),
                        version = row.getInt("version"),
                        description = row.getString("description"),
                        annotations = LowcodeMetadataJson.readDtoAnnotations(row.getString("annotations")),
                        superTypes = LowcodeMetadataJson.readDtoTypes(row.getString("super_types")),
                        sourceModelCode = row.getString("source_model_code"),
                        selectionMode = LowcodeDtoSelectionMode.valueOf(row.getString("selection_mode")),
                        excludedPaths = LowcodeMetadataJson.readStringList(row.getString("excluded_paths")),
                        fields = LowcodeMetadataJson.readDtoFields(row.getString("fields")),
                    )
                }
            }
        }

    private fun Connection.hasColumn(tableName: String, columnName: String): Boolean =
        metaData.getColumns(null, null, tableName, columnName).use { rows -> rows.next() }

    private fun Connection.readDictionaries(): List<LowcodeDictionaryMeta> {
        val items = prepareStatement(DICTIONARY_ITEMS_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    row.getString("dictionary_code") to LowcodeDictionaryItemMeta(
                        orderNo = row.getInt("order_no"),
                        value = row.getString("value"),
                        label = row.getString("label"),
                        enumName = row.getString("enum_name"),
                    )
                }
            }
        }.groupBy(Pair<String, LowcodeDictionaryItemMeta>::first, Pair<String, LowcodeDictionaryItemMeta>::second)
        return prepareStatement(DICTIONARIES_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    val dictionaryCode = row.getString("dictionary_code")
                    LowcodeDictionaryMeta(
                        dictionaryCode = dictionaryCode,
                        name = row.getString("name"),
                        generateEnum = row.getBoolean("generate_enum"),
                        ownerModelCode = row.getString("owner_model_code"),
                        enumClassName = row.getString("enum_class_name"),
                        enumStorage = LowcodeEnumStorage.valueOf(row.getString("enum_storage")),
                        items = items[dictionaryCode].orEmpty(),
                    )
                }
            }
        }
    }

    private fun Connection.readConstantGroups(): List<LowcodeConstantGroupMeta> {
        val constants = prepareStatement(CONSTANT_ITEMS_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    row.getString("group_code") to LsiConstant(
                        name = row.getString("constant_name"),
                        type = LsiConstantType.valueOf(row.getString("value_type")),
                        value = row.getString("constant_value"),
                        description = row.getString("description"),
                    )
                }
            }
        }.groupBy(Pair<String, LsiConstant>::first, Pair<String, LsiConstant>::second)
        return prepareStatement(CONSTANT_GROUPS_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    val groupCode = row.getString("group_code")
                    LowcodeConstantGroupMeta(
                        groupCode = groupCode,
                        featurePackageName = row.getString("feature_package_name"),
                        contributorId = row.getString("contributor_id"),
                        objectName = row.getString("object_name"),
                        description = row.getString("description"),
                        constants = constants[groupCode].orEmpty(),
                    )
                }
            }
        }
    }

    private fun Connection.readFeatures(): List<LsiLowcodeFeature> =
        prepareStatement(FEATURES_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    LsiLowcodeFeature(
                        featureCode = row.getString("feature_code"),
                        name = row.getString("name"),
                        description = row.getString("description"),
                        packageName = row.getString("package_name"),
                        contributorId = row.getString("contributor_id"),
                        modelCodes = LowcodeMetadataJson.readStringList(row.getString("model_codes")),
                        dtoCodes = LowcodeMetadataJson.readStringList(row.getString("dto_codes")),
                        contractCodes = LowcodeMetadataJson.readStringList(row.getString("contract_codes")),
                        featureId = row.getLong("feature_id"),
                        libraryId = row.getLong("library_id"),
                        parentId = row.getLong("parent_id").takeUnless { row.wasNull() },
                    )
                }
            }
        }

    private fun Connection.readRouteBindings(): List<LowcodeRouteBinding> =
        prepareStatement(ROUTE_BINDINGS_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                rows.mapRows { row ->
                    LowcodeRouteBinding(
                        routeCode = row.getString("route_code"),
                        contributorId = row.getString("contributor_id"),
                        route = requireNotNull(LowcodeMetadataJson.readRoute(row.getString("route_config"))) {
                            "低代码路由绑定 ${row.getString("route_code")} 缺少路由配置"
                        },
                    )
                }
            }
        }

    private inline fun <T> ResultSet.mapRows(transform: (ResultSet) -> T): List<T> = buildList {
        while (next()) {
            add(transform(this@mapRows))
        }
    }

    private fun ResultSet.requiredLong(column: String): Long {
        val value = getLong(column)
        check(!wasNull()) { "低代码元数据字段 $column 不能为空" }
        return value
    }
}

private fun List<LowcodeModelMeta>.rebindGeneratedTypeReferences(): List<LowcodeModelMeta> {
    val aliases = associate { model ->
        "${model.packageName}.generated.${model.className}" to model.entityQualifiedName()
    }.filter { (source, target) -> source != target }
    if (aliases.isEmpty()) {
        return this
    }
    return map { model ->
        model.copy(
            fields = model.fields.map { field -> field.copy(kotlinType = field.kotlinType.rebind(aliases)) },
            entityConfig = model.entityConfig.copy(
                superTypes = model.entityConfig.superTypes.map { type -> type.rebind(aliases) },
                inheritedProperties = model.entityConfig.inheritedProperties.map { property ->
                    property.copy(kotlinType = property.kotlinType.rebind(aliases))
                },
                formulaProperties = model.entityConfig.formulaProperties.map { property ->
                    property.copy(kotlinType = property.kotlinType.rebind(aliases))
                },
                transientProperties = model.entityConfig.transientProperties.map { property ->
                    property.copy(kotlinType = property.kotlinType.rebind(aliases))
                },
            ),
        )
    }
}

private fun String.rebind(aliases: Map<String, String>): String = aliases.entries.fold(this) { value, (source, target) ->
    value.replace(source, target)
}

fun String.belongsToFeaturePackage(featurePackageName: String): Boolean =
    this == featurePackageName || startsWith("$featurePackageName.")

private data class ModelRow(
    val id: Long,
    val modelCode: String,
    val name: String,
    val packageName: String,
    val className: String,
    val tableName: String,
    val kind: LowcodeModelKind,
    val status: Int,
    val version: Int,
    val contributorId: String?,
    val entityConfig: LsiLowcodeEntityConfig,
    val routeConfig: LsiLowcodeRoute?,
) {
    fun toMeta(
        fields: List<LowcodeFieldMeta>,
        queries: List<LowcodeQueryMeta>,
        relations: List<LowcodeRelationMeta>,
    ): LowcodeModelMeta = LowcodeModelMeta(
        id = id,
        modelCode = modelCode,
        name = name,
        packageName = packageName,
        className = className,
        tableName = tableName,
        kind = kind,
        status = status,
        version = version,
        contributorId = contributorId,
        entityConfig = entityConfig,
        routeConfig = routeConfig,
        fields = fields,
        queries = queries,
        relations = relations,
    )
}

private data class QueryRow(
    val id: Long,
    val modelId: Long,
    val orderNo: Int,
    val queryCode: String,
    val label: String,
    val logic: LowcodeQueryLogic,
) {
    fun toMeta(items: List<LowcodeQueryConditionMeta>): LowcodeQueryMeta = LowcodeQueryMeta(
        id = id,
        modelId = modelId,
        orderNo = orderNo,
        queryCode = queryCode,
        label = label,
        logic = logic,
        items = items,
    )
}

private const val MODELS_SQL = """
    SELECT model.id, model.model_code, model.name,
           (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
           model.class_name, model.table_name, model.model_type, model.status, model.version,
           definition.code AS contributor_id,
           model.entity_config,
           CASE WHEN model.route_config IS NULL THEN NULL ELSE model.route_config || jsonb_build_object(
               'packageName', (library.spec ->> 'packagePrefix') || '.' || feature.feature_code,
               'featurePackageName', (library.spec ->> 'packagePrefix') || '.' || feature.feature_code,
               'qualifiedName', COALESCE(
                   NULLIF(model.entity_config ->> 'sourceQualifiedName', ''),
                   (library.spec ->> 'packagePrefix') || '.' || feature.feature_code ||
                       '.generated.entity.' || model.class_name
               )
           ) END AS route_config
    FROM lowcode_model model
    INNER JOIN library_feature feature ON feature.id = model.feature_id
    INNER JOIN library_definition library ON library.id = feature.library_id
    INNER JOIN lowcode_definition definition ON definition.id = library.id
    ORDER BY model.model_code
"""

private const val FIELDS_SQL = """
    SELECT field.id, field.model_id, field.order_no, field.field_code, field.label,
           field.kotlin_type, field.db_column, field.required, field.list_visible,
           field.form_visible, field.form_control, field.dict_code, field.default_value, field.remark,
           field.serialized, field.max_length, field.enum_storage, field.natural_key,
           field.create_writable, field.update_writable
    FROM lowcode_field field
    INNER JOIN lowcode_model model ON model.id = field.model_id AND model.status = 1
    ORDER BY field.model_id, field.order_no, field.id
"""

private const val QUERIES_SQL = """
    SELECT query.id, query.model_id, query.order_no, query.query_code, query.label, query.query_logic
    FROM lowcode_query query
    INNER JOIN lowcode_model model ON model.id = query.model_id AND model.status = 1
    ORDER BY query.model_id, query.order_no, query.id
"""

private const val QUERY_CONDITIONS_SQL = """
    SELECT condition.id, condition.query_id, condition.order_no, condition.field_code,
           condition.query_operator, condition.value_type, condition.param_name
    FROM lowcode_query_condition condition
    INNER JOIN lowcode_query query ON query.id = condition.query_id
    INNER JOIN lowcode_model model ON model.id = query.model_id AND model.status = 1
    ORDER BY condition.query_id, condition.order_no, condition.id
"""

private const val RELATIONS_SQL = """
    SELECT relation.id, relation.model_id, relation.order_no, relation.relation_code, relation.label,
           relation.relation_type, relation.target_model_id, relation.join_column, relation.mapped_by,
           relation.join_table, relation.join_table_join_column, relation.join_table_inverse_column,
           relation.join_table_filter_column, relation.join_table_filter_values,
           relation.dissociate_action,
           relation.required, relation.list_visible, relation.form_visible,
           relation.create_writable, relation.update_writable
    FROM lowcode_relation relation
    INNER JOIN lowcode_model model ON model.id = relation.model_id AND model.status = 1
    ORDER BY relation.model_id, relation.order_no, relation.id
"""

private const val CONTRACTS_SQL = """
    SELECT contract.contract_code, contract.name, contract.description,
           (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
           contract.class_name, contract.path,
           definition.code AS contributor_id,
           contract.operations, contract.agent_exposure
    FROM lowcode_api_contract contract
    INNER JOIN library_feature feature ON feature.id = contract.feature_id
    INNER JOIN library_definition library ON library.id = feature.library_id
    INNER JOIN lowcode_definition definition ON definition.id = library.id
    WHERE contract.status = 1
    ORDER BY contract.contract_code
"""

private const val CONVENTION_FILES_SQL = """
    SELECT file.file_code, file.name, file.class_name, file.kind, file.description,
           (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
           definition.code AS contributor_id
    FROM convention_file file
    INNER JOIN library_feature feature ON feature.id = file.feature_id
    INNER JOIN library_definition library ON library.id = feature.library_id
    INNER JOIN lowcode_definition definition ON definition.id = library.id
    WHERE file.status = 1
    ORDER BY definition.code, feature.feature_code, file.kind, file.file_code
"""

private const val DTOS_SQL = """
    SELECT dto.dto_code, dto.name,
           (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
           dto.class_name, dto.kind, dto.visibility,
           definition.code AS contributor_id,
           dto.status, dto.version, dto.description,
           dto.selection_mode, dto.excluded_paths, dto.fields, dto.annotations, __DTO_SUPER_TYPES__,
           model.model_code AS source_model_code
    FROM lowcode_dto dto
    INNER JOIN library_feature feature ON feature.id = dto.feature_id
    INNER JOIN library_definition library ON library.id = feature.library_id
    INNER JOIN lowcode_definition definition ON definition.id = library.id
    LEFT JOIN lowcode_model model ON model.id = dto.source_model_id
    WHERE dto.status = 1
    ORDER BY dto.dto_code
"""

private const val DICTIONARIES_SQL = """
    SELECT dictionary.dictionary_code, dictionary.name, dictionary.generate_enum,
           model.model_code AS owner_model_code, dictionary.enum_class_name, dictionary.enum_storage
    FROM lowcode_dictionary dictionary
    LEFT JOIN lowcode_model model ON model.id = dictionary.owner_model_id
    WHERE dictionary.status = 1
    ORDER BY dictionary.dictionary_code
"""

private const val DICTIONARY_ITEMS_SQL = """
    SELECT dictionary.dictionary_code, item.order_no, item.value, item.label, item.enum_name
    FROM lowcode_dictionary_item item
    INNER JOIN lowcode_dictionary dictionary ON dictionary.id = item.dictionary_id
    WHERE dictionary.status = 1 AND item.status = 1
    ORDER BY dictionary.dictionary_code, item.order_no, item.id
"""

private const val CONSTANT_GROUPS_SQL = """
    SELECT constant_group.group_code,
           (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS feature_package_name,
           definition.code AS contributor_id,
           constant_group.object_name, constant_group.description
    FROM lowcode_constant_group constant_group
    INNER JOIN library_feature feature ON feature.id = constant_group.feature_id
    INNER JOIN library_definition library ON library.id = feature.library_id
    INNER JOIN lowcode_definition definition ON definition.id = library.id
    WHERE constant_group.status = 1
    ORDER BY feature_package_name, constant_group.object_name, constant_group.group_code
"""

private const val CONSTANT_ITEMS_SQL = """
    SELECT constant_group.group_code, item.constant_name, item.value_type,
           item.constant_value, item.description
    FROM lowcode_constant_item item
    INNER JOIN lowcode_constant_group constant_group ON constant_group.id = item.constant_group_id
    WHERE constant_group.status = 1 AND item.status = 1
    ORDER BY constant_group.group_code, item.order_no, item.id
"""

private const val ROUTE_BINDINGS_SQL = """
    SELECT route_code, contributor_id, route_config
    FROM lowcode_route_binding
    WHERE status = 1
    ORDER BY route_code
"""

private const val FEATURES_SQL = """
    SELECT feature.id AS feature_id, feature.library_id, feature.parent_id,
           feature.feature_code, feature.name, feature.description,
           (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
           definition.code AS contributor_id,
           COALESCE((
               SELECT jsonb_agg(model.model_code ORDER BY model.model_code)
               FROM lowcode_model model
               WHERE model.feature_id = feature.id AND model.status = 1
           ), '[]'::JSONB) AS model_codes,
           COALESCE((
               SELECT jsonb_agg(dto.dto_code ORDER BY dto.dto_code)
               FROM lowcode_dto dto
               WHERE dto.feature_id = feature.id AND dto.status = 1
           ), '[]'::JSONB) AS dto_codes,
           COALESCE((
               SELECT jsonb_agg(contract.contract_code ORDER BY contract.contract_code)
               FROM lowcode_api_contract contract
               WHERE contract.feature_id = feature.id AND contract.status = 1
           ), '[]'::JSONB) AS contract_codes
    FROM library_feature feature
    INNER JOIN library_definition library ON library.id = feature.library_id
    INNER JOIN lowcode_definition definition ON definition.id = library.id
    WHERE definition.status = 1 AND definition.definition_type = 'LIBRARY'
    ORDER BY definition.code, feature.feature_code
"""

private const val ENABLED_STATUS = 1
private const val DTO_SUPER_TYPES_PROJECTION = "__DTO_SUPER_TYPES__"
private val KOTLIN_TYPE_NAME_PATTERN = Regex("[A-Z][A-Za-z0-9_]*")
private val KOTLIN_ENUM_NAME_PATTERN = Regex("[A-Z][A-Z0-9_]*")
