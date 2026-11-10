package com.druggradientchip.android.ui.forward

import com.druggradientchip.android.forward.ForwardResult
import com.druggradientchip.android.R
import com.druggradientchip.android.ui.UiText
import com.druggradientchip.android.ui.uiText
import java.util.Locale

data class ForwardResultRow(val position: Int, val concentration: String)

class ForwardResultFormatter {
    fun resultRows(result: ForwardResult): List<ForwardResultRow> =
        result.concentrations.mapIndexed { index, value ->
            ForwardResultRow(index + 1, String.format(Locale.US, "%.6f", value))
        }

    fun modelLabel(modelIdentifier: String): UiText = when {
        modelIdentifier.startsWith("fixed-six") -> uiText(R.string.forward_model_fixed_six)
        modelIdentifier.startsWith("dynamic-zero-shot") -> uiText(R.string.forward_model_dynamic_zero_shot)
        else -> UiText.Technical(modelIdentifier.substringBefore(":"))
    }
}
