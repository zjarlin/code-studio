package site.addzero.studio.report.internal

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import site.addzero.studio.metadata.withPostgresFixture
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportStoreTest {
    @Test
    fun `草稿 CAS 发布快照和撤回保持单表状态语义`(@TempDir resources: Path) {
        withPostgresFixture(resources) { fixture ->
            runBlocking {
                val store = ReportStore(fixture.dataSource, fixture.schema)
                val created = store.create("sales-overview", "{\"title\":\"draft-1\"}")
                assertEquals(1, created.revision)

                val updated = store.update("sales-overview", 1, "{\"title\":\"draft-2\"}")
                assertEquals(2, updated.revision)

                val stale = runCatching {
                    store.update("sales-overview", 1, "{\"title\":\"stale\"}")
                }.exceptionOrNull()
                assertIs<ReportRequestException>(stale, stale?.stackTraceToString())
                assertEquals(409, stale.status.value)

                val published = store.publish("sales-overview", 2, "{\"title\":\"published-2\"}")
                val repeated = store.publish("sales-overview", 2, "{\"title\":\"ignored\"}")
                assertEquals(2, published.publishedRevision)
                assertEquals(published.publishedDocument, repeated.publishedDocument)

                val edited = store.update("sales-overview", 2, "{\"title\":\"draft-3\"}")
                assertEquals(3, edited.revision)
                assertEquals(2, edited.publishedRevision)
                assertTrue(edited.publishedDocument.orEmpty().contains("published-2"))

                assertEquals(1, store.publishedPage(1, 20).totalRowCount)
                assertEquals(true, store.withdraw("sales-overview"))
                assertEquals(true, store.withdraw("sales-overview"))
                assertNull(store.detail("sales-overview").publishedRevision)
                assertEquals(0, store.publishedPage(1, 20).totalRowCount)
            }
        }
    }
}
