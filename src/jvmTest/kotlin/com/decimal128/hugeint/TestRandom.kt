package com.decimal128.hugeint

import com.decimal128.hugeint.HugeIntExtensions.toBigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import java.math.BigInteger
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

            val hiBitCount = hi.magnitudeBitCount()
            val biBitCount = bi.bitCount()
            assertEquals(biBitCount, hiBitCount)

            val hiNtz = hi.trailingZeroCount()
            val biNtz = bi.getLowestSetBit()
            assertEquals(biNtz, hiNtz)
        }
    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int) =
        HugeInt.fromRandom(rng.nextInt(hiBitLen), rng)

}