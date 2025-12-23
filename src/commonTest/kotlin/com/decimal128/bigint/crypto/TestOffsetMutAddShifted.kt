package com.decimal128.bigint.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestOffsetMutAddShifted {

    @Test
    fun add_zero_plus_zero_shift1() {
        val z = intArrayOf(99,99,99)
        val x = intArrayOf(99,99)

        val len = Karatsuba.mutAddShifted(z, 1, 0, x, 1, 0, 1)

        assertEquals(0, len, "zero normalized length")
        assertContentEquals(intArrayOf(99, 99, 99), z)
        assertContentEquals(intArrayOf(99, 99), x)
    }

    @Test
    fun add_equal_length_0_shift_0() {
        val z = intArrayOf(99,5,-1, 99)
        val x = intArrayOf(99,7, 99)

        val len = Karatsuba.mutAddShifted(z, 1, 1, x, 1, 1, 0)

        assertEquals(1, len)
        assertContentEquals(intArrayOf(99, 12, 0, 99), z)
        assertContentEquals(intArrayOf(99, 7, 99), x)
    }

    @Test
    fun add_equal_length_1_shift_1() {
        val z = intArrayOf(99,5,-1, -1, 99)
        val x = intArrayOf(99,7, 99)

        val len = Karatsuba.mutAddShifted(z, 1, 1, x, 1, 1, 1)

        assertEquals(2, len)
        assertContentEquals(intArrayOf(99, 5, 7, 0, 99), z)
        assertContentEquals(intArrayOf(99, 7, 99), x)
    }

    @Test
    fun add_equal_length_1_shift_2() {
        val z = intArrayOf(99,5,-1, -1, -1, 99)
        val x = intArrayOf(99,7, 99)

        val len = Karatsuba.mutAddShifted(z, 1, 1, x, 1, 1, 2)

        assertEquals(3, len)
        assertContentEquals(intArrayOf(99, 5, 0, 7, 0, 99), z)
        assertContentEquals(intArrayOf(99, 7, 99), x)
    }

    @Test
    fun add_equal_length_1_shift_3() {
        val z = intArrayOf(99,5,-1, -1, -1, -1, 99)
        val x = intArrayOf(99,7, 99)

        val len = Karatsuba.mutAddShifted(z, 1, 1, x, 1, 1, 3)

        assertEquals(4, len)
        assertContentEquals(intArrayOf(99, 5, 0, 0, 7, 0, 99), z)
        assertContentEquals(intArrayOf(99, 7, 99), x)
    }
    @Test
    fun rejects_out_of_bounds_x() {
        val z = intArrayOf(0,0)
        val x = intArrayOf(1)

        assertFailsWith<IllegalArgumentException> {
            Karatsuba.setAdd(z, 0, x, 1, 1, x, 0, 1)
        }
    }

    @Test
    fun rejects_out_of_bounds_z() {
        val z = intArrayOf(0)
        val x = intArrayOf(1)

        assertFailsWith<IllegalArgumentException> {
            // z must have 2 limbs to handle overflow from 1 limb + 1 limb
            Karatsuba.setAdd(z, 0, x, 0, 1, x, 0, 1)
        }
    }

    @Test
    fun rejects_negative_lengths() {
        val z = intArrayOf(99,5,-1, -1, 99)
        val x = intArrayOf(99,7, 99)

        val len = Karatsuba.mutAddShifted(z, 1, 1, x, 1, 1, 1)

        assertFailsWith<IllegalArgumentException> {
            val len = Karatsuba.mutAddShifted(z, -1, 1, x, 1, 1, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            val len = Karatsuba.mutAddShifted(z, 1, -1, x, 1, 1, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            val len = Karatsuba.mutAddShifted(z, 1, 1, x, -1, 1, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            val len = Karatsuba.mutAddShifted(z, 1, 1, x, 1, -1, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            val len = Karatsuba.mutAddShifted(z, 1, 1, x, 1, 1, -1)
        }
    }
}