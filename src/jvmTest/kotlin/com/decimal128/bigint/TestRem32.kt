package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.BigIntExtensions.toBigInt
import org.junit.jupiter.api.Assertions.assertThrows
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRem32 {

    val verbose = false

    @Test
    fun testRem32() {
        for (i in 0..<10000) {
            testUnsigned()
            testSigned()
        }
    }

    fun testUnsigned() {
        val hi = BigInt.randomWithRandomBitLen(512)
        val bi = hi.toBigInteger()
        val w = Random.nextUInt()
        if (verbose)
            println("hi:$hi w:$w")
        if (w != 0u) {
            val remBi = (bi % BigInteger.valueOf(w.toLong())).toBigInt()
            val rem1 = hi % BigInt.from(w)
            val rem2 = hi % w
            assertEquals(remBi, rem1)
            assertEquals(rem1, rem2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val remBi = (bi % BigInteger.valueOf(w.toLong())).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val rem1 = hi % BigInt.from(w)
            }
            assertThrows(ArithmeticException::class.java) {
                val rem2 = hi % w
            }
        }

        if (hi.isNotZero()) {
            val inverseBi = (BigInteger.valueOf(w.toLong()) % bi).toBigInt()
            val inverse1 = BigInt.from(w) % hi
            val inverse2 = w % hi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger.valueOf(w.toLong()) % bi).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = BigInt.from(w) % hi
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse2 = w % hi
            }

        }
    }

    fun testSigned() {
        val bi = BigInt.randomWithRandomBitLen(66, withRandomSign = true)
        val jbi = bi.toBigInteger()
        val n = Random.nextInt()
        if (verbose)
            println("bi:$bi n:$n")
        if (n != 0) {
            val quotBi = (jbi % BigInteger.valueOf(n.toLong())).toBigInt()
            val quot1 = bi % BigInt.from(n)
            val quot2 = bi % n
            assertEquals(quotBi, quot1)
            assertEquals(quot1, quot2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val quotBi = (jbi % BigInteger.valueOf(n.toLong())).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val quot1 = bi % BigInt.from(n)
            }
            assertThrows(ArithmeticException::class.java) {
                val quot2 = bi % n
            }
        }

        if (bi.isNotZero()) {
            val inverseBi = (BigInteger.valueOf(n.toLong()) % jbi).toBigInt()
            val inverse1 = BigInt.from(n) % bi
            val inverse2 = n % bi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger.valueOf(n.toLong()) % jbi).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = BigInt.from(n) % bi
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse2 = n % bi
            }

        }
    }

    @Test
    fun testProblemChild() {
        val hi = BigInt.from("-1021459206398")
        val w = 3967413780u
        val remBi = (hi.toBigInteger() % BigInteger("$w")).toBigInt()
        val rem = hi % w
        val rem2 = hi % BigInt.from(w)
        assertEquals(remBi, rem)
        assertEquals(rem2, rem)

        val biInv = BigInteger("$w") % BigInteger("$hi")
        val inverseBi = biInv.toBigInt()
        val inverse = w % hi
        assertEquals(inverseBi, inverse)
        val inverse2 = BigInt.from(w) % hi
        assertEquals(inverse2, inverse)
    }

    @Test
    fun testProblemChild2() {
        val hi = BigInt.from("-374001150")
        val n = -1716976294
        val remBi = (BigInteger("$hi") % BigInteger("$n")).toBigInt()
        val rem = hi % n
        val rem2 = hi % BigInt.from(n)
        assertEquals(remBi, rem)
        assertEquals(rem2, rem)

        val invBi = (BigInteger("$n") % BigInteger("$hi")).toBigInt()
        val inverse = n % hi
        val inverse2 = BigInt.from(n) % hi
        assertEquals(invBi, inverse)
        assertEquals(inverse2, inverse)
    }

}