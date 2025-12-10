package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.EQ
import com.decimal128.bigint.BigIntExtensions.toBigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TestShiftLeft {

    val verbose = false

    val tcs = arrayOf(
        "2147483648",
        "0x2_00000000",
        "1",
        "0x80000000",
    )

    @Test
    fun testCases() {
        for (tc in tcs) {
            test1(tc.toBigInt())
        }
    }

    fun test1(bi: BigInt) {
        if (verbose)
            println(bi)
        val jbi = bi.toBigInteger()
        assertTrue(bi EQ jbi)

        val shl1 = bi.shl(1)
        val jshl1 = jbi.shiftLeft(1)
        assertTrue(shl1 EQ jshl1)

        val shl127 = bi.shl(127)
        val jshl127 = jbi.shiftLeft(127)
        assertTrue(shl127 EQ jshl127)

        val rnd = Random.nextInt(300)
        val shlRnd = bi.shl(rnd)
        val jshlRnd = jbi.shiftLeft(rnd)
        assertTrue(shlRnd EQ jshlRnd)

        val roundTrip = shlRnd shr rnd
        val jroundTrip = jshlRnd.shiftRight(rnd)
        assertTrue(roundTrip EQ jroundTrip)
        assertTrue(roundTrip EQ bi)

    }

    @Test
    fun testRandom() {
        repeat(10000) {
            val bi = BigInt.randomWithRandomBitLen(maxBitLen = 33)
            test1(bi)
        }
    }

    @Test
    fun testX() {
        testLowLevel(0.toBigInt(), 128)
        testLowLevel(0.toBigInt(), 1)
        testLowLevel(0.toBigInt(), 0)
        testLowLevel(1.toBigInt(), 128)
        testLowLevel(1.toBigInt(), 127)
        testLowLevel(1.toBigInt(), 64)
        testLowLevel(1.toBigInt(), 63)
        testLowLevel(1.toBigInt(), 32)
        testLowLevel(1.toBigInt(), 31)
        testLowLevel(1.toBigInt(), 1)
    }

    fun testLowLevel(bi: BigInt, shiftLeft: Int) {
        val magia = bi.magnitudeToLittleEndianIntArray()
        val bi2 = BigInt.fromLittleEndianIntArray(bi.isNegative(), magia)
        assertTrue(bi EQ bi2)

        val biLeft = bi shl shiftLeft
        val biBitLen = bi.magnitudeBitLen()
        val limbCount = if (biBitLen == 0) 0 else (biBitLen + shiftLeft + 31) / 32
        val magiaLeft = IntArray(limbCount)
        val limbCount2 = Magus.setShiftLeft(magiaLeft, magia, magia.size, shiftLeft)
        val bi2Left = BigInt.fromLittleEndianIntArray(bi.isNegative(), magiaLeft)
        assertEquals(limbCount, limbCount2)
        assertTrue(biLeft EQ bi2Left)
    }

    @Test
    fun testProblemChild() {
        val tc = 2147483648.toBigInt()

    }
}