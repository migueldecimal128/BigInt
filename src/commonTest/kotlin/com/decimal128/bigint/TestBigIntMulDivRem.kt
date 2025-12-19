package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestBigIntMulDivRem {

    val verbose = false

    private fun bi(v: Long) = BigInt.from(v)
    private fun mbi(v: BigInt) = MutableBigInt().set(v)

    // ------------------------------------------------------------
    // 1. Small deterministic tests (signs + identities)
    // ------------------------------------------------------------
    @Test
    fun testMulSmall() {
        val cases = listOf(
            Pair(3L, 5L),
            Pair(-3L, 5L),
            Pair(3L, -5L),
            Pair(-3L, -5L),
            Pair(0L, 7L),
            Pair(7L, 0L)
        )

        for ((a, b) in cases) {
            val A = bi(a)
            val B = bi(b)
            val mbi = MutableBigInt().set(A)
            mbi.setMul(A, B)
            assertEquals(bi(a * b), mbi.toBigInt(), "mul failed: $a * $b")
        }
    }

    @Test
    fun testDivRemSmall() {
        val cases = listOf(
            Pair(17L, 5L),
            Pair(-17L, 5L),
            Pair(17L, -5L),
            Pair(-17L, -5L),
            Pair(1L, 1L),
            Pair(-1L, 1L),
            Pair(1L, -1L)
        )

        for ((a, b) in cases) {
            val A = bi(a)
            val B = bi(b)

            val qAcc = mbi(A)
            qAcc.setDiv(A, B)

            val rAcc = mbi(A)
            rAcc.setRem(A, B)

            val q = bi(a / b)
            val r = bi(a % b)

            assertEquals(q, qAcc.toBigInt(), "div failed: $a / $b")
            assertEquals(r, rAcc.toBigInt(), "rem failed: $a % $b")
        }
    }

    @Test
    fun testBigIntRem_Knuth_17_mod_5() {
        val x = BigInt.from(17)
        val y = BigInt.from(5)

        val r = x % y   // this forces your BigInt rem path → knuthDivide

        assertEquals(BigInt.from(2), r, "BigInt remainder failed: 17 % 5")
    }

    @Test
    fun testAccumulatorRem_Knuth_17_mod_5() {
        val x = BigInt.from(17)
        val y = BigInt.from(5)

        val mbi = MutableBigInt().set(x)
        mbi.setRem(x, y)

        assertEquals(
            BigInt.from(2),
            mbi.toBigInt(),
            "Accumulator remainder failed: 17 % 5"
        )
    }

    @Test
    fun debugAccumulatorRemState_17_mod_5() {
        val x = BigInt.from(17)
        val y = BigInt.from(5)

        val mbi = MutableBigInt().set(x)
        mbi.setRem(x, y)

        println("mbi.magia = ${mbi.magia.joinToString()}")
        println("meta.normLen = ${mbi.meta.normLen}")
        println("normLen(magia) = ${Mago.normLen(mbi.magia)}")
    }

    // ------------------------------------------------------------
    // 2. Div/Rem identity for small values
    // ------------------------------------------------------------
    @Test
    fun testDivRemIdentity() {
        for (a in listOf(-20L, -7L, -1L, 0L, 1L, 7L, 20L)) {
            for (b in listOf(-7L, -3L, 1L, 3L, 7L)) {
                val A = bi(a)
                val B = bi(b)

                val divAcc = mbi(A)
                divAcc.setDiv(A, B)

                val remAcc = mbi(A)
                remAcc.setRem(A, B)

                val q = divAcc.toBigInt()
                val r = remAcc.toBigInt()

                // Check identity: A = B*q + r
                val lhs = A
                val rhs = B * q + r

                assertEquals(lhs, rhs, "identity failed for $a, $b")

                // Check |r| < |b|
                assertTrue(r.abs() < B.abs(), "|r| < |b| violated for $a % $b")
            }
        }
    }

    // ------------------------------------------------------------
    // 3. Aliasing tests: this === x, this === y, x === y === this
    // ------------------------------------------------------------

    @Test
    fun testMulAliasing_this_is_x() {
        val x = bi(12)
        val y = bi(7)
        val mbi = mbi(x)
        mbi.setMul(mbi.toBigInt(), y)
        assertEquals(bi(84), mbi.toBigInt())
    }

    @Test
    fun testMulAliasing_this_is_y() {
        val x = bi(12)
        val y = bi(7)
        val mbi = mbi(y)
        mbi.setMul(x, mbi.toBigInt())
        assertEquals(bi(84), mbi.toBigInt())
    }

    @Test
    fun testMulAliasing_double() {
        val x = bi(15)
        val mbi = mbi(x)
        mbi.setMul(mbi.toBigInt(), mbi.toBigInt())  // x * x
        assertEquals(bi(225), mbi.toBigInt())
    }

    @Test
    fun testDivAliasing_this_is_x() {
        val x = bi(35)
        val y = bi(7)
        val mbi = mbi(x)
        mbi.setDiv(mbi.toBigInt(), y)
        assertEquals(bi(5), mbi.toBigInt())
    }

    @Test
    fun testRemAliasing_this_is_x() {
        val x = bi(35)
        val y = bi(7)
        val mbi = mbi(x)
        mbi.setRem(mbi.toBigInt(), y)
        assertEquals(bi(0), mbi.toBigInt())
    }

    // ------------------------------------------------------------
    // 4. Multi-limb tests
    // ------------------------------------------------------------

    @Test
    fun testMulLargeRandom() {
        val A = BigInt.randomWithBitLen(500)
        val B = BigInt.randomWithBitLen(500)

        val mbi = MutableBigInt().set(A)
        mbi.setMul(A, B)

        assertEquals(A * B, mbi.toBigInt())
    }

    @Test
    fun testDivRemLargeRandom() {
        val A = BigInt.randomWithBitLen(500)
        val B = BigInt.randomWithBitLen(300).abs() + bi(1)  // avoid zero

        val qAcc = mbi(A)
        qAcc.setDiv(A, B)

        val rAcc = mbi(A)
        rAcc.setRem(A, B)

        val q = qAcc.toBigInt()
        val r = rAcc.toBigInt()

        // identity
        assertEquals(A, B * q + r)
        assertTrue(r.abs() < B.abs())
    }

    @Test
    fun testRem_twoLimb_by_oneLimb() {
        val x = BigInt.from(0x1_0000_0000L + 123) // 2 limbs
        val y = BigInt.from(97)
        val mbi = MutableBigInt().set(x)
        mbi.setRem(x, y)
        assertEquals(x % y, mbi.toBigInt())
    }

    @Test
    fun testRem_multiLimb_by_oneLimb_calcRem() {
        val x = BigInt.randomWithBitLen(300) // ~10 limbs
        val y = BigInt.from(97)
        val mbi = MutableBigInt().set(x)
        mbi.setRem(x, y)
        assertEquals(x % y, mbi.toBigInt())
    }

    @Test
    fun testRem_xLessThanY() {
        val x = BigInt.from(17)
        val y = BigInt.from(500)
        val mbi = MutableBigInt().set(x)
        mbi.setRem(x, y)
        assertEquals(x, mbi.toBigInt())
    }

    @Test
    fun testRem_twoLimb_by_twoLimb() {
        val x = BigInt.from(0x1_0000_0000L + 123)
        val y = BigInt.from(0x2_0000_0000L + 7)
        val mbi = MutableBigInt().set(x)
        mbi.setRem(x, y)
        assertEquals(x % y, mbi.toBigInt())
    }

    @Test
    fun testRem_multiLimb_by_twoLimb_knuth() {
        val x = BigInt.randomWithBitLen(300)
        val y = BigInt.randomWithBitLen(64)  // 2 limbs
        val mbi = MutableBigInt().set(x)
        mbi.setRem(x, y)
        assertEquals(x % y, mbi.toBigInt())
    }

    @Test
    fun testRem_knuth_multiLimb() {
        val x = BigInt.randomWithBitLen(500)
        val y = BigInt.randomWithBitLen(300)
        if (verbose)
            println("x:$x y:$y")
        val mbi = MutableBigInt().set(x)
        mbi.setRem(x, y)
        assertEquals(x % y, mbi.toBigInt())
    }

    @Test
    fun testProblem() {
        val x = "2693898200269885236149444911153".toBigInt()
        val y = "874066428351266532052357330140".toBigInt()
        if (verbose)
            println("x:$x y:$y")
        val mbi = MutableBigInt().set(x)
        mbi.setRem(x, y)
        val biRem = x % y
        val accRem = mbi.toBigInt()
        if (verbose)
            println("biRem:$biRem accRem:$accRem")
        assertEquals(x % y, mbi.toBigInt())
    }


}