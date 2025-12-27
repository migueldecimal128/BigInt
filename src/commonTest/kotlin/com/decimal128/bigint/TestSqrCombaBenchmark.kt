package com.decimal128.bigint

import com.decimal128.bigint.Mago.setSqr
import com.decimal128.bigint.Mago.setSqrComba
import com.decimal128.bigint.Mago.setSqrCombaG
import com.decimal128.bigint.Mago.setSqrCombaSplit
import com.decimal128.bigint.Mago.setSqrLE3Limbs
import kotlin.test.Test
import kotlin.time.TimeSource


class TestSqrCombaBenchmark {

    fun bench(label: String, runs: Int = 21, iters: Int = 5_000, block: () -> Int) {
        val clock = TimeSource.Monotonic

        // warmup
        repeat(5_000) { block() }

        val samples = LongArray(runs)
        var sink = 0

        for (r in 0 until runs) {
            val t0 = clock.markNow()
            repeat(iters) { sink += block() }
            samples[r] = t0.elapsedNow().inWholeNanoseconds
        }

        samples.sort()
        println("$label median = ${samples[runs / 2]/1000} micro sec  (sink=$sink)")
    }


    @Test
    fun testCompareSqr() {

        val n = 16
        val a = IntArray(n) { (it + 1) * 0x9E3779B9.toInt() }
        val z = IntArray(2 * n)

        bench("schoolbook") {
            setSqr(z, a, n)
        }

        bench("comba") {
            setSqrComba(z, a, n)
        }

        bench("combaSplit") {
            setSqrCombaSplit(z, a, n)
        }

        bench("combaG") {
            setSqrCombaG(z, a, n)
        }

        if (n <= 3) {
            bench("direct") {
                setSqrLE3Limbs(z, a, n)
            }
        }
    }
}