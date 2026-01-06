package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigInteger
import kotlin.random.Random

class TestMbiBooleanOps {

    @Test
    fun testSetAndWithZeroValues() {
        val m = MutableBigInt()
        val zero = BigInt.ZERO
        val nonZero = 123.toBigInt()

        // zero AND zero = zero
        m.setAnd(zero, zero)
        assertEquals(BigInteger.ZERO, m.toBigInteger())

        // zero AND nonZero = zero
        m.setAnd(zero, nonZero)
        assertEquals(BigInteger.ZERO, m.toBigInteger())

        // nonZero AND zero = zero
        m.setAnd(nonZero, zero)
        assertEquals(BigInteger.ZERO, m.toBigInteger())
    }

    @Test
    fun testSetOrWithZeroValues() {
        val m = MutableBigInt()
        val zero = BigInt.ZERO
        val value = 123.toBigInt()

        // zero OR zero = zero
        m.setOr(zero, zero)
        assertEquals(BigInteger.ZERO, m.toBigInteger())

        // zero OR value = value
        m.setOr(zero, value)
        assertEquals(value.toBigInteger(), m.toBigInteger())

        // value OR zero = value
        m.setOr(value, zero)
        assertEquals(value.toBigInteger(), m.toBigInteger())
    }

    @Test
    fun testSetXorWithZeroValues() {
        val m = MutableBigInt()
        val zero = BigInt.ZERO
        val value = 123.toBigInt()

        // zero XOR zero = zero
        m.setXor(zero, zero)
        assertEquals(BigInteger.ZERO, m.toBigInteger())

        // zero XOR value = value
        m.setXor(zero, value)
        assertEquals(value.toBigInteger(), m.toBigInteger())

        // value XOR zero = value
        m.setXor(value, zero)
        assertEquals(value.toBigInteger(), m.toBigInteger())
    }

    @Test
    fun testSetAndWithSimpleValues() {
        val m = MutableBigInt()

        val testCases = listOf(
            Triple(0xFFu, 0x0Fu, 0xF0u),
            Triple(0xFFFFu, 0xFF00u, 0x00FFu),
            Triple(0x12345678u, 0x87654321u, 0x95511559u),
            Triple(1u, 1u, 0u), // cancels to zero
            Triple(UInt.MAX_VALUE, UInt.MAX_VALUE, 0) // cancels to zero
        )

        for ((xVal, yVal, expected) in testCases) {
            val x = xVal.toBigInt()
            val y = yVal.toBigInt()

            m.setAnd(x, y)

            val expectedBig = x.toBigInteger().and(y.toBigInteger())
            assertEquals(expectedBig, m.toBigInteger(),
                "Failed for $xVal AND $yVal")
        }
    }

    @Test
    fun testSetOrWithSimpleValues() {
        val m = MutableBigInt()

        val testCases = listOf(
            Triple(0xFFu, 0x0Fu, 0xF0u),
            Triple(0xFFFFu, 0xFF00u, 0x00FFu),
            Triple(0x12345678u, 0x87654321u, 0x95511559u),
            Triple(1u, 1u, 0u), // cancels to zero
            Triple(UInt.MAX_VALUE, UInt.MAX_VALUE, 0) // cancels to zero
        )

        for ((xVal, yVal, _) in testCases) {
            val x = xVal.toBigInt()
            val y = yVal.toBigInt()

            m.setOr(x, y)

            val expectedBig = x.toBigInteger().or(y.toBigInteger())
            assertEquals(expectedBig, m.toBigInteger(),
                "Failed for $xVal OR $yVal")
        }
    }

    @Test
    fun testSetXorWithSimpleValues() {
        val m = MutableBigInt()

        val testCases = listOf(
            Triple(0xFFu, 0x0Fu, 0xF0u),
            Triple(0xFFFFu, 0xFF00u, 0x00FFu),
            Triple(0x12345678u, 0x87654321u, 0x95511559u),
            Triple(1u, 1u, 0u), // cancels to zero
            Triple(UInt.MAX_VALUE, UInt.MAX_VALUE, 0) // cancels to zero
        )

        for ((xVal, yVal, _) in testCases) {
            val x = xVal.toBigInt()
            val y = yVal.toBigInt()

            m.setXor(x, y)

            val expectedBig = x.toBigInteger().xor(y.toBigInteger())
            assertEquals(expectedBig, m.toBigInteger(),
                "Failed for $xVal XOR $yVal")
        }
    }

