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

}