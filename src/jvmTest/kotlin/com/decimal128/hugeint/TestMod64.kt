package com.decimal128.hugeint

import com.decimal128.hugeint.HugeIntExtensions.toBigInteger
import com.decimal128.hugeint.HugeIntExtensions.toHugeInt
import org.junit.jupiter.api.Assertions.assertThrows
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMod64 {

    val verbose = false

    @Test
    fun testMod64() {
        for (i in 0..<10000) {
            testUnsigned()
            testSigned()
        }
    }

    fun testUnsigned() {
        val hi = randomHi(65)
        val bi = hi.toBigInteger()
        val dw = rng.nextULong()
        if (verbose)
            println("hi:$hi dw:$dw")
        if (dw != 0uL) {
            val remBi = (bi % BigInteger("$dw")).toHugeInt()
            val rem1 = hi % HugeInt.from(dw)
            val rem2 = hi % dw
            assertEquals(remBi, rem1)
            assertEquals(rem1, rem2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val remBi = (bi % BigInteger("$dw")).toHugeInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val rem1 = hi % HugeInt.from(dw)
            }
            assertThrows(ArithmeticException::class.java) {
                val rem2 = hi % dw
            }
        }

        if (hi.isNotZero()) {
            val inverseBi = (BigInteger("$dw") % bi).toHugeInt()
            val inverse1 = HugeInt.from(dw) % hi
            val inverse2 = dw % hi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger.valueOf(dw.toLong()) % bi).toHugeInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = HugeInt.from(dw) % hi
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse2 = dw % hi
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
            val quotBi = (bi % BigInteger.valueOf(l.toLong())).toHugeInt()
            val quot1 = hi % HugeInt.from(l)
            val quot2 = hi % l
            assertEquals(quotBi, quot1)
            assertEquals(quot1, quot2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val quotBi = (bi % BigInteger.valueOf(l.toLong())).toHugeInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val quot1 = hi % HugeInt.from(l)
            }
            assertThrows(ArithmeticException::class.java) {
                val quot2 = hi % l
            }
        }

        if (hi.isNotZero()) {
            val inverseBi = (BigInteger.valueOf(l.toLong()) % bi).toHugeInt()
            val inverse1 = HugeInt.from(l) % hi
            val inverse2 = l % hi
            assertEquals(inverseBi, inverse1)
            assertEquals(inverse1, inverse2)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (BigInteger.valueOf(l.toLong()) % bi).toHugeInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse1 = HugeInt.from(l) % hi
            }
            assertThrows(ArithmeticException::class.java) {
                val inverse2 = l % hi
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
        val dw = 3967413780uL
        val remBi = (hi.toBigInteger() % BigInteger("$dw")).toHugeInt()
        val rem = hi % dw
        val rem2 = hi % HugeInt.from(dw)
        assertEquals(remBi, rem)
        assertEquals(rem2, rem)

        val biInv = BigInteger("$dw") % BigInteger("$hi")
        val inverseBi = biInv.toHugeInt()
        val inverse = dw % hi
        assertEquals(inverseBi, inverse)
        val inverse2 = HugeInt.from(dw) % hi
        assertEquals(inverse2, inverse)
    }

    @Test
    fun testProblemChild2() {
        val hi = HugeInt.from("-374001150")
        val l = -1716976294
        val remBi = (BigInteger("$hi") % BigInteger("$l")).toHugeInt()
        val rem = hi % l
        val rem2 = hi % HugeInt.from(l)
        assertEquals(remBi, rem)
        assertEquals(rem2, rem)

        val invBi = (BigInteger("$l") % BigInteger("$hi")).toHugeInt()
        val inverse = l % hi
        val inverse2 = HugeInt.from(l) % hi
        assertEquals(invBi, inverse)
        assertEquals(inverse2, inverse)
    }

}