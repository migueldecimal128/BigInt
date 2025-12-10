package com.decimal128.bigint

import com.decimal128.bigint.BigInt.Companion.ONE
import com.decimal128.bigint.BigInt.Companion.ZERO
import com.decimal128.bigint.BigInt.Companion.from
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object BigIntAlgorithms {

    /**
     * Returns `w!` as a [BigInt].
     *
     * Uses an optimized multiplication tree and
     * fast paths for small `w`, and returns `ONE` for `w == 0` or `1`.
     */
    fun factorial(w: UInt): BigInt {
        if (w <= 20u) {
            if (w <= 1u)
                return ONE
            var f = 1uL
            for (i in 2uL..w.toULong())
                f *= i
            return from(f)
        }
        val limbLen = estimateFactorialLimbLen(w)
        val f = IntArray(limbLen)
        val twentyBang = 2_432_902_008_176_640_000
        f[0] = twentyBang.toInt()
        f[1] = (twentyBang ushr 32).toInt()
        var fNormLen = 2
        for (i in 21u..w)
            fNormLen = Magus.setMul(f, f, fNormLen, i)
        return BigInt(f, fNormLen)
    }

    private fun estimateFactorialLimbLen(w: UInt): Int {
        val bits = estimateFactorialBits(w)
        val limbs = ((bits + 0x1FuL) shr 5)
        if (limbs == limbs.toInt().toULong())
            return limbs.toInt()
        throw IllegalArgumentException("factorial will overflow memory constraints")
    }

    private fun estimateFactorialBits(w: UInt): ULong {
        if (w < 2u) return 1uL

        val nn = w.toDouble()
        val log2e = 1.4426950408889634
        val pi = 3.141592653589793

        // n log2 n - n log2 e + 0.5 log2(2πn)
        val term1 = nn * kotlin.math.log2(nn)
        val term2 = -log2e * nn
        val term3 = 0.5 * kotlin.math.log2(2 * pi * nn)

        val estimate = term1 + term2 + term3

        // Add correction term 1/(12n ln 2)
        val correction = 0.12022644346 / nn

        return kotlin.math.floor(estimate + correction).toULong() + 1u
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
        val magia = Magus.gcd(a.magia, a.meta.normLen, b.magia, b.meta.normLen)
        check(magia !== Magus.ZERO)
        return BigInt(magia)
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
        val gcd = Magus.gcd(a.magia, a.meta.normLen, b.magia, b.meta.normLen)
        val lcm = if (Magus.bitLen(a.magia) < Magus.bitLen(b.magia))
            Magus.newMul(Magus.newDiv(a.magia, gcd), b.magia)
        else
            Magus.newMul(Magus.newDiv(b.magia, gcd), a.magia)
        return BigInt(lcm)
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
        val resultSign = base.meta.signFlag && ((exp and 1) != 0)
        return when {
            exp < 0 -> throw IllegalArgumentException("cannot raise BigInt to negative power:$exp")
            exp == 0 -> ONE
            exp == 1 -> base
            base.isZero() -> ZERO
            Magus.EQ(base.magia, 1) -> if (resultSign) BigInt.Companion.NEG_ONE else ONE
            Magus.EQ(base.magia, 2) -> BigInt(resultSign, Magus.newWithSetBit(exp))
            exp == 2 -> base.sqr()
            else -> {
                val maxBitLen = Magus.bitLen(base.magia) * exp
                val maxBitLimbLen = (maxBitLen + 0x1F) ushr 5
                var baseMag = Magus.newCopyWithExactLimbLen(base.magia, maxBitLimbLen)
                var baseLen = Magus.normLen(base.magia)
                var resultMag = IntArray(maxBitLimbLen)
                resultMag[0] = 1
                var resultLen = 1
                var tmpMag = IntArray(maxBitLimbLen)

                var e = exp
                while (true) {
                    if ((e and 1) != 0) {
                        tmpMag.fill(0, 0, baseLen)
                        resultLen = Magus.setMul(tmpMag, resultMag, resultLen, baseMag, baseLen)
                        val t = tmpMag
                        tmpMag = resultMag
                        resultMag = t
                    }
                    e = e ushr 1
                    if (e == 0)
                        break
                    tmpMag.fill(0, 0, min(tmpMag.size, 2 * baseLen))
                    baseLen = Magus.setSqr(tmpMag, baseMag, baseLen)
                    val t = tmpMag
                    tmpMag = baseMag
                    baseMag = t
                }
                BigInt(resultSign, resultMag)
            }
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
    fun isqrt(radicand: BigInt): BigInt {
        if (radicand.meta.isNegative)
            throw ArithmeticException("Square root of a negative BigInt")
        val bitLen = Magus.bitLen(radicand.magia)
        if (bitLen <= 53) {
            return when {
                bitLen == 0 -> ZERO
                bitLen == 1 -> ONE
                else -> {
                    val dw = Magus.toRawULong(radicand.magia)
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
                    check(isqrt * isqrt <= dw && (isqrt + 1uL) * (isqrt + 1uL) > dw)
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
        val top = Magus.extractULongAtBitIndex(radicand.magia, topBitsIndex) + 1uL + 1uL
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

        // 27 == sqrt of the top bits
        // topBitsIndex/2 accounts for shifting
        // 1 is for the carry when we average in-place
        val xInitialBitLen = 27 + (topBitsIndex / 2) + 1
        val xInitialLimbLen = (xInitialBitLen + 0x1F) ushr 5
        val tmpLimbLen = max(xInitialLimbLen, radicand.meta.normLen - xInitialLimbLen + 1)

        var x = IntArray(tmpLimbLen)
        x[0] = topSqrt.toInt()
        var xNormLen = Magus.setShiftLeft(x, x, 1, topBitsIndex shr 1)
        val x2 = Magus.newWithUIntAtBitIndex(topSqrt.toUInt(), topBitsIndex shr 1)
        check (Magus.EQ(x, x2))

        var xPrev = IntArray(tmpLimbLen)
        var xPrevNormLen: Int
        do {
            val t = xPrev; xPrev = x; x = t
            xPrevNormLen = xNormLen

            xNormLen = Magus.setDiv(x, radicand.magia, radicand.meta.normLen, xPrev, xPrevNormLen)
            xNormLen = Magus.setAdd(x, x, xNormLen, xPrev, xPrevNormLen)
            xNormLen = Magus.setShiftRight(x, x, xNormLen, 1)

        } while (Magus.compare(x, xNormLen, xPrev, xPrevNormLen) < 0)
        val ret = BigInt(xPrev, xPrevNormLen)
        return ret
    }
}

