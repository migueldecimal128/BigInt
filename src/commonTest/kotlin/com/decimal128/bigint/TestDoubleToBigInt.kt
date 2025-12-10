package com.decimal128.bigint

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestDoubleToBigInt {

    val casesZero = doubleArrayOf(
        0.0, -0.0,
        0.1, -0.1,
        0.99999999999,
        Double.MIN_VALUE,

        Double.NaN,
        Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY
    )

    @Test
    fun testZeros() {
        for (tc in casesZero) {
            assertTrue(tc.toBigInt().isZero())
        }
    }

    val casesLong = doubleArrayOf(
        1.0, 2.0, (1L shl 53).toDouble(), 123456789012345.0
    )

    @Test
    fun testLongs() {
        for (tc in casesLong) {
            val l = tc.toLong()
            val bi = tc.toBigInt()
            assertTrue(l EQ bi)
        }
    }

    @Test
    fun testPow2() {
        var d = 1.0
        for (i in 1..<1023) {
            d *= 2.0
            val hi = d.toBigInt()
            assertTrue(hi.isMagnitudePowerOfTwo())
            assertTrue(hi.testBit(i))
        }
    }

    @Test
    fun finiteSimpleIntegers() {
        val values = listOf(
            0.0,
            -0.0,
            1.0,
            -1.0,
            2.0,
            -2.0,
            123.0,
            -123.0,
            9_007_199_254_740_992.0,      // 2^53, largest integer exactly representable
            -9_007_199_254_740_992.0
        )
        for (d in values) {
            assertEquals(
                d.toLong(),
                BigInt.from(d).toLong()
            )
        }
    }

    @Test
    fun fractionalTruncation() {
        val cases = listOf(
            1.5 to 1,
            -1.5 to -1,
            123.9999 to 123,
            -123.9999 to -123
        )
        for ((dbl, expected) in cases) {
            assertEquals(
                BigInt.from(expected).toString(),
                BigInt.from(dbl).toString()
            )
        }
    }

    @Test
    fun nonFiniteAreZero() {
        assertEquals(BigInt.ZERO, BigInt.from(Double.NaN))
        assertEquals(BigInt.ZERO, BigInt.from(Double.POSITIVE_INFINITY))
        assertEquals(BigInt.ZERO, BigInt.from(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun randomValues() {
        repeat(10_000) {
            val d = Random.nextDouble(-1e300, 1e300)

            if (!d.isFinite()) {
                assertEquals(BigInt.ZERO, BigInt.from(d))
                return@repeat
            }

            val truncated = if (d >= 0) kotlin.math.floor(d) else kotlin.math.ceil(d)
            val bi = BigInt.from(d).toString()

            // Compare string forms because the truncated value may exceed Long range
            assertEquals(BigInt.from(truncated).toString(), bi)
        }
    }

    @Test
    fun largeMagnitudeBoundary() {
        // Uses all 52 mantissa bits + exponent expansion
        val d = Double.fromBits(0x7FEFFFFF_FFFFFFFFL) // largest finite double
        val bi = BigInt.from(d)

        val truncated = if (d >= 0) kotlin.math.floor(d) else kotlin.math.ceil(d)
        assertEquals(BigInt.from(truncated).toString(), bi.toString())
    }


}