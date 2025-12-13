package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestBigIntAccBitOps {

    private fun accOf(i: Long) = BigIntAccumulator().set(i)
    private fun bi(i: Long) = BigInt.from(i)

    // ----------------------------------------
    // Basic: setBit, clearBit, testBit
    // ----------------------------------------

    @Test
    fun testSetBitOnZero() {
        for (i in 0..200 step 17) {
            val acc = BigIntAccumulator().setZero().setBit(i)
            val expected = BigInt.withSetBit(i)
            assertEquals(expected, acc.toBigInt(), "setBit($i) on zero")
            assertTrue(acc.testBit(i))
        }
    }

    @Test
    fun testClearBitOnZero() {
        for (i in 0..200 step 17) {
            val acc = BigIntAccumulator().setZero().clearBit(i)
            assertEquals(BigInt.ZERO, acc.toBigInt(), "clearBit($i) on zero")
            assertFalse(acc.testBit(i))
        }
    }

    @Test
    fun testSetThenClearSameBit() {
        for (i in 0..150) {
            val acc = BigIntAccumulator().setZero()
            acc.setBit(i)
            acc.clearBit(i)

            assertEquals(BigInt.ZERO, acc.toBigInt(), "setBit($i) then clearBit($i)")
            assertFalse(acc.testBit(i))
        }
    }

    @Test
    fun testClearThenSetSameBit() {
        for (i in 0..150) {
            val acc = BigIntAccumulator().setOne().clearBit(0).setBit(0)
            assertEquals(BigInt.ONE, acc.toBigInt(), "clearBit(0) then setBit(0)")
        }
    }

    // ----------------------------------------
    // Setting bits at & beyond current normLen
    // ----------------------------------------

    @Test
    fun testSetBitExpandsMagnitude() {
        var acc = BigIntAccumulator().set(5)  // 0b101
        acc = acc.setBit(10)                 // should grow into limb 0..10 bits

        val expected = bi(5).withSetBit(10)
        assertEquals(expected, acc.toBigInt(), "setBit expanded magnitude")
        assertEquals(11, acc.toBigInt().magnitudeBitLen())
    }

    @Test
    fun testSetBitInsideExistingRange() {
        val acc = accOf(0b1010).setBit(2) // already set? No -> becomes 1110
        val expected = bi(0b1110)
        assertEquals(expected, acc.toBigInt(), "setBit inside range")
    }

    // ----------------------------------------
    // Clearing bits reduces magnitude
    // ----------------------------------------

    @Test
    fun testClearHighestBitReducesNormLen() {
        val acc = accOf(1L shl 40).clearBit(40)
        assertEquals(BigInt.ZERO, acc.toBigInt(), "clearing highest bit should normalize to zero")
    }

    @Test
    fun testClearBitInsideMiddleDoesNotReduceNormLen() {
        val x = (1L shl 40) or (1L shl 20) or 1L
        val acc = accOf(x).clearBit(20)

        val expected = bi(x and (1L shl 20).inv())
        assertEquals(expected, acc.toBigInt())
    }

    // ----------------------------------------
    // Randomized tests for many bits
    // ----------------------------------------

    @Test
    fun testRandomSetClearBits() {
        repeat(200) {
            val acc = BigIntAccumulator().setZero()
            var ref = BigInt.ZERO

            repeat(50) {
                val b = (0..300).random()

                if ((0..1).random() == 0) {
                    acc.setBit(b)
                    ref = ref.withSetBit(b)
                } else {
                    acc.clearBit(b)
                    ref = ref.withClearBit(b)
                }

                assertEquals(ref, acc.toBigInt(), "random bit op at $b")
            }
        }
    }

    // ----------------------------------------
    // testBit behavior
    // ----------------------------------------

    @Test
    fun testTestBitMatchesReference() {
        repeat(200) {
            var acc = BigIntAccumulator().setZero()
            var ref = BigInt.ZERO

            repeat(50) {
                val b = (0..250).random()
                acc.setBit(b)
                ref = ref.withSetBit(b)

                assertTrue(acc.testBit(b))
                assertEquals(ref.testBit(b), acc.testBit(b))
            }

            repeat(50) {
                val b = (0..250).random()
                acc.clearBit(b)
                ref = ref.withClearBit(b)

                assertEquals(ref.testBit(b), acc.testBit(b))
            }
        }
    }
}