    @Test
    fun testMutAndInPlaceOperation() {
        val m = MutableBigInt(0xFF)
        val y = 0x0F.toBigInt()

        m.mutAnd(y)

        assertEquals(BigInteger.valueOf(0x0F), m.toBigInteger())
    }

    @Test
    fun testMutOrInPlaceOperation() {
        val m = MutableBigInt(0xF0)
        val y = 0x0F.toBigInt()

        m.mutOr(y)

        assertEquals(BigInteger.valueOf(0xFF), m.toBigInteger())
    }

    @Test
    fun testMutXorInPlaceOperation() {
        val m = MutableBigInt(0xFF)
        val y = 0x0F.toBigInt()

        m.mutXor(y)

        assertEquals(BigInteger.valueOf(0xF0), m.toBigInteger())
    }

    @Test
    fun testSetAndWithRandomExactBitLengths() {
        val m = MutableBigInt()

        repeat(100) { iteration ->
            val bitLen = Random.nextInt(1, 640)
            val x = BigInt.randomWithBitLen(bitLen)
            val y = BigInt.randomWithBitLen(bitLen)

            m.setAnd(x, y)

            val expected = x.toBigInteger().and(y.toBigInteger())
            assertEquals(expected, m.toBigInteger(),
                "Failed on iteration $iteration with bitLen=$bitLen")
        }
    }

    @Test
    fun testSetOrWithRandomExactBitLengths() {
        val m = MutableBigInt()

        repeat(100) { iteration ->
            val bitLen = Random.nextInt(1, 640)
            val x = BigInt.randomWithBitLen(bitLen)
            val y = BigInt.randomWithBitLen(bitLen)

            m.setOr(x, y)

            val expected = x.toBigInteger().or(y.toBigInteger())
            assertEquals(expected, m.toBigInteger(),
                "Failed on iteration $iteration with bitLen=$bitLen")
        }
    }

    @Test
    fun testSetXorWithRandomExactBitLengths() {
        val m = MutableBigInt()

        repeat(100) { iteration ->
            val bitLen = Random.nextInt(1, 640)
            val x = BigInt.randomWithBitLen(bitLen)
            val y = BigInt.randomWithBitLen(bitLen)

            m.setXor(x, y)

            val expected = x.toBigInteger().xor(y.toBigInteger())
            assertEquals(expected, m.toBigInteger(),
                "Failed on iteration $iteration with bitLen=$bitLen")
        }
    }

    @Test
    fun testSetAndWithRandomMaxBitLengths() {
        val m = MutableBigInt()

        repeat(100) { iteration ->
            val maxBitLen = Random.nextInt(1, 640)
            val x = BigInt.randomWithMaxBitLen(maxBitLen)
            val y = BigInt.randomWithMaxBitLen(maxBitLen)

            m.setAnd(x, y)

            val expected = x.toBigInteger().and(y.toBigInteger())
            assertEquals(expected, m.toBigInteger(),
                "Failed on iteration $iteration with maxBitLen=$maxBitLen")
        }
    }

    @Test
    fun testSetOrWithRandomMaxBitLengths() {
        val m = MutableBigInt()

        repeat(100) { iteration ->
            val maxBitLen = Random.nextInt(1, 640)
            val x = BigInt.randomWithMaxBitLen(maxBitLen)
            val y = BigInt.randomWithMaxBitLen(maxBitLen)

            m.setOr(x, y)

            val expected = x.toBigInteger().or(y.toBigInteger())
            assertEquals(expected, m.toBigInteger(),
                "Failed on iteration $iteration with maxBitLen=$maxBitLen")
        }
    }

