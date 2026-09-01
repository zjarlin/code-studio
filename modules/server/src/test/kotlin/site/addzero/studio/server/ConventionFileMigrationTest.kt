package site.addzero.studio.server

import kotlin.test.Test
import kotlin.test.assertTrue

class ConventionFileMigrationTest {
    @Test
    fun `核心迁移先创建约定文件目录`() {
        val resource = requireNotNull(
            javaClass.classLoader.getResource("db/studio/core/V4__create_convention_file_catalog.sql"),
        )
        val sql = resource.readText()

        assertTrue(sql.contains("CREATE TABLE \${studioSchema}.convention_file"))
        assertTrue(sql.contains("REFERENCES \${studioSchema}.library_feature"))
        assertTrue(sql.contains("'SERVICE', 'SCHEDULED_JOB'"))
    }
}
