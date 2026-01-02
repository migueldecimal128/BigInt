package com.decimal128.bigint

import com.decimal128.bigint.BigIntStats
import com.decimal128.bigint.MagoSqr.setSqrSchoolbook
import com.decimal128.bigint.MagoSqr.karatsubaSqr
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class TestKaratsubaSetSqr {

    // Helper to verify results against a simpler schoolbook implementation
    // or a known BigInt implementation if available in your KMP environment.
    private fun verifySquare(a: IntArray) {
        val n = a.size
        val zKaratsuba = IntArray(2 * n + 1)
        val zSchoolbook = IntArray(2 * n + 1)

        // Workspace size based on your formula: 3 * k1 + 3
        val k1 = n - (n / 2)
        val t = IntArray(3 * k1 + 3)

        // 1. Run Karatsuba
        karatsubaSqr(zKaratsuba, 0, a, 0, n, t)

        // 2. Run Schoolbook (Reference)
        setSqrSchoolbook(zSchoolbook, 0, a, 0, n)

        assertContentEquals(zSchoolbook, zKaratsuba, "Karatsuba squaring failed for size $n")
    }

    @Test
    fun testSmallSquare() {
        // Test a value that likely hits the threshold base case
        verifySquare(intArrayOf(0x12345678))
    }

    @Test
    fun testMiddleSquare() {
        verifySquare(intArrayOf(1, 2))
        verifySquare(intArrayOf(-2, -3))
    }

    @Test
    fun test3Limbs() {
        verifySquare(intArrayOf(1, 2, 3))
    }

    @Test
    fun test3LimbsHi() {
        verifySquare(intArrayOf(1, 1, 0x7FFF_FFFF.toInt()))
        verifySquare(intArrayOf(1, 1, 0x8000_0000.toInt()))
    }

    @Test
    fun testLargeSquarePowerOfTwo() {
        // Test a power of 2 size (e.g., 4 limbs)
        // Ensure minLimbThreshold is <= 4 for this to trigger Karatsuba
        verifySquare(intArrayOf(0x7FFFFFFF, 0x7FFFFFFF, 0x7FFFFFFF, 0x7FFFFFFF))
    }

    @Test
    fun testLargeSquareOddSize() {
        // Test an odd size to verify k0/k1 splitting logic (e.g., 5 limbs)
        verifySquare(intArrayOf(1, 2, 3, 4, 5))
    }

    @Test
    fun testCarryPropagation() {
        // Test values that generate many carries (all bits set)
        for (i in 2..<20) {
            val a = IntArray(i) { -1 } // 0xFFFFFFFF
            verifySquare(a)
        }
    }

    @Test
    fun testCarryPropagationX() {
        val a = IntArray(4) { -1 } // 0xFFFFFFFF
        verifySquare(a)
    }

    @Test
    fun testZeroValues() {
        verifySquare(IntArray(2) { 0 })
    }

    @Test
    fun testTwo() {
        verifySquare(intArrayOf(2, 3))
    }

    @Test
    fun testOffsetHandling() {
        val a = intArrayOf(0, 0, 1, 2, 3, 4, 0)
        val z = IntArray(12)
        val t = IntArray(3 * 2 + 3)

        // Square the middle 4 elements [1, 2, 3, 4] into z starting at index 2
        karatsubaSqr(z, 2, a, 2, 4, t)

        val expectedResult = IntArray(8)
        setSqrSchoolbook(expectedResult, 0, intArrayOf(1, 2, 3, 4), 0, 4)

        // Verify z[2..9] matches the expected square
        for (i in 0 until 8) {
            assertTrue(z[i + 2] == expectedResult[i], "Mismatch at index ${i + 2}")
        }
    }
}
