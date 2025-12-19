package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigInt.Companion.ONE
import com.decimal128.bigint.BigInt.Companion.ZERO
import com.decimal128.bigint.MutableBigInt
import com.decimal128.bigint.toBigInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestModPow {

    @Test
    fun modPow_smallValues() {
        val m = 11.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modPow(2.toBigInt(), 10.toBigInt(), out)
        // 2^10 = 1024 ≡ 1 (mod 11)
        assertEquals(1.toBigInt(), out.toBigInt())
    }

    @Test
    fun modPow_expZero() {
        val m = 97.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modPow(42.toBigInt(), ZERO, out)
        assertEquals(ONE, out.toBigInt())
    }

    @Test
    fun modPow_expOne() {
        val m = 101.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modPow(77.toBigInt(), ONE, out)
        assertEquals(77.toBigInt(), out.toBigInt())
    }

    @Test
    fun modPow_baseZero() {
        val m = 13.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modPow(BigInt.ZERO, 5.toBigInt(), out)
        assertEquals(BigInt.ZERO, out.toBigInt())
    }

    @Test
    fun modPow_baseOne() {
        val m = 13.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modPow(BigInt.ONE, 12345.toBigInt(), out)
        assertEquals(BigInt.ONE, out.toBigInt())
    }

    @Test
    fun modPow_negativeExponent_throws() {
        val m = 17.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        assertFailsWith<IllegalArgumentException> {
            ctx.modPow(3.toBigInt(), (-1).toBigInt(), out)
        }
    }

    @Test
    fun modPow_resultIsReduced() {
        val m = 97.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modPow(123456.toBigInt(), 789.toBigInt(), out)

        val r = out.toBigInt()
        assertTrue(r >= BigInt.ZERO)
        assertTrue(r < m)
    }

}