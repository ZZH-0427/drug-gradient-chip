package com.druggradientchip.android.reverse

import android.content.Context
import com.druggradientchip.android.forward.ForwardRuntimeFactory

object ReverseRuntimeFactory {
    @Volatile
    private var sharedController: ReverseTaskController? = null

    fun taskController(context: Context): ReverseTaskController =
        sharedController ?: synchronized(this) {
            sharedController ?: createController(context.applicationContext).also {
                sharedController = it
            }
        }

    fun cancelActive(): Boolean = sharedController?.cancel() ?: false

    internal fun createController(
        context: Context,
        config: ReverseSearchConfig = ReverseSearchConfig(),
    ): ReverseTaskController {
        val evaluator = FixedSixReverseEvaluator(ForwardRuntimeFactory.createForRepeatedEvaluation(context))
        return ReverseTaskController(
            runtime = HybridReverseRuntime(evaluator, config),
            executionPolicy = ReverseForegroundExecutionPolicy(context),
        )
    }
}
