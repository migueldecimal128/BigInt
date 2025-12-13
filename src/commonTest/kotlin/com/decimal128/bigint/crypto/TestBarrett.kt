package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.toBigInt
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBarrett {

    @Test
    fun testBarrettAgainstKnuth() {

        fun checkRemainder(m: BigInt, x: BigInt) {
            val bar = Barrett(m)
            val r1 = bar.reduce(x)
            val r2 = x % m
            assertEquals(r2, r1, "Mismatch for x=$x, m=$m")
        }

        // ------------------------------------------------
        // 1. Small moduli
        // ------------------------------------------------
        val smallMods = listOf(
            2.toBigInt(),
            3.toBigInt(),
            7.toBigInt(),
            10.toBigInt(),
            12345.toBigInt()
        )

        for (m in smallMods) {
            val bar = Barrett(m)
            val m2 = m * m

            checkRemainder(m, 0.toBigInt())
            checkRemainder(m, 1.toBigInt())
            checkRemainder(m, (m - 1.toBigInt()))
            checkRemainder(m, m)
            checkRemainder(m, (m + 1.toBigInt()))
            checkRemainder(m, (m2 - 1.toBigInt()))
            checkRemainder(m, (m2 shr 1))
        }

        // ------------------------------------------------
        // 2. Medium & large moduli (random)
        // ------------------------------------------------
        val testMods = listOf(
            BigInt.Companion.randomWithMaxBitLen(64),
            BigInt.Companion.randomWithMaxBitLen(128),
            BigInt.Companion.randomWithMaxBitLen(192),
            BigInt.Companion.randomWithMaxBitLen(256),
            BigInt.Companion.randomWithMaxBitLen(511).withSetBit(510) // force top bit
        )

        for (m in testMods) {
            val m2 = m.sqr()
            val m2BitLen = m2.magnitudeBitLen()

            // boundary points
            checkRemainder(m, m - 1.toBigInt())
            checkRemainder(m, m)
            checkRemainder(m, m + 1.toBigInt())
            checkRemainder(m, m2 - 1.toBigInt())

            // random tests inside x < m²
            repeat(200) {
                var x = BigInt.Companion.randomWithMaxBitLen(m2BitLen)
                while (x >= m2)
                    x = BigInt.Companion.randomWithMaxBitLen(m2BitLen)
                checkRemainder(m, x)
            }
        }

        // ------------------------------------------------
        // 3. Pathological moduli
        // ------------------------------------------------
        val pathologicalMods = listOf(
            (1.toBigInt() shl 255) - 1.toBigInt(),       // 2^255 - 1
            (1.toBigInt() shl 256) - 123.toBigInt(),     // dense
            (1.toBigInt() shl 300),                      // pure power of two
            (1.toBigInt() shl 300) + 1.toBigInt(),       // 2^300 + 1
            (1.toBigInt() shl 511)                       // sparse
        )

        for (m in pathologicalMods) {
            val bar = Barrett(m)
            val m2 = m * m

            repeat(200) {
                val x = BigInt.Companion.randomBelow(m2)
                checkRemainder(m, x)
            }
        }

        // ------------------------------------------------
        // 4. Deterministic brute-force for small m
        // ------------------------------------------------
        repeat(20) {
            val m = BigInt.Companion.randomWithMaxBitLen(64)
            val m2 = m * m

            for (v in 0..2000) {
                checkRemainder(m, v.toBigInt())
            }
        }
    }

    @Test
    fun randomLargeBarrett() {
        val bits = listOf(512, 1024, 1536, 2048, 3072, 4096)
        for (k in bits) {
            val m = BigInt.Companion.randomWithBitLen(k)
            val bar = Barrett(m)
            val m2 = m * m
            repeat(1) {
                val m2bit = m2.magnitudeBitLen()
                var x: BigInt
                do {
                    x = BigInt.Companion.randomWithBitLen(m2bit)
                } while (x >= m2)
                assertEquals(x % m, bar.reduce(x))
            }
        }
    }

    @Test
    fun testProblem0() {
        val m = "12345678901234567890123".toBigInt()
        val x = "123456789012345678901234567890".toBigInt()
        val barrett = Barrett(m)
        val rKnuth = x % m
        val rBarrett = barrett.reduce(x)
        assertEquals(rKnuth, rBarrett)
    }

    @Test
    fun testProblem1() {
        val m = "15720338158108356290".toBigInt()
        val x = "235477347269641899085489191398656418338".toBigInt()
        val barrett = Barrett(m)
        val rKnuth = x % m
        val rBarrett = barrett.reduce(x)
        assertEquals(rKnuth, rBarrett)
    }
}