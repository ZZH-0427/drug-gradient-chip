package com.druggradientchip.android.ui.forward

import com.druggradientchip.android.forward.ForwardChipInput
import com.druggradientchip.android.forward.ForwardRequest
import com.druggradientchip.android.forward.ForwardValidation
import com.druggradientchip.android.R
import com.druggradientchip.android.ui.UiText
import com.druggradientchip.android.ui.uiText

class ForwardRequestMapper {
    fun map(state: ForwardFormState): ForwardRequest {
        if (state.chipCount !in ForwardFormState.allowedCounts) {
            throw ForwardFormException(uiText(R.string.forward_error_unsupported_chip_count))
        }
        if (state.chips.size != state.chipCount) {
            throw ForwardFormException(uiText(R.string.forward_error_chip_editor_count))
        }
        val request = ForwardRequest(
            sampleName = state.sampleName.trim().ifEmpty { "Android sample" },
            chipCount = state.chipCount,
            inlet1 = parseRequiredFloat(state.inlet1, R.string.inlet1_hint),
            inlet2 = parseRequiredFloat(state.inlet2, R.string.inlet2_hint),
            chips = state.chips.sortedBy { it.position }.map { chip ->
                if (!ForwardFormState.allowedChipTypes.contains(chip.chipType)) {
                    throw ForwardFormException(uiText(R.string.forward_error_invalid_chip_type, chip.position))
                }
                ForwardChipInput(
                    position = chip.position,
                    chipType = chip.chipType,
                    leftWidthMm = parseRequiredFloat(chip.leftWidthMm, R.string.left_width),
                    rightWidthMm = parseRequiredFloat(chip.rightWidthMm, R.string.right_width),
                    leftBendStraightDistanceMm = parseRequiredFloat(chip.leftBendStraightDistanceMm, R.string.left_distance),
                    rightBendStraightDistanceMm = parseRequiredFloat(chip.rightBendStraightDistanceMm, R.string.right_distance),
                )
            },
        )
        ForwardValidation.validate(request)
        return request
    }

    private fun parseRequiredFloat(value: String, labelResource: Int): Float {
        val text = value.trim()
        if (text.isEmpty()) {
            throw ForwardFormException(uiText(R.string.forward_error_required, UiText.Resource(labelResource)))
        }
        val parsed = text.toFloatOrNull()
            ?: throw ForwardFormException(uiText(R.string.forward_error_valid_number, UiText.Resource(labelResource)))
        if (!parsed.isFinite()) {
            throw ForwardFormException(uiText(R.string.forward_error_finite, UiText.Resource(labelResource)))
        }
        return parsed
    }

    private fun Float.isFinite(): Boolean = !isNaN() && this != Float.POSITIVE_INFINITY && this != Float.NEGATIVE_INFINITY
}

class ForwardFormException(val uiText: UiText) : RuntimeException()
