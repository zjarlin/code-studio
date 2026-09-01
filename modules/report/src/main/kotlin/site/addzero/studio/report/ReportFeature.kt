package site.addzero.studio.report

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import site.addzero.studio.report.generated.controller.ReportController
import site.addzero.studio.report.generated.controller.ReportWebController
import site.addzero.studio.report.generated.controller.SpreadsheetTemplateController
import site.addzero.studio.server.StudioFeature
import site.addzero.studio.server.StudioFeatureContext
import site.addzero.studio.server.StudioFeatureContribution

@Module
@Configuration
@ComponentScan("site.addzero.studio.report")
class ReportKoinModule

/** 将报表 JAR 的路由、接口和迁移装配为一个可选 Studio 功能。 */
@Single
class ReportFeature : StudioFeature {
    override fun contribute(context: StudioFeatureContext): StudioFeatureContribution {
        val reportController = ReportController(context.dataSource, context.schema)
        val spreadsheetController = SpreadsheetTemplateController(context.dataSource, context.schema)
        val migration = ReportSchemaMigration(context.dataSource, context.classLoader, context.schema)
        return StudioFeatureContribution(
            rootControllers = listOf(ReportWebController()),
            consoleApiControllers = listOf(reportController, spreadsheetController),
            schemaMigrations = listOf(migration),
        )
    }
}
