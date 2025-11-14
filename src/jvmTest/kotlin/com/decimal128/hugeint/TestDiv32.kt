package com.decimal128.hugeint

import com.decimal128.hugeint.HugeIntExtensions.toBigInteger
import com.decimal128.hugeint.HugeIntExtensions.toHugeInt
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
            val quotBi = (bi / BigInteger.valueOf(w.toLong())).toHugeInt()
            val quot1 = hi / HugeInt.from(w)
            val quot2 = hi / w
            assertEquals(quotBi, quot1)
            assertEquals(quot1, quot2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val quotBi = (bi / BigInteger.valueOf(w.toLong())).toHugeInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val quot1 = hi / HugeInt.from(w)
            }
            assertThrows(ArithmeticException::class.java) {
                val quot2 = hi / w
            }
        }

        if (hi.isNotZero()) {
            val inverseBi = (BigInteger.valueOf(w.toLong()) / bi).toHugeInt()
            val inverse1 = HugeInt.from(w) / hi
            val inverse2 = w / hi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger.valueOf(w.toLong()) / bi).toHugeInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = HugeInt.from(w) / hi
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
            val quotBi = (bi / BigInteger.valueOf(n.toLong())).toHugeInt()
            val quot1 = hi / HugeInt.from(n)
            val quot2 = hi / n
            assertEquals(quotBi, quot1)
            assertEquals(quot1, quot2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val quotBi = (bi / BigInteger.valueOf(n.toLong())).toHugeInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val quot1 = hi / HugeInt.from(n)
            }
            assertThrows(ArithmeticException::class.java) {
                val quot2 = hi / n
            }
        }

        if (hi.isNotZero()) {
            val inverseBi = (BigInteger.valueOf(n.toLong()) / bi).toHugeInt()
            val inverse1 = HugeInt.from(n) / hi
            val inverse2 = n / hi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger.valueOf(n.toLong()) / bi).toHugeInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = HugeInt.from(n) / hi
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse2 = n / hi
            }

        }
    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): HugeInt {
        val rand = HugeInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

    @Test
    fun testProblemChild() {
        val hi = HugeInt.from("-1021459206398")
        val w = 3967413780u
        val quot = hi / w
        val quot2 = hi / HugeInt.from(w)
        assertEquals(quot2, quot)

        val inverse = w / hi
        val inverse2 = HugeInt.from(w) / hi
        assertEquals(inverse2, inverse)
    }

    @Test
    fun testProblemChild2() {
        val hi = HugeInt.from("-213010038")
        val n = 46736949
        val quot = hi / n
        val quot2 = hi / HugeInt.from(n)
        assertEquals(quot2, quot)

        val inverse = n / hi
        val inverse2 = HugeInt.from(n) / hi
        assertEquals(inverse2, inverse)
    }

}