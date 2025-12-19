package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestBigIntAccSetClearBit {

    private fun mbi() = MutableBigInt()

    @Test
    fun setBit_zeroBecomesOne() {
        val a = mbi().setBit(0)
        assertEquals(1.toBigInt(), a.toBigInt())
    }

    @Test
    fun setBit_oneLimbHighBit() {
        val a = mbi().setBit(31) // top bit of first limb
        assertEquals(1.toBigInt() shl 31, a.toBigInt())
    }

    @Test
    fun setBit_crossesIntoSecondLimb() {
        val a = mbi().setBit(32)
        assertEquals(1.toBigInt() shl 32, a.toBigInt())
    }

    @Test
    fun setBit_largeIndex() {
        val a = mbi().setBit(200)
        assertEquals(1.toBigInt() shl 200, a.toBigInt())
        // Check that normLen reflects enough limbs
        assertTrue(a.magnitudeBitLen() > 190)
    }

    @Test
    fun setBit_twiceSameBit_noChange() {
        val a = mbi().setBit(50)
        val before = a.toBigInt()
        a.setBit(50)
        assertEquals(before, a.toBigInt())
    }

    @Test
    fun clearBit_clearsSingleBit() {
        val a = mbi().setBit(5)
        a.clearBit(5)
        assertEquals(BigInt.ZERO, a.toBigInt())
    }

    @Test
    fun clearBit_highBitShrinksNormalization() {
        val a = mbi().setBit(100)
        assertTrue(a.magnitudeBitLen() > 90)
        a.clearBit(100)
        assertEquals(BigInt.ZERO, a.toBigInt())
        assertEquals(0, a.magnitudeBitLen())
    }

    @Test
    fun clearBit_onlyMiddleBit() {
        val a = mbi().setBit(0).setBit(100)
        a.clearBit(0)
        assertEquals(1.toBigInt() shl 100, a.toBigInt())
    }

    @Test
    fun clearBit_whenAlreadyZero_noThrow() {
        val a = mbi().setBit(60)
        a.clearBit(10) // was zero
        assertEquals(1.toBigInt() shl 60, a.toBigInt())
    }

    @Test
    fun setBit_thenClearBit_backAndForth() {
        val a = mbi()
        a.setBit(7);  assertEquals(1.toBigInt() shl 7, a.toBigInt())
        a.setBit(70); assertEquals((1.toBigInt() shl 7) + (1.toBigInt() shl 70), a.toBigInt())
        a.clearBit(7); assertEquals(1.toBigInt() shl 70, a.toBigInt())
        a.clearBit(70); assertEquals(BigInt.ZERO, a.toBigInt())
    }

    @Test
    fun clearBit_at32Boundary() {
        val a = mbi().setBit(32).setBit(2)
        a.clearBit(32)
        assertEquals(1.toBigInt() shl 2, a.toBigInt())
    }

}