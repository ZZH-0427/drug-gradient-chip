package com.druggradientchip.android.forward

import android.content.Context
import org.pytorch.executorch.Module
import java.io.File

class ExecuTorchModelLoader(
    private val context: Context,
) {
    private val loadedModules = mutableMapOf<String, Module>()

    @Synchronized
    fun load(artifact: ForwardModelArtifact): Module =
        loadedModules.getOrPut(artifact.filename) {
            try {
                val modelFile = copyAssetToFilesDir(artifact.filename)
                Module.load(modelFile.absolutePath)
            } catch (error: UnsatisfiedLinkError) {
                throw ForwardRuntimeException(
                    ForwardErrorCode.UNSUPPORTED_DEVICE_RUNTIME,
                    "ExecuTorch native runtime is not supported on this device.",
                    error,
                )
            } catch (error: ForwardRuntimeException) {
                throw error
            } catch (error: Throwable) {
                throw ForwardRuntimeException(
                    ForwardErrorCode.MODEL_LOAD_FAILURE,
                    "Failed to load model asset ${artifact.filename}.",
                    error,
                )
            }
        }

    @Synchronized
    fun <T> withModule(artifact: ForwardModelArtifact, action: (Module) -> T): T =
        action(load(artifact))

    @Synchronized
    fun close() {
        loadedModules.values.forEach { module ->
            try {
                module.destroy()
            } catch (_: Throwable) {
            }
        }
        loadedModules.clear()
    }

    private fun copyAssetToFilesDir(assetName: String): File {
        val output = File(context.filesDir, assetName)
        try {
            context.assets.open(assetName).use { input ->
                output.outputStream().use { input.copyTo(it) }
            }
        } catch (error: Throwable) {
            throw ForwardRuntimeException(
                ForwardErrorCode.MISSING_MODEL_ASSET,
                "Missing model asset: $assetName",
                error,
            )
        }
        return output
    }
}
