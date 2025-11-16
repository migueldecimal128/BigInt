package com.decimal128.hugeint

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class TestISqrt {

    val verbose = false

    @Test
    fun testISqrt() {
        repeat(100000) {
            val bitLen = Random.nextInt(2000)
            testSqrtUp(bitLen)
            testSqrtDown(bitLen)
        }
    }

    fun testSqrtUp(bitLen: Int) {
        val hi = HugeInt.fromRandom(bitLen)
        if (verbose)
            println("testSqrUp hi:$hi")
        val hi2 = hi.sqr()
        if (hi != hi2.isqrt()) {
            println("kilroy was here!")
            assertEquals(hi, hi2.isqrt())
        }

        if (hi > 1) {
            if (verbose)
                println("hi:$hi hi2:$hi2")
            val isqrtDown = (hi2 - 1).isqrt()
            if (hi-1 != isqrtDown) {
                println("snafu!")
                val down2 = (hi2 - 1).isqrt()
                assertEquals(hi - 1, down2)
            }

            val isqrtUp = (hi2 + 1).isqrt()
            assertEquals(hi, isqrtUp)
        }
    }

    fun testSqrtDown(bitLen: Int) {
        val hi = HugeInt.fromRandom(bitLen)
        if (verbose)
            println("testSqrtDown hi:$hi")
        val isqrt = hi.isqrt()
        assertTrue(isqrt.sqr() <= hi)
        assertTrue((isqrt + 1).sqr() > hi)
    }

    @Test
    fun testA() {
        val hi = HugeInt.from(2_000_000_000_000uL)
        val sqr = hi.sqr()
        val isqrt = sqr.isqrt()
        assertEquals(hi, isqrt)
    }

    @Test
    fun testNeg() {
        val hi = HugeInt.from(-4)
        assertThrows<ArithmeticException> {
            hi.isqrt()
        }
    }

    @Test
    fun testSmallThatFailsHardwareSqrt() {
        val hi = HugeInt.from(89515880)
        val hi2 = hi.sqr()
        val hi2a = hi * hi
        assertEquals(hi2, hi2a)
        val bitLen2 = hi2.magnitudeBitLen()
        check (bitLen2 == 53)
        val roundTrip = hi2.isqrt()
        assertEquals(hi, roundTrip)

        val down1 = hi2-1
        val isqrtDown1 = down1.isqrt()
        assertTrue(isqrtDown1.sqr() <= down1 && (isqrtDown1 + 1).sqr() > down1)
        assertTrue(isqrtDown1 < hi)

    }

    @Test
    fun testProblemChild1() {
        val hi = HugeInt.from("17826081680315188308")
        val hi2 = hi.sqr()
        val roundTrip = hi2.isqrt()
        assertEquals(hi, roundTrip)
    }

}