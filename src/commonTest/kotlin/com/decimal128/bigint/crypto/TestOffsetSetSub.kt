package com.decimal128.bigint

import com.decimal128.bigint.crypto.Karatsuba.setSub
import kotlin.test.*

class TestOffsetSetSub {

    @Test
    fun sub_zero_minus_zero() {
        val z = intArrayOf(0,0,0)
        val x = IntArray(2)
        val y = IntArray(2)

        val len = setSub(z, 1, x, 1, 0, y, 1, 0)

        assertEquals(0, len, "zero normalized length")
    }

    @Test
    fun sub_no_carry_equal_length() {
        val z = intArrayOf(99,-1,99)
        val x = intArrayOf(99, 7, 99)
        val y = intArrayOf(99, 5, 99)

        val len = setSub(z, 1, x, 1, 1, y, 1, 1)

        assertEquals(1, len)
        assertEquals(2, z[1])
        assertEquals(99, z[2])
    }

    @Test
    fun sub_with_single_borrow() {
        val z = intArrayOf(99,-1,-1,99) // 0xFFFFFFFF
        val x = intArrayOf(99,0, 1,99)
        val y = intArrayOf(99,1,99)

        val len = setSub(z, 1, x, 1, 2, y, 1, 1)

        assertEquals(1, len)
        assertEquals(-1, z[1])
        assertEquals(0, z[2])
    }

    @Test
    fun sub_longer_x() {
        val z = intArrayOf(99,99,99,11,11,99)
        val x = intArrayOf(99,99,10, 20, 99)
        val y = intArrayOf(99,5,99)

        val len = setSub(z, 3, x, 2, 2, y, 1, 1)

        assertEquals(99, z[2])
        assertEquals(2, len)
        assertEquals(5, z[3])
        assertEquals(20, z[4])
        assertEquals(99, z[5])
    }

    @Test
    fun sub_multiple_borrow() {
        val z = intArrayOf(0,0,0,0)
        val x = intArrayOf(99, 1, 0, 0, 1, 99) // both max limbs
        val y = intArrayOf(99, 2, 99)

        val len = setSub(z, 0, x, 1, 4, y, 1, 1)

        assertEquals(3, len)
        assertEquals(-1, z[0])
        assertEquals(-1, z[1])
        assertEquals(-1, z[2])
        assertEquals(0, z[3])
    }

    @Test
    fun rejects_out_of_bounds_x() {
        val z = intArrayOf(0,0)
        val x = intArrayOf(1)

        assertFailsWith<IllegalArgumentException> {
            setSub(z, 0, x, 1, 1, x, 0, 1)
        }
    }

    @Test
    fun rejects_out_of_bounds_z() {
        val z = intArrayOf(99)
        val x = intArrayOf(1)

        assertFailsWith<IllegalArgumentException> {
            setSub(z, 1, x, 0, 1, x, 0, 1) // insufficient space for sum
        }
    }

    @Test
    fun rejects_negative_lengths() {
        val z = intArrayOf(0,0)
        val x = intArrayOf(1)

        assertFailsWith<IllegalArgumentException> {
            setSub(z, -1, x, 0, -1, x, 0, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            setSub(z, 0, x, -1, 0, x, 0, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            setSub(z, 0, x, 0, -1, x, 0, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            setSub(z, 0, x, 0, 1, x, 0, -1)
        }
        assertFailsWith<IllegalArgumentException> {
            setSub(z, 0, x, 0, -1, x, 0, 1)
        }
    }
}
