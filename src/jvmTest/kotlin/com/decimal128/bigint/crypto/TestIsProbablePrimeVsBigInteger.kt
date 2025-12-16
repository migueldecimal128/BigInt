package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigIntExtensions.toBigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class TestIsProbablePrimeVsBigInteger {

    @Test
    fun testIsProbablePrime_againstJavaBigInteger() {
        repeat(500) {
            val n = BigInt.randomWithMaxBitLen(2048) or BigInt.ONE  // odd
            val ours = BigIntPrime.isProbablePrime(n)

            val java = n.toBigInteger().isProbablePrime(50)

            assertEquals(java, ours, "mismatch on $n")
        }
    }

}