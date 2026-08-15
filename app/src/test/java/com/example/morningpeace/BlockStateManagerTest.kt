package com.example.morningpeace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockStateManagerTest {

    @Test
    fun blockTypes_constants_areCorrect() {
        assertEquals("morning", BlockStateManager.BLOCK_TYPE_MORNING)
        assertEquals("focus", BlockStateManager.BLOCK_TYPE_FOCUS)
    }

    @Test
    fun actions_constants_areCorrect() {
        assertEquals("com.example.morningpeace.BLOCK_STATE_CHANGED", BlockStateManager.ACTION_BLOCK_STATE_CHANGED)
        assertEquals("com.example.morningpeace.AUTO_UNLOCK", BlockStateManager.ACTION_AUTO_UNLOCK)
    }

    @Test
    fun durationCalculation_isAccurate() {
        val minutes = 45
        val expectedMs = minutes * 60 * 1000L
        assertEquals(2700000L, expectedMs)
    }

    @Test
    fun thresholdHoursCalculation_isAccurate() {
        val hours = 6
        val expectedMs = hours * 3600 * 1000L
        assertEquals(21600000L, expectedMs)
    }
}
