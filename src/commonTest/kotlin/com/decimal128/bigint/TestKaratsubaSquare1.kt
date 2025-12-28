package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.Mago.normLen
import com.decimal128.bigint.MagoSqr.karatsubaSqr
import kotlin.test.*

class TestKaratsubaSquare1 {

    private fun checkKaratsubaSquare(a: BigInt) {
        val n = a.meta.normLen
        require(n >= 2)

        val z = IntArray(2 * n + 2)          // == IntArray(2*n + 2)
        val k0 = n / 2
        val k1 = n - k0
        val t = IntArray(3 * k1 + 3)

        karatsubaSqr(
            z, 0,
            a.magia, 0, n,
            t
        )

        val zLen = normLen(z, z.size)

        val got = BigInt.fromLittleEndianIntArray(
            false,   // non-negative
            z,
            zLen
        )
        val expect = a * a

        assertEquals(expect, got, "karatsubaSquare1 failed for a=$a")
    }

    @Test
    fun karatsubaSquare1_len2() {
        val a = BigInt.fromLittleEndianIntArray(
            false,
            intArrayOf(
                0x12345678,
                0x9ABCDEF0.toInt()
            ),
            2
        )
        checkKaratsubaSquare(a)
    }

    @Test
    fun karatsubaSquare1_oddLen() {
        val a = BigInt.fromLittleEndianIntArray(
            false,
            intArrayOf(
                0xAAAAAAAA.toInt(),
                0xBBBBBBBB.toInt(),
                0xCCCCCCCC.toInt()
            ),
            3
        )
        checkKaratsubaSquare(a)
    }

    @Test
    fun karatsubaSquare1_sumCarry() {
        val a = BigInt.fromLittleEndianIntArray(
            false,
            intArrayOf(
                -1, -1, 1
            ),
            3
        )
        checkKaratsubaSquare(a)
    }

    @Test
    fun karatsubaSquare1_randomWithBitLen() {
        repeat(300) {
            val bitLen = 64 + it % 256   // always ≥ 2 limbs
            val a = BigInt.randomWithBitLen(bitLen)

            // Ensure we stay in-domain
            if (a.meta.normLen >= 2) {
                checkKaratsubaSquare(a)
            }
        }
    }

    @Test
    fun karatsubaSquare1_randomWithMaxBitLen() {
        repeat(500) {
            val a = BigInt.randomWithMaxBitLen(512)

            if (a.meta.normLen >= 2) {
                checkKaratsubaSquare(a)
            }
        }
    }


}