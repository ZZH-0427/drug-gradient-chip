package com.druggradientchip.android.reverse

import com.druggradientchip.android.forward.ForwardChipInput
import com.druggradientchip.android.forward.ForwardErrorCode
import com.druggradientchip.android.forward.ForwardModelManifest
import com.druggradientchip.android.forward.ForwardPredictor
import com.druggradientchip.android.forward.ForwardRequest
import com.druggradientchip.android.forward.ForwardRuntimeException

internal class FixedSixReverseEvaluator(
    private val forwardPredictor: ForwardPredictor,
) : ReverseEvaluator {
    var evaluationCount: Int = 0
        private set
    var totalEvaluationMs: Double = 0.0
        private set
    var firstEvaluationMs: Double? = null
        private set

    override fun predict(candidate: SearchCandidate, inlet: HighConcentrationInlet): FloatArray {
        candidate.validate()
        val request = ForwardRequest(
            sampleName = "android-reverse",
            chipCount = REVERSE_CHIP_COUNT,
            inlet1 = inlet.inlet1,
            inlet2 = inlet.inlet2,
            chips = (0 until REVERSE_CHIP_COUNT).map { chip ->
                val left = chip * 2
                val right = left + 1
                ForwardChipInput(
                    position = chip + 1,
                    chipType = "${candidate.bends[left]}to${candidate.bends[right]}",
                    leftWidthMm = candidate.widths[left],
                    rightWidthMm = candidate.widths[right],
                    leftBendStraightDistanceMm = distance(candidate.bends[left]),
                    rightBendStraightDistanceMm = distance(candidate.bends[right]),
                )
            },
        )
        val started = System.nanoTime()
        return try {
            forwardPredictor.predict(request).also { result ->
                if (result.modelIdentifier != MODEL_IDENTIFIER || result.concentrations.size != REVERSE_CHIP_COUNT) {
                    throw ReverseRuntimeException(
                        ReverseErrorCode.UNEXPECTED_OUTPUT,
                        "Reverse evaluator received an unexpected fixed-six result.",
                    )
                }
            }.concentrations.copyOf()
        } catch (error: ReverseRuntimeException) {
            throw error
        } catch (error: ForwardRuntimeException) {
            throw mapForwardError(error)
        } catch (error: Throwable) {
            throw ReverseRuntimeException(ReverseErrorCode.INFERENCE_FAILURE, "Fixed-six inference failed.", error)
        } finally {
            val elapsed = (System.nanoTime() - started).toDouble() / 1_000_000.0
            if (firstEvaluationMs == null) firstEvaluationMs = elapsed
            totalEvaluationMs += elapsed
            evaluationCount += 1
        }
    }

    private fun mapForwardError(error: ForwardRuntimeException): ReverseRuntimeException {
        val code = when (error.code) {
            ForwardErrorCode.MISSING_MODEL_ASSET -> ReverseErrorCode.MODEL_ASSET_MISSING
            ForwardErrorCode.MODEL_LOAD_FAILURE -> ReverseErrorCode.MODEL_LOAD_FAILURE
            ForwardErrorCode.TENSOR_CONSTRUCTION_FAILURE -> ReverseErrorCode.TENSOR_BUILD_FAILURE
            ForwardErrorCode.EXECUTORCH_INFERENCE_FAILURE -> ReverseErrorCode.INFERENCE_FAILURE
            ForwardErrorCode.UNEXPECTED_OUTPUT_LENGTH -> ReverseErrorCode.UNEXPECTED_OUTPUT
            ForwardErrorCode.UNSUPPORTED_DEVICE_RUNTIME -> ReverseErrorCode.UNSUPPORTED_DEVICE_RUNTIME
            ForwardErrorCode.INVALID_REQUEST,
            ForwardErrorCode.UNSUPPORTED_CHIP_COUNT,
            -> ReverseErrorCode.INVALID_REQUEST
        }
        return ReverseRuntimeException(code, error.message ?: "Fixed-six inference failed.", error)
    }

    private fun distance(bend: Int): Float = 1.0f + 6.0f * bend

    companion object {
        const val MODEL_IDENTIFIER = "fixed-six:fixed_six_tensor_only.pte"
        const val SOURCE_CHECKPOINT_SHA256 = ForwardModelManifest.CHECKPOINT_SHA256
    }
}
