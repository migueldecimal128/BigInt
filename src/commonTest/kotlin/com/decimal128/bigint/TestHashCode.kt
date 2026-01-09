// SPDX-License-Identifier: MIT

package com.decimal128.bigint

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestHashCode {

    val verbose = false

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
            val hiPos = BigInt.randomWithMaxBitLen(maxBitLen = rng.nextInt(10))
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
            val biPos = BigInt.randomWithMaxBitLen(rng.nextInt(500))
           if (biPos.isZero())
               continue
            val biggerBitLen = biPos.magnitudeBitLen() + rng.nextInt(1000) + 32
            // subtraction will generate non-normalized BigInt magia
            // arrays ... which are not SuperNormal
            val bigger = BigInt.withSetBit(biggerBitLen)
            val biPos2 = (bigger + biPos) - bigger
            //biPos2 might be superNormalized
            //check (!biPos2.isSuperNormalized())

            assertEquals(biPos, biPos2)
            assertEquals(biPos.hashCode(), biPos2.hashCode())
        }
    }

    @Test
    fun testProblem1() {
        val biPos = 1785.toBigInt()
        val bigger = 281474976710656.toBigInt()
        val biPos2 = (bigger + biPos) - bigger
        // this check is no longer valid ... it could be superNormalized
        // check(!biPos2.isSuperNormalized())
        if (verbose)
            println("biPos:$biPos biPos2:$biPos2")
        assertEquals(biPos, biPos2)
    }

}
