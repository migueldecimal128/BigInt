package com.decimal128.bigint.crypto

import com.decimal128.bigint.Mago.setMulSchoolbook
import com.decimal128.bigint.Mago.setSqrLE4Limbs
import com.decimal128.bigint.Mago.setSqrSchoolbookG
import kotlin.test.Test
import kotlin.time.TimeSource

class TestKaratsubaSqrBenchmark {


    val verbose = true

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
        if (verbose)
            println("$label median = ${samples[runs / 2]/iters} ns  (sink0=$sink0 sink1=$sink1)")
    }


    @Test
    fun testSqrBenchmark() {

        for (n in 64..256 step 16) {
            if (verbose)
                println("n=$n")
            val a = IntArray(n) { (it + 1) * 0x9E3779B9.toInt() }
            val z = IntArray(2 * n + 1)
            val k1 = (n + 1) / 2
            val t = IntArray(3 * k1 + 3)

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

            //bench("setMulSchoolbook(a,a)") {
            //    setMulSchoolbook(z, a, n, a, n)
            //}

            //bench("Karatsuba.setSqrSchoolbookK") {
            //    z.fill(0)
            //    Karatsuba.setSqrSchoolbookK(z, 0, a, 0, n)
            //}

            bench("Karatsuba.setSqrKaratsuba") {
                z.fill(0)
                Karatsuba.setSqrKaratsuba(z, 0, a, 0, n, t)
            }

            bench("Karatsuba.setSqrSchoolbookKaratG") {
                z.fill(0)
                Karatsuba.setSqrSchoolbookKaratG(z, 0, a, 0, n)
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