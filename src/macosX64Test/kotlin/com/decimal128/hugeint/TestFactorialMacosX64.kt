package com.decimal128.hugeint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

class TestFactorialMacosX64 {

    val verbose = true

    @Test
    fun testSmall() {
        assertEquals(HugeInt.ONE, HugeInt.factorial(0))
        val accum = HugeIntAccumulator().set(1)
        var f = 1uL
        for (i in 1..20) {
            f *= i.toULong()
            accum *= i
            val hi = HugeInt.factorial(i)
            assertTrue(f EQ hi)
            assertTrue(f EQ accum.toHugeInt())
        }
        if (verbose)
            println("Tada!")
    }

    @Test
    fun testMedium() {
        var f = HugeInt.factorial(20)
        val accum = HugeIntAccumulator().set(f)
        for (i in 21..100) {
            f *= i
            accum *= i
            val hi = HugeInt.factorial(i)
            assertEquals(f, hi)
            assertEquals(f, accum.toHugeInt())
        }
    }

    @Test
    fun testLarge() {
        val bigNum = 10000// 800000

        var mul = HugeInt.ONE
        val mulTime = measureTime {
            for (i in 2..bigNum)
                mul *= i
        }

        val accum = HugeIntAccumulator().set(1)
        val accumTime = measureTime {
            for (i in 2..bigNum)
                accum *= i
        }
        var hi = HugeInt.ZERO
        val factorialTime = measureTime { hi = HugeInt.factorial(bigNum)}
        assertEquals(accum.toHugeInt(), hi)
        assertEquals(mul, hi)
        if (verbose) {
            println("macosX64Test: $bigNum! mulTime:$mulTime accumTime:$accumTime factorialTime:$factorialTime")
            val length = hi.toString().length
            println("digitCount:$length")
        }
    }
}
