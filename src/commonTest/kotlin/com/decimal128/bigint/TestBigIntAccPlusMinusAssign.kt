package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntAccPlusMinusAssign {

    /* ---------- helpers ---------- */

    private fun acc(l: Long): BigIntAccumulator =
        BigIntAccumulator().apply { this += l }

    private fun assertAccEquals(expected: Long, acc: BigIntAccumulator) {
        assertEquals(expected.toBigInt(), acc.toBigInt())
    }

    private fun assertAccEquals(expected: BigInt, acc: BigIntAccumulator) {
        assertEquals(expected, acc.toBigInt())
    }

    private fun newLargeAcc(bits: Int): BigIntAccumulator {
        val acc = BigIntAccumulator()
        acc += BigInt.ONE
        acc.mutShl(bits)   // explicit in-place shift
        return acc
    }

    /* ---------- plusAssign primitives ---------- */

    @Test
    fun plusAssign_Int() {
        assertAccEquals(5, acc(3).apply { this += 2 })
        assertAccEquals(1, acc(3).apply { this += -2 })
        assertAccEquals(-1, acc(-3).apply { this += 2 })
        assertAccEquals(-5, acc(-3).apply { this += -2 })
    }

    @Test
    fun plusAssign_UInt() {
        assertAccEquals(5, acc(3).apply { this += 2u })
        assertAccEquals(-1, acc(-3).apply { this += 2u })
    }

    @Test
    fun plusAssign_Long() {
        assertAccEquals(5, acc(3).apply { this += 2L })
        assertAccEquals(1, acc(3).apply { this += -2L })
        assertAccEquals(-1, acc(-3).apply { this += 2L })
        assertAccEquals(-5, acc(-3).apply { this += -2L })
    }

    @Test
    fun plusAssign_ULong() {
        assertAccEquals(5, acc(3).apply { this += 2uL })
        assertAccEquals(-1, acc(-3).apply { this += 2uL })
    }

    /* ---------- plusAssign BigInt / Accumulator ---------- */

    @Test
    fun plusAssign_BigInt() {
        val pos = 7.toBigInt()
        val neg = (-7).toBigInt()

        assertAccEquals(10, acc(3).apply { this += pos })
        assertAccEquals(-4, acc(3).apply { this += neg })
    }

    @Test
    fun plusAssign_BigIntAccumulator() {
        val pos = acc(7)
        val neg = acc(-7)

        assertAccEquals(10, acc(3).apply { this += pos })
        assertAccEquals(-4, acc(3).apply { this += neg })
    }

    @Test
    fun plusAssign_Long_multiLimb() {
        val acc = newLargeAcc(192)
        val add: Long = 0x1_0000_0001L

        val expected = acc.toBigInt() + add.toBigInt()

        acc += add

        assertAccEquals(expected, acc)
    }

    @Test
    fun plusAssign_ULong_multiLimb() {
        val acc = newLargeAcc(224)
        val add: ULong = 0x1_0000_0001uL

        val expected = acc.toBigInt() + add.toBigInt()

        acc += add

        assertAccEquals(expected, acc)
    }

    @Test
    fun plusAssign_BigInt_multiLimb() {
        val acc = newLargeAcc(256)
        val add = BigInt.from("18446744073709551617") // 2^64 + 1

        val expected = acc.toBigInt() + add

        acc += add

        assertAccEquals(expected, acc)
    }

    @Test
    fun plusAssign_BigIntAccumulator_multiLimb() {
        val acc = newLargeAcc(288)
        val addAcc = newLargeAcc(160)

        val expected = acc.toBigInt() + addAcc.toBigInt()

        acc += addAcc

        assertAccEquals(expected, acc)
    }

    @Test
    fun plusAssign_negativeOperand_multiLimb() {
        val acc = newLargeAcc(256)
        val add: Long = -0x1_0000_0001L

        val expected = acc.toBigInt() + add.toBigInt()

        acc += add

        assertAccEquals(expected, acc)
    }

    /* ---------- minusAssign primitives ---------- */

    @Test
    fun minusAssign_Int() {
        assertAccEquals(1, acc(3).apply { this -= 2 })
        assertAccEquals(5, acc(3).apply { this -= -2 })
        assertAccEquals(-5, acc(-3).apply { this -= 2 })
        assertAccEquals(-1, acc(-3).apply { this -= -2 })
    }

    @Test
    fun minusAssign_UInt() {
        assertAccEquals(1, acc(3).apply { this -= 2u })
        assertAccEquals(-5, acc(-3).apply { this -= 2u })
    }

    @Test
    fun minusAssign_Long() {
        assertAccEquals(1, acc(3).apply { this -= 2L })
        assertAccEquals(5, acc(3).apply { this -= -2L })
        assertAccEquals(-5, acc(-3).apply { this -= 2L })
        assertAccEquals(-1, acc(-3).apply { this -= -2L })
    }

    @Test
    fun minusAssign_ULong() {
        assertAccEquals(1, acc(3).apply { this -= 2uL })
        assertAccEquals(-5, acc(-3).apply { this -= 2uL })
    }

    /* ---------- minusAssign BigInt / Accumulator ---------- */

    @Test
    fun minusAssign_BigInt() {
        val pos = 7.toBigInt()
        val neg = (-7).toBigInt()

        assertAccEquals(-4, acc(3).apply { this -= pos })
        assertAccEquals(10, acc(3).apply { this -= neg })
    }

    @Test
    fun minusAssign_BigIntAccumulator() {
        val pos = acc(7)
        val neg = acc(-7)

        assertAccEquals(-4, acc(3).apply { this -= pos })
        assertAccEquals(10, acc(3).apply { this -= neg })
    }

    /* ---------- zero / identity ---------- */

    @Test
    fun addSubtractZero() {
        assertAccEquals(3, acc(3).apply { this += 0 })
        assertAccEquals(3, acc(3).apply { this -= 0 })
        assertAccEquals(3, acc(3).apply { this += 0L })
        assertAccEquals(3, acc(3).apply { this -= 0L })
    }

    @Test
    fun minusAssign_Long_multiLimb() {
        val acc = newLargeAcc(192)
        val sub: Long = 0x1_0000_0001L

        val expected = acc.toBigInt() - sub.toBigInt()

        acc -= sub

        assertAccEquals(expected, acc)
    }

    @Test
    fun minusAssign_ULong_multiLimb() {
        val acc = newLargeAcc(224)
        val sub: ULong = 0x1_0000_0001uL

        val expected = acc.toBigInt() - sub.toBigInt()

        acc -= sub

        assertAccEquals(expected, acc)
    }

    @Test
    fun minusAssign_BigInt_multiLimb() {
        val acc = newLargeAcc(256)
        val sub = BigInt.from("340282366920938463463374607431768211457") // 2^128 + 1

        val expected = acc.toBigInt() - sub

        acc -= sub

        assertAccEquals(expected, acc)
    }

    @Test
    fun minusAssign_BigIntAccumulator_multiLimb() {
        val acc = newLargeAcc(320)
        val subAcc = newLargeAcc(192)

        val expected = acc.toBigInt() - subAcc.toBigInt()

        acc -= subAcc

        assertAccEquals(expected, acc)
    }

    @Test
    fun minusAssign_negativeOperand_multiLimb() {
        val acc = newLargeAcc(256)
        val sub: Long = -0x1_0000_0001L

        val expected = acc.toBigInt() - sub.toBigInt()

        acc -= sub

        assertAccEquals(expected, acc)
    }

}