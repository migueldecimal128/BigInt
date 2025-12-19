package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntAccMutShlUshrShr {

    private fun newAccFromLong(v: Long): MutableBigInt =
        MutableBigInt().set(v)

    private fun newLargeAcc(bits: Int): MutableBigInt =
        MutableBigInt().setBit(bits)

    private fun assertAccEquals(expected: BigInt, mbi: MutableBigInt) {
        val actual = mbi.toBigInt()
        assertEquals(expected, actual)
    }

    @Test
    fun shl_small_positive() {
        val mbi = newAccFromLong(3)
        val expected = mbi.toBigInt() shl 5

        mbi.mutShl(5)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun shl_small_negative() {
        val mbi = newAccFromLong(-3)
        val expected = mbi.toBigInt() shl 7

        mbi.mutShl(7)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun shl_zero() {
        val mbi = newAccFromLong(0)
        val expected = BigInt.ZERO

        mbi.mutShl(123)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun shl_exact_limb_boundary() {
        val mbi = newAccFromLong(1)
        val expected = mbi.toBigInt() shl 32

        mbi.mutShl(32)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun shl_large_multiLimb() {
        val mbi = newLargeAcc(192)   // ≥ 6 limbs
        val expected = mbi.toBigInt() shl 65

        mbi.mutShl(65)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun shl_large_negative_multiLimb() {
        val mbi = newLargeAcc(224)
        mbi -= BigInt.from("123456789")   // make it negative

        val expected = mbi.toBigInt() shl 91

        mbi.mutShl(91)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun shr_small_positive() {
        val mbi = newAccFromLong(96)
        val expected = mbi.toBigInt() shr 5

        mbi.mutShr(5)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun shr_small_negative_signExtend() {
        val mbi = newAccFromLong(-96)
        val expected = mbi.toBigInt() shr 5

        mbi.mutShr(5)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun shr_to_zero() {
        val mbi = newAccFromLong(1)
        val expected = BigInt.ZERO

        mbi.mutShr(100)
        assertAccEquals(expected, mbi)

        mbi.set(-1)
        mbi.mutShr(2)
        assertAccEquals(BigInt.NEG_ONE, mbi)
    }

    @Test
    fun shr_exact_limb_boundary() {
        val mbi = newLargeAcc(192)
        val expected = mbi.toBigInt() shr 32

        mbi.mutShr(32)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun shr_large_multiLimb_positive() {
        val mbi = newLargeAcc(256)
        val expected = mbi.toBigInt() shr 113

        mbi.mutShr(113)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun shr_large_multiLimb_negative() {
        val mbi = newLargeAcc(256)
        mbi -= BigInt.from("999999999999")

        val expected = mbi.toBigInt() shr 113

        mbi.mutShr(113)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun ushr_small_positive() {
        val mbi = newAccFromLong(96)
        val expected = mbi.toBigInt().ushr(5)

        mbi.mutUshr(5)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun ushr_small_negative_zeroFill() {
        val mbi = newAccFromLong(-96)
        val expected = mbi.toBigInt().ushr(5)

        mbi.mutUshr(5)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun ushr_large_multiLimb_positive() {
        val mbi = newLargeAcc(224)
        val expected = mbi.toBigInt().ushr(97)

        mbi.mutUshr(97)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun ushr_large_multiLimb_negative() {
        val mbi = newLargeAcc(224)
        mbi -= BigInt.from("123456789012345")

        val expected = mbi.toBigInt().ushr(97)

        mbi.mutUshr(97)

        assertAccEquals(expected, mbi)
    }

    @Test
    fun ushr_allBits() {
        val mbi = newLargeAcc(256)
        val expected = BigInt.ZERO

        mbi.mutUshr(512)

        assertAccEquals(expected, mbi)
    }

}