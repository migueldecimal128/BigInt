package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestGoogolSqrt {

    @Test
    fun testGoogolSqrt() {
        val googol = 10.toBigInt().pow(100)
        run {
            val sqrt = googol.isqrt()
            val squared = sqrt.sqr()
            assertEquals(googol, squared)
        }

        val googolPlus1 = googol + 1
        run {
            val sqrt = googolPlus1.isqrt()
            val squared = sqrt.sqr()
            val diff = googolPlus1 - squared
            assertTrue(diff EQ 1)
        }

        val googolMinus1 = googol - 1
        run {
            val sqrt = googolMinus1.isqrt()
            val squared = sqrt.sqr()
            assertTrue(googolMinus1 > squared)
            val plus1Squared = (sqrt + 1).sqr()
            assertTrue(googolMinus1 < plus1Squared)
        }

    }
}