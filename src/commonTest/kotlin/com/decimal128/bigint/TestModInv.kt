package com.decimal128.bigint

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigInt.Companion.ONE
import com.decimal128.bigint.MutableBigInt
import com.decimal128.bigint.toBigInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestModInv {

    @Test
    fun modInv_basicSmall() {
        val m = 11.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modInv(7.toBigInt(), out)
        // 7 * 8 ≡ 1 (mod 11)
        assertEquals(8.toBigInt(), out.toBigInt())
    }

    @Test
    fun modInv_ofOne() {
        val m = 97.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modInv(ONE, out)
        assertEquals(ONE, out.toBigInt())
    }

    @Test
    fun modInv_negativeResultNormalized() {
        val m = 13.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        // inverse of 10 mod 13 is 4
        ctx.modInv(10.toBigInt(), out)
        assertEquals(4.toBigInt(), out.toBigInt())
    }

    @Test
    fun modInv_notInvertible_throws() {
        val m = 21.toBigInt() // 3 * 7
        val ctx = ModContext(m)
        val out = MutableBigInt()

        val ex = assertFailsWith<ArithmeticException> {
            ctx.modInv(14.toBigInt(), out) // gcd(14, 21) = 7
        }

        assertTrue(ex.message!!.contains("invertible"))
    }

    @Test
    fun modInv_zero_throws() {
        val m = 17.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        assertFailsWith<ArithmeticException> {
            ctx.modInv(BigInt.ZERO, out)
        }
    }


}