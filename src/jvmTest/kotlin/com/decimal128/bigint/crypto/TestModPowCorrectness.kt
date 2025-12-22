package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.MutableBigInt
import com.decimal128.bigint.toBigInt
import kotlin.test.Test
import kotlin.test.assertEquals

class TestModPowCorrectness {

    val verbose = false

    private fun modPowRef(a: BigInt, e: Int, m: BigInt): BigInt {
        require(e >= 0)
        if (m == BigInt.ONE) return BigInt.ZERO
        var base = a % m           // ensure 0 <= base < m
        var exp = e
        var res = BigInt.ONE

        while (exp > 0) {
            if ((exp and 1) != 0) {
                res = (res * base) % m
            }
            base = (base * base) % m
            exp = exp ushr 1
        }
        return res
    }

    @Test
    fun modPow_matches_reference_small() {
        val m = 1019.toBigInt()       // small prime modulus
        val a = 123.toBigInt()
        val e = 2

        val ctxMont = ModContext(m, useBarrettOnly = false)
        val ctxBarr = ModContext(m, useBarrettOnly = true)

        val ref = modPowRef(a, e, m)

        val outMont = MutableBigInt()
        val outBarr = MutableBigInt()

        ctxMont.modPow(a, e, outMont)
        ctxBarr.modPow(a, e, outBarr)
        if (verbose)
            println("a:$a e:$e m:$m ref:$ref, outMont:$outMont: outBarr:$outBarr")
        assertEquals(ref, outBarr.toBigInt(), "Barrett modPow incorrect")
        assertEquals(ref, outMont.toBigInt(), "Montgomery modPow incorrect")
    }

    @Test
    fun modPow_micro_tests() {
        val m = 1019.toBigInt()
        val a = 123.toBigInt()

        val ctxM = ModContext(m, useBarrettOnly = false)
        val ctxB = ModContext(m, useBarrettOnly = true)

        val outM = MutableBigInt()
        val outB = MutableBigInt()

        val expected = intArrayOf(1, 123, 863, 173, 899, 525)

        for (e in 0..5) {
            ctxM.modPow(a, e, outM)
            ctxB.modPow(a, e, outB)

            val mVal = outM.toBigInt().toInt()
            val bVal = outB.toBigInt().toInt()
            val exp  = expected[e]

            if (verbose)
                println("e=$e  M=$mVal  B=$bVal  expected=$exp")

            //require(mVal == exp) { "Mont incorrect at e=$e" }
            require(bVal == exp) { "Barr incorrect at e=$e" }
        }
    }

    @Test
    fun test_modMul_direct() {
        val m = 1019.toBigInt()
        val ctx = ModContext(m, useBarrettOnly = true)
        val a = 123.toBigInt()
        val out = MutableBigInt()

        // a * a mod m
        ctx.modMul(a, a, out)
        println(out.toBigInt())
    }

    @Test
    fun test_modSqr_direct() {
        val m = 1019.toBigInt()
        val ctx = ModContext(m, useBarrettOnly = true)
        val a = 123.toBigInt()
        val out = MutableBigInt()

        ctx.modSqr(a, out)
        if (verbose)
            println("modSqr(123) mod 1019 = ${out.toBigInt()} (expected 863)")
    }

}