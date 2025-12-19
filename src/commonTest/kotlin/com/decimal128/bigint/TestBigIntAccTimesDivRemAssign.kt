package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntAccTimesDivRemAssign {

    val verbose = true
    /* ---------- helpers ---------- */

    private fun newAcc(value: Long): MutableBigInt {
        val mbi = MutableBigInt()
        mbi += value
        return mbi
    }

    private fun assertAccEqualsLong(expected: Long, mbi: MutableBigInt) {
        val actual = mbi.toBigInt()
        val expectedBig = expected.toBigInt()
        assertEquals(expectedBig, actual)
    }

    private fun assertAccEqualsBig(expected: BigInt, mbi: MutableBigInt) {
        val actual = mbi.toBigInt()
        assertEquals(expected, actual)
    }

    private fun newLargeAccFromDecimal(s: String): MutableBigInt {
        val mbi = MutableBigInt()
        val bi = BigInt.from(s)
        mbi += bi
        return mbi
    }

    private fun newLargeAccShift(bits: Int): MutableBigInt {
        val mbi = MutableBigInt()
        mbi += BigInt.ONE
        mbi.mutShl(bits)   // assume you have in-place shift
        return mbi
    }

    /* ====================================================================== */
    /* ============================ timesAssign ============================== */
    /* ====================================================================== */

    @Test
    fun timesAssign_Int() {
        var mbi: MutableBigInt

        mbi = newAcc(3)
        mbi *= 2
        assertAccEqualsLong(6, mbi)

        mbi = newAcc(3)
        mbi *= -2
        assertAccEqualsLong(-6, mbi)

        mbi = newAcc(-3)
        mbi *= 2
        assertAccEqualsLong(-6, mbi)

        mbi = newAcc(-3)
        mbi *= -2
        assertAccEqualsLong(6, mbi)
    }

    @Test
    fun timesAssign_UInt() {
        var mbi: MutableBigInt

        mbi = newAcc(3)
        mbi *= 2u
        assertAccEqualsLong(6, mbi)

        mbi = newAcc(-3)
        mbi *= 2u
        assertAccEqualsLong(-6, mbi)
    }

    @Test
    fun timesAssign_Long() {
        var mbi: MutableBigInt

        mbi = newAcc(3)
        mbi *= 2L
        assertAccEqualsLong(6, mbi)

        mbi = newAcc(3)
        mbi *= -2L
        assertAccEqualsLong(-6, mbi)
    }

    @Test
    fun timesAssign_ULong() {
        var mbi: MutableBigInt

        mbi = newAcc(3)
        mbi *= 2uL
        assertAccEqualsLong(6, mbi)

        mbi = newAcc(-3)
        mbi *= 2uL
        assertAccEqualsLong(-6, mbi)
    }

    @Test
    fun timesAssign_BigInt() {
        var mbi: MutableBigInt
        val pos = 7.toBigInt()
        val neg = (-7).toBigInt()

        mbi = newAcc(3)
        mbi *= pos
        assertAccEqualsLong(21, mbi)

        mbi = newAcc(3)
        mbi *= neg
        assertAccEqualsLong(-21, mbi)
    }

    @Test
    fun timesAssign_BigIntAccumulator() {
        var mbi: MutableBigInt
        val pos = newAcc(7)
        val neg = newAcc(-7)

        mbi = newAcc(3)
        mbi *= pos
        assertAccEqualsLong(21, mbi)

        mbi = newAcc(3)
        mbi *= neg
        assertAccEqualsLong(-21, mbi)
    }

    @Test
    fun timesAssign_Zero() {
        var mbi: MutableBigInt

        mbi = newAcc(3)
        mbi *= 0
        assertAccEqualsLong(0, mbi)

        mbi = newAcc(-3)
        mbi *= 0L
        assertAccEqualsLong(0, mbi)
    }

    @Test
    fun timesAssign_Long_multiLimb() {
        val mbi = newLargeAccShift(192)   // 192 bits = 6 limbs
        val mul: Long = 0x1_0000_0001L     // >32 bits

        val expected = mbi.toBigInt() * mul.toBigInt()

        mbi *= mul

        assertAccEqualsBig(expected, mbi)
    }

    @Test
    fun timesAssign_ULong_multiLimb() {
        val mbi = newLargeAccShift(224)   // 7 limbs
        val mul: ULong = 0x1_0000_0001uL

        val expected = mbi.toBigInt() * mul.toBigInt()

        mbi *= mul

        assertAccEqualsBig(expected, mbi)
    }

    @Test
    fun timesAssign_BigInt_multiLimb() {
        val mbi = newLargeAccShift(256)
        val mul = BigInt.from("18446744073709551617") // 2^64 + 1

        val expected = mbi.toBigInt() * mul

        mbi *= mul

        assertAccEqualsBig(expected, mbi)
    }


    /* ====================================================================== */
    /* ============================= divAssign =============================== */
    /* ====================================================================== */

    @Test
    fun divAssign_Int() {
        var mbi: MutableBigInt

        mbi = newAcc(6)
        mbi /= 2
        assertAccEqualsLong(3, mbi)

        mbi = newAcc(6)
        mbi /= -2
        assertAccEqualsLong(-3, mbi)

        mbi = newAcc(-6)
        mbi /= 2
        assertAccEqualsLong(-3, mbi)

        mbi = newAcc(-6)
        mbi /= -2
        assertAccEqualsLong(3, mbi)
    }

    @Test
    fun divAssign_UInt() {
        var mbi: MutableBigInt

        mbi = newAcc(6)
        mbi /= 2u
        assertAccEqualsLong(3, mbi)

        mbi = newAcc(-6)
        mbi /= 2u
        assertAccEqualsLong(-3, mbi)
    }

    @Test
    fun divAssign_Long() {
        var mbi: MutableBigInt

        mbi = newAcc(6)
        mbi /= 2L
        assertAccEqualsLong(3, mbi)

        mbi = newAcc(6)
        mbi /= -2L
        assertAccEqualsLong(-3, mbi)
    }

    @Test
    fun divAssign_ULong() {
        var mbi: MutableBigInt

        mbi = newAcc(6)
        mbi /= 2uL
        assertAccEqualsLong(3, mbi)

        mbi = newAcc(-6)
        mbi /= 2uL
        assertAccEqualsLong(-3, mbi)
    }

    @Test
    fun divAssign_BigInt() {
        var mbi: MutableBigInt
        val pos = 7.toBigInt()
        val neg = (-7).toBigInt()

        mbi = newAcc(21)
        mbi /= pos
        assertAccEqualsLong(3, mbi)

        mbi = newAcc(21)
        mbi /= neg
        assertAccEqualsLong(-3, mbi)
    }

    @Test
    fun divAssign_BigIntAccumulator() {
        var mbi: MutableBigInt
        val pos = newAcc(7)
        val neg = newAcc(-7)

        mbi = newAcc(21)
        mbi /= pos
        assertAccEqualsLong(3, mbi)

        mbi = newAcc(21)
        mbi /= neg
        assertAccEqualsLong(-3, mbi)
    }

    @Test
    fun divAssign_Long_multiLimb() {
        val mbi = newLargeAccFromDecimal(
            "12345678901234567890123456789012345678901234567890"
        )
        val div: Long = 0x1_0000_0001L

        val expected = mbi.toBigInt() / div.toBigInt()

        mbi /= div

        if (verbose)
            println("expected:$expected observed:$mbi")

        assertAccEqualsBig(expected, mbi)
    }

    @Test
    fun divAssign_ULong_multiLimb() {
        val mbi = newLargeAccShift(240)
        val div: ULong = 0x1_0000_0001uL

        val expected = mbi.toBigInt() / div.toBigInt()

        mbi /= div

        assertAccEqualsBig(expected, mbi)
    }

    @Test
    fun divAssign_BigInt_multiLimb() {
        val mbi = newLargeAccShift(320)
        val div = BigInt.from("340282366920938463463374607431768211457") // 2^128+1

        val expected = mbi.toBigInt() / div

        mbi /= div

        assertAccEqualsBig(expected, mbi)
    }

    /* ====================================================================== */
    /* ============================= remAssign =============================== */
    /* ====================================================================== */

    @Test
    fun remAssign_Int() {
        var mbi: MutableBigInt

        mbi = newAcc(7)
        mbi %= 3
        assertAccEqualsLong(1, mbi)

        mbi = newAcc(7)
        mbi %= -3
        assertAccEqualsLong(1, mbi)

        mbi = newAcc(-7)
        mbi %= 3
        assertAccEqualsLong(-1, mbi)

        mbi = newAcc(-7)
        mbi %= -3
        assertAccEqualsLong(-1, mbi)
    }

    @Test
    fun remAssign_UInt() {
        var mbi: MutableBigInt

        mbi = newAcc(7)
        mbi %= 3u
        assertAccEqualsLong(1, mbi)

        mbi = newAcc(-7)
        mbi %= 3u
        assertAccEqualsLong(-1, mbi)
    }

    @Test
    fun remAssign_Long() {
        var mbi: MutableBigInt

        mbi = newAcc(7)
        mbi %= 3L
        assertAccEqualsLong(1, mbi)

        mbi = newAcc(-7)
        mbi %= 3L
        assertAccEqualsLong(-1, mbi)
    }

    @Test
    fun remAssign_ULong() {
        var mbi: MutableBigInt

        mbi = newAcc(7)
        mbi %= 3uL
        assertAccEqualsLong(1, mbi)

        mbi = newAcc(-7)
        mbi %= 3uL
        assertAccEqualsLong(-1, mbi)
    }

    @Test
    fun remAssign_BigInt() {
        var mbi: MutableBigInt
        val pos = 3.toBigInt()
        val neg = (-3).toBigInt()

        mbi = newAcc(7)
        mbi %= pos
        assertAccEqualsLong(1, mbi)

        mbi = newAcc(7)
        mbi %= neg
        assertAccEqualsLong(1, mbi)

        mbi = newAcc(-7)
        mbi %= pos
        assertAccEqualsLong(-1, mbi)
    }

    @Test
    fun remAssign_BigIntAccumulator() {
        var mbi: MutableBigInt
        val pos = newAcc(3)
        val neg = newAcc(-3)

        mbi = newAcc(7)
        mbi %= pos
        assertAccEqualsLong(1, mbi)

        mbi = newAcc(7)
        mbi %= neg
        assertAccEqualsLong(1, mbi)

        mbi = newAcc(-7)
        mbi %= pos
        assertAccEqualsLong(-1, mbi)
    }

    @Test
    fun remAssign_ZeroDividend() {
        var mbi: MutableBigInt

        mbi = newAcc(0)
        mbi %= 3
        assertAccEqualsLong(0, mbi)

        mbi = newAcc(0)
        mbi %= 3L
        assertAccEqualsLong(0, mbi)
    }

    @Test
    fun remAssign_Long_multiLimb() {
        val mbi = newLargeAccFromDecimal(
            "99999999999999999999999999999999999999999999999999"
        )
        val div: Long = 0x1_0000_0001L

        val expected = mbi.toBigInt() % div.toBigInt()

        mbi %= div

        assertAccEqualsBig(expected, mbi)
    }

    @Test
    fun remAssign_ULong_multiLimb() {
        val mbi = newLargeAccShift(288)
        val div: ULong = 0x1_0000_0001uL

        val expected = mbi.toBigInt() % div.toBigInt()

        mbi %= div

        assertAccEqualsBig(expected, mbi)
    }

    @Test
    fun remAssign_BigInt_multiLimb() {
        val mbi = newLargeAccShift(384)
        val div = BigInt.from("18446744073709551617") // 2^64+1

        val expected = mbi.toBigInt() % div

        mbi %= div

        assertAccEqualsBig(expected, mbi)
    }


}