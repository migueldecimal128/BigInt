package com.decimal128.bigint

import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSub32 {

    val verbose = false

    @Test
    fun testSub32() {
        for (i in 0..<10000) {
            testUnsigned()
            testSigned()
        }
    }

    @Test
    fun testUnsigned() {
        val hi = this.randomHi(128)
        val w = rng.nextUInt()
        if (verbose)
            println("hi:$hi w:$w")
        val diff0 = hi - BigInt.from(w)
        val diff1 = hi - w
        if (verbose)
            println(" => diff0:$diff0 diff1:$diff1")

        if (diff0 != diff1)
            println("diff0:$diff0 diff1:$diff1")
        assertEquals(diff0, diff1)

        val biW = BigInt.from(w)
        val reverse0 = biW - hi
        val reverse1 = w - hi
        if (verbose)
            println(" => reverse0:$reverse0 reverse1:$reverse1")

        if (reverse0 != reverse1)
            println("reverse0:$reverse0 reverse1:$reverse1")
        assertEquals(reverse0, reverse1)

        assertEquals(diff1, -reverse1)

    }

    @Test
    fun testSigned() {
        val hi = this.randomHi(128)
        val n = rng.nextInt()
        if (verbose)
            println("hi:$hi n:$n")
        val diff0 = hi - BigInt.from(n)
        val diff1 = hi - n

        if (diff0 != diff1)
            println("diff0:$diff0 diff1:$diff1")
        assertEquals(diff0, diff1)

        val reverse0 = BigInt.from(n) - hi
        val reverse1 = n - hi
        assertEquals(reverse0, reverse1)

        assertEquals(diff1, -reverse1)

    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): BigInt {
        val rand = BigInt.randomWithMaxBitLen(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

}