package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigIntAccumulator
import com.decimal128.bigint.toBigInt
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestIsProbablePrime {

    private fun bi(i: Int) = i.toBigInt()

    @Test
    fun testSmallValues() {
        val tmp = BigIntAccumulator()

        assertFalse(BigIntPrime.isProbablePrime(bi(0), tmp))
        assertFalse(BigIntPrime.isProbablePrime(bi(1), tmp))

        assertTrue(BigIntPrime.isProbablePrime(bi(2), tmp))
        assertTrue(BigIntPrime.isProbablePrime(bi(3), tmp))

        assertFalse(BigIntPrime.isProbablePrime(bi(4), tmp))
    }

    @Test
    fun testSmallPrimesInTable() {
        val tmp = BigIntAccumulator()

        val primes = intArrayOf(
            3, 5, 7, 11, 13, 17, 19, 23,
            29, 31, 37, 41, 43, 47,
            53, 59, 61, 67, 71, 73,
            79, 83, 89, 97, 101, 103,
            107, 109, 113, 127, 131,
            137, 139, 149, 151, 157,
            163, 167, 173, 179, 181,
            191, 193, 197, 199, 211,
            223, 227, 229, 233, 239,
            241, 251, 257, 263, 269,
            271, 277, 281, 283, 293,
            307, 311, 313, 317
        )

        for (p in primes) {
            assertTrue(
                BigIntPrime.isProbablePrime(bi(p), tmp),
                "Prime $p incorrectly rejected"
            )
        }
    }

    @Test
    fun testSmallCompositeFactors() {
        val tmp = BigIntAccumulator()

        val composites = intArrayOf(
            9, 15, 21, 25, 27, 33, 35, 39,
            49, 51, 55, 57, 63, 77, 91,
            121, 143, 169, 187, 221, 289
        )

        for (c in composites) {
            assertFalse(
                BigIntPrime.isProbablePrime(bi(c), tmp),
                "Composite $c incorrectly accepted"
            )
        }
    }

    @Test
    fun testLargePrimeAndComposite() {
        val tmp = BigIntAccumulator()

        // 61-bit prime
        val prime = BigInt.from("2305843009213693951") // 2^61 − 1
        assertTrue(BigIntPrime.isProbablePrime(prime, tmp))

        // Same prime * 3
        val composite = prime * 3.toBigInt()
        assertFalse(BigIntPrime.isProbablePrime(composite, tmp))
    }

    @Test
    fun testNegativeRejected() {
        val tmp = BigIntAccumulator()
        assertFailsWith<IllegalArgumentException> {
            BigIntPrime.isProbablePrime((-7).toBigInt(), tmp)
        }
    }

    @Test
    fun testIsProbablePrime_negative_throws() {
        assertFailsWith<IllegalArgumentException> {
            BigIntPrime.isProbablePrime((-1).toBigInt())
        }
    }

    @Test
    fun testIsProbablePrime_trivial() {
        assertFalse(BigIntPrime.isProbablePrime(0.toBigInt()))
        assertFalse(BigIntPrime.isProbablePrime(1.toBigInt()))

        assertTrue(BigIntPrime.isProbablePrime(2.toBigInt()))
        assertTrue(BigIntPrime.isProbablePrime(3.toBigInt()))

        assertFalse(BigIntPrime.isProbablePrime(4.toBigInt()))
        assertFalse(BigIntPrime.isProbablePrime(9.toBigInt()))
    }

    @Test
    fun testIsProbablePrime_smallValues() {
        val primes = listOf(
            3, 5, 7, 11, 13, 17, 19, 23, 29, 31,
            37, 41, 43, 47, 53, 59, 61
        )

        val composites = listOf(
            4, 6, 8, 9, 10, 12, 14, 15, 16,
            18, 20, 21, 22, 24, 25, 27, 28
        )

        for (p in primes) {
            assertTrue(BigIntPrime.isProbablePrime(p.toBigInt()), "prime $p")
        }

        for (c in composites) {
            assertFalse(BigIntPrime.isProbablePrime(c.toBigInt()), "composite $c")
        }
    }

    @Test
    fun testIsProbablePrime_carmichaelNumbers() {
        val carmichaels = listOf(
            "561",
            "1105",
            "1729",
            "2465",
            "2821",
            "6601",
            "8911",
            "10585",
            "15841",
            "29341",
            "41041",
            "46657",
            "52633",
            "62745",
            "63973"
        )

        for (s in carmichaels) {
            val n = s.toBigInt()
            assertFalse(
                BigIntPrime.isProbablePrime(n),
                "Carmichael incorrectly accepted: $n"
            )
        }
    }

    @Test
    fun testIsProbablePrime_knownLargePrimes() {
        val primes = listOf(
            // Mersenne primes
            "2305843009213693951",         // 2^61 − 1
            "618970019642690137449562111", // 2^89 − 1
            "170141183460469231731687303715884105727", // 2**127 - 1
            "6864797660130609714981900799081393217269435300" +
                    "14330540939446345918554318339765605212" +
                    "25596406614545549772963113914808580371" +
                    "21987999716643812574028291115057151", // 2**251 - 1

            // Random large primes
            "32416190071",  // 11-digit known prime
        )

        for (s in primes) {
            val p = s.toBigInt()
            assertTrue(
                BigIntPrime.isProbablePrime(p),
                "prime rejected: $p"
            )
        }
    }

    @Test
    fun testIsProbablePrime_largeComposites() {
        val composites = listOf(
            // prime * prime
            ("2305843009213693951".toBigInt() *
                    "2305843009213693951".toBigInt()),

            // prime * small
            ("2305843009213693951".toBigInt() * 3.toBigInt()),

            // near-prime even
            ("2305843009213693951".toBigInt() + 1.toBigInt()),

            "3825123056546413051".toBigInt(),
            "318665857834031151167461".toBigInt(),
            ("803837457453639491257079614341942108138837688287558" +
                    "14583748891752229742737653336521865023361639" +
                    "60045457915042023603208766569966760987284043" +
                    "96540823292873879185086916685732826776177102" +
                    "93896977394701670823042868710999743997654414" +
                    "48453411558724506334092790222752962294149842" +
                    "30688168540432645753401832978611129896064484" +
                    "5216191652872597534901").toBigInt(),

        )

        for (n in composites) {
            assertFalse(
                BigIntPrime.isProbablePrime(n),
                "composite accepted: $n"
            )
        }
    }

    @Test
    fun testIsProbablePrime_regression_mersenne61() {
        val p = "2305843009213693951".toBigInt()
        assertTrue(BigIntPrime.isProbablePrime(p))
    }

}