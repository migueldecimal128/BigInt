package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.BigIntExtensions.toBigInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestRem {

    val verbose = false

    val mbiA = MutableBigInt()

    @Test
    fun testRem() {
        for (i in 0..<10000) {
            testRandom1()
        }
    }

    fun testRandom1() {
        val hiDividend = BigInt.randomWithRandomBitLen(256, withRandomSign = true)
        val hiDivisor = BigInt.randomWithRandomBitLen(hiDividend.magnitudeBitLen(),
            withRandomSign = true)
        test1(hiDividend, hiDivisor)
    }

    @Test
    fun testProblemChild() {
        val hiDividend = BigInt.from("18852484663843340740")
        val hiDivisor = BigInt.from("26620419243123035246")

        test1(hiDividend, hiDivisor)
    }

    fun test1(biDividend: BigInt, biDivisor: BigInt) {
        val jbiDividend = biDividend.toBigInteger()
        val jbiDivisor = biDivisor.toBigInteger()

        if (verbose)
            println("biDividend:$biDividend biDivisor:$biDivisor")
        if (biDivisor.isNotZero()) {
            val remBi = (jbiDividend % jbiDivisor).toBigInt()
            val remHi = biDividend % biDivisor
            assertEquals(remBi, remHi)

            mbiA.setRem(biDividend, biDivisor)
            if (mbiA NE remHi) {
                println("kilroy was here!")
                mbiA.setRem(biDividend, biDivisor)
            }
            assertEquals(mbiA.toBigInt(), remHi)

            mbiA.set(biDividend)
            mbiA %= biDivisor
            assertEquals(mbiA.toBigInt(), remHi)
        } else {
            assertFailsWith<ArithmeticException> {
                val remBi = (jbiDividend % jbiDivisor).toBigInt()
            }
            assertFailsWith<ArithmeticException> {
                val remHi = biDividend % biDivisor
            }
            assertFailsWith<ArithmeticException> {
                mbiA.setRem(biDividend, biDivisor)
            }
            assertFailsWith<ArithmeticException> {
                mbiA.set(biDividend)
                mbiA %= biDivisor
            }
        }

        if (biDividend.isNotZero()) {
            val inverseBi = (jbiDivisor % jbiDividend).toBigInt()
            val inverse1 = biDivisor % biDividend
            assertEquals(inverseBi, inverse1)
        } else {
            assertFailsWith<ArithmeticException> {
                val inverseBi = (jbiDivisor % jbiDividend).toBigInt()
            }
            assertFailsWith<ArithmeticException> {
                val inverseHi = biDivisor % biDividend
            }

        }
    }

    @Test
    fun testProblemChildMbiA() {
        val biDividend = "204659303738342529".toBigInt()
        val biDivisor = "2443838061".toBigInt()
        val expected = "2229403455".toBigInt()

        val biObserved = biDividend % biDivisor
        assertEquals(expected, biObserved)

        val mbi = MutableBigInt()
        mbi.setRem(biDividend, biDivisor)
        assertEquals(expected, mbi.toBigInt())

        mbi.set(biDividend)
        mbi %= biDivisor
        assertEquals(expected, mbi.toBigInt())
    }

    @Test
    fun testProblemChildMbiB() {
        val biDividend = "77308206518156360178609516352109184660592338850427396297979040620".toBigInt()
        val biDivisor = "16091165179321870975".toBigInt()
        val expected = "3027753328848771245".toBigInt()

        val biObserved = biDividend % biDivisor
        assertEquals(expected, biObserved)

        val mbi = MutableBigInt()
        mbi.setRem(biDividend, biDivisor)
        assertEquals(expected, mbi.toBigInt())

        mbi.set(biDividend)
        mbi %= biDivisor
        assertEquals(expected, mbi.toBigInt())

        mbi.set(biDividend)
        mbi %= biDivisor
        assertEquals(expected, mbi.toBigInt())
    }

}