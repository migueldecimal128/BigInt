package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInt
import com.decimal128.bigint.BigIntExtensions.toBigInteger
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntAccShift {

    private fun bi(l: Long): BigInt = l.toBigInt()

    private fun BigInt.toJava(): BigInteger =
        BigInteger(this.toTwosComplementBigEndianByteArray())   // or your own converter

    // Java BigInteger logical right shift:
    private fun BigInteger.ushr(n: Int): BigInteger =
        if (this.signum() >= 0) this.shiftRight(n)
        else {
            // Convert to unsigned 2's-complement representation
            val bitLen = this.bitLength().coerceAtLeast(1)
            val twoPow = BigInteger.ONE.shiftLeft(bitLen)
            val unsigned = this + twoPow
            unsigned.shiftRight(n)
        }

    // ----------------------------------------------------------------------
    // 1. Deterministic sanity tests
    // ----------------------------------------------------------------------

    @Test
    fun testShl_basic() {
        val values = listOf(-5L, -1L, 0L, 1L, 2L, 7L, 123456789L)
        val shifts = listOf(0, 1, 2, 5, 31, 32, 63, 64, 100)

        for (v in values) {
            for (s in shifts) {
                val a = bi(v)
                val actual = a shl s
                val expected = BigInteger.valueOf(v).shiftLeft(s)
                assertEquals(expected, actual.toJava(), "shl failed for $v << $s")
            }
        }
    }

    @Test
    fun testShr_basic() {
        val values = listOf(-500L, -3L, -1L, 0L, 1L, 2L, 500L, 123456789L)
        val shifts = listOf(0, 1, 2, 5, 31, 32, 63, 64, 100)

        for (v in values) {
            for (s in shifts) {
                val a = bi(v)
                val actual = a shr s
                val expected = BigInteger.valueOf(v).shiftRight(s) // arithmetic
                assertEquals(expected, actual.toJava(), "shr failed for $v >> $s")
            }
        }
    }

    @Test
    fun testUshr_basic() {
        val values = listOf(
            0L,
            1L,
            2L,
            5L,
            123456789L,
            Long.MAX_VALUE       // still non-negative ;)
        )

        val shifts = listOf(0, 1, 2, 5, 31, 32, 63, 64, 100)


        for (v in values) {
            val a = bi(v)
            for (s in shifts) {
                val actual = a.ushr(s)
                val expected = BigInteger.valueOf(v).ushr(s)
                assertEquals(expected, actual.toJava(), "ushr failed for $v >>> $s")
            }
        }
    }

    // ----------------------------------------------------------------------
    // 2. Random stress testing
    // ----------------------------------------------------------------------

    @Test
    fun testShl_random() {
        repeat(5000) {
            val bitLen = Random.nextInt(1, 500)
            val shift = Random.nextInt(0, 500)
            val j = BigInteger(bitLen, java.util.Random())
            val a = j.toBigInt()

            val actual = a shl shift
            val expected = j.shiftLeft(shift)

            assertEquals(expected, actual.toBigInteger(), "random shl failed")
        }
    }

    @Test
    fun testShr_random() {
        repeat(5000) {
            val bitLen = Random.nextInt(1, 500)
            val shift = Random.nextInt(0, 500)
            val j = BigInteger(bitLen, java.util.Random()).subtract(BigInteger.ONE.shiftLeft(bitLen - 1)) // random signed
            val a = j.toBigInt()

            val actual = a shr shift
            val expected = j.shiftRight(shift) // arithmetic

            assertEquals(expected, actual.toBigInteger(), "random shr failed")
        }
    }

    @Test
    fun testUshr_random() {
        repeat(5000) {
            val bitLen = Random.nextInt(1, 500)
            val shift = Random.nextInt(0, 500)
            val j = BigInteger(bitLen, java.util.Random()) // non-negative
            val a = j.toBigInt()

            val actual = a.ushr(shift)
            val expected = j.ushr(shift) // custom logical shift

            assertEquals(expected, actual.toJava(), "random ushr failed")
        }
    }
}