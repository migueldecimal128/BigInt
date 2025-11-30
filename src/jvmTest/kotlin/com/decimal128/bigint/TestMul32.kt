package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.BigIntExtensions.toBigInt
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMul32 {

    val verbose = false

    @Test
    fun testMul32() {
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
        val prodBi = (bi * BigInteger.valueOf(w.toLong())).toBigInt()
        val prod1 = hi * BigInt.from(w)
        val prod2 = hi * w
        val prod3 = w * hi
        assertEquals(prodBi, prod1)
        assertEquals(prod1, prod2)
        assertEquals(prod2, prod3)

    }

    fun testSigned() {
        val hi = randomHi(300)
        val bi = hi.toBigInteger()
        val n = rng.nextInt()
        if (verbose)
            println("hi:$hi n:$n")
        val prodBi = (bi * BigInteger.valueOf(n.toLong())).toBigInt()
        val prod1 = hi * BigInt.from(n)
        val prod2 = hi * n
        val prod3 = n * hi
        assertEquals(prodBi, prod1)
        assertEquals(prod1, prod2)
        assertEquals(prod2, prod3)

    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): BigInt {
        val rand = BigInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

    @Test
    fun testProblemChild() {
        val hi = BigInt.from("16592360993111090955868366364669943649")
        val w = 4164364427u
        val prod = hi * w
        val prod2 = hi * BigInt.from(w)
        assertEquals(prod2, prod)
    }

}