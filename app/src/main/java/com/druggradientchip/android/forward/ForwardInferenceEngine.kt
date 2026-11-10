package com.druggradientchip.android.forward

import android.os.SystemClock
import android.util.Log
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Tensor

fun interface ForwardPredictor {
    fun predict(request: ForwardRequest): ForwardResult
}

class ForwardInferenceEngine(
    private val router: ForwardRuntimeRouter,
    private val tensorBuilder: ForwardTensorBuilder,
    private val modelLoader: ExecuTorchModelLoader,
    private val logInference: Boolean = true,
) : ForwardPredictor {
    override fun predict(request: ForwardRequest): ForwardResult {
        val artifact = router.route(request.chipCount)
        if (logInference) {
            Log.i(TAG, "selected_model chip_count=${request.chipCount} file=${artifact.filename} family=${artifact.family}")
        }
        val tensors = tensorBuilder.build(request, artifact)
        val started = SystemClock.elapsedRealtimeNanos()
        val output = try {
            modelLoader.withModule(artifact) { module ->
                module.forward(
                    EValue.from(Tensor.fromBlob(tensors.x.values, tensors.x.shape)),
                    EValue.from(Tensor.fromBlob(tensors.edgeIndex.values, tensors.edgeIndex.shape)),
                    EValue.from(Tensor.fromBlob(tensors.edgeAttr.values, tensors.edgeAttr.shape)),
                    EValue.from(Tensor.fromBlob(tensors.u.values, tensors.u.shape)),
                    EValue.from(Tensor.fromBlob(tensors.chipPhysics.values, tensors.chipPhysics.shape)),
                    EValue.from(Tensor.fromBlob(tensors.chipPhysicsZ.values, tensors.chipPhysicsZ.shape)),
                    EValue.from(Tensor.fromBlob(tensors.nodeType.values, tensors.nodeType.shape)),
                ).first().toTensor().dataAsFloatArray
            }
        } catch (error: ForwardRuntimeException) {
            throw error
        } catch (error: UnsatisfiedLinkError) {
            throw ForwardRuntimeException(
                ForwardErrorCode.UNSUPPORTED_DEVICE_RUNTIME,
                "ExecuTorch native runtime could not execute on this device.",
                error,
            )
        } catch (error: Throwable) {
            throw ForwardRuntimeException(
                ForwardErrorCode.EXECUTORCH_INFERENCE_FAILURE,
                "ExecuTorch forward inference failed.",
                error,
            )
        }
        val latencyMs = (SystemClock.elapsedRealtimeNanos() - started).toDouble() / 1_000_000.0
        if (output.size != request.chipCount) {
            throw ForwardRuntimeException(
                ForwardErrorCode.UNEXPECTED_OUTPUT_LENGTH,
                "Expected ${request.chipCount} outputs, got ${output.size}.",
            )
        }
        if (logInference) {
            Log.i(TAG, "inference_result chip_count=${request.chipCount} output_length=${output.size} latency_ms=$latencyMs")
        }
        return ForwardResult(
            chipCount = request.chipCount,
            concentrations = output,
            modelIdentifier = "${artifact.family}:${artifact.filename}",
            latencyMs = latencyMs,
        )
    }

    companion object {
        private const val TAG = "DrugChipAndroidForward"
    }
}
