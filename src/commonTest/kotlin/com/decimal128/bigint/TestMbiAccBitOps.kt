package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestMbiAccBitOps {

    private fun accOf(i: Long) = MutableBigInt().set(i)
    private fun bi(i: Long) = BigInt.from(i)

    // ----------------------------------------
    // Basic: setBit, clearBit, testBit
    // ----------------------------------------

    @Test
    fun testSetBitOnZero() {
        for (i in 0..200 step 17) {
            val mbi = MutableBigInt().setZero().setBit(i)
            val expected = BigInt.withSetBit(i)
            assertEquals(expected, mbi.toBigInt(), "setBit($i) on zero")
            assertTrue(mbi.testBit(i))
        }
    }

    @Test
    fun testClearBitOnZero() {
        for (i in 0..200 step 17) {
            val mbi = MutableBigInt().setZero().clearBit(i)
            assertEquals(BigInt.ZERO, mbi.toBigInt(), "clearBit($i) on zero")
            assertFalse(mbi.testBit(i))
        }
    }

    @Test
    fun testSetThenClearSameBit() {
        for (i in 0..150) {
            val mbi = MutableBigInt().setZero()
            mbi.setBit(i)
            mbi.clearBit(i)

            assertEquals(BigInt.ZERO, mbi.toBigInt(), "setBit($i) then clearBit($i)")
            assertFalse(mbi.testBit(i))
        }
    }

    @Test
    fun testClearThenSetSameBit() {
        for (i in 0..150) {
            val mbi = MutableBigInt().setOne().clearBit(0).setBit(0)
            assertEquals(BigInt.ONE, mbi.toBigInt(), "clearBit(0) then setBit(0)")
        }
    }

    // ----------------------------------------
    // Setting bits at & beyond current normLen
    // ----------------------------------------

    @Test
    fun testSetBitExpandsMagnitude() {
        var mbi = MutableBigInt().set(5)  // 0b101
        mbi = mbi.setBit(10)                 // should grow into limb 0..10 bits

        val expected = bi(5).withSetBit(10)
        assertEquals(expected, mbi.toBigInt(), "setBit expanded magnitude")
        assertEquals(11, mbi.toBigInt().magnitudeBitLen())
    }

    @Test
    fun testSetBitInsideExistingRange() {
        val mbi = accOf(0b1010).setBit(2) // already set? No -> becomes 1110
        val expected = bi(0b1110)
        assertEquals(expected, mbi.toBigInt(), "setBit inside range")
    }

    // ----------------------------------------
    // Clearing bits reduces magnitude
    // ----------------------------------------

    @Test
    fun testClearHighestBitReducesNormLen() {
        val mbi = accOf(1L shl 40).clearBit(40)
        assertEquals(BigInt.ZERO, mbi.toBigInt(), "clearing highest bit should normalize to zero")
    }

    @Test
    fun testClearBitInsideMiddleDoesNotReduceNormLen() {
        val x = (1L shl 40) or (1L shl 20) or 1L
        val mbi = accOf(x).clearBit(20)

        val expected = bi(x and (1L shl 20).inv())
        assertEquals(expected, mbi.toBigInt())
    }

    // ----------------------------------------
    // Randomized tests for many bits
    // ----------------------------------------

    @Test
    fun testRandomSetClearBits() {
        repeat(200) {
            val mbi = MutableBigInt().setZero()
            var ref = BigInt.ZERO

            repeat(50) {
                val b = (0..300).random()

                if ((0..1).random() == 0) {
                    mbi.setBit(b)
                    ref = ref.withSetBit(b)
                } else {
                    mbi.clearBit(b)
                    ref = ref.withClearBit(b)
                }

                assertEquals(ref, mbi.toBigInt(), "random bit op at $b")
            }
        }
    }

    // ----------------------------------------
    // testBit behavior
    // ----------------------------------------

    @Test
    fun testTestBitMatchesReference() {
        repeat(200) {
            var mbi = MutableBigInt().setZero()
            var ref = BigInt.ZERO

            repeat(50) {
                val b = (0..250).random()
                mbi.setBit(b)
                ref = ref.withSetBit(b)

                assertTrue(mbi.testBit(b))
                assertEquals(ref.testBit(b), mbi.testBit(b))
            }

            repeat(50) {
                val b = (0..250).random()
                mbi.clearBit(b)
                ref = ref.withClearBit(b)

                assertEquals(ref.testBit(b), mbi.testBit(b))
            }
        }
    }
}