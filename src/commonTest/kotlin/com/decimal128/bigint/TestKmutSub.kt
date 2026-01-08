package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class TestKmutSub {

    // Mirroring your internal logic
    private fun dw32(v: Int): ULong = v.toUInt().toULong()

    @Test
    fun testBasicSubtraction() {
        val t = intArrayOf(10, 20, 30)
        val z = intArrayOf(5, 5)

        // t = t - z -> [10-5, 20-5, 30]
        kmutSub(t, 0, z, 0, 2)

        assertContentEquals(intArrayOf(5, 15, 30), t)
    }

    @Test
    fun testInternalBorrow() {
        // t = 0x00000002 00000000 (represented as [0, 2])
        // z = 0x00000000 00000001 (represented as [1])
        val t = intArrayOf(0, 2)
        val z = intArrayOf(1)

        kmutSub(t, 0, z, 0, 1)

        // Result should be 0x00000001 FFFFFFFF -> [-1, 1]
        assertContentEquals(intArrayOf(-1, 1), t)
    }

    @Test
    fun testRippleBorrowAcrossMultipleLimbs() {
        // t = 1 00000000 00000000 (1 followed by 64 bits of zeros)
        val t = intArrayOf(0, 0, 1)
        val z = intArrayOf(1)

        // Subtract 1 from the lowest limb
        kmutSub(t, 0, z, 0, 1)

        // Should ripple through:
        // Limb 0: 0 - 1 = 0xFFFFFFFF (borrow 1)
        // Limb 1: 0 - 1 = 0xFFFFFFFF (borrow 1)
        // Limb 2: 1 - 1 = 0
        assertContentEquals(intArrayOf(-1, -1, 0), t)
    }

    @Test
    fun testWithOffsets() {
        // Source z: [99, 5, 5, 99] (we only want the middle 5s)
        // Dest t: [0, 0, 10, 10, 0] (we want to subtract from the middle 10s)
        val t = intArrayOf(0, 0, 10, 10, 0)
        val z = intArrayOf(99, 5, 5, 99)

        kmutSub(t, 2, z, 1, 2)

        assertContentEquals(intArrayOf(0, 0, 5, 5, 0), t)
    }

    @Test
    fun testUnderflowReturn() {
        // If you modify kmutSub to return the borrow, this test is vital.
        // Subtracting 10 from 5
        val t = intArrayOf(5)
        val z = intArrayOf(10)


        assertFailsWith<IllegalStateException> {
            // Current implementation doesn't return borrow, but we can check the result
            // 5 - 10 = -5 (which is 0xFFFFFFFB in 32-bit unsigned)
            kmutSub(t, 0, z, 0, 1)

        }
    }
}