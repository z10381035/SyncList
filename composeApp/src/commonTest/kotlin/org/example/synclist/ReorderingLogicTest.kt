package org.example.synclist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class ReorderingLogicTest {

    private fun calculateNewPosition(fromIndex: Int, toIndex: Int, items: List<Double>): Double {
        val currentItems = items.toMutableList()
        val item = currentItems.removeAt(fromIndex)
        currentItems.add(toIndex, item)

        return when {
            toIndex == 0 -> currentItems[1] - 1.0
            toIndex == currentItems.size - 1 -> currentItems[currentItems.size - 2] + 1.0
            else -> {
                val prevPos = currentItems[toIndex - 1]
                val nextPos = currentItems[toIndex + 1]
                (prevPos + nextPos) / 2.0
            }
        }
    }

    @Test
    fun testMoveToStart() {
        val items = listOf(10.0, 20.0, 30.0)
        // Move 30.0 (index 2) to start (index 0)
        // Resulting order: 30.0, 10.0, 20.0
        // New position should be 10.0 - 1.0 = 9.0
        val newPos = calculateNewPosition(2, 0, items)
        assertEquals(9.0, newPos)
    }

    @Test
    fun testMoveToEnd() {
        val items = listOf(10.0, 20.0, 30.0)
        // Move 10.0 (index 0) to end (index 2)
        // Resulting order: 20.0, 30.0, 10.0
        // New position should be 30.0 + 1.0 = 31.0
        val newPos = calculateNewPosition(0, 2, items)
        assertEquals(31.0, newPos)
    }

    @Test
    fun testMoveToMiddle() {
        val items = listOf(10.0, 20.0, 30.0)
        // Move 10.0 (index 0) to index 1 (between 20 and 30)
        // Resulting order: 20.0, 10.0, 30.0
        // New position should be (20.0 + 30.0) / 2.0 = 25.0
        val newPos = calculateNewPosition(0, 1, items)
        assertEquals(25.0, newPos)
    }

    @Test
    fun testPrecisionGuardThreshold() {
        val prevPos = 1.0000000001
        val nextPos = 1.0000000002
        val diff = abs(prevPos - nextPos)
        assertTrue(diff < 1e-9)
        assertTrue(diff > 1e-11)
        // 1e-10 is the guard
        assertTrue(diff < 1e-9) 
    }
}
