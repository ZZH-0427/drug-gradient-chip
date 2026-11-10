package com.druggradientchip.android.reverse

fun interface ReverseRuntime {
    fun run(
        request: ReverseRequest,
        cancellation: ReverseCancellationToken,
        onProgress: (ReverseProgress) -> Unit,
    ): ReverseResult
}

internal class HybridReverseRuntime(
    evaluator: ReverseEvaluator,
    config: ReverseSearchConfig = ReverseSearchConfig(),
) : ReverseRuntime {
    private val engine = ReverseSearchEngine(evaluator, config)

    override fun run(
        request: ReverseRequest,
        cancellation: ReverseCancellationToken,
        onProgress: (ReverseProgress) -> Unit,
    ): ReverseResult = engine.optimize(request, cancellation, onProgress).result
}
