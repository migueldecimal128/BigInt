// SPDX-License-Identifier: MIT

package com.decimal128.bigint

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestHashCode {

    val verbose = true

    @Test
    fun testSignHashCode() {
        val trueHashCode = true.hashCode()
        val falseHashCode = false.hashCode()
        if (verbose)
            println("trueHashCode:$trueHashCode falseHashCode:$falseHashCode")
        assertEquals(1231, trueHashCode)
        assertEquals(1237, falseHashCode)
    }

    val rng = Random.Default

    @Test
    fun testPosNegHash() {
        repeat(1000) {
            val hiPos = BigInt.fromRandom(bitLen = rng.nextInt(10))
            if (verbose)
                println("hiPos:$hiPos")
            // hiNeg will not be negative in the case of zero
            val hiNeg = hiPos.negate()
            assertTrue( hiNeg + hiPos EQ 0)

            val hashPos = hiPos.hashCode()
            val hashNeg = hiNeg.hashCode()

            if (verbose)
                println("hashPos:$hashPos hashNeg:$hashNeg")

            val hashPosMag = hashPos - (31 * false.hashCode())
            // hiNeg will not be negative in the case of zero
            val hashNegMag = hashNeg - (31 * hiNeg.isNegative().hashCode())

            assertEquals(hashPosMag, hashNegMag)
        }
    }

    @Test
    fun testNonNormalized() {
        for (i in 0..<1000) {
            val hiPos = BigInt.fromRandom(rng.nextInt(500))
           if (hiPos.isZero())
               continue
            val biggerBitLen = hiPos.magnitudeBitLen() + rng.nextInt(1000) + 32
            // subtraction will generate non-normalized BigInt values
            val bigger = BigInt.withSetBit(biggerBitLen)
            val hiPos2 = (bigger + hiPos) - bigger
            check (!hiPos2.isNormalized())

            assertEquals(hiPos, hiPos2)
            assertEquals(hiPos.hashCode(), hiPos2.hashCode())
        }
    }

}