    @Test
    fun testSetXorWithRandomMaxBitLengths() {
        val m = MutableBigInt()

        repeat(100) { iteration ->
            val maxBitLen = Random.nextInt(1, 640)
            val x = BigInt.randomWithMaxBitLen(maxBitLen)
            val y = BigInt.randomWithMaxBitLen(maxBitLen)

            m.setXor(x, y)

            val expected = x.toBigInteger().xor(y.toBigInteger())
            assertEquals(expected, m.toBigInteger(),
                "Failed on iteration $iteration with maxBitLen=$maxBitLen")
        }
    }

    @Test
    fun testSetAndWithDifferentLengthOperands() {
        val m = MutableBigInt()

        repeat(50) { iteration ->
            val maxBitLen1 = Random.nextInt(32, 640)
            val maxBitLen2 = Random.nextInt(32, 640)

            val x = BigInt.randomWithRandomBitLen(maxBitLen1)
            val y = BigInt.randomWithRandomBitLen(maxBitLen2)

            m.setAnd(x, y)

            val expected = x.toBigInteger().and(y.toBigInteger())
            assertEquals(expected, m.toBigInteger(),
                "Failed on iteration $iteration")
        }
    }

    @Test
    fun testSetOrWithDifferentLengthOperands() {
        val m = MutableBigInt()

        repeat(50) { iteration ->
            val maxBitLen1 = Random.nextInt(32, 640)
            val maxBitLen2 = Random.nextInt(32, 640)

            val x = BigInt.randomWithRandomBitLen(maxBitLen1)
            val y = BigInt.randomWithRandomBitLen(maxBitLen2)

            m.setOr(x, y)

            val expected = x.toBigInteger().or(y.toBigInteger())
            assertEquals(expected, m.toBigInteger(),
                "Failed on iteration $iteration")
        }
    }

    @Test
    fun testSetXorWithDifferentLengthOperands() {
        val m = MutableBigInt()

        repeat(50) { iteration ->
            val maxBitLen1 = Random.nextInt(32, 640)
            val maxBitLen2 = Random.nextInt(32, 640)

            val x = BigInt.randomWithRandomBitLen(maxBitLen1)
            val y = BigInt.randomWithRandomBitLen(maxBitLen2)

            m.setXor(x, y)

            val expected = x.toBigInteger().xor(y.toBigInteger())
            assertEquals(expected, m.toBigInteger(),
                "Failed on iteration $iteration")
        }
    }

    @Test
    fun testXorCancellationWithEqualValues() {
        val m = MutableBigInt()
        val x = BigInt.randomWithBitLen(256)

        m.setXor(x, x)

        assertEquals(BigInteger.ZERO, m.toBigInteger(),
            "XOR of equal values should be zero")
    }

    @Test
    fun testXorPartialCancellation() {
        val m = MutableBigInt()

        // Create two values that are equal in high-order limbs
        val x = BigInt.randomWithBitLen(320)
        val y = BigInt.randomWithBitLen(320)

        // Force the high limb to be equal (will cancel in XOR)
        val xCopy = x.toMutableBigInt()
        xCopy.magia[xCopy.meta.normLen - 1] = y.magia[y.meta.normLen - 1]
        val xModified = xCopy.toBigInt()

        m.setXor(xModified, y)

        val expected = xModified.toBigInteger().xor(y.toBigInteger())
        assertEquals(expected, m.toBigInteger())
        assertTrue(m.meta.normLen < xModified.meta.normLen,
            "XOR should have reduced length due to cancellation")
    }

    @Test
    fun testAliasingSetAndWithSelf() {
        val m = MutableBigInt(0xFF)

        m.setAnd(m, m)

        assertEquals(BigInteger.valueOf(0xFF), m.toBigInteger(),
            "AND with self should equal self")
    }

    @Test
    fun testAliasingSetOrWithSelf() {
        val m = MutableBigInt(0xFF)

        m.setOr(m, m)

        assertEquals(BigInteger.valueOf(0xFF), m.toBigInteger(),
            "OR with self should equal self")
    }

    @Test
    fun testAliasingSetXorWithSelf() {
        val m = MutableBigInt(0xFF)

        m.setXor(m, m)

        assertEquals(BigInteger.ZERO, m.toBigInteger(),
            "XOR with self should be zero")
    }

