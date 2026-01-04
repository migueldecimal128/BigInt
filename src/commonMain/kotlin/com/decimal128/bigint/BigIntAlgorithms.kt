// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.BigInt.Companion.NEG_ONE
import com.decimal128.bigint.BigInt.Companion.ONE
import com.decimal128.bigint.BigInt.Companion.ZERO
import com.decimal128.bigint.BigInt.Companion.from
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

object BigIntAlgorithms {

    /**
     * Returns `w!` as a [BigInt].
     *
     * Uses an optimized multiplication tree and
     * fast paths for small `w`, and returns `ONE` for `w == 0` or `1`.
     */
    fun factorial(n: Int): BigInt {
        if (n < 0)
            throw IllegalArgumentException("factorial of a negative number")
        if (n <= 20) {
            if (n <= 1)
                return ONE
            var f = 1L
            for (i in 2L..n.toLong())
                f *= i
            return from(f)
        }
        val bitCapacityRequired = estimateFactorialBitLen(n)
        val f = MutableBigInt.withBitCapacityHint(bitCapacityRequired)
        val twentyBang = 2_432_902_008_176_640_000L
        f.set(twentyBang)
        for (i in 21..n)
            f *= i
        return f.toBigInt()
    }

    private fun estimateFactorialBitLen(n: Int): Int {
        if (n < 2) return 1

        val nn = n.toDouble()
        val log2e = 1.4426950408889634
        val pi = 3.141592653589793

        // n log2 n - n log2 e + 0.5 log2(2πn)
        val term1 = nn * kotlin.math.log2(nn)
        val term2 = -log2e * nn
        val term3 = 0.5 * kotlin.math.log2(2 * pi * nn)

        val estimate = term1 + term2 + term3

        // Add correction term 1/(12n ln 2)
        val correction = 0.12022644346 / nn

        val final = estimate + correction.toLong() + 1L
        if (final > Int.MAX_VALUE)
            throw ArithmeticException("factorial will overflow max bitLen of Int.MAX_VALUE")

        return final.toInt()
    }

    /**
     * Returns the greatest common divisor (GCD) of the two values [a] and [b].
     *
     * The GCD is always non-negative, and `gcd(a, b) == gcd(b, a)`.
     * If either argument is zero, the result is the absolute value of the other.
     *
     * This implementation uses Stein’s binary GCD algorithm, which avoids
     * multiprecision division and relies only on subtraction, comparison,
     * and bit-shifts — operations that are efficient on `BigInt`.
     *
     * @return the non-negative greatest common divisor of [a] and [b]
     */
    fun gcd(a: BigInt, b: BigInt): BigInt {
        if (a.isZero())
            return b.abs()
        if (b.isZero())
            return a.abs()

        var x = a.toMutableBigInt().mutAbs()
        var y = b.toMutableBigInt().mutAbs()

        val ctzX = x.countTrailingZeroBits()
        val ctzY = y.countTrailingZeroBits()
        val initialShift = min(ctzX, ctzY)
        x.mutShr(ctzX)
        y.mutShr(ctzY)

        // Now both x and y are odd
        while (y.isNotZero()) {
            // Remove factors of 2 from y
            y.mutShr(y.countTrailingZeroBits())
            // Ensure x <= y
            if (x > y) {
                val swap = x; x = y; y = swap
            }
            // y = y - x
            y -= x
        }
        // Final result = u * 2^shift
        x.mutShl(initialShift)
        return x.toBigInt()
    }

    /**
     * Returns the least common multiple (LCM) of [a] and [b].
     *
     * If either argument is zero, the result is `BigInt.ZERO`. Otherwise the LCM is
     * defined as `|a / gcd(a, b)| * |b|` and is always non-negative.
     *
     * This implementation divides the smaller magnitude by the GCD to minimize the
     * cost of multiprecision division, then multiplies by the larger magnitude.
     */
    fun lcm(a: BigInt, b: BigInt): BigInt {
        if (a.isZero() || b.isZero())
            return ZERO
        val aAbs = a.abs()
        val bAbs = b.abs()
        val gcd = gcd(aAbs, bAbs)
        if (aAbs < bAbs)
            return (aAbs / gcd) * bAbs
        else
            return (bAbs / gcd) * aAbs
    }

