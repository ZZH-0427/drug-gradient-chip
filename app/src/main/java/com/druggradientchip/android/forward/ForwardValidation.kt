package com.druggradientchip.android.forward

import kotlin.math.abs

object ForwardValidation {
    const val MIN_CHIP_COUNT = 4
    const val MAX_CHIP_COUNT = 10
    const val MIN_WIDTH_MM = 0.04f
    const val MAX_WIDTH_MM = 0.31f

    fun validate(request: ForwardRequest) {
        if (request.sampleName.isBlank()) {
            invalid("sample_name must not be empty")
        }
        if (request.chipCount !in MIN_CHIP_COUNT..MAX_CHIP_COUNT) {
            throw ForwardRuntimeException(
                ForwardErrorCode.UNSUPPORTED_CHIP_COUNT,
                "chip_count must be between 4 and 10",
            )
        }
        finite(request.inlet1, "inlet1")
        finite(request.inlet2, "inlet2")
        if (request.chips.size != request.chipCount) {
            invalid("chips length must equal chip_count")
        }
        val positions = request.chips.map { it.position }.sorted()
        val expected = (1..request.chipCount).toList()
        if (positions != expected) {
            invalid("chips positions must be consecutive from 1 through chip_count without duplicates")
        }
        request.chips.forEach { chip ->
            if (chip.chipType.isBlank()) invalid("chip_type must not be empty")
            width(chip.leftWidthMm, "left_width_mm")
            width(chip.rightWidthMm, "right_width_mm")
            positiveFinite(chip.leftBendStraightDistanceMm, "left_bend_straight_distance_mm")
            positiveFinite(chip.rightBendStraightDistanceMm, "right_bend_straight_distance_mm")
        }
    }

    private fun width(value: Float, name: String) {
        finite(value, name)
        if (value < MIN_WIDTH_MM || value > MAX_WIDTH_MM) {
            invalid("$name must be between 0.04 and 0.31")
        }
    }

    private fun positiveFinite(value: Float, name: String) {
        finite(value, name)
        if (value <= 0.0f) invalid("$name must be positive")
    }

    private fun finite(value: Float, name: String) {
        if (!value.isFinite()) invalid("$name must be finite")
    }

    private fun invalid(message: String): Nothing =
        throw ForwardRuntimeException(ForwardErrorCode.INVALID_REQUEST, message)

    private fun Float.isFinite(): Boolean = !isNaN() && abs(this) != Float.POSITIVE_INFINITY
}
