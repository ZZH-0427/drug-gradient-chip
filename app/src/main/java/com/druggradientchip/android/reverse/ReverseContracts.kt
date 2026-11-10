package com.druggradientchip.android.reverse

import java.util.concurrent.atomic.AtomicBoolean

const val REVERSE_CHIP_COUNT = 6
const val REVERSE_VARIABLE_COUNT = 12

enum class HighConcentrationInlet(val inlet1: Float, val inlet2: Float) {
    INLET_1(1.0f, 0.0f),
    INLET_2(0.0f, 1.0f),
}

data class ReverseRequest(
    val selectedHighInlet: HighConcentrationInlet,
    val targetConcentrations: FloatArray,
) {
    override fun equals(other: Any?): Boolean = other is ReverseRequest &&
        selectedHighInlet == other.selectedHighInlet &&
        targetConcentrations.contentEquals(other.targetConcentrations)

    override fun hashCode(): Int = 31 * selectedHighInlet.hashCode() + targetConcentrations.contentHashCode()
}

data class ReverseChipCandidate(
    val position: Int,
    val chipType: String,
    val leftWidthMm: Float,
    val rightWidthMm: Float,
    val leftBendClass: Int,
    val rightBendClass: Int,
    val leftDistanceMm: Float,
    val rightDistanceMm: Float,
)

data class ReverseCandidate(val chips: List<ReverseChipCandidate>)

data class ReverseMetrics(
    val mae: Double,
    val rmse: Double,
    val maxAe: Double,
    val spearman: Double,
    val qualityPassed: Boolean,
)

data class ReverseSearchConfigSnapshot(
    val restartCount: Int,
    val widthMinMm: Float,
    val widthMaxMm: Float,
    val bendClassMin: Int,
    val bendClassMax: Int,
    val widthStepsMm: List<Float>,
    val maeWeight: Double,
    val seed: Int,
    val deterministic: Boolean,
    val evaluationBudget: Int,
)

data class ReverseResult(
    val selectedHighInlet: HighConcentrationInlet,
    val chipCount: Int,
    val candidate: ReverseCandidate,
    val recomputedConcentrations: FloatArray,
    val perPositionErrors: FloatArray,
    val metrics: ReverseMetrics,
    val evaluationCount: Int,
    val runtimeMs: Double,
    val searchConfig: ReverseSearchConfigSnapshot,
    val warnings: List<String>,
    val modelIdentifier: String,
)

enum class ReverseProgressState { SEARCHING, VERIFYING, COMPLETED }

data class ReverseProgress(
    val completedEvaluations: Int,
    val totalEvaluationBudget: Int,
    val fraction: Double,
    val restartIndex: Int,
    val state: ReverseProgressState,
)

enum class ReverseErrorCode {
    INVALID_REQUEST,
    INVALID_TARGET,
    INVALID_INLET,
    MODEL_ASSET_MISSING,
    MODEL_LOAD_FAILURE,
    TENSOR_BUILD_FAILURE,
    INFERENCE_FAILURE,
    SEARCH_FAILURE,
    CANCELED,
    UNEXPECTED_OUTPUT,
    UNSUPPORTED_DEVICE_RUNTIME,
}

class ReverseRuntimeException(
    val code: ReverseErrorCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class ReverseCancellationToken {
    private val canceled = AtomicBoolean(false)
    fun cancel() = canceled.set(true)
    fun throwIfCanceled() {
        if (canceled.get()) throw ReverseRuntimeException(ReverseErrorCode.CANCELED, "Reverse prediction was canceled.")
    }
}

object ReverseRequestValidator {
    fun validate(request: ReverseRequest) {
        if (request.targetConcentrations.size != REVERSE_CHIP_COUNT) {
            throw ReverseRuntimeException(ReverseErrorCode.INVALID_TARGET, "Reverse prediction requires exactly six targets.")
        }
        if (!request.targetConcentrations.all(Float::isFinite)) {
            throw ReverseRuntimeException(ReverseErrorCode.INVALID_TARGET, "Target concentrations must be finite.")
        }
    }

    fun highInlet(inlet1: Float, inlet2: Float): HighConcentrationInlet = when {
        inlet1 == 1.0f && inlet2 == 0.0f -> HighConcentrationInlet.INLET_1
        inlet1 == 0.0f && inlet2 == 1.0f -> HighConcentrationInlet.INLET_2
        else -> throw ReverseRuntimeException(ReverseErrorCode.INVALID_INLET, "High inlet must be exactly Inlet1 or Inlet2.")
    }
}
