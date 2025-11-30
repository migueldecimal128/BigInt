package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.random.Random
import kotlin.test.Test

class TestRandom {

    @Test
    fun test1() {
        for (i in 0..<10000) {
            val hi = randomHi(1024)
            val bi = hi.toBigInteger()

            val hiBitLen = hi.magnitudeBitLen()
            val biBitLen = bi.bitLength()
            assertEquals(biBitLen, hiBitLen)

            val hiBitCount = hi.magnitudeCountOneBits()
            val biBitCount = bi.bitCount()
            assertEquals(biBitCount, hiBitCount)

            val hiNtz = hi.countTrailingZeroBits()
            val biNtz = bi.getLowestSetBit()
            assertEquals(biNtz, hiNtz)
        }
    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int) =
        BigInt.fromRandom(rng.nextInt(hiBitLen), rng)

}