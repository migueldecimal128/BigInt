package com.decimal128.bigint

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMbiSetDiv {

    @Test
    fun setDiv_basic() {
        val xBi = "16943852051772892430707956759219".toBigInt()
        val x = xBi.toMutableBigInt()
        val y = 16883797134507450982uL.toBigInt()

        val out = MutableBigInt()
        out.setDiv(x, y)

        assertEquals(xBi / y, out.toBigInt())
    }

    @Test
    fun setDiv_alias_out_is_x() {
        val biX = "16943852051772892430707956759219".toBigInt()
        val x = MutableBigInt()
        val biY = 16883797134507450982uL.toBigInt()
        val y = biY.toMutableBigInt()

        x.set(biX)
        x.setDiv(x, biY)
        assertEquals(biX / biY, x.toBigInt())

        x.set(biX)
        x /= biY
        assertEquals(biX / biY, x.toBigInt())

        x.set(biX)
        x.setDiv(x, y)
        assertEquals(biX/ biY, x.toBigInt())

        x.set(biX)
        x /= y
        assertEquals(biX/ biY, x.toBigInt())
    }

    @Test
    fun setDiv_alias_out_is_divisor() {
        val xBi = "123456789012345678901234567890".toBigInt()
        val x = xBi.toMutableBigInt()

        val yBi = "9876543210987654321".toBigInt()
        val y = yBi.toMutableBigInt()

        y.setDiv(x, y)

        assertEquals(xBi / yBi, y.toBigInt())
        assertEquals(xBi, x.toBigInt())
    }

    @Test
    fun setDiv_alias_small_divisor() {
        val xBi = "987654321098765432109876543210".toBigInt()
        val x = xBi.toMutableBigInt()
        val y = 97.toBigInt()

        x.setDiv(x, y)

        assertEquals(xBi / y, x.toBigInt())
    }

    @Test
    fun setDiv_invariant_reconstruction() {
        val xBi = "16943852051772892430707956759219".toBigInt()
        val x = xBi.toMutableBigInt()
        val y = "16883797134507450982".toBigInt()

        val q = MutableBigInt()
        q.setDiv(x, y)

        val r = MutableBigInt()
        r.setRem(x, y)

        assertEquals(xBi, q.toBigInt() * y + r.toBigInt())
    }

    @Test
    fun setDiv_random_aliasing() {
        val rnd = Random(2)

        repeat(1_000) {
            val a =
                rnd.nextLong().toBigInt().abs() +
                        rnd.nextLong().toBigInt().abs().shl(64)

            val b = rnd.nextLong()
                .let { if (it == 0L) 1L else it }
                .toBigInt()
                .abs()

            val x = a.toMutableBigInt()
            val ref = a / b

            // out === x
            x.setDiv(x, b)

            assertEquals(ref, x.toBigInt())
        }
    }

}