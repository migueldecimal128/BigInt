package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.BigIntExtensions.toBigInt
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
        val kbi = BigInt.randomWithMaxBitLen(300)
        val jbi = kbi.toBigInteger()
        val w = rng.nextUInt()
        if (verbose)
            println("kbi:$kbi w:$w")
        val sumBi = (jbi + BigInteger.valueOf(w.toLong())).toBigInt()
        val sum1 = kbi + BigInt.from(w)
        val sum2 = kbi + w
        val sum3 = w + kbi
        assertEquals(sumBi, sum1)
        assertEquals(sum1, sum2)
        assertEquals(sum2, sum3)
    }

    fun testSigned() {
        val kbi = BigInt.randomWithMaxBitLen(300)
        val jbi = kbi.toBigInteger()
        val n = rng.nextInt()
        if (verbose)
            println("hi:$kbi n:$n")
        val sumBi = (jbi + BigInteger.valueOf(n.toLong())).toBigInt()
        val sum1 = kbi + BigInt.from(n)
        val sum2 = kbi + n
        val sum3 = n + kbi
        assertEquals(sumBi, sum1)
        assertEquals(sum1, sum2)
        assertEquals(sum2, sum3)
    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): BigInt {
        val rand = BigInt.randomWithMaxBitLen(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

    @Test
    fun testProblemChild() {
        val hi = BigInt.from("35689796992407102546798857499")
        val w = 137190797555284112
        val sum = hi + w
        val sum2 = hi + BigInt.from(w)
        assertEquals(sum2, sum)
    }

    @Test
    fun testProblemChild2() {
        val hi = BigInt.from("13814960379311575371116077557")
        val w = 2401666871u
        val sum0 = hi + BigInt.from(w)
        val sum1 = hi + w
        val sum2 = w + hi
        assertEquals(sum0, sum1)
        assertEquals(sum0, sum2)
    }

    @Test
    fun testProblemChild3() {
        val hi = BigInt.from("-1044467618609941889539867")
        val bi = hi.toBigInteger()
        val n = -818208931
        val sum0 = hi + BigInt.from(n)
        val sum1 = hi + n
        val sum2 = n + hi
        assertEquals(sum0, sum1)
        assertEquals(sum0, sum2)
    }

    @Test
    fun testProblemChild3a() {
        //val hi = BigInt.from("-1044467618609941889539867")
        val hi = BigInt.from("10")
        val bi = hi.toBigInteger()
        //val n = -818208931
        val n = -1
        val biNeg1 = BigInt.from(n)
        val sum0 = hi + biNeg1
        val sum1 = hi + n
        val sum2 = n + hi
        assertEquals(sum0, sum1)
        assertEquals(sum0, sum2)
    }

    @Test
    fun testProblem3() {
        val kbi = BigInt.from("35689796992407102546798857499")
        val jbi = kbi.toBigInteger()
        val kbi2 = jbi.toBigInt()
        assert(kbi EQ kbi2)
    }

    @Test
    fun testProblem4() {
        val kbi = BigInt.from("310032710060098610463334926699")
        val n = -1843762480
        val expected = BigInt.from("310032710060098610461491164219")
        val observed = kbi + n
        assertEquals(expected, observed)
    }

}