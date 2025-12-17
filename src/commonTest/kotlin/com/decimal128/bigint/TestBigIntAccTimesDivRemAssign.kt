package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntAccTimesDivRemAssign {

    val verbose = true
    /* ---------- helpers ---------- */

    private fun newAcc(value: Long): BigIntAccumulator {
        val acc = BigIntAccumulator()
        acc += value
        return acc
    }

    private fun assertAccEqualsLong(expected: Long, acc: BigIntAccumulator) {
        val actual = acc.toBigInt()
        val expectedBig = expected.toBigInt()
        assertEquals(expectedBig, actual)
    }

    private fun assertAccEqualsBig(expected: BigInt, acc: BigIntAccumulator) {
        val actual = acc.toBigInt()
        assertEquals(expected, actual)
    }

    private fun newLargeAccFromDecimal(s: String): BigIntAccumulator {
        val acc = BigIntAccumulator()
        val bi = BigInt.from(s)
        acc += bi
        return acc
    }

    private fun newLargeAccShift(bits: Int): BigIntAccumulator {
        val acc = BigIntAccumulator()
        acc += BigInt.ONE
        acc.mutShl(bits)   // assume you have in-place shift
        return acc
    }

    /* ====================================================================== */
    /* ============================ timesAssign ============================== */
    /* ====================================================================== */

    @Test
    fun timesAssign_Int() {
        var acc: BigIntAccumulator

        acc = newAcc(3)
        acc *= 2
        assertAccEqualsLong(6, acc)

        acc = newAcc(3)
        acc *= -2
        assertAccEqualsLong(-6, acc)

        acc = newAcc(-3)
        acc *= 2
        assertAccEqualsLong(-6, acc)

        acc = newAcc(-3)
        acc *= -2
        assertAccEqualsLong(6, acc)
    }

    @Test
    fun timesAssign_UInt() {
        var acc: BigIntAccumulator

        acc = newAcc(3)
        acc *= 2u
        assertAccEqualsLong(6, acc)

        acc = newAcc(-3)
        acc *= 2u
        assertAccEqualsLong(-6, acc)
    }

    @Test
    fun timesAssign_Long() {
        var acc: BigIntAccumulator

        acc = newAcc(3)
        acc *= 2L
        assertAccEqualsLong(6, acc)

        acc = newAcc(3)
        acc *= -2L
        assertAccEqualsLong(-6, acc)
    }

    @Test
    fun timesAssign_ULong() {
        var acc: BigIntAccumulator

        acc = newAcc(3)
        acc *= 2uL
        assertAccEqualsLong(6, acc)

        acc = newAcc(-3)
        acc *= 2uL
        assertAccEqualsLong(-6, acc)
    }

    @Test
    fun timesAssign_BigInt() {
        var acc: BigIntAccumulator
        val pos = 7.toBigInt()
        val neg = (-7).toBigInt()

        acc = newAcc(3)
        acc *= pos
        assertAccEqualsLong(21, acc)

        acc = newAcc(3)
        acc *= neg
        assertAccEqualsLong(-21, acc)
    }

    @Test
    fun timesAssign_BigIntAccumulator() {
        var acc: BigIntAccumulator
        val pos = newAcc(7)
        val neg = newAcc(-7)

        acc = newAcc(3)
        acc *= pos
        assertAccEqualsLong(21, acc)

        acc = newAcc(3)
        acc *= neg
        assertAccEqualsLong(-21, acc)
    }

    @Test
    fun timesAssign_Zero() {
        var acc: BigIntAccumulator

        acc = newAcc(3)
        acc *= 0
        assertAccEqualsLong(0, acc)

        acc = newAcc(-3)
        acc *= 0L
        assertAccEqualsLong(0, acc)
    }

    @Test
    fun timesAssign_Long_multiLimb() {
        val acc = newLargeAccShift(192)   // 192 bits = 6 limbs
        val mul: Long = 0x1_0000_0001L     // >32 bits

        val expected = acc.toBigInt() * mul.toBigInt()

        acc *= mul

        assertAccEqualsBig(expected, acc)
    }

    @Test
    fun timesAssign_ULong_multiLimb() {
        val acc = newLargeAccShift(224)   // 7 limbs
        val mul: ULong = 0x1_0000_0001uL

        val expected = acc.toBigInt() * mul.toBigInt()

        acc *= mul

        assertAccEqualsBig(expected, acc)
    }

    @Test
    fun timesAssign_BigInt_multiLimb() {
        val acc = newLargeAccShift(256)
        val mul = BigInt.from("18446744073709551617") // 2^64 + 1

        val expected = acc.toBigInt() * mul

        acc *= mul

        assertAccEqualsBig(expected, acc)
    }


    /* ====================================================================== */
    /* ============================= divAssign =============================== */
    /* ====================================================================== */

    @Test
    fun divAssign_Int() {
        var acc: BigIntAccumulator

        acc = newAcc(6)
        acc /= 2
        assertAccEqualsLong(3, acc)

        acc = newAcc(6)
        acc /= -2
        assertAccEqualsLong(-3, acc)

        acc = newAcc(-6)
        acc /= 2
        assertAccEqualsLong(-3, acc)

        acc = newAcc(-6)
        acc /= -2
        assertAccEqualsLong(3, acc)
    }

    @Test
    fun divAssign_UInt() {
        var acc: BigIntAccumulator

        acc = newAcc(6)
        acc /= 2u
        assertAccEqualsLong(3, acc)

        acc = newAcc(-6)
        acc /= 2u
        assertAccEqualsLong(-3, acc)
    }

    @Test
    fun divAssign_Long() {
        var acc: BigIntAccumulator

        acc = newAcc(6)
        acc /= 2L
        assertAccEqualsLong(3, acc)

        acc = newAcc(6)
        acc /= -2L
        assertAccEqualsLong(-3, acc)
    }

    @Test
    fun divAssign_ULong() {
        var acc: BigIntAccumulator

        acc = newAcc(6)
        acc /= 2uL
        assertAccEqualsLong(3, acc)

        acc = newAcc(-6)
        acc /= 2uL
        assertAccEqualsLong(-3, acc)
    }

    @Test
    fun divAssign_BigInt() {
        var acc: BigIntAccumulator
        val pos = 7.toBigInt()
        val neg = (-7).toBigInt()

        acc = newAcc(21)
        acc /= pos
        assertAccEqualsLong(3, acc)

        acc = newAcc(21)
        acc /= neg
        assertAccEqualsLong(-3, acc)
    }

    @Test
    fun divAssign_BigIntAccumulator() {
        var acc: BigIntAccumulator
        val pos = newAcc(7)
        val neg = newAcc(-7)

        acc = newAcc(21)
        acc /= pos
        assertAccEqualsLong(3, acc)

        acc = newAcc(21)
        acc /= neg
        assertAccEqualsLong(-3, acc)
    }

    @Test
    fun divAssign_Long_multiLimb() {
        val acc = newLargeAccFromDecimal(
            "12345678901234567890123456789012345678901234567890"
        )
        val div: Long = 0x1_0000_0001L

        val expected = acc.toBigInt() / div.toBigInt()

        acc /= div

        if (verbose)
            println("expected:$expected observed:$acc")

        assertAccEqualsBig(expected, acc)
    }

    @Test
    fun divAssign_ULong_multiLimb() {
        val acc = newLargeAccShift(240)
        val div: ULong = 0x1_0000_0001uL

        val expected = acc.toBigInt() / div.toBigInt()

        acc /= div

        assertAccEqualsBig(expected, acc)
    }

    @Test
    fun divAssign_BigInt_multiLimb() {
        val acc = newLargeAccShift(320)
        val div = BigInt.from("340282366920938463463374607431768211457") // 2^128+1

        val expected = acc.toBigInt() / div

        acc /= div

        assertAccEqualsBig(expected, acc)
    }

    /* ====================================================================== */
    /* ============================= remAssign =============================== */
    /* ====================================================================== */

    @Test
    fun remAssign_Int() {
        var acc: BigIntAccumulator

        acc = newAcc(7)
        acc %= 3
        assertAccEqualsLong(1, acc)

        acc = newAcc(7)
        acc %= -3
        assertAccEqualsLong(1, acc)

        acc = newAcc(-7)
        acc %= 3
        assertAccEqualsLong(-1, acc)

        acc = newAcc(-7)
        acc %= -3
        assertAccEqualsLong(-1, acc)
    }

    @Test
    fun remAssign_UInt() {
        var acc: BigIntAccumulator

        acc = newAcc(7)
        acc %= 3u
        assertAccEqualsLong(1, acc)

        acc = newAcc(-7)
        acc %= 3u
        assertAccEqualsLong(-1, acc)
    }

    @Test
    fun remAssign_Long() {
        var acc: BigIntAccumulator

        acc = newAcc(7)
        acc %= 3L
        assertAccEqualsLong(1, acc)

        acc = newAcc(-7)
        acc %= 3L
        assertAccEqualsLong(-1, acc)
    }

    @Test
    fun remAssign_ULong() {
        var acc: BigIntAccumulator

        acc = newAcc(7)
        acc %= 3uL
        assertAccEqualsLong(1, acc)

        acc = newAcc(-7)
        acc %= 3uL
        assertAccEqualsLong(-1, acc)
    }

    @Test
    fun remAssign_BigInt() {
        var acc: BigIntAccumulator
        val pos = 3.toBigInt()
        val neg = (-3).toBigInt()

        acc = newAcc(7)
        acc %= pos
        assertAccEqualsLong(1, acc)

        acc = newAcc(7)
        acc %= neg
        assertAccEqualsLong(1, acc)

        acc = newAcc(-7)
        acc %= pos
        assertAccEqualsLong(-1, acc)
    }

    @Test
    fun remAssign_BigIntAccumulator() {
        var acc: BigIntAccumulator
        val pos = newAcc(3)
        val neg = newAcc(-3)

        acc = newAcc(7)
        acc %= pos
        assertAccEqualsLong(1, acc)

        acc = newAcc(7)
        acc %= neg
        assertAccEqualsLong(1, acc)

        acc = newAcc(-7)
        acc %= pos
        assertAccEqualsLong(-1, acc)
    }

    @Test
    fun remAssign_ZeroDividend() {
        var acc: BigIntAccumulator

        acc = newAcc(0)
        acc %= 3
        assertAccEqualsLong(0, acc)

        acc = newAcc(0)
        acc %= 3L
        assertAccEqualsLong(0, acc)
    }

    @Test
    fun remAssign_Long_multiLimb() {
        val acc = newLargeAccFromDecimal(
            "99999999999999999999999999999999999999999999999999"
        )
        val div: Long = 0x1_0000_0001L

        val expected = acc.toBigInt() % div.toBigInt()

        acc %= div

        assertAccEqualsBig(expected, acc)
    }

    @Test
    fun remAssign_ULong_multiLimb() {
        val acc = newLargeAccShift(288)
        val div: ULong = 0x1_0000_0001uL

        val expected = acc.toBigInt() % div.toBigInt()

        acc %= div

        assertAccEqualsBig(expected, acc)
    }

    @Test
    fun remAssign_BigInt_multiLimb() {
        val acc = newLargeAccShift(384)
        val div = BigInt.from("18446744073709551617") // 2^64+1

        val expected = acc.toBigInt() % div

        acc %= div

        assertAccEqualsBig(expected, acc)
    }


}