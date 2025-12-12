package com.decimal128.bigint

import com.decimal128.bigint.BigIntCrypto.modInv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestBigIntCryptoModInv {

    @Test
    fun modInv_basicSmall() {
        val m = 11.toBigInt()
        val a = 7.toBigInt()

        val inv = BigIntCrypto.modInv(a, m)

        assertEquals(8.toBigInt(), inv) // 7 * 8 ≡ 1 (mod 11)
    }

    @Test
    fun modInv_ofOne() {
        val m = 97.toBigInt()
        val a = BigInt.ONE

        val inv = BigIntCrypto.modInv(a, m)

        assertEquals(BigInt.ONE, inv)
    }

    @Test
    fun modInv_negativeA() {
        val m = 13.toBigInt()
        val a = (-3).toBigInt()

        val inv = BigIntCrypto.modInv(a, m)

        // -3 ≡ 10 (mod 13), inverse of 10 is 4
        assertEquals(4.toBigInt(), inv)
    }

    @Test
    fun modInv_notInvertible() {
        val m = 21.toBigInt()
        val a = 14.toBigInt() // gcd(14,21) = 7

        assertFailsWith<ArithmeticException> {
            BigIntCrypto.modInv(a, m)
        }
    }

    @Test
    fun modInv_modulusOne() {
        val m = BigInt.ONE
        val a = 0.toBigInt()

        assertFailsWith<IllegalArgumentException> {
            BigIntCrypto.modInv(a, m)
        }
    }

    @Test
    fun modInv_zeroA() {
        val m = 101.toBigInt()
        val a = BigInt.ZERO

        assertFailsWith<ArithmeticException> {
            BigIntCrypto.modInv(a, m)
        }
    }

    @Test
    fun modInv_resultInRange() {
        val m = 101.toBigInt()
        val a = 37.toBigInt()

        val inv = BigIntCrypto.modInv(a, m)

        assertTrue(inv >= BigInt.ZERO)
        assertTrue(inv < m)
    }

    @Test
    fun modInv_largePrimes() {
        val primes = listOf(
            ("11579208923731619542357098500868790785326998466564056403945758" +
                    "4007913129639747").toBigInt(),
            ("394020061963944792122790401001436138050797392704654466679482934" +
                    "04245721771496870329047266088258938001861606973112319").toBigInt(),
            ("6864797660130609714981900799081393217269435300143305409394463459" +
                    "185543183397656052122559640661454554977296311391480858037" +
                    "121987999716643812574028291115057151").toBigInt(),
            ("17976931348623159077293051907890247336179769789423065727343008115" +
                    "7732675805505620686985379449212982959585501387537164015710" +
                    "1398586478337786069255834975410851965916151280575759407526" +
                    "3500747593528871082364994994077189561705436114947486504671" +
                    "1015101563940680527540071584560878577663743040086340742855" +
                    "278549092581").toBigInt()
        )

        val testAs = listOf(
            2.toBigInt(),
            3.toBigInt(),
            5.toBigInt(),
            17.toBigInt(),
            65537.toBigInt()
        )

        for (m in primes) {
            for (a in testAs) {
                val inv = modInv(a, m)
                val check = (a * inv) % m
                assertEquals(BigInt.ONE, check, "failed for a=$a mod m(bitLen=${m.magnitudeBitLen()})")
            }
        }
    }


}