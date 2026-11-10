package com.druggradientchip.android.ui.forward

import com.druggradientchip.android.R
import com.druggradientchip.android.forward.ForwardErrorCode
import com.druggradientchip.android.forward.ForwardRuntimeException
import com.druggradientchip.android.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardErrorMapperTest {
    private val mapper = ForwardErrorMapper()

    @Test
    fun mapsTypedErrorsToUserMessages() {
        assertEquals(
            UiText.Resource(R.string.forward_error_model_missing),
            mapper.message(ForwardRuntimeException(ForwardErrorCode.MISSING_MODEL_ASSET, "missing")),
        )
        assertEquals(
            UiText.Resource(R.string.forward_error_runtime_unsupported),
            mapper.message(ForwardRuntimeException(ForwardErrorCode.UNSUPPORTED_DEVICE_RUNTIME, "unsupported")),
        )
    }
}
