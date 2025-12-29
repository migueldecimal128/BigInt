package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.MutableBigInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TimeSource

class TestBarrettVsMontgomery {

    val WARMUP = 5 // 5000

    private inline fun measureNano(block: () -> Unit): Long {
        val mark = TimeSource.Monotonic.markNow()
        block()
        return mark.elapsedNow().inWholeNanoseconds
    }

    private fun modPowRef(a: BigInt, e: BigInt, m: BigInt): BigInt {
        require(e >= 0)
        if (m == BigInt.ONE) return BigInt.ZERO
        var base = a % m           // ensure 0 <= base < m
        var exp = e
        var res = BigInt.ONE

        while (exp > 0) {
            if (exp.testBit(0)) {
                res = (res * base) % m
            }
            base = (base * base) % m
            exp = exp ushr 1
        }
        return res
    }


    @Test
    fun compareBarrettVsMontgomery_modMul_2kBits() {
        val bitLen = 2048

        val m = BigInt.randomWithBitLen(bitLen, ensureOdd = true)
        val ctxMont = ModContext(m, useBarrettOnly = false)
        val ctxBarr = ModContext(m, useBarrettOnly = true)

        val a = BigInt.randomBelow(m)
        val b = BigInt.randomBelow(m)
        val expected = (a * b) % m

        val outMont = MutableBigInt()
        val outBarr = MutableBigInt()

        // Warmup
        repeat(WARMUP) {
            ctxMont.modMul(a, b, outMont)
            ctxBarr.modMul(a, b, outBarr)
        }

        assertEquals(expected, outMont.toBigInt())
        assertEquals(expected, outBarr.toBigInt())

        val montTimes = LongArray(20)
        repeat(20) { i ->
            montTimes[i] = measureNano {
                repeat(200) {
                    ctxMont.modMul(a, b, outMont)
                }
            }
        }
        val montMedian = montTimes.sorted()[montTimes.size / 2]

        val barrTimes = LongArray(20)
        repeat(20) { i ->
            barrTimes[i] = measureNano {
                repeat(200) {
                    ctxBarr.modMul(a, b, outBarr)
                }
            }
        }
        val barrMedian = barrTimes.sorted()[barrTimes.size / 2]

        println("modMul (2kBits * 2kBits) mod 2kBits")
        println("Median Barrett    = $barrMedian ns")
        println("Median Montgomery = $montMedian ns")

        val ratio = barrMedian.toDouble() / montMedian
        val ratioRounded = (ratio * 1000).toInt() / 1000.0
        println("ratio Barrett/Mont = $ratioRounded")
    }

    @Test
    fun compareBarrettVsMontgomery_modPow_2kBits_Long() {
        val bitLen = 2048

        // 2048-bit odd modulus (Montgomery eligible)
        val m = BigInt.randomWithBitLen(bitLen, ensureOdd = true)

        // Engines
        val ctxMont = ModContext(m, useBarrettOnly = false)
        val ctxBarr = ModContext(m, useBarrettOnly = true)

        // Base < m
        val a = BigInt.randomBelow(m)

        // Use RSA-style exponent 65537, so we drive many modular multiplies
        val e = 65_537

        val outMont = MutableBigInt()
        val outBarr = MutableBigInt()

        // Warm up to trigger inlining & allocation flattening
        repeat(WARMUP) {
            ctxMont.modPow(a, e, outMont)
            ctxBarr.modPow(a, e, outBarr)
        }
        assertEquals(outMont, outBarr)

        // Compute reference using your existing int-exponent operator(s)
        // i.e., (a^e) % m computed naïvely through BigInt mul
        val refM = MutableBigInt().set(1)
        repeat(e) {
            refM *= a
            refM %= m
        }
        val ref = refM.toBigInt()

        // Correctness check
        ctxMont.modPow(a, e, outMont)
        ctxBarr.modPow(a, e, outBarr)
        assertEquals(ref, outBarr.toBigInt(), "Barrett correctness")
        assertEquals(ref, outMont.toBigInt(), "Montgomery correctness")

        // Benchmark Montgomery
        val montTimes = LongArray(15)
        repeat(montTimes.size) { i ->
            montTimes[i] = measureNano {
                ctxMont.modPow(a, e, outMont)
            }
        }
        val montMedian = montTimes.sorted()[montTimes.size / 2]

        // Benchmark Barrett
        val barrTimes = LongArray(15)
        repeat(barrTimes.size) { i ->
            barrTimes[i] = measureNano {
                ctxBarr.modPow(a, e, outBarr)
            }
        }
        val barrMedian = barrTimes.sorted()[barrTimes.size / 2]

        println("modPow 2kBits**65537 mod 2kBits")
        println("Barrett    modPow median = $barrMedian ns")
        println("Montgomery modPow median = $montMedian ns")

        val ratio = barrMedian.toDouble() / montMedian
        val rounded = (ratio * 1000).toInt() / 1000.0
        println("ratio Barrett/Mont = $rounded")
    }

