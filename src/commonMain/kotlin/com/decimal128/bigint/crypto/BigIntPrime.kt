package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigIntAccumulator
import com.decimal128.bigint.toBigInt
import com.decimal128.bigint.toBigIntAccumulator

object BigIntPrime {

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
        tmp: BigIntAccumulator
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

    private val MR_BASES = intArrayOf(
        2,
        325,
        9375,
        28178,
        450775,
        9780504,
        1795265022
    )

    fun isProbablePrime(n: BigInt, tmp: BigIntAccumulator = BigIntAccumulator()) =
        isBailliePSWProbablePrime(n, tmp)

    fun isBailliePSWProbablePrime(n: BigInt, tmp: BigIntAccumulator = BigIntAccumulator()): Boolean {
        require(!n.isNegative())
        return when (classifyBySmallPrimes(n, tmp)) {
            SmallPrimeResult.COMPOSITE -> false
            SmallPrimeResult.PRIME -> true
            SmallPrimeResult.INCONCLUSIVE -> {
                if (! isMillerRabinBase2(n, tmp)) return false
                val selfridge = selectSelfridgeParams(n)
                selfridge.D != 0 && isStrongLucasProbablePrime(n, selfridge)
            }
        }
    }

    fun isMillerRabinBase2(n: BigInt, tmp: BigIntAccumulator): Boolean {
        require (! n.isNegative())
        val nMinusOne = n - 1
        var d = nMinusOne
        var s = 0
        while (d.isEven()) {
            d = d shr 1
            s++
        }

        val ctx = ModContext(n)

        for (a in MR_BASES) {
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

    fun jacobi(a: Int, n: Int): Int = jacobi(a, n.toBigInt())

    fun jacobi(a: Int, n: BigInt): Int {
        require (n > 0 && n.isOdd())
        var v = n.toBigIntAccumulator()
        var u = a.toBigIntAccumulator()
        u %= v
        if (u < 0)
            u += n
        var j = 1
        while (u.isNotZero()) {
            while (u.isEven()) {
                u.mutShr(1)
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

    data class LucasParams(val D: Int, val P: Int, val Q: Int)

    fun selectSelfridgeParams(n: BigInt): LucasParams {
        require(n.isPositive() && n.isOdd())
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
                return LucasParams(0, 0, 0)
            }
            // next D in 5, -7, 9, -11, 13, ...
            D += 2
            sign = -sign
        }
    }

    fun isStrongLucasProbablePrime(
        n: BigInt,
        params: LucasParams
    ): Boolean {

        // n + 1 = d * 2^s, with d odd
        val n1 = n + 1
        val s = n1.countTrailingZeroBits()
        val d = n1 shr s

        // U_d, V_d, Q^d (all normalized mod n)
        val (U, V, Qk) = lucasUVQk(n, d, params.D, params.Q)

        if (U.isZero()) return true

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

    fun lucasUVQk(
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
}