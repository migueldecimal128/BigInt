package com.decimal128.bigint.crypto

import com.decimal128.bigint.MutableBigInt
import com.decimal128.bigint.toBigInt
import java.math.BigInteger
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestModPowVsBigInteger {

    @Test
    fun modPow_randomizedAgainstBigInteger() {
        val rnd = Random(123)

        val mJava = BigInteger.probablePrime(128, rnd)
        val m = mJava.toString().toBigInt()
        val ctx = ModContext(m)
        val out = MutableBigInt()

        repeat(500) {
            val aJava = BigInteger(mJava.bitLength() - 1, rnd).mod(mJava)
            val eJava = BigInteger(64, rnd)   // moderate exponent

            ctx.modPow(
                aJava.toString().toBigInt(),
                eJava.toString().toBigInt(),
                out
            )

            val expected = aJava.modPow(eJava, mJava)
            assertEquals(expected.toString(), out.toBigInt().toString())
        }
    }

}