    @Test
    fun compareBarrettVsMontgomery_modPow_2kBits_2k() {
        val bitLen = 2048

        // 2048-bit odd modulus (Montgomery eligible)
        val m = BigInt.randomWithBitLen(bitLen, ensureOdd = true)

        // Engines
        val ctxMont = ModContext(m, useBarrettOnly = false)
        val ctxBarr = ModContext(m, useBarrettOnly = true)

        // Base < m
        val a = BigInt.randomBelow(m)

        val e = BigInt.randomWithMaxBitLen(2048)

        val outMont = MutableBigInt()
        val outBarr = MutableBigInt()

        // Warm up to trigger inlining & allocation flattening
        // repeat(5000) {
        repeat(WARMUP) {
            ctxMont.modPow(a, e, outMont)
            ctxBarr.modPow(a, e, outBarr)
        }
        assertEquals(outMont, outBarr)

        // Compute reference using your existing int-exponent operator(s)
        // i.e., (a^e) % m computed naïvely through BigInt mul
        val ref = modPowRef(a, e, m)

        // Correctness check
        ctxMont.modPow(a, e, outMont)
        ctxBarr.modPow(a, e, outBarr)
        assertEquals(ref, outBarr.toBigInt(), "Barrett correctness")
        assertEquals(ref, outMont.toBigInt(), "Montgomery correctness")

        // Benchmark Montgomery
        val montTimes = LongArray(10)
        repeat(10) { i ->
            montTimes[i] = measureNano {
                ctxMont.modPow(a, e, outMont)
            }
        }
        val montMedian = montTimes.sorted()[montTimes.size / 2]

        // Benchmark Barrett
        val barrTimes = LongArray(10)
        repeat(10) { i ->
            barrTimes[i] = measureNano {
                ctxBarr.modPow(a, e, outBarr)
            }
        }
        val barrMedian = barrTimes.sorted()[barrTimes.size / 2]

        println("modPow 2kBits**2kBits mod 2kBits")
        println("Barrett    modPow median = $barrMedian ns")
        println("Montgomery modPow median = $montMedian ns")

        val ratio = barrMedian.toDouble() / montMedian
        val rounded = (ratio * 1000).toInt() / 1000.0
        println("ratio Barrett/Mont = $rounded")
    }

    @Test
    fun compareBarrettVsMontgomery_modPow_X() {
        val bitLen = 64

        // 2048-bit odd modulus (Montgomery eligible)
        val m = BigInt.randomWithBitLen(bitLen, ensureOdd = true)

        // Engines
        val ctxMont = ModContext(m, useBarrettOnly = false)
        val ctxBarr = ModContext(m, useBarrettOnly = true)

        // Base < m
        val a = BigInt.randomBelow(m)

        val e = BigInt.randomWithMaxBitLen(bitLen-1)

        val outMont = MutableBigInt()
        val outBarr = MutableBigInt()

        // Warm up to trigger inlining & allocation flattening
        // repeat(5000) {
        repeat(1) {
            ctxMont.modPow(a, e, outMont)
            ctxBarr.modPow(a, e, outBarr)
        }
        assertEquals(outMont, outBarr)

        // Compute reference using your existing int-exponent operator(s)
        // i.e., (a^e) % m computed naïvely through BigInt mul
        val ref = modPowRef(a, e, m)

        // Correctness check
        ctxMont.modPow(a, e, outMont)
        ctxBarr.modPow(a, e, outBarr)
        assertEquals(ref, outBarr.toBigInt(), "Barrett correctness")
        assertEquals(ref, outMont.toBigInt(), "Montgomery correctness")

        // Benchmark Montgomery
        val montTimes = LongArray(10)
        repeat(10) { i ->
            montTimes[i] = measureNano {
                ctxMont.modPow(a, e, outMont)
            }
        }
        val montMedian = montTimes.sorted()[montTimes.size / 2]

        // Benchmark Barrett
        val barrTimes = LongArray(10)
        repeat(10) { i ->
            barrTimes[i] = measureNano {
                ctxBarr.modPow(a, e, outBarr)
            }
        }
        val barrMedian = barrTimes.sorted()[barrTimes.size / 2]

        println("Montgomery modPow median = $montMedian ns")
        println("Barrett    modPow median = $barrMedian ns")

        val ratio = barrMedian.toDouble() / montMedian
        val rounded = (ratio * 1000).toInt() / 1000.0
        println("ratio Barrett/Mont = $rounded")
    }

}
