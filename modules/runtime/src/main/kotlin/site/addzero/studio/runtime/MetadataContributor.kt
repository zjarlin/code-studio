package site.addzero.studio.runtime

const val METADATA_CONTRIBUTOR_RESOURCE: String = "META-INF/code-studio/contributor.json"
const val METADATA_SNAPSHOT_RESOURCE_DIRECTORY: String = "META-INF/code-studio/snapshots"
const val METADATA_CONTRIBUTOR_FORMAT_VERSION: Int = 1
private val METADATA_CONTRIBUTOR_ID = Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")

fun metadataSnapshotResource(contributorId: String): String {
    require(METADATA_CONTRIBUTOR_ID.matches(contributorId)) {
        "元数据贡献 id 必须以小写字母开头，只允许小写字母、数字、- 和 .: $contributorId"
    }
    return "$METADATA_SNAPSHOT_RESOURCE_DIRECTORY/$contributorId.json"
}

/** 一个应用或库随 JAR 发布的元数据贡献清单。 */
data class MetadataContributor(
    val formatVersion: Int,
    val id: String,
    val migrationLocation: String,
    val requires: List<String> = emptyList(),
) {
    init {
        require(formatVersion == METADATA_CONTRIBUTOR_FORMAT_VERSION) {
            "不支持的元数据贡献格式版本: $formatVersion"
        }
        require(id.isNotBlank()) {
            "元数据贡献 id 不能为空"
        }
        require(METADATA_CONTRIBUTOR_ID.matches(id)) {
            "元数据贡献 id 必须以小写字母开头，只允许小写字母、数字、- 和 .: $id"
        }
        val expectedMigrationLocation = "classpath:db/studio/metadata/$id"
        require(migrationLocation == expectedMigrationLocation) {
            "元数据贡献迁移位置必须为 $expectedMigrationLocation"
        }
        require(requires.all(METADATA_CONTRIBUTOR_ID::matches)) {
            "元数据贡献依赖必须是稳定的小写标识: $id"
        }
        require(requires.distinct().size == requires.size) {
            "元数据贡献依赖不能重复: $id"
        }
        require(id !in requires) {
            "元数据贡献不能依赖自身: $id"
        }
    }
}
