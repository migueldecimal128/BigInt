package com.decimal128.hugeint

import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSub64 {

    val verbose = false

    @Test
    fun testSub64() {
        for (i in 0..<10000) {
            testUnsigned()
            testSigned()
        }
    }

    fun testUnsigned() {
        val hi = randomHi(66)
        val dw = rng.nextULong()
        if (verbose)
            println("hi:$hi dw:$dw")
        val diff0 = hi - HugeInt.from(dw)
        val diff1 = hi - dw

        assertEquals(diff0, diff1)

        val reverse0 = HugeInt.from(dw) - hi
        val reverse1 = dw - hi
        assertEquals(reverse0, reverse1)

        assertEquals(diff1, -reverse1)

    }

    fun testSigned() {
        val hi = randomHi(66)
        val l = rng.nextLong()
        if (verbose)
            println("hi:$hi l:$l")
        val diff0 = hi - HugeInt.from(l)
        val diff1 = hi - l

        assertEquals(diff0, diff1)

        val reverse0 = HugeInt.from(l) - hi
        val reverse1 = l - hi
        assertEquals(reverse0, reverse1)

        assertEquals(diff1, -reverse1)

    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): HugeInt {
        val rand = HugeInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

    @Test
    fun testProblemChild() {
        val hi = HugeInt.from("34652241377136429301")
        val ul = 10000000000uL //9543044438753291034uL
        val diff0 = hi - HugeInt.from(ul)
        val diff1 = hi - ul
        assertEquals(diff0, diff1)
    }

}