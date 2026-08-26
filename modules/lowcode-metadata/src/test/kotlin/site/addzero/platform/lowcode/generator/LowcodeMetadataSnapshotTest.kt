package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LowcodeMetadataSnapshotTest {
    @Test
    fun `snapshot is deterministic and round trips`() {
        val metadata = LowcodeMetadata(
            models = emptyList(),
            dtoDefinitions = emptyList(),
            routeBindings = emptyList(),
            contracts = emptyList(),
        )

        val snapshot = LowcodeMetadataSnapshot(
            contributorId = "example.library",
            contributorIds = listOf("example.foundation", "example.library"),
            metadata = metadata,
        )
        val encoded = LowcodeMetadataSnapshots.encode(snapshot)

        assertEquals(encoded, LowcodeMetadataSnapshots.encode(LowcodeMetadataSnapshots.decode(encoded)))
        assertEquals(snapshot, LowcodeMetadataSnapshots.decode(encoded))
        assertEquals('\n', encoded.last())
    }

    @Test
    fun `snapshot canonicalizes contributor and feature order`() {
        val metadata = LowcodeMetadata(
            models = emptyList(),
            dtoDefinitions = emptyList(),
            routeBindings = emptyList(),
            contracts = emptyList(),
            features = listOf(
                feature("z", "example.z", "example.library"),
                feature("a", "example.a", "example.foundation"),
            ),
        )
        val snapshot = LowcodeMetadataSnapshot(
            contributorId = "example.library",
            contributorIds = listOf("example.library", "example.foundation"),
            metadata = metadata,
        )

        val encoded = LowcodeMetadataSnapshots.encode(snapshot)

        val contributorIds = encoded.substringAfter("\"contributorIds\"").substringBefore("\"metadata\"")
        assertTrue(contributorIds.indexOf("example.foundation") < contributorIds.indexOf("example.library"))
        assertTrue(encoded.indexOf("\"featureCode\" : \"a\"") < encoded.indexOf("\"featureCode\" : \"z\""))
    }

    @Test
    fun `snapshot keeps only the requested contributor closure`() {
        val metadata = LowcodeMetadata(
            models = emptyList(),
            dtoDefinitions = emptyList(),
            routeBindings = emptyList(),
            contracts = emptyList(),
            features = listOf(
                feature("application", "example.application", "example.application"),
                feature("foundation", "example.foundation", "example.foundation"),
                feature("unrelated", "example.unrelated", "example.unrelated"),
            ),
        )

        val restricted = metadata.restrictToContributors(
            setOf("example.application", "example.foundation"),
        )

        assertEquals(
            setOf("example.application", "example.foundation"),
            restricted.features.map(LsiLowcodeFeature::contributorId).toSet(),
        )
    }

    private fun feature(code: String, packageName: String, contributorId: String) = LsiLowcodeFeature(
        featureCode = code,
        name = code,
        packageName = packageName,
        contributorId = contributorId,
    )
}
