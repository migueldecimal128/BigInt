package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.BigIntExtensions.toBigInt
import org.junit.jupiter.api.Assertions.assertThrows
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRem64 {

    val verbose = false

    @Test
    fun testRem64() {
        for (i in 0..<10000) {
            testUnsigned()
            testSigned()
        }
    }

    fun testUnsigned() {
        val bi = BigInt.randomWithRandomBitLen(1024, withRandomSign = true)
        val jbi = bi.toBigInteger()
        val dw = Random.nextULong()
        if (verbose)
            println("bi:$bi dw:$dw")
        if (dw != 0uL) {
            val remBi = (jbi % BigInteger("$dw")).toBigInt()
            val rem1 = bi % BigInt.from(dw)
            val rem2 = bi % dw
            assertEquals(remBi, rem1)
            assertEquals(rem1, rem2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val remBi = (jbi % BigInteger("$dw")).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val rem1 = bi % BigInt.from(dw)
            }
            assertThrows(ArithmeticException::class.java) {
                val rem2 = bi % dw
            }
        }

        if (bi.isNotZero()) {
            val inverseBi = (BigInteger("$dw") % jbi).toBigInt()
            val inverse1 = BigInt.from(dw) % bi
            val inverse2 = dw % bi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger.valueOf(dw.toLong()) % jbi).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = BigInt.from(dw) % bi
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse2 = dw % bi
            }

        }
    }

    fun testSigned() {
        val hi = BigInt.randomWithRandomBitLen(512)
        val bi = hi.toBigInteger()
        val l = Random.nextLong()
        if (verbose)
            println("hi:$hi l:$l")
        if (l != 0L) {
            val quotBi = (bi % BigInteger.valueOf(l.toLong())).toBigInt()
            val quot1 = hi % BigInt.from(l)
            val quot2 = hi % l
            assertEquals(quotBi, quot1)
            assertEquals(quot1, quot2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val quotBi = (bi % BigInteger.valueOf(l.toLong())).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val quot1 = hi % BigInt.from(l)
            }
            assertThrows(ArithmeticException::class.java) {
                val quot2 = hi % l
            }
        }

        if (hi.isNotZero()) {
            val inverseBi = (BigInteger.valueOf(l.toLong()) % bi).toBigInt()
            val inverse1 = BigInt.from(l) % hi
            val inverse2 = l % hi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger.valueOf(l.toLong()) % bi).toBigInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = BigInt.from(l) % hi
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse2 = l % hi
            }

        }
    }

    @Test
    fun testProblemChild() {
        val hi = BigInt.from("-1021459206398")
        val dw = 3967413780uL
        val remBi = (hi.toBigInteger() % BigInteger("$dw")).toBigInt()
        val rem = hi % dw
        val rem2 = hi % BigInt.from(dw)
        assertEquals(remBi, rem)
        assertEquals(rem2, rem)

        val biInv = BigInteger("$dw") % BigInteger("$hi")
        val inverseBi = biInv.toBigInt()
        val inverse = dw % hi
        assertEquals(inverseBi, inverse)
        val inverse2 = BigInt.from(dw) % hi
        assertEquals(inverse2, inverse)
    }

    @Test
    fun testProblemChild2() {
        val hi = BigInt.from("-374001150")
        val l = -1716976294
        val remBi = (BigInteger("$hi") % BigInteger("$l")).toBigInt()
        val rem = hi % l
        val rem2 = hi % BigInt.from(l)
        assertEquals(remBi, rem)
        assertEquals(rem2, rem)

        val invBi = (BigInteger("$l") % BigInteger("$hi")).toBigInt()
        val inverse = l % hi
        val inverse2 = BigInt.from(l) % hi
        assertEquals(invBi, inverse)
        assertEquals(inverse2, inverse)
    }

}