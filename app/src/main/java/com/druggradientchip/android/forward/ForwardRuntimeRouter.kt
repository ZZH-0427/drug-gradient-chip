package com.druggradientchip.android.forward

class ForwardRuntimeRouter(
    private val artifacts: Map<Int, ForwardModelArtifact> = ForwardModelManifest.artifacts,
) {
    fun route(chipCount: Int): ForwardModelArtifact =
        artifacts[chipCount]
            ?: throw ForwardRuntimeException(
                ForwardErrorCode.UNSUPPORTED_CHIP_COUNT,
                "Unsupported chip_count: $chipCount",
            )
}
