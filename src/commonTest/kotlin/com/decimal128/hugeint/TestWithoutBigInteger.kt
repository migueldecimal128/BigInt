// SPDX-License-Identifier: MIT

package com.decimal128.hugeint

import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals

class TestWithoutBigInteger {

    @Test
    fun test() {
        repeat(100) {
            test1()
        }
    }

    val rng = Random.Default

    fun test1() {
        var hi1 = HugeInt.ZERO
        var hi2 = HugeInt.ZERO

        val n = rng.nextInt()
        hi1 += n
        hi2 = hi2 - n + n.toLong() * 2
        assertEquals(hi1, hi2)

        val w = rng.nextUInt()
        val count = rng.nextInt(10)
        repeat(count) {
            hi1 += w
        }
        hi2 += w.toULong() * count.toULong()
        assertEquals(hi1, hi2)

        val l = rng.nextLong()
        hi1 *= l
        hi2 = hi2 * (l and 1L.inv()) + if ((l and 1L) != 0L) hi2 else HugeInt.ZERO
        assertEquals(hi1, hi2)

        val dw = rng.nextULong()
        hi1 = ((hi1 * (dw shr 32)) shl 32) + hi1 * dw.toUInt()
        hi2 *= dw
        assertEquals(hi1, hi2)

        val exp = rng.nextInt(20)
        val t1 = hi1
        hi1 = HugeInt.from(1)
        repeat(exp) { hi1 *= t1 }
        hi2 = hi2.pow(exp)
        assertEquals(hi1, hi2)

        val dw2 = rng.nextULong()
        if (dw2 != 0uL) {
            val div = hi1 / dw2
            val mod = hi1 % dw2
            hi1 = dw2 * div + mod
            assertEquals(hi1, hi2)
        }

        hi1 = hi1.sqr()
        hi2 = hi2 * hi2
        assertEquals(hi1, hi2)

        assertEquals(hi1.magnitudeBitLen(), (-hi2).magnitudeBitLen())

        var bitCount = 0
        for (i in 0..<hi1.magnitudeBitLen())
            if (hi1.isBitSet(i))
                ++bitCount
        assertEquals(bitCount, hi2.magnitudeCountOneBits())

        hi1 = hi1.abs()
        hi2 -= hi2
        for (i in 0..<hi1.magnitudeBitLen())
            if (hi1.isBitSet(i)) {
                when (rng.nextInt(3)) {
                    0 -> hi2 += HugeInt.withSetBit(i)
                    1 -> hi2 = hi2 or HugeInt.withSetBit(i)
                    2 -> hi2 = hi2 xor HugeInt.withSetBit(i)
                    3 -> hi2 += HugeInt.ONE shl i
                }
            }
        assertEquals(hi1, hi2)

        assertEquals((hi1 + -hi2).compareTo(0), 0)
        assertEquals((hi1 - hi2).compareTo(0), 0)
        assertEquals((+hi1 - hi2).compareTo(0), 0)
    }

}