package com.decimal128.bigint

import com.decimal128.bigint.MagoSqr
import com.decimal128.bigint.MagoSqr.setSqrKaratsuba
import com.decimal128.bigint.MagoSqr.setSqrSchoolbook
import kotlin.test.Test
import kotlin.time.TimeSource

class TestKaratsubaSqrBenchmark {


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

        for (n in 64..96 step 4) {
            if (verbose)
                println("n=$n")
            val a = IntArray(n) { (it + 1) * 0x9E3779B9.toInt() }
            val z = IntArray(2 * n + 1)
            val k1 = (n + 1) / 2
            val t = IntArray(3 * k1 + 3)

            if (n <= 4) {
                bench("hand rolled") {
                    MagoSqr.setSqr(z, a, n)
                }
            }

            //bench("setSqrSchoolbook") {
            //    setSqrSchoolbook(z, a, n)
            //}

            //bench("setMulSchoolbook(a,a)") {
            //    setMulSchoolbook(z, a, n, a, n)
            //}

            //bench("Karatsuba.setSqrSchoolbookK") {
            //    z.fill(0)
            //    Karatsuba.setSqrSchoolbookK(z, 0, a, 0, n)
            //}

            bench("Karatsuba.setSqrKaratsuba") {
                z.fill(0)
                setSqrKaratsuba(z, a, n, t)
            }

            bench("Mago.setSqrSchoolbook") {
                setSqrSchoolbook(z, a, n)
            }

            //bench("setSqrCombaFused") {
            //    setSqrCombaFused(z, a, n)
            //}

            //bench("setSqrCombaPhased") {
            //    setSqrCombaPhased(z, a, n)
            //}

        }

    }

}