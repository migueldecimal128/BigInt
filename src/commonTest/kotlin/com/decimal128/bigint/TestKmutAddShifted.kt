package com.decimal128.bigint

import com.decimal128.bigint.MagoSqr.kmutAddShifted
import kotlin.test.Test
import kotlin.test.assertContentEquals

class TestKmutAddShifted {

    private fun dw32(v: Int): ULong = v.toULong() and 0xFFFFFFFFuL

    @Test
    fun testShiftedAdditionWithRipple() {
        // z represents the result array, currently holding some values
        // Imagine z = [0, 0, 0, 0xFFFFFFFF, 0, 0]
        val z = intArrayOf(0, 0, 0, -1, 0, 0)

        // t represents the middle term to be added: [1, 1]
        val t = intArrayOf(1, 1)

        // Shift by 2: start adding t at z[2]
        // z[2] += t[0] -> 0 + 1 = 1
        // z[3] += t[1] -> 0xFFFFFFFF + 1 = 0 (carry 1)
        // z[4] (ripple) -> 0 + 1 = 1
        kmutAddShifted(z, 0, t, 0, 2, 2)

        // Expected: [0, 0, 1, 0, 1, 0]
        assertContentEquals(intArrayOf(0, 0, 1, 0, 1, 0), z)
    }

    @Test
    fun testSimpleShiftNoRipple() {
        val z = intArrayOf(1, 1, 1, 1)
        val t = intArrayOf(2, 2)

        // Add t starting at index 1
        kmutAddShifted(z, 0, t, 0, 2, 1)

        // z[1] = 1+2=3, z[2] = 1+2=3
        assertContentEquals(intArrayOf(1, 3, 3, 1), z)
    }

    @Test
    fun testLongDistanceRipple() {
        // z = [0, 0, -1, -1, -1, 0]
        val z = intArrayOf(0, 0, -1, -1, -1, 0)
        val t = intArrayOf(1)

        // Add t[0] at z[2].
        // z[2] becomes 0, carries to z[3]
        // z[3] becomes 0, carries to z[4]
        // z[4] becomes 0, carries to z[5]
        // z[5] becomes 1
        kmutAddShifted(z, 0, t, 0, 1, 2)

        assertContentEquals(intArrayOf(0, 0, 0, 0, 0, 1), z)
    }
}