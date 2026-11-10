package com.druggradientchip.android.ui.reverse

import com.druggradientchip.android.reverse.ReverseErrorCode
import com.druggradientchip.android.R
import com.druggradientchip.android.ui.UiText
import com.druggradientchip.android.ui.uiText

class ReverseErrorMapper {
    fun message(code: ReverseErrorCode): UiText = when (code) {
        ReverseErrorCode.INVALID_REQUEST,
        ReverseErrorCode.INVALID_TARGET,
        -> uiText(R.string.reverse_error_six_targets)
        ReverseErrorCode.INVALID_INLET -> uiText(R.string.reverse_error_select_inlet)
        ReverseErrorCode.MODEL_ASSET_MISSING -> uiText(R.string.reverse_error_model_missing)
        ReverseErrorCode.MODEL_LOAD_FAILURE -> uiText(R.string.reverse_error_model_load)
        ReverseErrorCode.TENSOR_BUILD_FAILURE -> uiText(R.string.reverse_error_tensor_build)
        ReverseErrorCode.INFERENCE_FAILURE,
        ReverseErrorCode.UNEXPECTED_OUTPUT,
        -> uiText(R.string.reverse_error_inference)
        ReverseErrorCode.SEARCH_FAILURE -> uiText(R.string.reverse_error_search)
        ReverseErrorCode.CANCELED -> uiText(R.string.reverse_error_canceled)
        ReverseErrorCode.UNSUPPORTED_DEVICE_RUNTIME -> uiText(R.string.reverse_error_runtime_unsupported)
    }

    fun message(error: Throwable): UiText = when (error) {
        is ReverseFormException -> error.uiText
        else -> uiText(R.string.reverse_error_start)
    }
}
