package com.druggradientchip.android.forward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ForwardRuntimeRouterTest {
    private val router = ForwardRuntimeRouter()

    @Test
    fun routesSupportedChipCountsToExpectedArtifacts() {
        assertEquals("dynamic_forward_n4.pte", router.route(4).filename)
        assertEquals("dynamic_forward_n5.pte", router.route(5).filename)
        assertEquals("fixed_six_tensor_only.pte", router.route(6).filename)
        assertEquals("dynamic_forward_n7.pte", router.route(7).filename)
        assertEquals("dynamic_forward_n8.pte", router.route(8).filename)
        assertEquals("dynamic_forward_n9.pte", router.route(9).filename)
        assertEquals("dynamic_forward_n10.pte", router.route(10).filename)
    }

    @Test
    fun rejectsUnsupportedChipCounts() {
        assertEquals(ForwardErrorCode.UNSUPPORTED_CHIP_COUNT, assertThrows(ForwardRuntimeException::class.java) { router.route(3) }.code)
        assertEquals(ForwardErrorCode.UNSUPPORTED_CHIP_COUNT, assertThrows(ForwardRuntimeException::class.java) { router.route(11) }.code)
    }

    @Test
    fun manifestDocumentsCheckpointAndExecuTorchIdentity() {
        ForwardModelManifest.artifacts.values.forEach { artifact ->
            assertEquals(ForwardModelManifest.CHECKPOINT_SHA256, artifact.sourceCheckpointSha256)
            assertEquals("1.4.0", artifact.executorchVersion)
        }
    }
}
