package com.decimal128.bigint

import kotlin.test.*

class TestBigIntSqr {

    private fun assertSqrEqualsMul(a: BigInt) {
        val sq = a.sqr()
        val mul = a * a
        assertEquals(mul, sq, "sqr() != a * a for a=$a")
    }

    @Test
    fun zero() {
        assertSqrEqualsMul(BigInt.ZERO)
    }

    @Test
    fun one() {
        assertSqrEqualsMul(BigInt.ONE)
    }

    @Test
    fun minusOne() {
        assertSqrEqualsMul(BigInt.NEG_ONE)
    }

    @Test
    fun smallIntegers() {
        for (i in -20..20) {
            assertSqrEqualsMul(i.toBigInt())
        }
    }

    @Test
    fun powersOfTwo() {
        val values = listOf(
            1L shl 1,
            1L shl 5,
            1L shl 16,
            1L shl 31
        )

        for (v in values) {
            assertSqrEqualsMul(v.toBigInt())
            assertSqrEqualsMul((-v).toBigInt())
        }
    }

    @Test
    fun limbBoundaryValues() {
        // 32-bit limb boundaries
        val values = listOf(
            0x7FFFFFFF,
            0x80000000u.toLong(),
            0xFFFFFFFFu.toLong()
        )

        for (v in values) {
            assertSqrEqualsMul(v.toBigInt())
            assertSqrEqualsMul(-v.toBigInt())
        }
    }

    @Test
    fun multiLimbExactBoundary() {
        // exactly 2 limbs
        val a = "4294967296".toBigInt()
        assertSqrEqualsMul(a)
        assertSqrEqualsMul(-a)
    }

    @Test
    fun multiLimbCarryPropagation() {
        val a = "0xFFFFFFFFFFFFFFFF".toBigInt()
        assertSqrEqualsMul(a)
    }

    @Test
    fun denseBitPattern() {
        val a = "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".toBigInt()
        assertSqrEqualsMul(a)
    }

    @Test
    fun sparseBitPattern() {
        val a = "0x8000000000000001".toBigInt()
        assertSqrEqualsMul(a)
    }

    @Test
    fun decimalStressValues() {
        val values = listOf(
            "10",
            "99",
            "100000000000000000000",
            "999999999999999999999999999999"
        )

        for (s in values) {
            assertSqrEqualsMul(s.toBigInt())
        }
    }

    @Test
    fun signInvariant() {
        val a = "-123456789012345678901234567890".toBigInt()
        val sq = a.sqr()
        assertTrue(!sq.isNegative(), "square must be non-negative")
    }

    @Test
    fun zeroIsCanonical() {
        val z = BigInt.ZERO.sqr()
        assertSame(BigInt.ZERO, z)
    }

}