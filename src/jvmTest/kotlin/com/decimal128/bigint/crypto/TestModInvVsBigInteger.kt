package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.MutableBigInt
import com.decimal128.bigint.toBigInt
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class TestModInvVsBigInteger {

    @Test
    fun modInv_resultInvariant() {
        val m = 101.toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        for (a in 1 until 101) {
            if (BigInteger.valueOf(a.toLong()).gcd(BigInteger.valueOf(101)) != BigInteger.ONE)
                continue

            ctx.modInv(a.toBigInt(), out)
            val inv = out.toBigInt()

            val check = (a.toBigInt() * inv) % m
            assertEquals(BigInt.ONE, check)
        }
    }

    @Test
    fun modInv_randomizedAgainstBigInteger() {
        val rnd = java.util.Random(98)

        val mJava = BigInteger.probablePrime(97, rnd)
        val m = mJava.toString().toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        repeat(1_000) {
            val aJava = BigInteger(mJava.bitLength() - 1, rnd).mod(mJava)

            // skip non-invertible cases
            if (aJava.gcd(mJava) != BigInteger.ONE)
                return@repeat

            ctx.modInv(aJava.toString().toBigInt(), out)

            val expected = aJava.modInverse(mJava).toString().toBigInt()
            val actual = out.toBigInt()

            if (expected != actual) {
                val ring = (expected - actual) % m
                println("expected:$expected actual:$actual m:$m")
                println("(expected - actual) % m ==> ring:$ring")
            }
            assertEquals(expected, actual)
        }
    }

}