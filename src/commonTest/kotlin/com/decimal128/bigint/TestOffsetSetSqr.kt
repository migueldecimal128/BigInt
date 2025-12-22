package com.decimal128.bigint
import kotlin.test.*

class TestOffsetSetSqr {

    @Test
    fun sqr_zero() {
        val z = intArrayOf(99,99,99)
        val x = IntArray(2)
        val y = IntArray(2)

        val len = Mago.setSqr(z, 1, x, 1, 0)

        assertEquals(0, len, "zero normalized length")
    }

    @Test
    fun sqr_no_grow() {
        val z = intArrayOf(99,-1,-1, 99)
        val x = intArrayOf(99, 5, 99)

        val len = Mago.setSqr(z, 1, x, 1, 1)

        assertEquals(1, len)
        assertEquals(25, z[1])
        assertEquals(0, z[2])
    }

    @Test
    fun sqr_grow() {
        val z = intArrayOf(99,99,99,-1,-1,99,99,99)
        val x = intArrayOf(99,99,0x2000_0000,99,99) // 0xFFFFFFFF

        val len = Mago.setSqr(z, 3, x, 2, 1)

        // 0xffffffff + 1 = 1_0000_0000 => limb=0, carry=1 => normalized length = 2
        assertEquals(2, len)
        assertEquals(0, z[3])
        assertEquals(0x0400_0000, z[4])
    }

    @Test
    fun rejects_out_of_bounds_x() {
        val z = intArrayOf(0,0)
        val x = intArrayOf(1)

        assertFailsWith<IllegalArgumentException> {
            Mago.setSqr(z, 0, x, 1, 1)
        }
    }

    @Test
    fun rejects_out_of_bounds_z() {
        val z = intArrayOf(0)
        val x = intArrayOf(1)

        assertFailsWith<IllegalArgumentException> {
            // z must have 2 limbs for (1 limb)**2
            Mago.setSqr(z, 0, x, 0, 1)
        }
    }

    @Test
    fun rejects_negative_lengths() {
        val z = intArrayOf(0,0)
        val x = intArrayOf(1)

        assertFailsWith<IllegalArgumentException> {
            Mago.setSqr(z, -1, x, 0, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            Mago.setSqr(z, 0, x, -1, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            Mago.setSqr(z, 0, x, 0, -1)
        }
    }
}
