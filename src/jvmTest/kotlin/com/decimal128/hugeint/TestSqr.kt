package com.decimal128.hugeint

import com.decimal128.hugeint.HugeIntExtensions.toBigInteger
import com.decimal128.hugeint.HugeIntExtensions.toHugeInt
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextUInt
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
        val hi = HugeInt.from("489124697967574338029000324")
        test1(hi)
    }

    fun test1(hi: HugeInt) {
        val bi = hi.toBigInteger()
        val biSqr = bi * bi

        val hiSqr = hi.sqr()
        val hiSqr2 = hi * hi
        val hiSqr3 = hi.pow(2)
        if (verbose)
            println("hi:$hi hiSqr:$hiSqr")

        assertEquals(biSqr.toHugeInt(), hiSqr)
        assertEquals(hiSqr, hiSqr2)
        assertEquals(hiSqr, hiSqr3)
    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): HugeInt {
        val rand = HugeInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

}