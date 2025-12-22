package com.decimal128.bigint
import kotlin.test.*

class TestOffsetSetAdd {

    @Test
    fun add_zero_plus_zero() {
        val z = intArrayOf(0,0,0)
        val x = IntArray(2)
        val y = IntArray(2)

        val len = Mago.setAdd(z, 1, x, 1, 0, y, 1, 0)

        assertEquals(0, len, "zero normalized length")
    }

    @Test
    fun add_no_carry_equal_length() {
        val z = intArrayOf(99,0,0, 99)
        val x = intArrayOf(99, 5, 99)
        val y = intArrayOf(99, 7, 99)

        val len = Mago.setAdd(z, 1, x, 1, 1, y, 1, 1)

        assertEquals(1, len)
        assertEquals(12, z[1])
        assertEquals(0, z[2])
    }

    @Test
    fun add_with_single_carry() {
        val z = intArrayOf(99,0,0,99)
        val x = intArrayOf(99,-1,99) // 0xFFFFFFFF
        val y = intArrayOf(99,1,99)

        val len = Mago.setAdd(z, 1, x, 1, 1, y, 1, 1)

        // 0xffffffff + 1 = 1_0000_0000 => limb=0, carry=1 => normalized length = 2
        assertEquals(2, len)
        assertEquals(0, z[1])
        assertEquals(1, z[2])
    }

    @Test
    fun add_longer_x() {
        val z = intArrayOf(99,99,99,11,11,11,99)
        val x = intArrayOf(99,99,10, 20, 99)
        val y = intArrayOf(99,5,99)

        val len = Mago.setAdd(z, 3, x, 2, 2, y, 1, 1)

        // (10 + 5) = 15, then remaining limb 20
        assertEquals(2, len)
        assertEquals(15, z[3])
        assertEquals(20, z[4])
    }

    @Test
    fun add_longer_y() {
        val z = intArrayOf(99,-1,-1,-1,99)
        val x = intArrayOf(77,77,7,77,77)
        val y = intArrayOf(4444,4444,4444,4444,1, 2, 3,3333,3333,3333,3333)

        val len = Mago.setAdd(z, 1, x, 2, 1, y, 4, 3)

        // limb[0] = 7+1=8
        // limb[1] = 2
        // limb[2] = 3
        assertEquals(3, len)
        assertEquals(8, z[1])
        assertEquals(2, z[2])
        assertEquals(3, z[3])
    }

    @Test
    fun add_into_offset_region() {
        val z = intArrayOf(0,0,0,0,0)
        val x = intArrayOf(9,9)
        val y = intArrayOf(1,1)

        val len = Mago.setAdd(z, 2, x, 0, 2, y, 0, 2)

        assertEquals(2, len)
        assertEquals(0, z[0])
        assertEquals(0, z[1])
        assertEquals(10, z[2])
        assertEquals(10, z[3])
    }

    @Test
    fun add_creates_final_carry_expands_len() {
        val z = intArrayOf(0,0,0,0)
        val x = intArrayOf(-1, -1) // both max limbs
        val y = intArrayOf(1)

        val len = Mago.setAdd(z, 0, x, 0, 2, y, 0, 1)

        // limb0 = FFFF_FFFF + 1 => 0 carry1
        // limb1 = FFFF_FFFF + carry => 0 carry1
        // final limb2 = 1
        assertEquals(3, len)
        assertEquals(0, z[0])
        assertEquals(0, z[1])
        assertEquals(1, z[2])
    }

    @Test
    fun rejects_out_of_bounds_x() {
        val z = intArrayOf(0,0)
        val x = intArrayOf(1)

        assertFailsWith<IllegalArgumentException> {
            Mago.setAdd(z, 0, x, 1, 1, x, 0, 1)
        }
    }

    @Test
    fun rejects_out_of_bounds_z() {
        val z = intArrayOf(0)
        val x = intArrayOf(1)

        assertFailsWith<IllegalArgumentException> {
            // z must have 2 limbs to handle overflow from 1 limb + 1 limb
            Mago.setAdd(z, 0, x, 0, 1, x, 0, 1)
        }
    }

    @Test
    fun rejects_negative_lengths() {
        val z = intArrayOf(0,0)
        val x = intArrayOf(1)

        assertFailsWith<IllegalArgumentException> {
            Mago.setAdd(z, 0, x, 0, -1, x, 0, 1)
        }
    }
}
