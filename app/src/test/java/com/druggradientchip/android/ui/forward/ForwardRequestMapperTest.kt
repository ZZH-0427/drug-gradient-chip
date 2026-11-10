package com.druggradientchip.android.ui.forward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ForwardRequestMapperTest {
    private val mapper = ForwardRequestMapper()

    @Test
    fun mapsN4N6N10WithExactChipCounts() {
        assertEquals(4, mapper.map(ForwardFormState().withChipCount(4)).chips.size)
        assertEquals(6, mapper.map(ForwardFormState().withChipCount(6)).chips.size)
        assertEquals(10, mapper.map(ForwardFormState().withChipCount(10)).chips.size)
    }

    @Test
    fun preservesPositionOrdering() {
        val request = mapper.map(ForwardFormState().withChipCount(10))
        assertEquals((1..10).toList(), request.chips.map { it.position })
    }

    @Test
    fun chipCountTransitionFromTenToFourSubmitsOnlyFourChips() {
        val state = ForwardFormState().withChipCount(10).withChipCount(4)
        val request = mapper.map(state)
        assertEquals(4, request.chipCount)
        assertEquals(4, request.chips.size)
        assertEquals((1..4).toList(), request.chips.map { it.position })
    }

    @Test
    fun blankNumericValueFailsBeforeRuntime() {
        val brokenChip = ForwardFormState.defaultChip(1).copy(leftWidthMm = "")
        val state = ForwardFormState(chipCount = 4, chips = listOf(brokenChip) + ForwardFormState.defaultChips(4).drop(1))
        assertThrows(ForwardFormException::class.java) { mapper.map(state) }
    }

    @Test
    fun invalidChipTypeFailsBeforeRuntime() {
        val brokenChip = ForwardFormState.defaultChip(1).copy(chipType = "7to1")
        val state = ForwardFormState(chipCount = 4, chips = listOf(brokenChip) + ForwardFormState.defaultChips(4).drop(1))
        assertThrows(ForwardFormException::class.java) { mapper.map(state) }
    }

    @Test
    fun nonFiniteNumericValueFailsBeforeRuntime() {
        val state = ForwardFormState(inlet1 = "Infinity")
        assertThrows(ForwardFormException::class.java) { mapper.map(state) }
    }
}