    /**
     * Raises [base] to the integer power [exp] using binary exponentiation.
     *
     * Special cases are handled efficiently:
     *  - `exp == 0` → returns [ONE]
     *  - `exp == 1` → returns [base]
     *  - `base == ZERO` → returns [ZERO]
     *  - `base == ±1` → returns ±1 depending on the parity of [exp]
     *  - `base == ±2` → uses bit-setting for fast 2ⁿ
     *  - `exp == 2` → uses `sqr()` for efficiency
     *
     * Uses a non-modular square-and-multiply loop with preallocated buffers sized
     * to the maximum possible bit length of the result.
     *
     * @param base the BigInt base value
     * @param exp  the non-negative exponent
     * @return `base^exp` with the mathematically correct sign
     * @throws IllegalArgumentException if [exp] is negative
     */
    fun pow(base: BigInt, exp: Int): BigInt {
        val resultSign = base.isNegative() && ((exp and 1) != 0)
        val baseAbs = base.abs()
        return when {
            exp < 0 -> throw IllegalArgumentException("cannot raise BigInt to negative power:$exp")
            exp == 0 -> ONE
            exp == 1 -> base
            exp == 2 -> baseAbs.sqr()
            base.isZero() -> ZERO
            baseAbs EQ 1 -> if (resultSign) NEG_ONE else ONE
            baseAbs EQ 2 -> BigInt.withSetBit(exp).withSign(resultSign)
            else -> {
                val maxBitLen = base.magnitudeBitLen() * exp
                val r = MutableBigInt.withBitCapacityHint(maxBitLen).setOne()
                powLeftToRight(base, exp, r)
                r.toBigInt()
            }
        }
    }

    fun tryPowFastPath(base: BigIntNumber, exp: Int, mbi: MutableBigInt): Boolean {
        val resultSign = base.isNegative() && ((exp and 1) != 0)
        when {
            exp < 0 -> throw IllegalArgumentException("cannot raise BigInt to negative power:$exp")
            exp == 0 -> mbi.setOne()
            exp == 1 -> mbi.set(base)
            exp == 2 -> mbi.setSqr(base)
            base.isZero() -> mbi.setZero()
            base magEQ 1 -> mbi.set(if (resultSign) -1 else 1)
            base magEQ 2 -> mbi.setBit(exp).mutWithSign(resultSign)
            else -> return false
        }
        return true
    }

    /**
     * Computes [base]^[exp] using left-to-right binary exponentiation.
     *
     * Scans the exponent from MSB to LSB, building the result through repeated
     * squaring and conditional multiplication. Sign handling is implicit: negative
     * bases with odd exponents naturally produce negative results through the
     * multiplication operations.
     *
     * This algorithm uses one working buffer instead of two (as required by
     * right-to-left), reducing memory allocation and pressure. The original base
     * value remains small and cache-resident throughout the computation.
     *
     * @param base the base value (may be negative)
     * @param exp the non-negative exponent (must be > 2)
     * @param ret destination for the result; must not alias [base]
     * @throws IllegalStateException if exp ≤ 2 (caller should use [tryPowFastPath])
     */
    fun powLeftToRight(base: BigIntNumber, exp: Int, ret: MutableBigInt) {
        if (exp <= 2)
            throw IllegalStateException() // should have been fast-path
        val maxBitLen = base.magnitudeBitLen() * exp
        ret.hintBitCapacity(maxBitLen).set(base)
        var bitIndex = 31 - exp.countLeadingZeroBits() - 1
        while (bitIndex >= 0) {
            ret.setSqr(ret)
            if ((exp shr bitIndex) and 1 != 0)
                ret *= base
            --bitIndex
        }
    }

