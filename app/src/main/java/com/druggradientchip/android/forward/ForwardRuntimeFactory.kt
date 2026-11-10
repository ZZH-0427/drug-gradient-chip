package com.druggradientchip.android.forward

import android.content.Context

object ForwardRuntimeFactory {
    @Volatile
    private var sharedLoader: ExecuTorchModelLoader? = null

    fun create(context: Context): ForwardInferenceEngine =
        create(context, logInference = true)

    internal fun createForRepeatedEvaluation(context: Context): ForwardInferenceEngine =
        create(context, logInference = false)

    private fun create(context: Context, logInference: Boolean): ForwardInferenceEngine =
        ForwardInferenceEngine(
            router = ForwardRuntimeRouter(),
            tensorBuilder = ForwardTensorBuilder(),
            modelLoader = modelLoader(context),
            logInference = logInference,
        )

    fun modelLoader(context: Context): ExecuTorchModelLoader =
        sharedLoader ?: synchronized(this) {
            sharedLoader ?: ExecuTorchModelLoader(context.applicationContext).also {
                sharedLoader = it
            }
        }
}
