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
        val bi = BigInt.randomWithRandomBitLen(500)
        val jbi = bi.toBigInteger()

        val pow = rng.nextInt(25)

        val biResult = bi.pow(pow)
        val jbiResult = jbi.pow(pow)

        assertTrue(biResult EQ jbiResult)
    }

}
