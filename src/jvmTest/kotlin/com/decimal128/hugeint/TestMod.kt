package com.decimal128.hugeint

import com.decimal128.hugeint.HugeIntExtensions.toBigInteger
import com.decimal128.hugeint.HugeIntExtensions.toHugeInt
import org.junit.jupiter.api.Assertions.assertThrows
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMod {

    val verbose = false

    @Test
    fun testMod() {
        for (i in 0..<10000) {
            testRandom1()
        }
    }

    fun testRandom1() {
        val hiDividend = randomHi(66)
        val hiDivisor = randomHi(66)
        test1(hiDividend, hiDivisor)
    }

    val rng = Random.Default

    fun randomHi(hiBitLen: Int): HugeInt {
        val rand = HugeInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

    @Test
    fun testProblemChild() {
        val hiDividend = HugeInt.from("18852484663843340740")
        val hiDivisor = HugeInt.from("26620419243123035246")

        test1(hiDividend, hiDivisor)
    }

    fun test1(hiDividend: HugeInt, hiDivisor: HugeInt) {
        val biDividend = hiDividend.toBigInteger()
        val biDivisor = hiDivisor.toBigInteger()

        if (verbose)
            println("hiDividend:$hiDividend hiDivisor:$hiDivisor")
        if (hiDivisor.isNotZero()) {
            val remBi = (biDividend % biDivisor).toHugeInt()
            val remHi = hiDividend % hiDivisor
            assertEquals(remBi, remHi)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val remBi = (biDividend % biDivisor).toHugeInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val remHi = hiDividend % hiDivisor
            }
        }

        if (hiDividend.isNotZero()) {
            val inverseBi = (biDivisor % biDividend).toHugeInt()
            val inverse1 = hiDivisor % hiDividend
            assertEquals(inverseBi, inverse1)
        } else {
            assertThrows(ArithmeticException::class.java) {
                val inverseBi = (biDivisor % biDividend).toHugeInt()
            }
            assertThrows(ArithmeticException::class.java) {
                val inverseHi = hiDivisor % hiDividend
            }

        }
    }

}