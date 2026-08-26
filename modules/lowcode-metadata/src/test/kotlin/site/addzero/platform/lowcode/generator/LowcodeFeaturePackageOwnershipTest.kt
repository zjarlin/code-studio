package site.addzero.platform.lowcode.generator

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LowcodeFeaturePackageSelectionTest {
    @Test
    fun `feature package owns its package subtree only`() {
        assertTrue("example.identity".belongsToFeaturePackage("example.identity"))
        assertTrue("example.identity.member".belongsToFeaturePackage("example.identity"))
        assertFalse("example.identities".belongsToFeaturePackage("example.identity"))
        assertFalse("example.catalog".belongsToFeaturePackage("example.identity"))
    }
}
