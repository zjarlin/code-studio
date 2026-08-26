package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LowcodeRuntimeContractMigrationGeneratorTest {
    @Test
    fun `generates deterministic module runtime contract snapshot`() {
        val route = LsiLowcodeRoute(
            packageName = "example.alarm.generated.entity",
            qualifiedName = "example.alarm.generated.entity.FaultAlarm",
            className = "FaultAlarm",
            modelCode = "faultAlarm",
            description = "故障告警",
            path = "/fault-alarm",
            enabledOperations = setOf("PAGE", "GET"),
            properties = listOf(
                LsiLowcodeProperty(
                    name = "id",
                    type = "integer",
                    format = "int64",
                    required = true,
                    arrayItemType = null,
                    description = "编号",
                ),
            ),
        )
        val contract = LsiLowcodeContract(
            contractCode = "alarmStats",
            name = "告警统计",
            packageName = "example.alarm",
            className = "AlarmStatsService",
            path = "/fault-alarm/statistics",
            contributorId = "example.example-alarm",
        )

        val first = LowcodeRuntimeContractMigrationGenerator.generate(
            contributorId = "example.example-alarm",
            routes = listOf(route),
            contracts = listOf(contract),
        )
        val second = LowcodeRuntimeContractMigrationGenerator.generate(
            contributorId = "example.example-alarm",
            routes = listOf(route),
            contracts = listOf(contract),
        )
        val snapshot = LowcodeRuntimeContractMigrationParser.parse(first.content)

        assertEquals(first.content, second.content)
        assertEquals(LowcodeGeneratedFileKind.RUNTIME_METADATA, first.kind)
        assertEquals("R__lowcode_runtime_contract_example_example_alarm", first.fileName)
        assertTrue(first.content.contains("'MODEL:faultAlarm'"))
        assertTrue(first.content.contains("'CONTRACT:alarmStats'"))
        assertTrue(first.content.contains("\"path\":\"/fault-alarm\""))
        assertTrue(first.content.contains("\"contractCode\":\"alarmStats\""))
        assertTrue(first.content.contains("\$lowcode\$ {"))
        assertTrue(first.content.contains("-- 运行时低代码契约快照格式: 1"))
        assertTrue(first.content.contains("IF to_regclass('lowcode_runtime_contract') IS NULL THEN"))
        assertTrue(first.content.contains("contributor_id"))
        assertFalse(first.content.contains("target_module"))
        assertFalse(first.content.contains("public.lowcode_runtime_contract"))
        assertTrue(first.content.contains("DO \$runtime_contract\$"))
        assertFalse(first.content.contains("\$lowcode\${"))
        assertEquals("/fault-alarm", snapshot.routes.getValue("faultAlarm").path)
        assertEquals("告警统计", snapshot.contracts.getValue("alarmStats").name)
        assertFalse(first.content.contains("\"generated\":"))
        assertFalse(first.content.contains("\"schemaName\":"))
        assertFalse(first.content.contains("LowcodeRestContract("))
    }
}
