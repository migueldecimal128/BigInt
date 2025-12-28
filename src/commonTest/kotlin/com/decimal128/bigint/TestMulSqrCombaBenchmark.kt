package com.decimal128.bigint

import com.decimal128.bigint.Mago.setMulCombaFused
import com.decimal128.bigint.Mago.setMulCombaPhased
import com.decimal128.bigint.Mago.setMulSchoolbook
import com.decimal128.bigint.Mago.setSqrCombaFused
import com.decimal128.bigint.Mago.setSqrCombaPhased
import com.decimal128.bigint.Mago.setSqrLE4Limbs
import com.decimal128.bigint.Mago.setSqrSchoolbook
import com.decimal128.bigint.Mago.setSqrSchoolbookG
import kotlin.test.Test
import kotlin.time.TimeSource


class TestMulSqrCombaBenchmark {

    fun bench(label: String, runs: Int = 31, iters: Int = 10_000, block: () -> Int) {
        val clock = TimeSource.Monotonic

        // warmup
        var sink0 = 0
        repeat(50_000) { sink0 += block() }

        val samples = LongArray(runs)
        var sink1 = 0

        for (r in 0 until runs) {
            val t0 = clock.markNow()
            repeat(iters) { sink1 += block() }
            samples[r] = t0.elapsedNow().inWholeNanoseconds
        }

        samples.sort()
        println("$label median = ${samples[runs / 2]/1000} micro sec  (sink0=$sink0 sink1=$sink1)")
    }


    @Test
    fun testSqrBenchmark() {

        for (n in 2..15) {
            println("n=$n")
            val a = IntArray(n) { (it + 1) * 0x9E3779B9.toInt() }
            val z = IntArray(2 * n)

            if (n <= 4) {
                bench("hand rolled") {
                    setSqrLE4Limbs(z, a, n)
                }
            }

            //bench("setSqrSchoolbook") {
            //    setSqrSchoolbook(z, a, n)
            //}

            bench("setSqrSchoolbookG") {
                setSqrSchoolbookG(z, a, n)
            }

            //bench("setSqrCombaFused") {
            //    setSqrCombaFused(z, a, n)
            //}

            bench("setSqrCombaPhased") {
                setSqrCombaPhased(z, a, n)
            }

        }

    }

    @Test
    fun testMulBenchmark() {

        val n = 16
        val m = 16
        val a = IntArray(n) { (it + 1) * 0x9E3779B9.toInt() }
        val b = IntArray(m) { (it + 1) * 0x6A09E667.toInt() }
        val z = IntArray(m + n)

        bench("setMulSchoolbook") {
            setMulSchoolbook(z, a, n, b, m)
        }

        bench("setMulCombaFused") {
            setMulCombaFused(z, a, n, b, m)
        }

        bench("setMulCombaPhased") {
            setMulCombaPhased(z, a, n, b, m)
        }

    }
}