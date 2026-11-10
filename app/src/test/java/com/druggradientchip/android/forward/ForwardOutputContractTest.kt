package com.druggradientchip.android.forward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ForwardOutputContractTest {
    @Test
    fun outputLengthMismatchUsesStructuredError() {
        val error = assertThrows(ForwardRuntimeException::class.java) {
            validateOutputLength(chipCount = 4, output = floatArrayOf(1.0f, 2.0f))
        }
        assertEquals(ForwardErrorCode.UNEXPECTED_OUTPUT_LENGTH, error.code)
    }

    private fun validateOutputLength(chipCount: Int, output: FloatArray) {
        if (output.size != chipCount) {
            throw ForwardRuntimeException(
                ForwardErrorCode.UNEXPECTED_OUTPUT_LENGTH,
                "Expected $chipCount outputs, got ${output.size}.",
            )
        }
    }
}
