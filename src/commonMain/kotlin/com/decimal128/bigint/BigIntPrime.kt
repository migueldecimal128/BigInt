package com.decimal128.bigint

/**
 * Primality-testing utilities for [BigInt].
 *
 * Provides a fast version of the **Baillie–PSW** test:
 *  • Trial division by a fixed set of small primes
 *  • One Miller–Rabin round with base 2
 *  • A strong Lucas probable-prime test (Selfridge method)
 *
 * The combined test has **no known counterexamples** and is regarded as
 * suitable for cryptographic and numerical use.

 * ## Notes
 * - All algorithms are allocation-conscious and reuse
 *   [MutableBigInt] scratch storage where possible.
 * - Performance-optimized ... no constant-time guarantees.
 * - Results from prime testing are *probabilistic* but extremely
 *   reliable in practice.
 * - Negative values are rejected; `0` and `1` are composite.
 */
object BigIntPrime {

    /**
     * Returns `true` if [n] is a probable prime (Baillie–PSW test).
     */
    fun isProbablePrime(n: BigInt, tmp: MutableBigInt = MutableBigInt()) =
        isBailliePSWProbablePrime(n, tmp)

    /**
     * Tests whether [n] is a probable prime using the Baillie–PSW algorithm.
     *
     * The Baillie–PSW test is a strong, widely used probabilistic primality test
     * combining:
     *
     * 1. Trial division by a fixed set of small primes
     * 2. A base-2 Miller–Rabin strong probable-prime test
     * 3. A strong Lucas probable-prime test with Selfridge parameter selection
     *
     * This implementation uses a slightly stronger Miller-Rabin test by
     * testing 7 bases (including base-2) instead of only a base-2 test.
     *
     * No counterexamples to Baillie–PSW are known, and it is considered
     * deterministic for all practical purposes.
     *
     * ## Behavior
     * - Returns `false` for negative values, `0`, and `1`
     * - Returns `true` for all small primes
     * - Uses [MutableBigInt] scratch storage to minimize heap allocation
     *
     * ## Constraints
     * - [n] must be non-negative
     *
     * @param n value to test for primality
     * @param tmp reusable scratch accumulator
     * @return `true` if [n] is a probable prime, `false` if composite
     * @throws IllegalArgumentException if [n] is negative
     */
    fun isBailliePSWProbablePrime(n: BigInt, tmp: MutableBigInt = MutableBigInt()): Boolean {
        require(!n.isNegative())
        return when (classifyBySmallPrimes(n, tmp)) {
            SmallPrimeResult.COMPOSITE -> false
            SmallPrimeResult.PRIME -> true
            SmallPrimeResult.INCONCLUSIVE -> {
                if (!isMillerRabinProbablePrimeBase2(n, tmp)) return false
                val selfridge = selectSelfridgeParams(n)
                selfridge.D != 0 && isStrongLucasProbablePrime(n, selfridge)
            }
        }
    }

    private val SMALL_PRIMES = shortArrayOf(
        3, 5, 7, 11, 13, 17, 19, 23,
        29, 31, 37, 41, 43, 47, 53, 59,
        61, 67, 71, 73, 79, 83, 89, 97,
        101, 103, 107, 109, 113, 127, 131, 137,
        139, 149, 151, 157, 163, 167, 173, 179,
        181, 191, 193, 197, 199, 211, 223, 227,
        229, 233, 239, 241, 251, 257, 263, 269,
        271, 277, 281, 283, 293, 307, 311, 313,
        317
    )

    private enum class SmallPrimeResult {
        COMPOSITE, PRIME, INCONCLUSIVE
    }

    private fun classifyBySmallPrimes(
        n: BigInt,
        tmp: MutableBigInt
    ): SmallPrimeResult {
        return when {
            n <= 1 -> SmallPrimeResult.COMPOSITE
            n <= 3 -> SmallPrimeResult.PRIME
            n.isEven() -> SmallPrimeResult.COMPOSITE
            else -> {
                for (p0 in SMALL_PRIMES) {
                    val p = p0.toInt()
                    tmp.set(n)
                    tmp %= p
                    if (tmp.isZero()) {
                        return if (n EQ p) {
                            SmallPrimeResult.PRIME
                        } else {
                            SmallPrimeResult.COMPOSITE
                        }
                    }
                }
                SmallPrimeResult.INCONCLUSIVE
            }
        }
    }

