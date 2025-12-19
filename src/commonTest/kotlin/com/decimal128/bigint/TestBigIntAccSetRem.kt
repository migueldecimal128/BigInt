package com.decimal128.bigint

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestBigIntAccSetRem {

    @Test
    fun setRem_basic() {
        val x = "16943852051772892430707956759219".toMutableBigInt()
        val y = 16883797134507450982uL.toBigInt()

        val out = MutableBigInt()
        out.setRem(x, y)

        assertEquals(x.toBigInt() % y, out.toBigInt())
    }

    @Test
    fun setRem_alias_out_is_x() {
        val biX = "16943852051772892430707956759219".toBigInt()
        val x = MutableBigInt()
        val biY = 16883797134507450982uL.toBigInt()
        val y = biY.toMutableBigInt()

        x.set(biX)
        x.setRem(x, biY)
        assertEquals(biX % biY, x.toBigInt())

        x.set(biX)
        x %= biY
        assertEquals(biX % biY, x.toBigInt())

        x.set(biX)
        x.setRem(x, y)
        assertEquals(biX % biY, x.toBigInt())

        x.set(biX)
        x %= y
        assertEquals(biX % biY, x.toBigInt())
    }

    @Test
    fun setRem_alias_out_is_y() {
        val xBi = "123456789012345678901234567890".toBigInt()
        val x = xBi.toMutableBigInt()
        val yBi = "9876543210987654321".toBigInt()
        val y = "9876543210987654321".toMutableBigInt()

        y.setRem(x, y)

        assertEquals(xBi % yBi, y.toBigInt())
        assertEquals(xBi, x.toBigInt())
    }


    @Test
    fun setRem_alias_x_mod_x_is_zero() {
        val x = "123456789012345678901234567890".toMutableBigInt()

        x.setRem(x, x.toBigInt())

        assertTrue(x.toBigInt().isZero())
    }

    @Test
    fun setRem_alias_small_divisor() {
        val x = "987654321098765432109876543210".toMutableBigInt()
        val y = 97.toBigInt()

        x.setRem(x, y)

        assertEquals("987654321098765432109876543210".toBigInt() % y, x.toBigInt())
    }

    @Test
    fun setRem_invariants() {
        val x = "16943852051772892430707956759219".toMutableBigInt()
        val y = "16883797134507450982".toBigInt()

        val out = MutableBigInt()
        out.setRem(x, y)

        val r = out.toBigInt()
        assertTrue(r >= BigInt.ZERO)
        assertTrue(r < y.abs())
    }

    @Test
    fun setRem_random_aliasing() {
        val rnd = Random(1)

        repeat(1_000) {
            val a =
                rnd.nextLong().toBigInt().abs() +
                        rnd.nextLong().toBigInt().abs().shl(64)

            val b = rnd.nextLong()
                .let { if (it == 0L) 1L else it }
                .toBigInt()
                .abs()

            val x = a.toMutableBigInt()
            val ref = a % b

            // out === x
            x.setRem(x, b)

            assertEquals(ref, x.toBigInt())
        }
    }



}