    /**
     * Returns the **integer square root** of this value.
     *
     * The integer square root of a non-negative integer `n` is defined as:
     *
     *     floor( sqrt(n) )
     *
     * This is the largest integer `r` such that:
     *
     *     r*r ≤ n < (r+1)*(r+1)
     *
     * The result is always non-negative.
     *
     * ### Negative input
     * If this value is negative, an [ArithmeticException] is thrown.
     *
     * ### Small values (bit-length ≤ 53)
     * For inputs whose magnitude fits in 53 bits, the computation uses
     * IEEE-754 `Double` arithmetic. All integers ≤ 2⁵³ are represented
     * exactly as `Double`, and the final result is verified and tweaked
     * to ensure correctness.
     *
     * ### Large values
     * For larger inputs, the algorithm proceeds as follows:
     *
     * 1. **High-precision floating-point estimate.**
     *    The top 52–53 bits of the magnitude are extracted and converted
     *    to `Double`. The 52 vs 53 decision is driven by the need to
     *    have an even number of bits below these top bits.
     *
     *    Two guard units are added:
     *
     *    - **+1** to account for the discarded low bits (which may all be 1s),
     *    - **+1** to guard against downward rounding of `sqrt(double)`.
     *
     *    The guarded chunk is square-rooted and rounded **upward**.
     *    A single correction step ensures the estimate is never too small.
     *
     * 2. **Newton iteration (monotone decreasing).**
     *
     *        x_{k+1} = floor( (x_k + n / x_k) / 2 )
     *
     *    The iteration is implemented entirely in limb arithmetic using
     *    platform-independent routines, and converges from above.
     *    The loop terminates when the sequence stops decreasing; the last
     *    decreasing value is the correct integer square root.
     *
     * ### Complexity
     * Dominated by big-integer division.
     * Overall time is approximately:
     *
     *     O( M(n) * log n )
     *
     * where `M(n)` is the multiplication/division cost for `n`-bit integers.
     *
     * ### Correctness guarantee
     * The returned value `r` always satisfies:
     *
     *     r*r ≤ this < (r+1)*(r+1)
     *
     * @return the non-negative integer square root of this value.
     * @throws ArithmeticException if this value is negative.
     */
    fun isqrt(radicand: BigIntNumber): BigInt {
        if (radicand.isNegative())
            throw ArithmeticException("Square root of a negative BigInt")
        val bitLen = radicand.magnitudeBitLen()
        if (bitLen <= 53) {
            return when {
                bitLen == 0 -> {
                    ZERO
                }
                bitLen == 1 -> {
                    ONE
                }
                else -> {
                    val dw = radicand.toULong()
                    val d = dw.toDouble()
                    val sqrt = sqrt(d)
                    var isqrt = sqrt.toULong()
                    var crossCheck = isqrt * isqrt
                    //while ((crossCheck) < dw) {
                    //    ++isqrt
                    //    crossCheck = isqrt * isqrt
                    //}
                    isqrt += (crossCheck - dw) shr 63
                    crossCheck = isqrt * isqrt
                    isqrt += (crossCheck - dw) shr 63
                    crossCheck = isqrt * isqrt
                    //if (crossCheck > dw)
                    //    --isqrt
                    isqrt -= (dw - crossCheck) shr 63
                    verify { isqrt * isqrt <= dw && (isqrt + 1uL) * (isqrt + 1uL) > dw }
                    // we started with 53 bits, so the result will be <= 27 bits
                    from(isqrt.toUInt())
                }
            }
        }
        // topBitsIndex is an even number
        // the isqrt will have bitsIndex/2 bits below topSqrt
        // above topBitsIndex are 52 or 53 bits .. which fits in a Double
        val topBitsIndex = (bitLen - 52) and 1.inv()
        // We now add 2 to the extracted 53-bit chunk for two independent reasons:
        //
        // (1) +1 accounts for the unknown lower bits of the original number.
        //     When we extract only the top 52–53 bits, the discarded lower bits
        //     could all be 1s, so the true value could be up to 1 larger than
        //     the extracted value at this scale.
        //
        // (2) +1 accounts for possible downward rounding of sqrt(double).
        //     Even though the input is an exactly representable 53-bit integer,
        //     the IEEE-754 sqrt() result may round down by as much as 1 integer.
        //
        // These two errors are independent, and each can reduce the estimate by 1.
        // Therefore we add +2 total, ensuring the initial estimate of sqrt()
        // (after a single correction pass) is never too small.
        val top = radicand.extractULongAtBitIndex(topBitsIndex) + 1uL + 1uL
        // a single check to ensure that the initial isqrt estimate >= the actual isqrt
        var topSqrt = ceil(sqrt(top.toDouble())).toULong()
        val crossCheck = topSqrt * topSqrt
        topSqrt += (crossCheck - top) shr 63 // add 1 iff crossCheck < top

        // FIXME
        //  improve 27 bit sqrt initial guess by shifting left 5 bits
        //  and dividing into the to 64 bits of N
        //  Do this in the 64-bit world
        //  Roll this into a better initial guess
        //  complicated because this might all be clamped by
        //  topBitsIndex

        var x = MutableBigInt().set(topSqrt)
        x.setShl(x, topBitsIndex shr 1)

        var xPrev = MutableBigInt()
        do {
            val t = xPrev; xPrev = x; x = t
            x.setDiv(radicand, xPrev)
            x += xPrev
            x.mutShr(1)
        } while (x < xPrev)
        return xPrev.toBigInt()
    }

