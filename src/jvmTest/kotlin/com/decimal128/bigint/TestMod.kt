package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.BigIntExtensions.toBigInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    fun randomHi(hiBitLen: Int): BigInt {
        val rand = BigInt.fromRandom(rng.nextInt(hiBitLen), rng)
        return if (rng.nextBoolean()) rand.negate() else rand
    }

    @Test
    fun testProblemChild() {
        val hiDividend = BigInt.from("18852484663843340740")
        val hiDivisor = BigInt.from("26620419243123035246")

        test1(hiDividend, hiDivisor)
    }

    fun test1(hiDividend: BigInt, hiDivisor: BigInt) {
        val biDividend = hiDividend.toBigInteger()
        val biDivisor = hiDivisor.toBigInteger()

        if (verbose)
            println("hiDividend:$hiDividend hiDivisor:$hiDivisor")
        if (hiDivisor.isNotZero()) {
            val remBi = (biDividend % biDivisor).toBigInt()
            val remHi = hiDividend % hiDivisor
            assertEquals(remBi, remHi)
        } else {
            assertFailsWith<ArithmeticException> {
                val remBi = (biDividend % biDivisor).toBigInt()
            }
            assertFailsWith<ArithmeticException> {
                val remHi = hiDividend % hiDivisor
            }
        }

        if (hiDividend.isNotZero()) {
            val inverseBi = (biDivisor % biDividend).toBigInt()
            val inverse1 = hiDivisor % hiDividend
            assertEquals(inverseBi, inverse1)
        } else {
            assertFailsWith<ArithmeticException> {
                val inverseBi = (biDivisor % biDividend).toBigInt()
            }
            assertFailsWith<ArithmeticException> {
                val inverseHi = hiDivisor % hiDividend
            }

        }
    }

}