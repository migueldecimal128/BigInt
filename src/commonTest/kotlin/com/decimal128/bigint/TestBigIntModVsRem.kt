package com.decimal128.bigint

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestBigIntModVsRem {

    @Test
    fun mod_basicInvariant() {
        val n = 7

        for (x in -50..50) {
            val r = x.toBigInt() mod n
            assertTrue(r >= BigInt.ZERO, "negative result for x=$x")
            assertTrue(r < n.toBigInt(), "out of range for x=$x")
            assertEquals(
                ((x % n) + n) % n,
                r.toInt(),
                "wrong modulo for x=$x"
            )
        }
    }

    @Test
    fun mod_differsFromRemainderForNegativeValues() {
        val n = 5

        val x = (-3).toBigInt()

        val rem = x % n
        val mod = x mod n

        assertEquals((-3).toBigInt(), rem)
        assertEquals(2.toBigInt(), mod)
    }

    @Test
    fun mod_zeroAlwaysZero() {
        val zero = BigInt.ZERO

        assertEquals(BigInt.ZERO, zero mod 1)
        assertEquals(BigInt.ZERO, zero.mod(7))
        assertEquals(BigInt.ZERO, zero mod ULong.MAX_VALUE)
    }

    @Test
    fun mod_identityWhenAlreadyInRange() {
        val n = 97

        for (x in 0 until n) {
            assertEquals(
                x.toBigInt(),
                x.toBigInt().mod(n),
                "identity failed for x=$x"
            )
        }
    }

    @Test
    fun mod_largePositiveAndNegative() {
        val n = 1_000_003 // prime, not power of two

        val big = BigInt.from("123456789012345678901234567890")
        val neg = -big

        val r1 = big.mod(n)
        val r2 = neg mod n

        assertTrue(r1 >= BigInt.ZERO && r1 < n)
        assertTrue(r2 >= BigInt.ZERO && r2 < n)

        // x + (-x) ≡ 0 mod n
        assertEquals(
            BigInt.ZERO,
            (r1 + r2).mod(n)
        )
    }

    @Test
    fun mod_crossTypeConsistency() {
        val x = BigInt.from("98765432109876543210")

        val nInt = 97
        val nUInt = 97u
        val nLong = 97L
        val nULong = 97uL

        val r = x.mod(nInt)

        assertEquals(r, x mod nUInt)
        assertEquals(r, x mod nLong)
        assertEquals(r, x mod nULong)
    }

    @Test
    fun mod_negativeDivisorFails() {
        val x = (-42).toBigInt()

        assertFailsWith<ArithmeticException> {
            x mod -7
        }

        assertFailsWith<ArithmeticException> {
            x mod -7L
        }
    }

    @Test
    fun mod_oneAlwaysZero() {
        for (x in -100..100) {
            assertEquals(
                BigInt.ZERO,
                x.toBigInt().mod(1),
                "mod 1 failed for x=$x"
            )
        }
    }

    @Test
    fun mod_preservesLucasInvariant() {
        val n = 101
        val Qk = (-250).toBigInt()

        val r = Qk.mod(n)

        assertTrue(r >= BigInt.ZERO)
        assertTrue(r < n)

        // value congruent modulo n
        assertEquals(
            BigInt.ZERO,
            (Qk - r).rem(n)
        )
    }

    @Test
    fun mod_randomStress() {
        val rnd = Random(1)
        val n = 1_000_000_007

        repeat(10_000) {
            val x = BigInt.from(rnd.nextLong()) * BigInt.from(rnd.nextLong())
            val r = x.mod(n)

            assertTrue(r >= BigInt.ZERO)
            assertTrue(r < n)
        }
    }

}