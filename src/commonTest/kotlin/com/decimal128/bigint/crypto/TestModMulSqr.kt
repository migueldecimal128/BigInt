package com.decimal128.bigint.crypto

import com.decimal128.bigint.MutableBigInt
import com.decimal128.bigint.toBigInt
import kotlin.test.Test
import kotlin.test.assertEquals

class TestModMulSqr {

    @Test
    fun modMul_basicSmall() {
        val m = 97.toBigInt()
        val ctx = ModContext(m)

        val out = MutableBigInt()

        ctx.modMul(3.toBigInt(), 5.toBigInt(), out)
        assertEquals(15.toBigInt(), out.toBigInt())

        ctx.modMul(20.toBigInt(), 5.toBigInt(), out)
        assertEquals((20 * 5 % 97).toBigInt(), out.toBigInt())
    }

    @Test
    fun modMul_withReduction() {
        val m = 11.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modMul(7.toBigInt(), 8.toBigInt(), out)
        assertEquals((7 * 8 % 11).toBigInt(), out.toBigInt())
    }

    @Test
    fun modMul_zeroAndOne() {
        val m = 101.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modMul(0.toBigInt(), 42.toBigInt(), out)
        assertEquals(0.toBigInt(), out.toBigInt())

        ctx.modMul(1.toBigInt(), 42.toBigInt(), out)
        assertEquals(42.toBigInt(), out.toBigInt())
    }


    @Test
    fun modSqr_basic() {
        val m = 97.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modSqr(5.toBigInt(), out)
        assertEquals(25.toBigInt(), out.toBigInt())
    }

    @Test
    fun modSqr_withReduction() {
        val m = 13.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modSqr(7.toBigInt(), out)
        assertEquals((7 * 7 % 13).toBigInt(), out.toBigInt())
    }

    @Test
    fun modSqr_zeroAndOne() {
        val m = 101.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modSqr(0.toBigInt(), out)
        assertEquals(0.toBigInt(), out.toBigInt())

        ctx.modSqr(1.toBigInt(), out)
        assertEquals(1.toBigInt(), out.toBigInt())
    }

}