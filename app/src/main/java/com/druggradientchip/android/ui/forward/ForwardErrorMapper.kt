package com.druggradientchip.android.ui.forward

import com.druggradientchip.android.forward.ForwardErrorCode
import com.druggradientchip.android.forward.ForwardRuntimeException
import com.druggradientchip.android.R
import com.druggradientchip.android.ui.UiText
import com.druggradientchip.android.ui.uiText

class ForwardErrorMapper {
    fun message(error: Throwable): UiText = when (error) {
        is ForwardFormException -> error.uiText
        is ForwardRuntimeException -> when (error.code) {
            ForwardErrorCode.UNSUPPORTED_CHIP_COUNT -> uiText(R.string.forward_error_unsupported_chip_count)
            ForwardErrorCode.INVALID_REQUEST -> uiText(R.string.forward_error_invalid_request)
            ForwardErrorCode.MISSING_MODEL_ASSET -> uiText(R.string.forward_error_model_missing)
            ForwardErrorCode.MODEL_LOAD_FAILURE -> uiText(R.string.forward_error_model_load)
            ForwardErrorCode.TENSOR_CONSTRUCTION_FAILURE -> uiText(R.string.forward_error_tensor_build)
            ForwardErrorCode.EXECUTORCH_INFERENCE_FAILURE -> uiText(R.string.forward_error_inference)
            ForwardErrorCode.UNEXPECTED_OUTPUT_LENGTH -> uiText(R.string.forward_error_output_length)
            ForwardErrorCode.UNSUPPORTED_DEVICE_RUNTIME -> uiText(R.string.forward_error_runtime_unsupported)
        }
        else -> uiText(R.string.forward_error_generic)
    }
}
