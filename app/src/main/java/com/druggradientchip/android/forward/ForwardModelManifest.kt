package com.druggradientchip.android.forward

data class ForwardModelArtifact(
    val chipCount: Int,
    val filename: String,
    val family: String,
    val sourceCheckpointSha256: String,
    val executorchVersion: String,
)

object ForwardModelManifest {
    const val CHECKPOINT_SHA256 = "f6f80287d3b04145daf01c5267f7b304cca88bd2331d380063f49d43b6ead0cc"
    const val EXECUTORCH_VERSION = "1.4.0"

    val artifacts: Map<Int, ForwardModelArtifact> = listOf(
        ForwardModelArtifact(4, "dynamic_forward_n4.pte", "dynamic-zero-shot", CHECKPOINT_SHA256, EXECUTORCH_VERSION),
        ForwardModelArtifact(5, "dynamic_forward_n5.pte", "dynamic-zero-shot", CHECKPOINT_SHA256, EXECUTORCH_VERSION),
        ForwardModelArtifact(6, "fixed_six_tensor_only.pte", "fixed-six", CHECKPOINT_SHA256, EXECUTORCH_VERSION),
        ForwardModelArtifact(7, "dynamic_forward_n7.pte", "dynamic-zero-shot", CHECKPOINT_SHA256, EXECUTORCH_VERSION),
        ForwardModelArtifact(8, "dynamic_forward_n8.pte", "dynamic-zero-shot", CHECKPOINT_SHA256, EXECUTORCH_VERSION),
        ForwardModelArtifact(9, "dynamic_forward_n9.pte", "dynamic-zero-shot", CHECKPOINT_SHA256, EXECUTORCH_VERSION),
        ForwardModelArtifact(10, "dynamic_forward_n10.pte", "dynamic-zero-shot", CHECKPOINT_SHA256, EXECUTORCH_VERSION),
    ).associateBy { it.chipCount }
}
