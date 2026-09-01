package site.addzero.studio.contract.report

import kotlinx.serialization.Serializable

@Serializable
data class ReportCreateCommand(
    val reportKey: String,
    val document: ReportDocument,
) {
    init {
        requireReportKey(reportKey, "报表")
    }
}

@Serializable
data class ReportUpdateCommand(
    val expectedRevision: Long,
    val document: ReportDocument,
) {
    init {
        requireRevision(expectedRevision, "预期修订号")
    }
}

@Serializable
data class ReportPublishCommand(
    val expectedRevision: Long,
) {
    init {
        requireRevision(expectedRevision, "预期修订号")
    }
}

@Serializable
data class ReportListItemView(
    val reportKey: String,
    val revision: Long,
    val name: String,
    val description: String? = null,
    val publishedRevision: Long? = null,
) {
    init {
        requireReportView(reportKey, revision, name, description)
        publishedRevision?.let { value -> requireRevision(value, "发布修订号") }
    }
}

@Serializable
data class ReportView(
    val reportKey: String,
    val revision: Long,
    val document: ReportDocument,
    val publishedRevision: Long? = null,
) {
    init {
        requireReportKey(reportKey, "报表")
        requireRevision(revision, "报表修订号")
        publishedRevision?.let { value -> requireRevision(value, "发布修订号") }
    }
}

@Serializable
data class ReportPublicationView(
    val reportKey: String,
    val publishedRevision: Long,
    val document: ReportDocument,
) {
    init {
        requireReportKey(reportKey, "报表")
        requireRevision(publishedRevision, "发布修订号")
    }
}

@Serializable
data class PublishedReportListItemView(
    val reportKey: String,
    val publishedRevision: Long,
    val name: String,
    val description: String? = null,
) {
    init {
        requireReportView(reportKey, publishedRevision, name, description)
    }
}

@Serializable
data class PublishedReportView(
    val reportKey: String,
    val publishedRevision: Long,
    val document: ReportDocument,
) {
    init {
        requireReportKey(reportKey, "报表")
        requireRevision(publishedRevision, "发布修订号")
    }
}

@Serializable
data class ReportListPage(
    val rows: List<ReportListItemView> = emptyList(),
    val totalRowCount: Long = 0,
    val totalPageCount: Long = 0,
)

@Serializable
data class PublishedReportListPage(
    val rows: List<PublishedReportListItemView> = emptyList(),
    val totalRowCount: Long = 0,
    val totalPageCount: Long = 0,
)

private fun requireReportView(
    reportKey: String,
    revision: Long,
    name: String,
    description: String?,
) {
    requireReportKey(reportKey, "报表")
    requireRevision(revision, "报表修订号")
    require(name.isNotBlank()) {
        "报表名称不能为空"
    }
    require(description == null || description.isNotBlank()) {
        "报表说明不能是空白字符串"
    }
}

private fun requireRevision(value: Long, role: String) {
    require(value > 0) {
        "$role 必须大于 0: $value"
    }
}
