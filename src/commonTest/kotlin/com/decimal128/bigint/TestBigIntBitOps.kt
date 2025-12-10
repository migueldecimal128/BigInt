package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestBigIntBitOps {

    private fun bi(l: Long): BigInt = l.toBigInt()

    @Test
    fun testBit_basic() {
        val x = bi(0b101101)

        assertTrue(x.testBit(0))   // 1
        assertFalse(x.testBit(1))  // 0
        assertTrue(x.testBit(2))   // 1
        assertTrue(x.testBit(3))   // 1
        assertFalse(x.testBit(4))  // 0
        assertTrue(x.testBit(5))   // 1

        // out-of-range bits must be false
        assertFalse(x.testBit(100))
    }

    @Test
    fun withSetBit_basic() {
        val x = bi(0)

        val y = x.withSetBit(5)
        assertEquals(bi(32), y)              // 1<<5
        assertTrue(y.testBit(5))
        assertFalse(y.testBit(4))

        // Setting an already-set bit returns same number
        val z = y.withSetBit(5)
        assertSame(y, z)
    }

    @Test
    fun withClearBit_basic() {
        val x = bi(0b111100)

        val y = x.withClearBit(2)
        assertEquals(bi(0b111000), y)
        assertFalse(y.testBit(2))

        // Clearing an already-clear bit returns same instance
        val z = y.withClearBit(2)
        assertSame(y, z)
    }

    @Test
    fun withSetBit_growsBitLength() {
        val x = bi(1)              // 0b1
        val y = x.withSetBit(100)  // should grow to bit 100

        assertTrue(y.testBit(100))
        assertEquals(y, bi(1).withSetBit(100))
        assertFalse(y.testBit(99))
    }

    @Test
    fun withClearBit_toZero() {
        val x = bi(1)
        val y = x.withClearBit(0)
        assertEquals(bi(0), y)
        assertTrue(y.isZero())
    }

    @Test
    fun randomSetAndClear() {
        repeat(500) {
            val v = (0..1_000_000).random().toLong()
            val i = (0..200).random()

            val x = bi(v)
            val set = x.withSetBit(i)
            val clear = x.withClearBit(i)

            // check correctness
            assertTrue(set.testBit(i))
            assertFalse(clear.testBit(i))

            // clearing after setting → back to original (for in-range bits)
            val restored = set.withClearBit(i)
            if (i < 63) {   // Long domain
                val expected = v and (1L shl i).inv()
                assertEquals(bi(expected), restored)
            }
        }
    }
}