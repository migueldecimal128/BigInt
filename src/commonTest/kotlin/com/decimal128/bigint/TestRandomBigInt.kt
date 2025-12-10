package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestRandomBigInt {
    @Test
    fun testRandomWithBitLen() {

        fun checkBitLen(k: Int) {
            repeat(500) {
                val x = BigInt.randomWithBitLen(k)

                val k1 = k
                val bitLen = x.magnitudeBitLen()
                if (bitLen != k1) {
                    println("kilroy was here!")
                    val x2 = BigInt.randomWithBitLen(k1)
                    println("kilroy was here!")
                }

                // 1. No negative numbers
                assertTrue(x >= BigInt.ZERO, "Negative value for k=$k: $x")

                if (k == 0) {
                    // The only valid value is ZERO
                    assertEquals(BigInt.ZERO, x, "k=0 must produce only ZERO")
                    return@repeat
                }

                // 2. Correct bit length: highest bit MUST be set
                assertEquals(k, x.magnitudeBitLen(),
                    "Incorrect bitLength for k=$k: got ${x.magnitudeBitLen()} value=$x")

                // 3. Check top bit explicitly (bit k-1)
                assertTrue(x.testBit(k - 1),
                    "Top bit (k-1) is not set for k=$k: value=$x")

                // 4. Value must be strictly less than 2^k
                val twoPowK = BigInt.withSetBit(k)
                assertTrue(x < twoPowK,
                    "Value >= 2^k for k=$k: x=$x")

                // 5. All bits below (k-1) free to vary:
                // sanity: not always the same pattern
                if (it == 0) return@repeat // skip first iteration
                // store previous if needed
            }
        }

        // Test a wide range of bit lengths
        val sizes = listOf(
            0, 1, 2, 3, 4, 5, 7, 8,
            15, 16, 31, 32, 33, 63, 64, 65,
            100, 127, 128, 129, 191, 192,
            255, 256, 257,
            300, 511, 512, 513
        )

        for (k in sizes) {
            checkBitLen(k)
        }
    }

}