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

    fun isProbablePrime(n: BigInt, tmp: BigIntAccumulator = BigIntAccumulator()): Boolean {
        require(!n.isNegative())

        return when (classifyBySmallPrimes(n, tmp)) {
            SmallPrimeResult.COMPOSITE -> false
            SmallPrimeResult.PRIME -> true
            SmallPrimeResult.INCONCLUSIVE -> millerRabin(n, tmp)
        }
    }

    fun millerRabin(n: BigInt, tmp: BigIntAccumulator): Boolean {
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

    fun jacobi(a: BigInt, n: BigInt): Int {
        require (n > 0 && n.isOdd())
        var u = (a % n).toBigIntAccumulator()          // ensure 0 <= a < n
        if (u < 0)
            u += n
        var v = n.toBigIntAccumulator()
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

}