package com.decimal128.bigint

import com.decimal128.bigint.Mago.setMulSchoolbook
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * These benchmarks were set up to determine crossover
 * threshold for Comba-style multiplication and
 * squaring where the entire column is accumulated
 * and written once.
 *
 * On hardware of this generation (with larger L1 cache)
 * Comba implementations started out slower than
 * schoolbook (as expected), but never caught up with
 * hundreds of limbs (unexpected). Perhaps one could
 * come up with extreme cases (that overwhelm L1 cache?)
 * but what would be the point ...
 */
class TestMulSqrCombaBenchmark {

    // runBenchmark = true will spew data
    val runBenchmark = false
    val verbose = runBenchmark

    val ITERS = if (runBenchmark) 10_000 else 10
    val WARMUP = if (runBenchmark) 50_000 else 5

    fun bench(label: String, runs: Int = 31, iters: Int = ITERS, block: () -> Int) {
        val clock = TimeSource.Monotonic

        // warmup
        var sink0 = 0
        repeat(WARMUP) { sink0 += block() }

        val samples = LongArray(runs)
        var sink1 = 0

        for (r in 0 until runs) {
            val t0 = clock.markNow()
            repeat(iters) { sink1 += block() }
            samples[r] = t0.elapsedNow().inWholeNanoseconds
        }

        samples.sort()
        if (verbose)
            println("$label median = ${samples[runs / 2]/iters} ns  (sink0=$sink0 sink1=$sink1)")
    }

    @Test
    fun testSqrBenchmark() {

        for (n in 2..24) {
            if (verbose)
                println("n=$n")
            val a = IntArray(n) { (it + 1) * 0x9E3779B9.toInt() }
            val z = IntArray(2 * n)

            if (n <= 4) {
                bench("hand rolled") {
                    setSqr(z, a, n)
                }
            }

            //bench("setSqrSchoolbook") {
            //    setSqrSchoolbook(z, a, n)
            //}

            bench("setSqrSchoolbook") {
                setSqrSchoolbook(z, a, n)
            }

            bench("setMulSchoolbook(a,a)") {
                setMulSchoolbook(z, a, n, a, n)
            }

            //bench("setSqrCombaFused") {
            //    setSqrCombaFused(z, a, n)
            //}

            //bench("setSqrCombaPhased") {
            //    setSqrCombaPhased(z, a, n)
            //}
        }
    }

    @Test
    fun testMulBenchmark() {

        for (n in 5..10) {
            for (m in 5..10) {
                val a = IntArray(n) { (it + 1) * 0x9E3779B9.toInt() }
                val b = IntArray(m) { (it + 1) * 0x6A09E667.toInt() }
                val z = IntArray(m + n)

                if (verbose)
                    println("n=$n m=$m")
                bench("setMulSchoolbook") {
                    setMulSchoolbook(z, a, n, b, m)
                }

                //bench("setMulCombaFused") {
                //    setMulCombaFused(z, a, n, b, m)
                //}

                //bench("setMulCombaPhased") {
                //    setMulCombaPhased(z, a, n, b, m)
                //}
            }
        }

    }
}