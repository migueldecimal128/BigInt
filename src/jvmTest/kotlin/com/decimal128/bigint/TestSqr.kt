package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.BigIntExtensions.toBigInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSqr {

    val verbose = false

    @Test
    fun testSqr() {
        for (i in 0..<100000) {
            val hi = randomHi(257)
            test1(hi)
        }
    }

    @Test
    fun testProblemChild() {
        val hi = BigInt.from("489124697967574338029000324")
        test1(hi)
    }

    fun test1(hi: BigInt) {
        val bi = hi.toBigInteger()
        val biSqr = bi * bi

        val hiSqr = hi.sqr()
        val hiSqr2 = hi * hi
        val hiSqr3 = hi.pow(2)
        if (verbose)
            println("hi:$hi hiSqr:$hiSqr")

        assertEquals(biSqr.toBigInt(), hiSqr)
        assertEquals(hiSqr, hiSqr2)
        assertEquals(hiSqr, hiSqr3)
    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): BigInt {
        val rand = BigInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

}