    /**
     * Returns `true` if [radicand] is a perfect square.
     *
     * This computes `r = isqrt(radicand)` and checks whether `r*r == radicand`.
     *
     * @param radicand the non-negative value to test
     */
    fun isPerfectSquare(radicand: BigIntNumber): Boolean {
        val isqrt = isqrt(radicand)
        val squared = isqrt.sqr()
        return squared EQ radicand
    }

    /**
     * Montgomery REDC (CIOS form) using 32-bit limbs.
     *
     * Computes  t = t * R⁻¹ mod N,  where R = 2^(32*nLen).
     *
     * Input layout:
     *  - t[0 .. tLen-1] contains the low limbs of T, with 0 ≤ tLen ≤ 2*nLen+1
     *  - n[0 .. nLen-1] contains modulus N (must be odd)
     *  - np = −N⁻¹ mod 2³² (32-bit Montgomery factor)
     *
     * Buffer requirements:
     *  - t must provide capacity for **at least 2*nLen + 1 limbs**
     *  - any limbs in t[tLen .. 2*nLen] must not contain live data
     *    (this implementation clears them before use)
     *
     * Mathematical preconditions:
     *  - N is odd
     *  - 0 ≤ T < N * R   so that REDC produces a value < 2N
     *
     * Postconditions:
     *  - t[0 .. k] holds a k-limb candidate, where k ≤ nLen+1
     *  - if that value ≥ N, one subtraction is applied
     *  - the final result satisfies 0 ≤ t < N and occupies ≤ nLen limbs
     *
     * Returns:
     *  - the normalized limb length of the reduced residue
     *
     * Notes:
     *  - This is the classic Coarsely Integrated Operand Scanning (CIOS) form.
     *  - One extra limb of temporary space is used to hold carry propagation
     *    during the elimination phase.
     */
    fun redc(t: Magia, tLen: Int, n: Magia, k: Int, np: UInt): Int {
        require(k > 0 && k <= n.size)
        require(tLen <= t.size)
        require(t.size >= 2*k + 1)
        require((n[0] and 1) != 0)

        // clear garbage limbs up to t[2*k + 1]
        t.fill(0, tLen, 2*k + 1)

        // --- Phase 1: eliminate low limbs one at a time ---
        for (i in 0..<k) {
            // m = (t[i] * np) mod 2^32
            val m = (t[i].toUInt() * np).toULong()

            var carry = 0uL
            var j = 0

            // t[i+j] += m * n[j] + carry
            while (j < k) {
                // 64-bit accumulator
                val acc =
                    t[i + j].toUInt().toULong() +
                            m * n[j].toUInt().toULong() +
                            carry

                t[i + j] = acc.toInt()
                carry   = acc shr 32
                j++
            }
            carry += t[i + k].toUInt().toULong()
            t[i + k] = carry.toInt()
            t[i + k + 1] += (carry shr 32).toInt()
        }

        // --- Phase 2: shift down the upper half ---
        // result initially resides in t[k .. 2k-1]
        t.copyInto(t, 0, k, k + k + 1)

        // --- Phase 3: conditional subtract modulus ---
        // if T >= N, subtract once

        val tNormLen = Mago.normLen(t, k + 1)
        if (Mago.compare(t, tNormLen, n, k) < 0)
            return tNormLen
        return Mago.setSub(t, t, tNormLen, n, k)
    }


}

