package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntAccMutShlUshrShr {

    private fun newAccFromLong(v: Long): BigIntAccumulator =
        BigIntAccumulator().set(v)

    private fun newLargeAcc(bits: Int): BigIntAccumulator =
        BigIntAccumulator().setBit(bits)

    private fun assertAccEquals(expected: BigInt, acc: BigIntAccumulator) {
        val actual = acc.toBigInt()
        assertEquals(expected, actual)
    }

    @Test
    fun shl_small_positive() {
        val acc = newAccFromLong(3)
        val expected = acc.toBigInt() shl 5

        acc.mutShl(5)

        assertAccEquals(expected, acc)
    }

    @Test
    fun shl_small_negative() {
        val acc = newAccFromLong(-3)
        val expected = acc.toBigInt() shl 7

        acc.mutShl(7)

        assertAccEquals(expected, acc)
    }

    @Test
    fun shl_zero() {
        val acc = newAccFromLong(0)
        val expected = BigInt.ZERO

        acc.mutShl(123)

        assertAccEquals(expected, acc)
    }

    @Test
    fun shl_exact_limb_boundary() {
        val acc = newAccFromLong(1)
        val expected = acc.toBigInt() shl 32

        acc.mutShl(32)

        assertAccEquals(expected, acc)
    }

    @Test
    fun shl_large_multiLimb() {
        val acc = newLargeAcc(192)   // ≥ 6 limbs
        val expected = acc.toBigInt() shl 65

        acc.mutShl(65)

        assertAccEquals(expected, acc)
    }

    @Test
    fun shl_large_negative_multiLimb() {
        val acc = newLargeAcc(224)
        acc -= BigInt.from("123456789")   // make it negative

        val expected = acc.toBigInt() shl 91

        acc.mutShl(91)

        assertAccEquals(expected, acc)
    }

    @Test
    fun shr_small_positive() {
        val acc = newAccFromLong(96)
        val expected = acc.toBigInt() shr 5

        acc.mutShr(5)

        assertAccEquals(expected, acc)
    }

    @Test
    fun shr_small_negative_signExtend() {
        val acc = newAccFromLong(-96)
        val expected = acc.toBigInt() shr 5

        acc.mutShr(5)

        assertAccEquals(expected, acc)
    }

    @Test
    fun shr_to_zero() {
        val acc = newAccFromLong(1)
        val expected = BigInt.ZERO

        acc.mutShr(100)
        assertAccEquals(expected, acc)

        acc.set(-1)
        acc.mutShr(2)
        assertAccEquals(BigInt.NEG_ONE, acc)
    }

    @Test
    fun shr_exact_limb_boundary() {
        val acc = newLargeAcc(192)
        val expected = acc.toBigInt() shr 32

        acc.mutShr(32)

        assertAccEquals(expected, acc)
    }

    @Test
    fun shr_large_multiLimb_positive() {
        val acc = newLargeAcc(256)
        val expected = acc.toBigInt() shr 113

        acc.mutShr(113)

        assertAccEquals(expected, acc)
    }

    @Test
    fun shr_large_multiLimb_negative() {
        val acc = newLargeAcc(256)
        acc -= BigInt.from("999999999999")

        val expected = acc.toBigInt() shr 113

        acc.mutShr(113)

        assertAccEquals(expected, acc)
    }

    @Test
    fun ushr_small_positive() {
        val acc = newAccFromLong(96)
        val expected = acc.toBigInt().ushr(5)

        acc.mutUshr(5)

        assertAccEquals(expected, acc)
    }

    @Test
    fun ushr_small_negative_zeroFill() {
        val acc = newAccFromLong(-96)
        val expected = acc.toBigInt().ushr(5)

        acc.mutUshr(5)

        assertAccEquals(expected, acc)
    }

    @Test
    fun ushr_large_multiLimb_positive() {
        val acc = newLargeAcc(224)
        val expected = acc.toBigInt().ushr(97)

        acc.mutUshr(97)

        assertAccEquals(expected, acc)
    }

    @Test
    fun ushr_large_multiLimb_negative() {
        val acc = newLargeAcc(224)
        acc -= BigInt.from("123456789012345")

        val expected = acc.toBigInt().ushr(97)

        acc.mutUshr(97)

        assertAccEquals(expected, acc)
    }

    @Test
    fun ushr_allBits() {
        val acc = newLargeAcc(256)
        val expected = BigInt.ZERO

        acc.mutUshr(512)

        assertAccEquals(expected, acc)
    }

}