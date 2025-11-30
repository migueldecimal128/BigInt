// SPDX-License-Identifier: MIT

package com.decimal128.bigint

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestGcd {

    val verbose = false

    @Test
    fun testGcd() {
        repeat(10000) {
            test1(Random.nextInt(8))
        }
    }

    fun test1(bitLen: Int) {
        val x = BigInt.fromRandom(bitLen, withRandomSign = true)
        val y = BigInt.fromRandom(bitLen, withRandomSign = true)

        testSymmetry(x, y)
        testIdempotence(x)
        testZero(x)
        testSigns(x, y)

        val k = BigInt.fromRandom(Random.nextInt(29))
        val a = x * k
        val b = y * k

        val gcdAB = a.gcd(b)
        val gcdXY = x.gcd(y)
        assertEquals(gcdAB, k * gcdXY)
    }

    fun testSymmetry(x: BigInt, y: BigInt) {
        val gcdXY = x.gcd(y)
        val gcdYX = y.gcd(x)
        assertEquals(gcdXY, gcdYX)
    }

    fun testIdempotence(x: BigInt) {
        val gcdXX = x.gcd(x)
        assertEquals(gcdXX, x.abs())
    }

    fun testZero(x: BigInt) {
        val gcdXZero = x.gcd(BigInt.ZERO)
        val gcdZeroX = BigInt.ZERO.gcd(x)
        assertEquals(gcdXZero, x.abs())
        assertEquals(gcdZeroX, x.abs())
    }

    fun testSigns(x: BigInt, y: BigInt) {
        val gcd0 = x.gcd(y)
        val gcd1 = x.gcd(y.negate())
        val gcd2 = x.negate().gcd(y)
        val gcd3 = x.negate().gcd(y.negate())

        assertEquals(gcd0, gcd1)
        assertEquals(gcd0, gcd2)
        assertEquals(gcd0, gcd3)
    }


    @Test
    fun testProblemChild() {
        val x = BigInt.from("75739105468096430")
        val y = BigInt.from("112746730774794142")
        testSymmetry(x, y)
    }

    @Test
    fun testProblemChild2() {
        val x = BigInt.from("4")
        val y = BigInt.from("1")
        testSymmetry(x, y)

    }

    @Test
    fun testProblemChild3() {
        val x = BigInt.from("-39")
        val y = BigInt.from("-39")
        val k = BigInt.from("115399892")
        val a = k * x
        val b = k * y
        val gcdAB = a.gcd(b)
        val gcdXY = x.gcd(y)
        val gcdXY_k = k * gcdXY
        if (verbose) {
            println("x:$x y:$y k:$k a:$a b:$b")
            println("gcdAB:$gcdAB gcdXY:$gcdXY gcdXY_k:$gcdXY_k")
            println("==> gcdAB:$gcdAB gcdXY_k:$gcdXY_k")
            val eq = gcdAB EQ gcdXY_k
            println("eq:$eq")
        }
        assertEquals(gcdAB, gcdXY_k)
    }

}