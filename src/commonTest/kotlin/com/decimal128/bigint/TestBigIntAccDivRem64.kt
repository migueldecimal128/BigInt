package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntAccDivRem64 {

    private fun newLargeAcc(bits: Int): MutableBigInt =
        MutableBigInt().setBit(bits)

    private fun newAccFromDecimal(s: String): MutableBigInt =
        MutableBigInt().set(BigInt.from(s))

    private fun assertAccEquals(expected: BigInt, mbi: MutableBigInt) {
        assertEquals(expected, mbi.toBigInt())
    }

    @Test
    fun setDiv_singleBitLarge() {
        val dividend = newLargeAcc(256)
        val divisor: ULong = 0x1_0000_0001uL

        val out = MutableBigInt()
        out.setDiv(dividend, divisor)

        val expected = dividend.toBigInt() / divisor.toBigInt()
        assertAccEquals(expected, out)
    }

    @Test
    fun setDiv_denseMultiLimb() {
        val dividend = newAccFromDecimal(
            "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        )
        val divisor: ULong = 0xFEDCBA9876543211uL

        val out = MutableBigInt()
        out.setDiv(dividend, divisor)

        val expected = dividend.toBigInt() / divisor.toBigInt()
        assertAccEquals(expected, out)
    }

    @Test
    fun setDiv_divisorNear64Bits() {
        val dividend = newLargeAcc(320)
        val divisor: ULong = 0xFFFF_FFFF_FFFF_FFFDuL

        val out = MutableBigInt()
        out.setDiv(dividend, divisor)

        val expected = dividend.toBigInt() / divisor.toBigInt()
        assertAccEquals(expected, out)
    }

    @Test
    fun setDiv_quotientZero() {
        val dividend = newLargeAcc(32)
        val divisor: ULong = 0x1_0000_0001uL

        val out = MutableBigInt()
        out.setDiv(dividend, divisor)

        val expected = BigInt.ZERO
        assertAccEquals(expected, out)
    }


}