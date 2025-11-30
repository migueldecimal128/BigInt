package com.decimal128.bigint

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntPrint {

    val verbose = false

    @Test
    fun testRandom() {
        for (i in 0..<1000000)
            testRandom1()
    }

    fun testRandom1() {
        val bi = randBi()
        val biStr = bi.toString()
        val hi = BigInt.from(biStr)
        val hiStr = hi.toString()
        assertEquals(biStr, hiStr)
    }

    val random = java.util.Random()

    fun randBi() : BigInteger {
        val bitLength = random.nextInt(0, 256)
        val bi = BigInteger(bitLength, random)
        return bi
    }

}