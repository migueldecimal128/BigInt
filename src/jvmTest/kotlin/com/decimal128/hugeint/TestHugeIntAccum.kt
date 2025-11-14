package com.decimal128.hugeint

import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.Test

class TestHugeIntAccum {

    val verbose = false

    @Test
    fun testHugeIntAccum() {
        repeat(10000) {
            testAddSub()
            testMul()
            testAddAbsValue()
            testAddSquareOf()
        }
    }

    fun testAddSub() {
        val hia = HugeIntAccumulator()
        var hi = HugeInt.ZERO

        repeat(rng.nextInt(1000)) {
            val n = randomInt()
            hia += n
            hi += n
            assertTrue(hi EQ hia.toHugeInt())
        }
        assertTrue(hi EQ hia.toHugeInt())

        repeat(rng.nextInt(1000)) {
            val w = randomUInt()
            hia += w
            hi += w
        }
        assertTrue(hi EQ hia.toHugeInt())

        repeat(rng.nextInt(1000)) {
            val l = randomLong()
            hia += l
            hi += l
        }
        assertTrue(hi EQ hia.toHugeInt())

        repeat(rng.nextInt(1000)) {
            val dw = randomULong()
            hia += dw
            hi += dw
        }
        assertTrue(hi EQ hia.toHugeInt())


        repeat(rng.nextInt(100)) {
            val rand = randomHugeInt(200)
            hia += rand
            hi += rand
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<5) {
            hia += hia
            hi += hi
        }
        assertTrue(hi EQ hia.toHugeInt())

        // now start subtracting

        repeat(rng.nextInt(1000)) {
            val n = randomInt()
            hia -= n
            hi -= n
        }
        assertTrue(hi EQ hia.toHugeInt())

        repeat(rng.nextInt(1000)) {
            val w = randomUInt()
            hia -= w
            hi -= w
        }
        assertTrue(hi EQ hia.toHugeInt())

        repeat(rng.nextInt(1000)) {
            val l = randomLong()
            hia -= l
            hi -= l
        }
        assertTrue(hi EQ hia.toHugeInt())

        repeat(rng.nextInt(1000)) {
            val dw = randomULong()
            hia -= dw
            hi -= dw
        }
        assertTrue(hi EQ hia.toHugeInt())

        repeat(rng.nextInt(100)) {
            val rand = randomHugeInt(200)
            hia -= rand
            hi -= rand
        }
        assertTrue(hi EQ hia.toHugeInt())

        hia -= hia
        hi -= hi
        assertTrue(hi EQ hia.toHugeInt())
    }

    fun testMul() {
        val hia = HugeIntAccumulator().set(1)
        var hi = HugeInt.ONE

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomInt()
            if (rand == 0)
                continue
            hia *= rand
            hi *= rand
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomUInt()
            if (rand == 0u)
                continue
            hia *= rand
            hi *= rand
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomLong()
            if (rand == 0L)
                continue
            hia *= rand
            hi *= rand
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomULong()
            if (rand == 0uL)
                continue
            hia *= rand
            hi *= rand
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomHugeInt(200)
            if (rand.isZero())
                continue
            hia *= rand
            hi *= rand
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<3) {
            hia *= hia
            hi *= hi
        }
        assertTrue(hi EQ hia.toHugeInt())
    }

    fun testAddAbsValue() {
        val hia = HugeIntAccumulator()
        var hi = HugeInt.ZERO

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomInt()
            hia.addAbsValueOf(rand)
            hi += rand.absoluteValue
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomLong()
            hia.addAbsValueOf(rand)
            hi += rand.absoluteValue
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomHugeInt(200)
            hia.addAbsValueOf(rand)
            hi += rand.abs()
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<3) {
            hia.addAbsValueOf(hia)
            hi += hi.absoluteValue
        }
        assertTrue(hi EQ hia.toHugeInt())
    }

    fun testAddSquareOf() {
        val hia = HugeIntAccumulator()
        var hi = HugeInt.ZERO

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomInt()
            hia.addSquareOf(rand)
            hi += rand.absoluteValue.toLong() * rand.absoluteValue.toLong()
        }
        assertTrue(hi EQ hia.toHugeInt())

        hia.setZero()
        hi = HugeInt.ZERO
        for (i in 0..<rng.nextInt(10)) {
            val rand = randomUInt()
            hia.addSquareOf(rand)
            hi += rand.toULong() * rand.toULong()
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomLong()
            hia.addSquareOf(rand)
            hi += HugeInt.from(rand).sqr()
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomULong()
            hia.addSquareOf(rand)
            hi += HugeInt.from(rand).sqr()
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<rng.nextInt(10)) {
            val rand = randomHugeInt(200)
            if (rand.isZero())
                continue
            hia.addSquareOf(rand)
            hi += if (rng.nextBoolean()) rand.sqr() else rand * rand
        }
        assertTrue(hi EQ hia.toHugeInt())

        for (i in 0..<3) {
            hia.addSquareOf(hia)
            hi += if (rng.nextBoolean()) hi.sqr() else hi * hi
        }
        assertTrue(hi EQ hia.toHugeInt())
    }

    val rng = Random.Default

    fun randomHugeInt(hiBitLen: Int): HugeInt {
        val n = rng.nextInt(hiBitLen)
        val v = HugeInt.fromRandom(n, rng)
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