package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.EQ
import com.decimal128.bigint.BigIntExtensions.toBigInteger
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TestShiftLeft {

    val tcs = arrayOf(
        "1",
        "0x80000000",
    )

    @Test
    fun testCases() {
        for (tc in tcs)
            test1(tc.toBigInt())
    }

    fun test1(bi: BigInt) {
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
            val bi = BigInt.randomWithRandomBitLen(maxBitLen = 500)
            test1(bi)
        }
    }

}