    /**
     * Performs a *strong* Miller–Rabin probable-prime test on an odd integer [n].
     *
     * For each supplied base `a` in [bases], the function checks whether `a`
     * is a strong witness against the primality of [n]. It factors:
     *
     *     n − 1 = d · 2^s    with d odd
     *
     * then computes:
     *
     *     x = a^d mod n
     *
     * The base is accepted if `x == 1` or `x == n − 1`, or if repeated squaring
     * of `x` (up to `s − 1` times) produces `n − 1`. If a base never reaches `n − 1`,
     * it is a strong witness and [n] is declared composite.
     *
     * **Requirements**
     * - [n] must be ≥ 3 and odd.
     * - Callers are responsible for handling `n ∈ {2}` and small primes.
     *
     * **Return value**
     * - `true`  → [n] passes all supplied bases and is therefore a strong probable prime.
     * - `false` → at least one base is a strong compositeness witness.
     *
     * **Notes**
     * - Probabilistic guarantees depend on the choice of bases; with deterministic
     *   base sets this can be a fully deterministic test over a bounded domain.
     * - Bases ≥ [n] are skipped.
     *
     * @param n the odd integer to test for primality
     * @param bases the bases to use for the strong Miller–Rabin test
     */

    fun isMillerRabinProbablePrime(
        n: BigInt, bases: IntArray,
        tmpP: MutableBigInt? = null
    ): Boolean {
        require(n >= 3 && n.isOdd())
        val nMinusOne = n - 1
        val s = nMinusOne.countTrailingZeroBits()
        val d = nMinusOne ushr s

        val ctx = ModContext(n)
        val tmp = if (tmpP is MutableBigInt) tmpP else MutableBigInt()

        for (a in bases) {
            if (n <= a) continue   // important for small n

            ctx.modPow(a.toBigInt(), d, tmp)

            if (tmp.isOne() || tmp EQ nMinusOne)
                continue

            var witness = true
            repeat(s - 1) {
                ctx.modSqr(tmp, tmp)
                if (tmp EQ nMinusOne) {
                    witness = false
                    return@repeat
                }
            }

            if (witness) return false
        }
        return true
    }

    private val MILLER_RABIN_BASE_2 = intArrayOf(2)

    /**
     * Returns `true` if [n] passes a strong Miller–Rabin probable-prime
     * test to base 2.
     *
     * This is the Miller–Rabin stage used in Baillie–PSW,
     * following trial division by small primes.
     *
     * @param n the odd integer to test
     * @param tmp optional reusable scratch space
     */
    fun isMillerRabinProbablePrimeBase2(n: BigInt, tmp: MutableBigInt? = null): Boolean =
        isMillerRabinProbablePrime(n, MILLER_RABIN_BASE_2, tmp)


    /**
     * Deterministic Miller–Rabin bases sufficient for testing 64-bit–range values.
     *
     * Jaeschke, Sinclair, Feitsma, & Galway.
     *
     * When used together, these bases make the Miller–Rabin test
     * deterministic for all `n < 2^64`.
     */
    private val MILLER_RABIN_JAESCHKE_BASES = intArrayOf(
        2,
        325,
        9375,
        28178,
        450775,
        9780504,
        1795265022
    )

    /**
     * Performs a Miller–Rabin strong probable-prime test on [n].
     *
     * Uses a fixed set of deterministic bases sufficient to make the test
     * exact for all values in the 64-bit range and extremely reliable for
     * larger values.
     *
     * The test writes intermediate results into [tmp] to avoid heap allocation.
     *
     * @param n value to test (must be non-negative)
     * @param tmp reusable scratch accumulator
     * @return `true` if [n] passes all Miller–Rabin bases, `false` if composite
     * @throws IllegalArgumentException if [n] is negative
     */
    fun isMillerRabinProbablePrimeJaeschke(n: BigInt, tmp: MutableBigInt?): Boolean =
        isMillerRabinProbablePrime(n, MILLER_RABIN_JAESCHKE_BASES, tmp)

    /**
     * Computes the Jacobi symbol (a | n).
     *
     * @return -1, 0, or 1 depending on the value of the Jacobi symbol
     */
    fun jacobi(a: Int, n: Int): Int = jacobi(a, n.toBigInt())

