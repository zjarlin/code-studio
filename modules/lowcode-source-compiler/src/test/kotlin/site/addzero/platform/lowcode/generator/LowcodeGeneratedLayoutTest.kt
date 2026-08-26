package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LowcodeGeneratedLayoutTest {
    @Test
    fun `places generated resources below the feature directory in source`() {
        val layout = LowcodeGeneratedLayout("example.feature.stats")

        assertEquals("example.feature.stats.generated", layout.generatedFeaturePackage)
        assertEquals(
            "example.feature.stats.generated.controller",
            layout.packageName(LowcodeGeneratedResourceKind.CONTROLLER),
        )
        assertEquals(
            "example.feature.stats.generated.service",
            layout.packageName(LowcodeGeneratedResourceKind.SERVICE),
        )
        assertEquals(
            "example.feature.stats.generated.dto",
            layout.packageName(LowcodeGeneratedResourceKind.DTO),
        )
        assertEquals(
            "example.feature.stats.generated.entity",
            layout.packageName(LowcodeGeneratedResourceKind.ENTITY),
        )
        assertEquals(
            "example.feature.stats.generated.enums",
            layout.packageName(LowcodeGeneratedResourceKind.ENUMS),
        )
        assertEquals(
            "src/main/kotlin/example/feature/stats/generated/entity/ExampleStats.kt",
            layout.relativeSourcePath(LowcodeGeneratedResourceKind.ENTITY, "ExampleStats"),
        )
        assertEquals(
            "src/main/kotlin/example/feature/stats/README.md",
            layout.relativeFeatureFilePath("README.md"),
        )
        assertEquals(
            "example.feature.stats.controller",
            layout.scaffoldPackageName(LowcodeScaffoldResourceKind.CONTROLLER),
        )
        assertEquals(
            "src/main/kotlin/example/feature/stats/service/impl/StatsServiceImpl.kt",
            layout.relativeScaffoldSourcePath(
                LowcodeScaffoldResourceKind.SERVICE_IMPLEMENTATION,
                "StatsServiceImpl",
            ),
        )
    }
}
