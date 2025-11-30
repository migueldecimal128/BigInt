package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.EQ
import com.decimal128.bigint.BigIntExtensions.toBigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class TestPow {

    @Test
    fun testPow() {
        for (i in 0..<1000)
            test1()
    }

    val rng = Random.Default

    fun test1() {
        val hi = randomHi(2048)
        val bi = hi.toBigInteger()

        val pow = rng.nextInt(25)

        val hiResult = hi.pow(pow)
        val biResult = bi.pow(pow)

        assertTrue(hiResult.EQ(biResult))
    }

    fun randomHi(hiBitLen: Int) =
        BigInt.fromRandom(rng.nextInt(hiBitLen), rng)
}
