package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.BigIntExtensions.toBigInt
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
        val hiDw = BigInt.from(dw)
        if (verbose)
            println("hi:$hi dw:$dw")
        val sumBi = (hi.toBigInteger() + hiDw.toBigInteger()).toBigInt()
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
        val hiL = BigInt.from(l)
        if (verbose)
            println("hi:$hi l:$l")
        val sumBi = (hi.toBigInteger() + hiL.toBigInteger()).toBigInt()
        val sum0 = hi + hiL
        val sum1 = hi + l
        val sum2 = l + hi

        assertEquals(sumBi, sum0)
        assertEquals(sum0, sum1)
        assertEquals(sum0, sum2)

    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): BigInt {
        val rand = BigInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

    @Test
    fun testProblemChild() {
        val hi = BigInt.from("-5624193776")
        val dw = 2336654976178044700uL
        val sum = hi + dw
        val sum2 = hi + BigInt.from(dw)
        assertEquals(sum2, sum)
    }

}