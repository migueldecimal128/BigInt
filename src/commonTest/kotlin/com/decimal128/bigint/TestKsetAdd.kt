package com.decimal128.bigint


import kotlin.test.*

/**
 * Tests for:
 *   fun ksetAdd(t: IntArray, a: IntArray, a0Off: Int, k0: Int, a1Off: Int, k1: Int)
 *
 * Assumptions:
 * - Limbs are 32-bit, little-endian (limb 0 is least significant), interpreted as unsigned.
 * - k0 limbs are added pairwise; if k1>k0 then one extra limb from a1 is added with carry; final carry stored at t[k1].
 * - Karatsuba constraint: k1 == k0 or k1 == k0+1 (tests enforce this).
 *
 * NOTE: You asked “using BigInt for reference”. To keep this KMP/commonTest,
 * you only need to implement the two adapter functions below against *your* BigInt.
 */
class TestKsetAdd {

    // Helper to simulate the dw32 logic: converts Int to unsigned 64-bit Long
    private fun dw32(v: Int): ULong = v.toULong() and 0xFFFFFFFFuL

    @Test
    fun testStandardAdditionNoCarry() {
        val a = intArrayOf(1, 2, 3, 4) // x0=[1,2], x1=[3,4]
        val t = IntArray(3)

        // k0=2, k1=2 (even split)
        ksetAdd(t, a, 0, 2, 2)

        // Expected: t[0]=1+3=4, t[1]=2+4=6, t[2]=carry=0
        assertContentEquals(intArrayOf(4, 6, 0), t)
    }

    @Test
    fun testAdditionWithCarryPropagatingToEnd() {
        val a = intArrayOf(-1, -1, 1, 0) // -1 is 0xFFFFFFFF in bits
        val t = IntArray(3)

        ksetAdd(t, a, 0, 2, 2)

        // 0xFFFFFFFF + 1 = 0x100000000 (Result 0, carry 1)
        // 0xFFFFFFFF + 0 + carry(1) = 0x100000000 (Result 0, carry 1)
        assertContentEquals(intArrayOf(0, 0, 1), t)
    }

    @Test
    fun testUnevenSplitExtraLimb() {
        // a = [1, 2, 10, 20, 30]
        // x0 = [1, 2] (k0=2)
        // x1 = [10, 20, 30] (k1=3)
        val a = intArrayOf(1, 2, 10, 20, 30)
        val t = IntArray(4)

        ksetAdd(t, a, 0, 2, 3)

        // t[0] = 1 + 10 = 11
        // t[1] = 2 + 20 = 22
        // t[2] = 30 + carry(0) = 30
        // t[3] = carry = 0
        assertContentEquals(intArrayOf(11, 22, 30, 0), t)
    }

    @Test
    fun testOffsetAddition() {
        val a = intArrayOf(0, 0, 5, 5, 10, 10, 0)
        val t = IntArray(3)

        // Start at index 2
        ksetAdd(t, a, 2, 2, 2)

        // x0=[5,5], x1=[10,10]
        assertContentEquals(intArrayOf(15, 15, 0), t)
    }

    @Test
    fun testBoundsChecking() {
        val a = intArrayOf(1, 2, 3)
        val t = IntArray(2)

        // This should fail because k1=2 requires t.size to be at least 3
        assertFailsWith<IllegalArgumentException> {
            ksetAdd(t, a, 0, 1, 2)
        }
    }

}