    @Test
    fun testChainingOperations() {
        val m = MutableBigInt()
        val a = 0xFF.toBigInt()
        val b = 0x0F.toBigInt()
        val c = 0xF0.toBigInt()

        // (a AND b) OR c
        m.setAnd(a, b).mutOr(c)

        val expected = a.toBigInteger()
            .and(b.toBigInteger())
            .or(c.toBigInteger())

        assertEquals(expected, m.toBigInteger())
    }

    @Test
    fun testMemoryReuseAcrossOperations() {
        val m = MutableBigInt()

        // Start with a large value
        val large = BigInt.randomWithBitLen(640)
        m.setOr(large, large)
        val capacityAfterLarge = m.magia.size

        // Do an operation with smaller values
        val small1 = 100.toBigInt()
        val small2 = 200.toBigInt()
        m.setAnd(small1, small2)

        // Capacity should be reused, not shrunk
        assertTrue(m.magia.size >= capacityAfterLarge,
            "Capacity should be reused")

        val expected = small1.toBigInteger().and(small2.toBigInteger())
        assertEquals(expected, m.toBigInteger())
    }

    @Test
    fun testBooleanIdentities() {
        val m = MutableBigInt()

        repeat(20) {
            val x = BigInt.randomWithRandomBitLen(320)
            val y = BigInt.randomWithRandomBitLen(320)

            // (x OR y) AND x = x
            m.setOr(x, y)
            m.mutAnd(x)
            assertEquals(x.toBigInteger(), m.toBigInteger(),
                "Identity: (x OR y) AND x = x")

            // (x AND y) OR x = x
            m.setAnd(x, y)
            m.mutOr(x)
            assertEquals(x.toBigInteger(), m.toBigInteger(),
                "Identity: (x AND y) OR x = x")

            // (x XOR y) XOR y = x
            m.setXor(x, y)
            m.mutXor(y)
            assertEquals(x.toBigInteger(), m.toBigInteger(),
                "Identity: (x XOR y) XOR y = x")
        }
    }

    @Test
    fun testCommutativeProperty() {
        val m1 = MutableBigInt()
        val m2 = MutableBigInt()

        repeat(20) {
            val x = BigInt.randomWithRandomBitLen(256)
            val y = BigInt.randomWithRandomBitLen(256)

            // AND is commutative
            m1.setAnd(x, y)
            m2.setAnd(y, x)
            assertEquals(m1.toBigInteger(), m2.toBigInteger(),
                "AND should be commutative")

            // OR is commutative
            m1.setOr(x, y)
            m2.setOr(y, x)
            assertEquals(m1.toBigInteger(), m2.toBigInteger(),
                "OR should be commutative")

            // XOR is commutative
            m1.setXor(x, y)
            m2.setXor(y, x)
            assertEquals(m1.toBigInteger(), m2.toBigInteger(),
                "XOR should be commutative")
        }
    }

    @Test
    fun testAssociativeProperty() {
        val m1 = MutableBigInt()
        val m2 = MutableBigInt()
        val temp = MutableBigInt()

        repeat(20) {
            val x = BigInt.randomWithRandomBitLen(192)
            val y = BigInt.randomWithRandomBitLen(192)
            val z = BigInt.randomWithRandomBitLen(192)

            // (x AND y) AND z = x AND (y AND z)
            temp.setAnd(x, y)
            m1.setAnd(temp, z)
            temp.setAnd(y, z)
            m2.setAnd(x, temp)
            assertEquals(m1.toBigInteger(), m2.toBigInteger(),
                "AND should be associative")

            // (x OR y) OR z = x OR (y OR z)
            temp.setOr(x, y)
            m1.setOr(temp, z)
            temp.setOr(y, z)
            m2.setOr(x, temp)
            assertEquals(m1.toBigInteger(), m2.toBigInteger(),
                "OR should be associative")

            // (x XOR y) XOR z = x XOR (y XOR z)
            temp.setXor(x, y)
            m1.setXor(temp, z)
            temp.setXor(y, z)
            m2.setXor(x, temp)
            assertEquals(m1.toBigInteger(), m2.toBigInteger(),
                "XOR should be associative")
        }
    }
}