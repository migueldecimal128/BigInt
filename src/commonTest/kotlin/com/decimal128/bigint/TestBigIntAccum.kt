// SPDX-License-Identifier: MIT

package com.decimal128.bigint

import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class TestBigIntAccum {

    val verbose = true

    @Test
    fun testBigIntAccum() {
        repeat(10) {
            testAddSub()
            testMul()
            testAddAbsValue()
            testAddSquareOf()
        }
    }

    fun testEQ(bi: BigInt, bia: BigIntAccumulator): Boolean {
        if (bi EQ bia.toBigInt())
            return true
        return false
    }

    fun testAddSub() {
        val hia = BigIntAccumulator()
        var hi = BigInt.ZERO

        repeat(rng.nextInt(1000)) {
            val n = randomInt()
            hia += n
            hi += n
            assertTrue(hi EQ hia.toBigInt())
        }
        assertTrue(hi EQ hia.toBigInt())

        repeat(rng.nextInt(1000)) {
            val w = randomUInt()
            hia += w
            hi += w
        }
        assertTrue(hi EQ hia.toBigInt())

        repeat(rng.nextInt(1000)) {
            val l = randomLong()
            hia += l
            hi += l
        }
        assertTrue(hi EQ hia.toBigInt())

        repeat(rng.nextInt(1000)) {
            val dw = randomULong()
            hia += dw
            hi += dw
        }
        assertTrue(hi EQ hia.toBigInt())


        repeat(rng.nextInt(100)) {
            val rand = BigInt.randomWithBitLen(31)
            hia += rand
            hi += rand
            assertTrue(testEQ(hi, hia))
        }
        assertTrue(testEQ(hi, hia))

        for (i in 0..<5) {
            hia += hia
            hi += hi
            assertTrue(testEQ(hi, hia))
        }
        assertTrue(testEQ(hi, hia))

        // now start subtracting

        repeat(rng.nextInt(1000)) {
            val n = randomInt()
            hia -= n
            hi -= n
            assertTrue(testEQ(hi, hia))
        }
        assertTrue(testEQ(hi, hia))

        repeat(rng.nextInt(1000)) {
            val w = randomUInt()
            hia -= w
            hi -= w
            assertTrue(testEQ(hi, hia))
        }

        repeat(rng.nextInt(1000)) {
            val l = randomLong()
            hia -= l
            hi -= l
            assertTrue(testEQ(hi, hia))
        }

        repeat(rng.nextInt(1000)) {
            val dw = randomULong()
            hia -= dw
            hi -= dw
            assertTrue(testEQ(hi, hia))
        }

        repeat(rng.nextInt(100)) {
            val rand = randomBigInt(200)
            if (verbose)
                println("before: hia:$hia hi:$hi rand:$rand")
            hia -= rand
            hi -= rand
            if (verbose)
                println("after: hia:$hia hi:$hi")
            assertTrue(testEQ(hi, hia))
        }

        hia -= hia
        hi -= hi
        assertTrue(testEQ(hi, hia))
    }

    fun testMul() {
        val hia = BigIntAccumulator().set(1)
        var hi = BigInt.ONE

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomInt()
            if (rand == 0)
                continue
            hia *= rand
            hi *= rand
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomUInt()
            if (rand == 0u)
                continue
            hia *= rand
            hi *= rand
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomLong()
            if (rand == 0L)
                continue
            hia *= rand
            hi *= rand
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomULong()
            if (rand == 0uL)
                continue
            hia *= rand
            hi *= rand
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomBigInt(200)
            if (rand.isZero())
                continue
            hia *= rand
            hi *= rand
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<3) {
            hia *= hia
            hi *= hi
        }
        assertTrue(hi EQ hia.toBigInt())
    }

    fun testAddAbsValue() {
        val hia = BigIntAccumulator()
        var hi = BigInt.ZERO

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomInt()
            hia.addAbsValueOf(rand)
            hi += rand.absoluteValue
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomLong()
            hia.addAbsValueOf(rand)
            hi += rand.absoluteValue
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomBigInt(200)
            hia.addAbsValueOf(rand)
            hi += rand.abs()
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<3) {
            hia.addAbsValueOf(hia)
            hi += hi.absoluteValue
        }
        assertTrue(hi EQ hia.toBigInt())
    }

    fun testAddSquareOf() {
        val hia = BigIntAccumulator()
        var hi = BigInt.ZERO

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomInt()
            hia.addSquareOf(rand)
            hi += rand.absoluteValue.toLong() * rand.absoluteValue.toLong()
        }
        assertTrue(hi EQ hia.toBigInt())

        hia.setZero()
        hi = BigInt.ZERO
        for (i in 0..<rng.nextInt(10)) {
            val rand = randomUInt()
            hia.addSquareOf(rand)
            hi += rand.toULong() * rand.toULong()
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomLong()
            hia.addSquareOf(rand)
            hi += BigInt.from(rand).sqr()
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomULong()
            hia.addSquareOf(rand)
            hi += BigInt.from(rand).sqr()
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomBigInt(200)
            if (rand.isZero())
                continue
            hia.addSquareOf(rand)
            hi += if (rng.nextBoolean()) rand.sqr() else rand * rand
        }
        assertTrue(hi EQ hia.toBigInt())

        for (i in 0..<3) {
            hia.addSquareOf(hia)
            hi += if (rng.nextBoolean()) hi.sqr() else hi * hi
        }
        assertTrue(hi EQ hia.toBigInt())
    }

    val rng = Random.Default

    fun randomBigInt(hiBitLen: Int): BigInt {
        val n = rng.nextInt(hiBitLen)
        val v = BigInt.randomWithMaxBitLen(n, rng)
        return if (rng.nextBoolean()) v.negate() else v
    }

    fun randomInt(): Int {
        val n = rng.nextInt(31)
        val v = rng.nextInt(1 shl n)
        return if (rng.nextBoolean()) -v else v
    }

    fun randomUInt(): UInt =
        rng.nextLong(1L shl rng.nextInt(33)).toUInt()

    fun randomLong(): Long {
        val n = rng.nextInt(63)
        val v = rng.nextLong(1L shl n)
        return if (rng.nextBoolean()) -v else v
    }

    fun randomULong(): ULong {
        val n = rng.nextInt(64)
        val v = if (n < 63)
            (rng.nextLong(1L shl n) shl 1) + rng.nextInt(2)
        else
            rng.nextLong()
        return v.toULong()
    }
}