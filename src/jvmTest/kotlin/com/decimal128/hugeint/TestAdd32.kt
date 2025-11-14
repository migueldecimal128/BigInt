package com.decimal128.hugeint

import com.decimal128.hugeint.HugeIntExtensions.toBigInteger
import com.decimal128.hugeint.HugeIntExtensions.toHugeInt
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAdd32 {

    val verbose = false

    @Test
    fun testAdd32() {
        for (i in 0..<10000) {
            testUnsigned()
            testSigned()
        }
    }

    fun testUnsigned() {
        val hi = randomHi(300)
        val bi = hi.toBigInteger()
        val w = rng.nextUInt()
        if (verbose)
            println("hi:$hi w:$w")
        val sumBi = (bi + BigInteger.valueOf(w.toLong())).toHugeInt()
        val sum1 = hi + HugeInt.from(w)
        val sum2 = hi + w
        val sum3 = w + hi
        assertEquals(sumBi, sum1)
        assertEquals(sum1, sum2)
        assertEquals(sum2, sum3)
    }

    fun testSigned() {
        val hi = randomHi(300)
        val bi = hi.toBigInteger()
        val n = rng.nextInt()
        if (verbose)
            println("hi:$hi n:$n")
        val sumBi = (bi + BigInteger.valueOf(n.toLong())).toHugeInt()
        val sum1 = hi + HugeInt.from(n)
        val sum2 = hi + n
        val sum3 = n + hi
        assertEquals(sumBi, sum1)
        assertEquals(sum1, sum2)
        assertEquals(sum2, sum3)

    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): HugeInt {
        val rand = HugeInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

    @Test
    fun testProblemChild() {
        val hi = HugeInt.from("35689796992407102546798857499")
        val w = 137190797555284112
        val sum = hi + w
        val sum2 = hi + HugeInt.from(w)
        assertEquals(sum2, sum)
    }

    @Test
    fun testProblemChild2() {
        val hi = HugeInt.from("13814960379311575371116077557")
        val w = 2401666871u
        val sum0 = hi + HugeInt.from(w)
        val sum1 = hi + w
        val sum2 = w + hi
        assertEquals(sum0, sum1)
        assertEquals(sum0, sum2)
    }

    @Test
    fun testProblemChild3() {
        val hi = HugeInt.from("-1044467618609941889539867")
        val bi = hi.toBigInteger()
        val n = -818208931
        val sum0 = hi + HugeInt.from(n)
        val sum1 = hi + n
        val sum2 = n + hi
        assertEquals(sum0, sum1)
        assertEquals(sum0, sum2)
    }

    @Test
    fun testProblem3() {
        val hi = HugeInt.from("35689796992407102546798857499")
        val bi = hi.toBigInteger()
        val hi2 = bi.toHugeInt()
        assert(hi EQ hi2)
    }

}