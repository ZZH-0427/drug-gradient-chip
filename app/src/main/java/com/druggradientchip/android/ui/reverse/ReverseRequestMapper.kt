package com.druggradientchip.android.ui.reverse

import com.druggradientchip.android.reverse.REVERSE_CHIP_COUNT
import com.druggradientchip.android.reverse.ReverseRequest
import com.druggradientchip.android.reverse.ReverseRequestValidator
import com.druggradientchip.android.R
import com.druggradientchip.android.ui.UiText
import com.druggradientchip.android.ui.uiText

class ReverseFormException(val uiText: UiText) : IllegalArgumentException()

class ReverseRequestMapper {
    fun map(form: ReverseFormState): ReverseRequest {
        if (form.targets.size != REVERSE_CHIP_COUNT) {
            throw ReverseFormException(uiText(R.string.reverse_error_target_count))
        }
        val targets = FloatArray(REVERSE_CHIP_COUNT) { index ->
            val text = form.targets[index].trim()
            val value = text.toFloatOrNull()
                ?: throw ReverseFormException(uiText(R.string.reverse_error_target_value, index + 1))
            if (!value.isFinite()) {
                throw ReverseFormException(uiText(R.string.reverse_error_target_value, index + 1))
            }
            value
        }
        return ReverseRequest(form.selectedHighInlet, targets).also(ReverseRequestValidator::validate)
    }
}
