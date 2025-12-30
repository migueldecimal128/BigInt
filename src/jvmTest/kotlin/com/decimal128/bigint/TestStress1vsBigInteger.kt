package com.decimal128.bigint

import org.junit.jupiter.api.Assertions.assertTrue
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TestStress1vsBigInteger {

    val REPETITION_COUNT = 10
    val MAX_BIT_LENGTH = 1000

    fun genBigInt(rng: Random, maxLimbs: Int): BigInt {
        return when (rng.nextInt(8)) {
            0 -> BigInt.ZERO
            1 -> BigInt.ONE
            2 -> BigInt.NEG_ONE
            3 -> BigInt.ONE shl rng.nextInt(maxLimbs * 32)
            4 -> BigInt.randomWithBitLen(rng.nextInt(maxLimbs * 32), withRandomSign = true)
            5 -> BigInt.randomWithMaxBitLen(rng.nextInt(maxLimbs * 32), withRandomSign = true)
            6 -> BigInt.randomWithRandomBitLen(rng.nextInt(maxLimbs * 32), withRandomSign = true)
            else -> {
                // pathological carries
                val n = rng.nextInt(1, maxLimbs)
                BigInt.fromLittleEndianIntArray(
                    rng.nextBoolean(), IntArray(n) { 0xFFFF_FFFF.toInt() })
            }
        }
    }

    private fun BigIntNumber.toJ(): BigInteger {
        return if (Random.nextBoolean())
            BigInteger(this.toString()) // or faster limb-based adapter if you have one
        else
            BigInteger(this.toTwosComplementBigEndianByteArray())
    }


    private fun jToBigInt(j: BigInteger): BigInt =
        BigInt.from(j.toString())

    private fun assertBI(actual: BigIntNumber, expected: BigInteger, msg: String = "") {
        val aj = actual.toJ()
        if (aj != expected) {
            fail(
                buildString {
                    appendLine("BigInt mismatch $msg")
                    appendLine("expected = $expected")
                    appendLine("actual   = $aj")
                }
            )
        }
    }

    private fun stressPair(a: BigInt, b: BigInt,
                           ma: MutableBigInt, mb: MutableBigInt,
                           mz: MutableBigInt) {
        val ja = a.toJ()
        val jb = b.toJ()

        ma.set(a)
        mb.set(b)

        // add / sub
        assertBI(a + b, ja + jb, "a+b")
        mz.set(ma)
        mz += b
        assertBI(mz, ja + jb, "mutable a+b")

        assertBI(a - b, ja - jb, "a-b")
        mz.set(a)
        mz -= b
        assertBI(mz, ja - jb, "mutable a-b")

        assertBI(b - a, jb - ja, "b-a")
        mz.setSub(mb, ma)
        assertBI(mz, jb - ja, "mutable b-a")

        // mul
        assertBI(a * b, ja * jb, "a*b")
        mz.setMul(ma, mb)
        assertBI(mz, jb * ja, "mutable a*b")

        // square
        assertBI(a.sqr(), ja * ja, "a.sqr")
        mz.setSqr(a)
        assertBI(mz, ja * ja, "mutable sqr")

        // div / rem (skip zero)
        if (!b.isZero()) {
            assertBI(a / b, ja / jb, "a/b")
            mz.set(ma)
            mz /= b
            assertBI(mz, ja / jb, "mutable a/b")

            assertBI(a % b, ja % jb, "a%b")
            mz.setRem(a, b)
            assertBI(mz, ja % jb, "mutable a%b")
            mz.set(a)
            mz %= b
            assertBI(mz, ja % jb, "mutable a%b")

            assertEquals(a, (a / b) * b + (a % b))
        }

        // shifts
        val s = (0..256).random()
        val biShiftLeft = a shl s
        val jaShiftLeft = ja.shiftLeft(s)
        assertBI(biShiftLeft, jaShiftLeft, "a<<$s")
        mz.setShl(a, s)
        if (mz.toString() != ja.shiftLeft(s).toString()) {
            println("kilroy was here!")
            mz.setShl(a, s)
        }
        assertBI(mz, ja.shiftLeft(s), "mut a<<$s")
        mz.set(a)
        mz.mutShl(s)
        assertBI(mz, ja.shiftLeft(s), "mut a<<$s")

        assertBI(a shr s, ja.shiftRight(s), "a>>$s")
        mz.setShr(a, s)
        assertBI(mz, ja.shiftRight(s), "mut a>>$s")
        mz.set(a)
        mz.mutShr(s)
        assertBI(mz, ja.shiftRight(s), "mut a>>$s")

        if (!a.isNegative()) {
            assertBI(a ushr s, ja.shiftRight(s), "a>>>$s")
            mz.setUshr(a, s)
            assertBI(mz, ja.shiftRight(s), "mut a>>>$s")
            mz.set(a)
            mz.mutUshr(s)
            assertBI(mz, ja.shiftRight(s), "mut a>>>$s")

        }

        // pow (small exponent)
        val e = (0..20).random()
        val jbiPow = ja.pow(e)
        assertBI(a.pow(e), jbiPow, "a.pow($e)")

        mz.setPow(a, e)
        assertBI(a.pow(e), jbiPow, "mut a.pow($e)")

        mz.set(a)
        mz.mutPow(e)
        assertBI(a.pow(e), jbiPow, "mut a.pow($e)")

        // isqrt (non-negative only)
        if (!a.isNegative()) {
            val r = a.isqrt()
            val jr = ja.sqrt()
            assertBI(r, jr, "isqrt")
            assertTrue(r * r <= a)
            assertTrue((r + BigInt.ONE) * (r + BigInt.ONE) > a)
        }
    }

    @Test
    fun stress_bigint_against_java() {
        val seed = System.currentTimeMillis()
        val rng = Random(seed)
        val maxLimbs = (MAX_BIT_LENGTH) + 31 / 32

        val ma = MutableBigInt()
        val mb = MutableBigInt()
        val mz = MutableBigInt()
        try {
            repeat(REPETITION_COUNT) { iter ->
                val a = genBigInt(rng, maxLimbs)
                val b = genBigInt(rng, maxLimbs)
                stressPair(a, b, ma, mb, mz)

                // extra square-heavy workload
                if (iter % 10 == 0) {
                    val c = genBigInt(rng, maxLimbs)
                    assertBI(c.sqr(), c.toJ() * c.toJ(), "extra sqr")
                }
            }
        } catch (t: Throwable) {
            println("FAIL seed=$seed")
            throw t
        }
    }


}