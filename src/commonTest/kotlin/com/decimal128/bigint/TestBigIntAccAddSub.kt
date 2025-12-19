package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntAccAddSub {

    @Test
    fun testAdd() {

    }

    private fun bi(v: Int): BigInt = BigInt.from(v)    // adjust to your constructor
    private fun mbi(v: Int): MutableBigInt =
        MutableBigInt().set(bi(v))

    @Test
    fun testAddAndSubAllCombinations() {
        val values = listOf(-20, -5, -1, 0, 1, 5, 20)

        for (x in values) {
            for (y in values) {
                // --- Test Addition ---
                val accAdd = mbi(x).setAdd(bi(x), bi(y))
                val expectedAdd = x + y
                assertEquals(
                    bi(expectedAdd),
                    accAdd.toBigInt(),
                    "FAILED: $x + $y"
                )

                // --- Test Subtraction ---
                val accSub = mbi(x).setSub(bi(x), bi(y))
                val expectedSub = x - y
                assertEquals(
                    bi(expectedSub),
                    accSub.toBigInt(),
                    "FAILED: $x - $y"
                )
            }
        }
    }

    @Test
    fun testAlias_this_is_x_add() {
        val mbi = MutableBigInt().set(bi(10))     // mbi = 10
        mbi.setAdd(mbi.toBigInt(), bi(7))             // mbi = mbi + 7
        assertEquals(bi(17), mbi.toBigInt(), "alias: this === x (addition) failed")
    }

    @Test
    fun testAlias_this_is_y_add() {
        val mbi = MutableBigInt().set(bi(7))      // mbi = 7
        mbi.setAdd(bi(10), mbi.toBigInt())            // mbi = 10 + mbi
        assertEquals(bi(17), mbi.toBigInt(), "alias: this === y (addition) failed")
    }

    // ------------------------------------------------------------
    // 1. Test multi-limb carry propagation
    // ------------------------------------------------------------
    @Test
    fun testLargeCarry() {
        // (2^1024 - 1) + 1 = 2^1024
        val x = BigInt.withBitMask(1024) - bi(1)   // all 1 bits
        val y = bi(1)

        val mbi = MutableBigInt().set(x)
        mbi.setAdd(x, y)

        val expected = BigInt.withBitMask(1024)
        assertEquals(expected, mbi.toBigInt(), "large carry propagation failed")
    }

    // ------------------------------------------------------------
    // 2. Test multi-limb borrow propagation
    // ------------------------------------------------------------
    @Test
    fun testLargeBorrow() {
        // (2^1024) - 1
        val x = BigInt.withBitMask(1024)
        val y = bi(1)

        val mbi = MutableBigInt().set(x)
        mbi.setSub(x, y)

        val expected = BigInt.withBitMask(1024) - bi(1)
        assertEquals(expected, mbi.toBigInt(), "large borrow propagation failed")
    }

    // ------------------------------------------------------------
    // 3. Test large random add (no alias)
    // ------------------------------------------------------------
    @Test
    fun testRandomLargeAdd() {
        val x = BigInt.randomWithBitLen(2000)
        val y = BigInt.randomWithBitLen(2000)

        val mbi = MutableBigInt().set(x)
        mbi.setAdd(x, y)

        val expected = x + y
        assertEquals(expected, mbi.toBigInt(), "random 2000-bit addition failed")
    }

    // ------------------------------------------------------------
    // 4. Test large random sub (no alias)
    // ------------------------------------------------------------
    @Test
    fun testRandomLargeSub() {
        val x = BigInt.randomWithBitLen(2000)
        val y = BigInt.randomWithBitLen(2000)

        val mbi = MutableBigInt().set(x)
        mbi.setSub(x, y)

        val expected = x - y
        assertEquals(expected, mbi.toBigInt(), "random 2000-bit subtraction failed")
    }

    // ------------------------------------------------------------
    // 5. Test alias: this === x for large values
    // ------------------------------------------------------------
    @Test
    fun testLargeAlias_this_is_x_add() {
        val x = BigInt.randomWithBitLen(1500)
        val y = BigInt.randomWithBitLen(1500)

        val mbi = MutableBigInt().set(x)
        mbi.setAdd(mbi.toBigInt(), y)

        val expected = x + y
        assertEquals(expected, mbi.toBigInt(), "alias (this === x) add failed")
    }

    // ------------------------------------------------------------
    // 6. Test alias: this === y for large values
    // ------------------------------------------------------------
    @Test
    fun testLargeAlias_this_is_y_add() {
        val x = BigInt.randomWithBitLen(1500)
        val y = BigInt.randomWithBitLen(1500)

        val mbi = MutableBigInt().set(y)
        mbi.setAdd(x, mbi.toBigInt())

        val expected = x + y
        assertEquals(expected, mbi.toBigInt(), "alias (this === y) add failed")
    }

    // ------------------------------------------------------------
    // 7. Alias: doubling a large number (x == y == this)
    // ------------------------------------------------------------
    @Test
    fun testLargeAlias_double() {
        val x = BigInt.randomWithBitLen(1800)

        val mbi = MutableBigInt().set(x)
        mbi.setAdd(mbi.toBigInt(), mbi.toBigInt())  // mbi = x + x

        val expected = x + x
        assertEquals(expected, mbi.toBigInt(), "alias (x == y == this) doubling failed")
    }

    // ------------------------------------------------------------
    // 8. Large subtract of identical values (alias)
    // ------------------------------------------------------------
    @Test
    fun testLargeAlias_cancelToZero() {
        val x = BigInt.randomWithBitLen(1600)

        val mbi = MutableBigInt().set(x)
        mbi.setSub(mbi.toBigInt(), x)  // mbi = x - x

        val expected = BigInt.ZERO
        assertEquals(expected, mbi.toBigInt(), "alias subtract to zero failed")
    }


}