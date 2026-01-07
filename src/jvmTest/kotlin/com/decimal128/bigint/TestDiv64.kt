package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.BigIntExtensions.toBigInt
import org.junit.jupiter.api.Assertions.assertThrows
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDiv64 {

    val verbose = false

    @Test
    fun testDiv64() {
        for (i in 0..<10000) {
            testUnsigned()
            testSigned()
        }
    }

    fun testUnsigned() {
        val hi = randomHi(130)
        val bi = hi.toBigInteger()
        val dw = rng.nextULong()
        if (verbose)
            println("hi:$hi dw:$dw")
        if (dw != 0uL) {
            val quotBi = (bi / BigInteger("$dw")).toBigInt()
            val quot1 = hi / BigInt.from(dw)
            val quot2 = hi / dw
            assertEquals(quotBi, quot1)
            assertEquals(quot1, quot2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val quotBi = (bi / BigInteger("$dw")).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val quot1 = hi / BigInt.from(dw)
            }
            assertThrows(ArithmeticException::class.java) {
                val quot2 = hi / dw
            }
        }

        if (hi.isNotZero()) {
            val inverseBi = (BigInteger("$dw") / bi).toBigInt()
            val inverse1 = BigInt.from(dw) / hi
            val inverse2 = dw / hi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger("$dw") / bi).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = BigInt.from(dw) / hi
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse2 = dw / hi
            }

        }
    }

    fun testSigned() {
        val hi = randomHi(65)
        val bi = hi.toBigInteger()
        val l = rng.nextLong()
        if (verbose)
            println("hi:$hi l:$l")
        if (l != 0L) {
            val quotBi = (bi / BigInteger.valueOf(l.toLong())).toBigInt()
            val quot1 = hi / BigInt.from(l)
            val quot2 = hi / l
            assertEquals(quotBi, quot1)
            assertEquals(quot1, quot2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val quotBi = (bi / BigInteger.valueOf(l.toLong())).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val quot1 = hi / BigInt.from(l)
            }
            assertThrows(ArithmeticException::class.java) {
                val quot2 = hi / l
            }
        }

        if (hi.isNotZero()) {
            val inverseBi = (BigInteger.valueOf(l.toLong()) / bi).toBigInt()
            val inverse1 = BigInt.from(l) / hi
            val inverse2 = l / hi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger.valueOf(l.toLong()) / bi).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = BigInt.from(l) / hi
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse2 = l / hi
            }

        }
    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): BigInt {
        val rand = BigInt.randomWithMaxBitLen(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

    @Test
    fun testProblemChild() {
        val hi = BigInt.from("16943852051772892430707956759219")
        val dw = 16883797134507450982uL
        val quot = hi / dw
        val quot2 = hi / BigInt.from(dw)
        assertEquals(quot2, quot)

        val inverse = dw / hi
        val inverse2 = BigInt.from(dw) / hi
        assertEquals(inverse2, inverse)
    }

    @Test
    fun testProblemChild2() {
        val hi = BigInt.from("-213010038")
        val n = 46736949
        val quot = hi / n
        val quot2 = hi / BigInt.from(n)
        assertEquals(quot2, quot)

        val inverse = n / hi
        val inverse2 = BigInt.from(n) / hi
        assertEquals(inverse2, inverse)
    }

    @Test
    fun testProblemChild3() {
        val biDividend = 23.toBigInt()
        val biDivisor = 16414802363296740285uL.toBigInt()

        val biQuot = biDividend / biDivisor
        assertEquals(BigInt.ZERO, biQuot)
    }

    @Test
    fun testProblemChild4() {
        val biDividend = (-74045).toBigInt()
        val divisor = -7866892704263223140

        val biQuot = biDividend / divisor
        assertEquals(BigInt.ZERO, biQuot)
    }

    @Test
    fun testProblemChild5() {
        val biDividend = BigInt.ZERO
        val divisor = 7866892704263223140

        val biQuot = biDividend / divisor
        assertEquals(BigInt.ZERO, biQuot)
    }

}