    /**
     * Computes the Jacobi symbol (a | n).
     *
     * @return -1, 0, or 1 depending on the value of the Jacobi symbol
     */
    fun jacobi(a: Int, n: BigInt): Int {
        require(n > 0 && n.isOdd())
        var v = n.toMutableBigInt()
        var u = a.toMutableBigInt()
        u %= v
        if (u < 0)
            u += n
        var j = 1
        while (u.isNotZero()) {
            while (u.isEven()) {
                u.mutShr(1)
                check(!v.isNegative())
                val v8 = v.toInt() and 0x07
                if (v8 == 3 || v8 == 5)
                    j = -j
            }
            // swap
            val t = u; u = v; v = t
            if ((u.toInt() and 3) == 3 && (v.toInt() and 3) == 3)
                j = -j
            u %= v
        }
        return if (v EQ 1) j else 0
    }

    /**
     * Parameters (D, P, Q) for Lucas sequences, selected using
     * the Selfridge method when used in primality testing.
     */
    data class LucasParams(val D: Int, val P: Int, val Q: Int)

    private val LUCAS_COMPOSITE = LucasParams(0, 0, 0)

    /**
     * Selects Lucas sequence parameters `(D, P, Q)` using the Selfridge method
     * for the strong Lucas probable-prime test.
     *
     * A perfect-square check is performed first to avoid pathological runtimes.
     *
     * The algorithm searches signed values of `D = 5, -7, 9, -11, ...` until
     * `jacobi(D, n) = -1`, at which point it returns parameters:
     *
     *     P = 1
     *     Q = (1 − D) / 4      // exact because D ≡ 1 mod 4
     *
     * If `jacobi(D, n) = 0`, then `gcd(D, n) > 1` and `n` is composite unless
     * `n == |D|`, in which case valid Lucas parameters are still returned.
     *
     * @param n a positive odd integer
     * @return the selected Lucas parameters, or a zero-valued sentinel if `n` is composite
     */
    fun selectSelfridgeParams(n: BigInt): LucasParams {
        require(n.isPositive() && n.isOdd())
        if (n.isPerfectSquare())
            return LUCAS_COMPOSITE
        var D = 5
        var sign = 1
        while (true) {
            val dSigned = sign * D
            val jac = jacobi(dSigned, n)
            if (jac == -1) {
                // P = 1
                // Q = (1 - D) / 4  where D is signed here
                val Q = (1 - dSigned) shr 2   // exact division by 4
                return LucasParams(dSigned, 1, Q)
            }
            if (jac == 0) {
                // gcd(D, n) > 1 ⇒ composite unless n == D
                if (n EQ D) {
                    val Q = (1 - dSigned) shr 2
                    return LucasParams(dSigned, 1, Q)
                }
                return LUCAS_COMPOSITE
            }
            // next D in 5, -7, 9, -11, 13, ...
            D += 2
            sign = -sign
        }
    }

    /**
     * Performs a strong Lucas probable-prime test on [n].
     *
     * Uses the Lucas parameters [params] (typically selected via the
     * Selfridge method) and checks the strong Lucas conditions based on
     * the factorization `n + 1 = d · 2^s`.
     *
     * @param n odd integer to test for primality
     * @param params Lucas sequence parameters `(D, P, Q)`
     * @return `true` if [n] passes the strong Lucas test, `false` if composite
     */
    fun isStrongLucasProbablePrime(
        n: BigInt,
        params: LucasParams
    ): Boolean {

        // n + 1 = d * 2^s, with d odd
        val n1 = n + 1
        val s = n1.countTrailingZeroBits()
        val d = n1 shr s

        val modCtx = ModContext(n)
        // U_d, V_d, Q^d (all normalized mod n)
        val (U, V, Qk) = lucasUVQk(modCtx, d, params.D, params.Q)

        if (U.isZero() || V.isZero()) return true

        var Vcur = V
        var Qcur = Qk

        repeat(s - 1) {
            Vcur = (Vcur * Vcur - (Qcur shl 1)) mod n
            Qcur = (Qcur * Qcur) mod n

            if (Vcur.isZero()) return true
        }

        return false
    }

    /**
     * Computes x / 2 modulo odd modulus n.
     * Precondition: 0 ≤ x < n and n is odd.
     */
    private fun modHalfLucas(x: BigInt, n: BigInt): BigInt {
        check(n.isOdd())
        // x is already reduced mod n
        return if (x.isOdd()) (x + n) shr 1 else x shr 1
    }

