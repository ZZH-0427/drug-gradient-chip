package com.druggradientchip.android.ui.forward

data class ForwardChipFormState(
    val position: Int,
    val chipType: String = "1to1",
    val leftWidthMm: String = "0.060",
    val rightWidthMm: String = "0.070",
    val leftBendStraightDistanceMm: String = "11",
    val rightBendStraightDistanceMm: String = "21",
)

data class ForwardFormState(
    val sampleName: String = "Android sample",
    val chipCount: Int = 6,
    val inlet1: String = "0.0",
    val inlet2: String = "1.0",
    val chips: List<ForwardChipFormState> = defaultChips(6),
) {
    fun withChipCount(nextCount: Int): ForwardFormState {
        val byPosition = chips.associateBy { it.position }
        return copy(
            chipCount = nextCount,
            chips = (1..nextCount).map { position ->
                byPosition[position] ?: defaultChip(position)
            },
        )
    }

    companion object {
        val allowedCounts: List<Int> = (4..10).toList()
        val allowedChipTypes: List<String> = (1..6).flatMap { start -> (1..6).map { end -> "${start}to$end" } }

        fun defaultChips(chipCount: Int): List<ForwardChipFormState> =
            (1..chipCount).map { defaultChip(it) }

        fun defaultChip(position: Int): ForwardChipFormState =
            ForwardChipFormState(
                position = position,
                chipType = allowedChipTypes[(position - 1) % allowedChipTypes.size],
                leftWidthMm = String.format(java.util.Locale.US, "%.3f", 0.05 + 0.01 * position),
                rightWidthMm = String.format(java.util.Locale.US, "%.3f", 0.06 + 0.01 * position),
                leftBendStraightDistanceMm = String.format(java.util.Locale.US, "%.0f", 10.0 + position),
                rightBendStraightDistanceMm = String.format(java.util.Locale.US, "%.0f", 20.0 + position),
            )
    }
}
