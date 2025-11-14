package com.decimal128.hugeint

import com.decimal128.hugeint.HugeIntExtensions.toBigInteger
import com.decimal128.hugeint.HugeIntExtensions.toHugeInt
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAdd64 {

    val verbose = false

    @Test
    fun testAdd64() {
        for (i in 0..<10000) {
            testUnsigned()
            testSigned()
        }
    }

    fun testUnsigned() {
        val hi = randomHi(300)
        val dw = rng.nextULong()
        val hiDw = HugeInt.from(dw)
        if (verbose)
            println("hi:$hi dw:$dw")
        val sumBi = (hi.toBigInteger() + hiDw.toBigInteger()).toHugeInt()
        val sum0 = hi + hiDw
        val sum1 = hi + dw
        val sum2 = dw + hi

        assertEquals(sumBi, sum0)
        assertEquals(sum0, sum1)
        assertEquals(sum0, sum2)

    }

    fun testSigned() {
        val hi = randomHi(300)
        val l = rng.nextLong()
        val hiL = HugeInt.from(l)
        if (verbose)
            println("hi:$hi l:$l")
        val sumBi = (hi.toBigInteger() + hiL.toBigInteger()).toHugeInt()
        val sum0 = hi + hiL
        val sum1 = hi + l
        val sum2 = l + hi

        assertEquals(sumBi, sum0)
        assertEquals(sum0, sum1)
        assertEquals(sum0, sum2)

    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): HugeInt {
        val rand = HugeInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

    @Test
    fun testProblemChild() {
        val hi = HugeInt.from("-5624193776")
        val dw = 2336654976178044700uL
        val sum = hi + dw
        val sum2 = hi + HugeInt.from(dw)
        assertEquals(sum2, sum)
    }

}