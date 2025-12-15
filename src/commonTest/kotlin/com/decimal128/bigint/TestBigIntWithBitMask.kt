package com.decimal128.bigint

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntWithBitMask {

    @Test
    fun testWithBitMaskInstanceVsFactory() {
        // Try a range of random values and mask shapes
        val rnd = Random(12345)

        repeat(10_000) {
            // Create a random BigInt, sometimes negative
            val bitLen = rnd.nextInt(1, 300)
            val x = BigInt.randomWithBitLen(bitLen, withRandomSign = true)

            val bitWidth  = rnd.nextInt(0, 80)      // covers 0, 1, multi-bit masks
            val bitIndex  = rnd.nextInt(0, 200)

            val instanceMasked = x.withBitMask(bitWidth, bitIndex)
            val factoryMask    = BigInt.withBitMask(bitWidth, bitIndex)

            val expected = x.abs() and factoryMask

            assertEquals(
                expected,
                instanceMasked,
                "Failed for x=$x width=$bitWidth index=$bitIndex"
            )
        }
    }
}