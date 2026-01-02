package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals

class TestMbiPlusMinusAssign {

    /* ---------- helpers ---------- */

    private fun mbi(l: Long): MutableBigInt =
        MutableBigInt().apply { this += l }

    private fun assertAccEquals(expected: Long, mbi: MutableBigInt) {
        assertEquals(expected.toBigInt(), mbi.toBigInt())
    }

    private fun assertAccEquals(expected: BigInt, mbi: MutableBigInt) {
        assertEquals(expected, mbi.toBigInt())
    }

    private fun newLargeAcc(bits: Int): MutableBigInt {
        val mbi = MutableBigInt()
        mbi += BigInt.ONE
        mbi.mutShl(bits)   // explicit in-place shift
        return mbi
    }

    /* ---------- plusAssign primitives ---------- */

    @Test
    fun plusAssign_Int() {
        assertAccEquals(5, mbi(3).apply { this += 2 })
        assertAccEquals(1, mbi(3).apply { this += -2 })
        assertAccEquals(-1, mbi(-3).apply { this += 2 })
        assertAccEquals(-5, mbi(-3).apply { this += -2 })
    }

    @Test
    fun plusAssign_UInt() {
        assertAccEquals(5, mbi(3).apply { this += 2u })
        assertAccEquals(-1, mbi(-3).apply { this += 2u })
    }

    @Test
    fun plusAssign_Long() {
        assertAccEquals(5, mbi(3).apply { this += 2L })
        assertAccEquals(1, mbi(3).apply { this += -2L })
        assertAccEquals(-1, mbi(-3).apply { this += 2L })
        assertAccEquals(-5, mbi(-3).apply { this += -2L })
    }

    @Test
    fun plusAssign_ULong() {
        assertAccEquals(5, mbi(3).apply { this += 2uL })
        assertAccEquals(-1, mbi(-3).apply { this += 2uL })
    }

    /* ---------- plusAssign BigInt / Accumulator ---------- */

    @Test
    fun plusAssign_BigInt() {
        val pos = 7.toBigInt()
        val neg = (-7).toBigInt()

        assertAccEquals(10, mbi(3).apply { this += pos })
        assertAccEquals(-4, mbi(3).apply { this += neg })
    }

    @Test
    fun plusAssign_BigIntAccumulator() {
        val pos = mbi(7)
        val neg = mbi(-7)

        assertAccEquals(10, mbi(3).apply { this += pos })
        assertAccEquals(-4, mbi(3).apply { this += neg })
    }

    @Test
    fun plusAssign_Long_multiLimb() {
        val mbi = newLargeAcc(192)
        val add: Long = 0x1_0000_0001L

        val expected = mbi.toBigInt() + add.toBigInt()

        mbi += add

        assertAccEquals(expected, mbi)
    }

    @Test
    fun plusAssign_ULong_multiLimb() {
        val mbi = newLargeAcc(224)
        val add: ULong = 0x1_0000_0001uL

        val expected = mbi.toBigInt() + add.toBigInt()

        mbi += add

        assertAccEquals(expected, mbi)
    }

    @Test
    fun plusAssign_BigInt_multiLimb() {
        val mbi = newLargeAcc(256)
        val add = BigInt.from("18446744073709551617") // 2^64 + 1

        val expected = mbi.toBigInt() + add

        mbi += add

        assertAccEquals(expected, mbi)
    }

    @Test
    fun plusAssign_BigIntAccumulator_multiLimb() {
        val mbi = newLargeAcc(288)
        val addAcc = newLargeAcc(160)

        val expected = mbi.toBigInt() + addAcc.toBigInt()

        mbi += addAcc

        assertAccEquals(expected, mbi)
    }

    @Test
    fun plusAssign_negativeOperand_multiLimb() {
        val mbi = newLargeAcc(256)
        val add: Long = -0x1_0000_0001L

        val expected = mbi.toBigInt() + add.toBigInt()

        mbi += add

        assertAccEquals(expected, mbi)
    }

    /* ---------- minusAssign primitives ---------- */

    @Test
    fun minusAssign_Int() {
        assertAccEquals(1, mbi(3).apply { this -= 2 })
        assertAccEquals(5, mbi(3).apply { this -= -2 })
        assertAccEquals(-5, mbi(-3).apply { this -= 2 })
        assertAccEquals(-1, mbi(-3).apply { this -= -2 })
    }

    @Test
    fun minusAssign_UInt() {
        assertAccEquals(1, mbi(3).apply { this -= 2u })
        assertAccEquals(-5, mbi(-3).apply { this -= 2u })
    }

    @Test
    fun minusAssign_Long() {
        assertAccEquals(1, mbi(3).apply { this -= 2L })
        assertAccEquals(5, mbi(3).apply { this -= -2L })
        assertAccEquals(-5, mbi(-3).apply { this -= 2L })
        assertAccEquals(-1, mbi(-3).apply { this -= -2L })
    }

    @Test
    fun minusAssign_ULong() {
        assertAccEquals(1, mbi(3).apply { this -= 2uL })
        assertAccEquals(-5, mbi(-3).apply { this -= 2uL })
    }

    /* ---------- minusAssign BigInt / Accumulator ---------- */

    @Test
    fun minusAssign_BigInt() {
        val pos = 7.toBigInt()
        val neg = (-7).toBigInt()

        assertAccEquals(-4, mbi(3).apply { this -= pos })
        assertAccEquals(10, mbi(3).apply { this -= neg })
    }

    @Test
    fun minusAssign_BigIntAccumulator() {
        val pos = mbi(7)
        val neg = mbi(-7)

        assertAccEquals(-4, mbi(3).apply { this -= pos })
        assertAccEquals(10, mbi(3).apply { this -= neg })
    }

    /* ---------- zero / identity ---------- */

    @Test
    fun addSubtractZero() {
        assertAccEquals(3, mbi(3).apply { this += 0 })
        assertAccEquals(3, mbi(3).apply { this -= 0 })
        assertAccEquals(3, mbi(3).apply { this += 0L })
        assertAccEquals(3, mbi(3).apply { this -= 0L })
    }

    @Test
    fun minusAssign_Long_multiLimb() {
        val mbi = newLargeAcc(192)
        val sub: Long = 0x1_0000_0001L

        val expected = mbi.toBigInt() - sub.toBigInt()

        mbi -= sub

        assertAccEquals(expected, mbi)
    }

    @Test
    fun minusAssign_ULong_multiLimb() {
        val mbi = newLargeAcc(224)
        val sub: ULong = 0x1_0000_0001uL

        val expected = mbi.toBigInt() - sub.toBigInt()

        mbi -= sub

        assertAccEquals(expected, mbi)
    }

    @Test
    fun minusAssign_BigInt_multiLimb() {
        val mbi = newLargeAcc(256)
        val sub = BigInt.from("340282366920938463463374607431768211457") // 2^128 + 1

        val expected = mbi.toBigInt() - sub

        mbi -= sub

        assertAccEquals(expected, mbi)
    }

    @Test
    fun minusAssign_BigIntAccumulator_multiLimb() {
        val mbi = newLargeAcc(320)
        val subAcc = newLargeAcc(192)

        val expected = mbi.toBigInt() - subAcc.toBigInt()

        mbi -= subAcc

        assertAccEquals(expected, mbi)
    }

    @Test
    fun minusAssign_negativeOperand_multiLimb() {
        val mbi = newLargeAcc(256)
        val sub: Long = -0x1_0000_0001L

        val expected = mbi.toBigInt() - sub.toBigInt()

        mbi -= sub

        assertAccEquals(expected, mbi)
    }

}