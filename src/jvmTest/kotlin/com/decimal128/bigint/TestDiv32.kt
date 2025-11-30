package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.BigIntExtensions.toBigInt
import org.junit.jupiter.api.Assertions.assertThrows
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDiv32 {

    val verbose = false

    @Test
    fun testDiv32() {
        for (i in 0..<10000) {
            testUnsigned()
            testSigned()
        }
    }

    fun testUnsigned() {
        val hi = randomHi(65)
        val bi = hi.toBigInteger()
        val w = rng.nextUInt()
        if (verbose)
            println("hi:$hi w:$w")
        if (w != 0u) {
            val quotBi = (bi / BigInteger.valueOf(w.toLong())).toBigInt()
            val quot1 = hi / BigInt.from(w)
            val quot2 = hi / w
            assertEquals(quotBi, quot1)
            assertEquals(quot1, quot2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val quotBi = (bi / BigInteger.valueOf(w.toLong())).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val quot1 = hi / BigInt.from(w)
            }
            assertThrows(ArithmeticException::class.java) {
                val quot2 = hi / w
            }
        }

        if (hi.isNotZero()) {
            val inverseBi = (BigInteger.valueOf(w.toLong()) / bi).toBigInt()
            val inverse1 = BigInt.from(w) / hi
            val inverse2 = w / hi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger.valueOf(w.toLong()) / bi).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = BigInt.from(w) / hi
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse2 = w / hi
            }

        }
    }

    fun testSigned() {
        val hi = randomHi(65)
        val bi = hi.toBigInteger()
        val n = rng.nextInt()
        if (verbose)
            println("hi:$hi n:$n")
        if (n != 0) {
            val quotBi = (bi / BigInteger.valueOf(n.toLong())).toBigInt()
            val quot1 = hi / BigInt.from(n)
            val quot2 = hi / n
            assertEquals(quotBi, quot1)
            assertEquals(quot1, quot2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val quotBi = (bi / BigInteger.valueOf(n.toLong())).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val quot1 = hi / BigInt.from(n)
            }
            assertThrows(ArithmeticException::class.java) {
                val quot2 = hi / n
            }
        }

        if (hi.isNotZero()) {
            val inverseBi = (BigInteger.valueOf(n.toLong()) / bi).toBigInt()
            val inverse1 = BigInt.from(n) / hi
            val inverse2 = n / hi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger.valueOf(n.toLong()) / bi).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = BigInt.from(n) / hi
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse2 = n / hi
            }

        }
    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): BigInt {
        val rand = BigInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

    @Test
    fun testProblemChild() {
        val hi = BigInt.from("-1021459206398")
        val w = 3967413780u
        val quot = hi / w
        val quot2 = hi / BigInt.from(w)
        assertEquals(quot2, quot)

        val inverse = w / hi
        val inverse2 = BigInt.from(w) / hi
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

}