package com.druggradientchip.android.ui.reverse

import com.druggradientchip.android.reverse.HighConcentrationInlet
import com.druggradientchip.android.reverse.ReverseChipCandidate
import com.druggradientchip.android.reverse.ReverseResult
import com.druggradientchip.android.R
import com.druggradientchip.android.ui.UiText
import com.druggradientchip.android.ui.uiText
import java.util.Locale

data class ReverseChipDisplay(
    val title: UiText,
    val structure: UiText,
    val geometry: UiText,
    val concentrations: UiText,
)

class ReverseResultFormatter {
    fun inlet(inlet: HighConcentrationInlet): UiText = when (inlet) {
        HighConcentrationInlet.INLET_1 -> uiText(R.string.reverse_inlet1_value)
        HighConcentrationInlet.INLET_2 -> uiText(R.string.reverse_inlet2_value)
    }

    fun quality(result: ReverseResult): UiText = uiText(
        if (result.metrics.qualityPassed) R.string.quality_passed else R.string.quality_warning,
    )

    fun summary(result: ReverseResult): UiText = uiText(
        R.string.reverse_summary,
        inlet(result.selectedHighInlet),
        format(result.runtimeMs / 1_000.0, 2),
        result.evaluationCount,
    )

    fun metrics(result: ReverseResult): String = buildString {
        append("MAE  ${format(result.metrics.mae, 6)}")
        append("\nRMSE  ${format(result.metrics.rmse, 6)}")
        append("\nMaxAE  ${format(result.metrics.maxAe, 6)}")
        append("\nSpearman  ${format(result.metrics.spearman, 4)}")
    }

    fun chips(result: ReverseResult, targets: FloatArray): List<ReverseChipDisplay> {
        require(targets.size == result.chipCount) { "Target count must match the Reverse result." }
        return result.candidate.chips.mapIndexed { index, chip ->
            chipDisplay(chip, targets[index], result.recomputedConcentrations[index], result.perPositionErrors[index])
        }
    }

    private fun chipDisplay(chip: ReverseChipCandidate, target: Float, recomputed: Float, error: Float) = ReverseChipDisplay(
        title = uiText(R.string.reverse_chip_title, chip.position),
        structure = uiText(R.string.reverse_chip_structure, chip.chipType),
        geometry = uiText(
            R.string.reverse_chip_geometry,
            format(chip.leftWidthMm, 3),
            format(chip.rightWidthMm, 3),
            format(chip.leftDistanceMm, 0),
            format(chip.rightDistanceMm, 0),
        ),
        concentrations = uiText(
            R.string.reverse_chip_concentrations,
            format(target, 6),
            format(recomputed, 6),
            format(error, 6),
        ),
    )

    private fun format(value: Number, precision: Int): String =
        String.format(Locale.US, "%.${precision}f", value.toDouble())
}
