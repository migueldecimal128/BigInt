package com.decimal128.hugeint

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

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
            val hiPos = HugeInt.fromRandom(bitLen = rng.nextInt(500))
            // hiNeg will not be negative in the case of zero
            val hiNeg = hiPos.negate()

            val hashPos = hiPos.hashCode()
            val hashNeg = hiNeg.hashCode()

            val hashPosMag = hashPos - (31 * false.hashCode())
            // hiNeg will not be negative in the case of zero
            val hashNegMag = hashNeg - (31 * hiNeg.isNegative().hashCode())

            assertEquals(hashPosMag, hashNegMag)
        }
    }

    @Test
    fun testNonNormalized() {
        for (i in 0..<1000) {
            val hiPos = HugeInt.fromRandom(rng.nextInt(500))
           if (hiPos.isZero())
               continue
            val biggerBitLen = hiPos.magnitudeBitLen() + rng.nextInt(1000) + 32
            // subtraction will generate non-normalized HugeInt values
            val bigger = HugeInt.withSetBit(biggerBitLen)
            val hiPos2 = (bigger + hiPos) - bigger
            check (!hiPos2.isNormalized())

            assertEquals(hiPos, hiPos2)
            assertEquals(hiPos.hashCode(), hiPos2.hashCode())
        }
    }

}
