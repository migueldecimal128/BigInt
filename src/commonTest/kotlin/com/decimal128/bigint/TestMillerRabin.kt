package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigIntPrime.isMillerRabinProbablePrime
import com.decimal128.bigint.toBigInt
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestMillerRabin {

    @Test
    fun testKnownProblem() {
        // 2047 a known composite that passes MR base 2 but fails
        // with better bases
        val n = (23 * 89).toBigInt()

// base 2 only
        assertTrue(isMillerRabinProbablePrime(n, intArrayOf(2)))        // ==> true

// 64-bit deterministic bases
        val bases64 = intArrayOf(2, 325, 9375, 28178, 450775, 9780504, 1795265022)
        assertFalse(isMillerRabinProbablePrime(n, bases64))
    }
}