package com.decimal128.bigint

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntAccApplyBitMask {
    @Test
    fun testApplyBitMaskMatchesBigInt() {
        val rnd = Random(12345)

        repeat(5000) {

            // --- Generate random BigInt value (positive or negative) ---
            val bitLen = rnd.nextInt(1, 400)
            val x = BigInt.randomWithBitLen(bitLen, withRandomSign = true)

            // Create accumulator version
            val mbi = MutableBigInt().set(x)

            // --- Random mask parameters ---
            val bitWidth = rnd.nextInt(0, 150)      // includes zero-width, 1-bit, wide masks
            val bitIndex = rnd.nextInt(0, 300)

            // --- Expected result (immutable BigInt) ---
            val expected = x.withBitMask(bitWidth, bitIndex)

            // --- Actual result (mutable accumulator) ---
            mbi.applyBitMask(bitWidth, bitIndex)
            val actual = mbi.toBigInt()   // or mbi.toImmutable(), whatever your API is

            assertEquals(
                expected,
                actual,
                """
            Mismatch:
              x         = $x
              width     = $bitWidth
              index     = $bitIndex
              expected  = $expected
              actual    = $actual
            """.trimIndent()
            )
        }
    }

}