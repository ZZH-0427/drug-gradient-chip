package com.druggradientchip.android.forward

data class ForwardChipInput(
    val position: Int,
    val chipType: String,
    val leftWidthMm: Float,
    val rightWidthMm: Float,
    val leftBendStraightDistanceMm: Float,
    val rightBendStraightDistanceMm: Float,
)

data class ForwardRequest(
    val sampleName: String,
    val chipCount: Int = 6,
    val inlet1: Float,
    val inlet2: Float,
    val chips: List<ForwardChipInput>,
)

data class ForwardResult(
    val chipCount: Int,
    val concentrations: FloatArray,
    val modelIdentifier: String,
    val latencyMs: Double? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ForwardResult) return false
        return chipCount == other.chipCount &&
            concentrations.contentEquals(other.concentrations) &&
            modelIdentifier == other.modelIdentifier &&
            latencyMs == other.latencyMs
    }

    override fun hashCode(): Int {
        var result = chipCount
        result = 31 * result + concentrations.contentHashCode()
        result = 31 * result + modelIdentifier.hashCode()
        result = 31 * result + (latencyMs?.hashCode() ?: 0)
        return result
    }
}

enum class ForwardErrorCode {
    UNSUPPORTED_CHIP_COUNT,
    INVALID_REQUEST,
    MISSING_MODEL_ASSET,
    MODEL_LOAD_FAILURE,
    TENSOR_CONSTRUCTION_FAILURE,
    EXECUTORCH_INFERENCE_FAILURE,
    UNEXPECTED_OUTPUT_LENGTH,
    UNSUPPORTED_DEVICE_RUNTIME,
}

class ForwardRuntimeException(
    val code: ForwardErrorCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