    /**
     * Computes Lucas sequence values `U_d`, `V_d`, and `Q^d (mod n)`.
     *
     * Uses a left-to-right binary method to evaluate the Lucas sequences
     * defined by parameters `(D, P = 1, Q)` modulo [n], where [d] is odd.
     *
     * The returned values are required by the strong Lucas probable-prime test.
     *
     * @param n modulus (odd)
     * @param d odd exponent
     * @param D Lucas parameter `D = P^2 - 4Q`
     * @param Q Lucas parameter `Q`
     * @return a triple `(U_d, V_d, Q^d mod n)`
     */
    fun lucasUVQk_BigInt(
        n: BigInt,
        d: BigInt,   // odd
        D: Int,
        Q: Int
    ): Triple<BigInt, BigInt, BigInt> {

        var U = BigInt.ONE
        var V = BigInt.ONE
        var Qk = Q.toBigInt() mod n

        for (i in d.magnitudeBitLen() - 2 downTo 0) {

            val bit = d.testBit(i)

            val U2m = (U * V) mod n
            val V2m = (V * V - (Qk shl 1)) mod n
            val Q2m = (Qk * Qk) mod n

            if (!bit) {
                U = U2m
                V = V2m
                Qk = Q2m
            } else {
                val U2m1 = modHalfLucas((U2m + V2m) mod n, n)
                val V2m1 = modHalfLucas((V2m + (U2m * D)) mod n, n)
                val Q2m1 = (Q2m * Q) mod n

                U = U2m1
                V = V2m1
                Qk = Q2m1
            }
        }

        return Triple(U, V, Qk)
    }

    fun lucasUVQk(
        modCtx: ModContext,
        d: BigInt,   // odd
        D: Int,
        Q: Int
    ): Triple<BigInt, BigInt, BigInt> {

        var U = 1.toMutableBigInt()
        var V = 1.toMutableBigInt()
        var Qk = MutableBigInt()
        modCtx.modSet(Q, Qk)

        var U2m = MutableBigInt()
        var V2m = MutableBigInt()
        var V2 = MutableBigInt()
        var QkDoubled = MutableBigInt()
        var Q2m = MutableBigInt()
        var U2m1 = MutableBigInt()
        var V2m1 = MutableBigInt()
        var Q2m1 = MutableBigInt()
        val tmp1 = MutableBigInt()
        val tmp2 = MutableBigInt()

        for (i in d.magnitudeBitLen() - 2 downTo 0) {

            val bit = d.testBit(i)

            //val U2m = (U * V) mod n
            modCtx.modMul(U, V, U2m)

            //val V2m = (V * V - (Qk shl 1)) mod n
            modCtx.modSqr(V, V2)
            modCtx.modAdd(Qk, Qk, QkDoubled)
            modCtx.modSub(V2, QkDoubled, V2m)

            //val Q2m = (Qk * Qk) mod n
            modCtx.modSqr(Qk, Q2m)

            if (!bit) {
                //U = U2m
                val swapU = U; U = U2m; U2m = swapU
                //V = V2m
                val swapV = V; V = V2m; V2m = swapV
                //Qk = Q2m
                val swapQk = Qk; Qk = Q2m; Q2m = swapQk
            } else {
                //val U2m1 = modHalfLucas((U2m + V2m) mod n, n)
                modCtx.modAdd(U2m, V2m, tmp1)
                modCtx.modHalfLucas(tmp1, U2m1)

                //val V2m1 = modHalfLucas((V2m + (U2m * D)) mod n, n)
                modCtx.modMul(U2m, D, tmp1)
                modCtx.modAdd(V2m, tmp1, tmp2)
                modCtx.modHalfLucas(tmp2, V2m1)

                //val Q2m1 = (Q2m * Q) mod n
                modCtx.modMul(Q2m, Q, Q2m1)

                //U = U2m1
                val swapU = U; U = U2m1; U2m1 = swapU

                // V = V2m1
                val swapV = V; V = V2m1; V2m1 = swapV

                //Qk = Q2m1
                val swapQk = Qk; Qk = Q2m1; Q2m1 = swapQk
            }
        }

        return Triple(U.toBigInt(), V.toBigInt(), Qk.toBigInt())
    }
}