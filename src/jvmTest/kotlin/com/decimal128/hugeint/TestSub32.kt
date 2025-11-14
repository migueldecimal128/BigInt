package com.decimal128.hugeint

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

    fun testUnsigned() {
        val hi = this.randomHi(128)
        val w = rng.nextUInt()
        if (verbose)
            println("hi:$hi w:$w")
        val diff0 = hi - HugeInt.from(w)
        val diff1 = hi - w

        if (diff0 != diff1)
            println("diff0:$diff0 diff1:$diff1")
        assertEquals(diff0, diff1)

        val reverse0 = HugeInt.from(w) - hi
        val reverse1 = w - hi
        assertEquals(reverse0, reverse1)

        assertEquals(diff1, -reverse1)

    }

    fun testSigned() {
        val hi = this.randomHi(128)
        val n = rng.nextInt()
        if (verbose)
            println("hi:$hi n:$n")
        val diff0 = hi - HugeInt.from(n)
        val diff1 = hi - n

        if (diff0 != diff1)
            println("diff0:$diff0 diff1:$diff1")
        assertEquals(diff0, diff1)

        val reverse0 = HugeInt.from(n) - hi
        val reverse1 = n - hi
        assertEquals(reverse0, reverse1)

        assertEquals(diff1, -reverse1)

    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): HugeInt {
        val rand = HugeInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

}