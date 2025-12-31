package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigIntNumber
import com.decimal128.bigint.MutableBigInt
import kotlin.math.absoluteValue

/**
 * Provides a reusable modular–arithmetic context for a fixed modulus [m].
 *
 * `ModContext` precomputes all state required for fast modular operations
 * modulo [m], including Barrett parameters and (when `m` is odd) a full
 * Montgomery reduction environment.  All operations use caller–supplied
 * [MutableBigInt] outputs to avoid heap allocation.
 *
 * ## Design notes
 * - The modulus [m] is immutable and must satisfy `m ≥ 1`.
 * - Internal scratch storage is owned by the context.  Because this state
 *   is mutated during reduction and exponentiation, **instances are not
 *   thread–safe** and must not be shared across threads without external
 *   synchronization.
 * - For **odd moduli**, modular exponentiation uses Montgomery arithmetic
 *   (conversion to/from the Montgomery domain plus Montgomery‐ladder style
 *   square-and-multiply).
 * - For **even moduli**, Montgomery is not applicable; the context falls
 *   back to its internal Barrett reducer.
 * - For addition, subtraction, multiplication, squaring, and inversion, the
 *   context writes directly into the caller’s [MutableBigInt] to eliminate
 *   allocation and avoid resizing in hot paths.
 *
 * ## Caller responsibilities
 * For performance reasons, the context does **not** universally normalize
 * its inputs.  It is the caller's responsibility to ensure that operands are
 * interpreted as values in the residue class modulo [m]:
 *
 * - Inputs should satisfy `0 ≤ a < m` if an operation assumes a canonical
 *   representative (e.g., `modInv` requires `0 ≤ a < m`).
 * - If values may exceed the modulus, the caller should first reduce them
 *   using [modSet] (or a field-specific normalization strategy).
 * - Negative inputs are not automatically mapped into `[0, m)` unless
 *   documented for that specific primitive overload.
 *
 * This policy is standard for high–performance modular arithmetic and avoids
 * redundant work in exponentiation, signature loops, and Lucas sequences.
 *
 * ## Supported operations
 * - Modular addition, subtraction (`modAdd`, `modSub`)
 * - Modular multiplication and squaring (`modMul`, `modSqr`)
 * - Modular exponentiation (`modPow`)
 *   - uses **Montgomery** for odd moduli
 *   - uses **Barrett** for even moduli
 * - Modular inverse via the extended Euclidean algorithm (`modInv`)
 * - Modular halving for odd moduli (`modHalfLucas`)
 *
 * ## Usage
 * ```
 * val ctx = ModContext(m)
 * val x = MutableBigInt()
 *
 * ctx.modMul(a, b, x)
 * ctx.modPow(a, e, x)
 * ctx.modInv(a, x)
 * ```
 *
 * @param m modulus for all modular operations; must be ≥ 1
 * @param useBarrettOnly do not use Montgomery, always use Barrett
 * @throws IllegalArgumentException if [m] < 1
 */
class ModContext(val m: BigInt, useBarrettOnly: Boolean = false) {
    init {
        if (m < 1)
            throw IllegalArgumentException()
    }
    val kBits = m.magnitudeBitLen()

    /**
     * Internal Barrett reduction implementation.
     */
    private val barrett = Barrett(m)
    /**
     * Internal Montgomery reduction implementation, used
     * for modPow when m is odd.
     */
    private val montgomery: Montgomery? =
        if (m.isOdd() && !useBarrettOnly) Montgomery(m) else null

    /**
     * Writes `(a mod m)` into [out], returning [out].
     *
     * Uses `[out].setMod(a, m)` to canonicalize into `[0, m)` with no allocation.
     * Suitable for pre-normalizing values before modular arithmetic.
     *
     * @param a   value to reduce
     * @param out destination receiving `(a mod m)`
     * @return [out]
     */
    fun modSet(a: BigIntNumber, out: MutableBigInt): MutableBigInt =
        out.setMod(a, m)

    /**
     * Reduces a [Long] modulo `m` into `[out]`, returning [out].
     *
     * @param a   primitive value to reduce
     * @param out destination
     * @return [out]
     */
    fun modSet(a: Int, out: MutableBigInt): MutableBigInt =
        run { out.set(a); out.setMod(out, m) }

    /**
     * Reduces a [Long] modulo `m` into `[out]`, returning [out].
     *
     * @param a   primitive value to reduce
     * @param out destination
     * @return [out]
     */
    fun modSet(a: Long, out: MutableBigInt): MutableBigInt =
        run { out.set(a); out.setMod(out, m) }

    /**
     * Computes `(a + b) mod m` into [out].
     *
     * Assumes non-negative inputs already lie in the residue range `[0, m)`;
     * this performs at most one post-addition correction (subtracting `m` once
     * if needed), not a full reduction.
     *
     * @param a first addend
     * @param b second addend
     * @param out destination for the result
     */
    fun modAdd(a: BigIntNumber, b: BigIntNumber, out: MutableBigInt) {
        out.setAdd(a, b)
        if (out >= m) out -= m
    }

    /**
     * Computes `(a + b) mod m` into [out].
     *
     * Assumes non-negative inputs already lie in the residue range `[0, m)`;
     * this performs at most one post-addition correction (subtracting `m` once
     * if needed), not a full reduction.
     *
     * @param a first addend
     * @param b second addend
     * @param out destination for the result
     */
    fun modAdd(a: BigIntNumber, b: Int, out: MutableBigInt) =
        modAdd(a, b.toLong(), out)

    /**
     * Computes `(a + b) mod m` into [out].
     *
     * Assumes non-negative inputs already lie in the residue range `[0, m)`;
     * this performs at most one post-addition correction (subtracting `m` once
     * if needed), not a full reduction.
     *
     * @param a first addend
     * @param b second addend
     * @param out destination for the result
     */
    fun modAdd(a: BigIntNumber, b: Long, out: MutableBigInt) {
        out.setAdd(a, b)
        if (out >= m) out -= m
    }

    /**
     * Computes `(a - b) mod m` into [out].
     *
     * Assumes non-negative operands already lie in the residue range `[0, m)`; this
     * performs at most one correction (adding `m` once if the raw difference is
     * negative), not a full reduction.
     *
     * @param a minuend
     * @param b subtrahend
     * @param out destination for the result
     */
    fun modSub(a: BigIntNumber, b: BigIntNumber, out: MutableBigInt) {
        out.setSub(a, b)
        if (out.isNegative()) out += m
    }

    /**
     * Computes `(a - b) mod m` into [out].
     *
     * Assumes non-negative operands already lie in the residue range `[0, m)`; this
     * performs at most one correction (adding `m` once if the raw difference is
     * negative), not a full reduction.
     *
     * @param a minuend
     * @param b subtrahend
     * @param out destination for the result
     */
    fun modSub(a: BigIntNumber, b: Int, out: MutableBigInt) =
        modSub(a, b.toLong(), out)

    /**
     * Computes `(a - b) mod m` into [out].
     *
     * Assumes non-negative operands already lie in the residue range `[0, m)`; this
     * performs at most one correction (adding `m` once if the raw difference is
     * negative), not a full reduction.
     *
     * @param a minuend
     * @param b subtrahend
     * @param out destination for the result
     */
    fun modSub(a: BigIntNumber, b: Long, out: MutableBigInt) {
        out.setSub(a, b)
        if (out.isNegative()) out += m
    }

    /**
     * Computes `(a * b) mod m` into [out].
     *
     * Assumes non-negative operands already lie in the residue range `[0, m)`; this
     * path performs full Barrett reduction rather than multiple corrective steps.
     *
     * @param a first multiplier
     * @param b second multiplier
     * @param out destination for the result
     */
    fun modMul(a: BigIntNumber, b: BigIntNumber, out: MutableBigInt) =
        barrett.modMul(a, b, out)

    /**
     * Computes `(a * b) mod m` into [out].
     *
     * Assumes non-negative operands already lie in the residue range `[0, m)`; this
     * path performs full Barrett reduction rather than multiple corrective steps.
     *
     * @param a first multiplier
     * @param b second multiplier
     * @param out destination for the result
     */
    fun modMul(a: BigIntNumber, b: Int, out: MutableBigInt) =
        barrett.modMul(a, b, out)

    /**
     * Computes `(a * b) mod m` into [out].
     *
     * Assumes non-negative operands already lie in the residue range `[0, m)`; this
     * path performs full Barrett reduction rather than multiple corrective steps.
     *
     * @param a first multiplier
     * @param b second multiplier
     * @param out destination for the result
     */
    fun modMul(a: BigIntNumber, b: Long, out: MutableBigInt) =
        barrett.modMul(a, b, out)

    /**
     * Computes `(a * a) mod m` into [out].
     *
     * Assumes a non-negative operand already in `[0, m)` and applies full
     * Barrett reduction.
     *
     * @param a value to square
     * @param out destination for the result
     */
    fun modSqr(a: BigIntNumber, out: MutableBigInt) =
        barrett.modSqr(a, out)

    /**
     * Computes `(base^exp) mod m` into [out].
     *
     * Uses Montgomery exponentiation when `m` is odd; otherwise falls back to
     * Barrett-based exponentiation. Inputs are assumed to be non-negative and
     * already reduced into `[0, m)`—no implicit normalization is performed.
     *
     * @param base base value (caller ensures `0 ≤ base < m`)
     * @param exp exponent (must be non-negative)
     * @param out destination for the result
     * @throws IllegalArgumentException if [exp] is negative
     */
    fun modPow(base: BigIntNumber, exp: BigInt, out: MutableBigInt) =
        montgomery?.modPow(base, exp, out) ?: barrett.modPow(base, exp, out)

    /**
     * Computes `(base^exp) mod m` into [out].
     *
     * Uses Montgomery exponentiation when `m` is odd; otherwise falls back to
     * Barrett-based exponentiation. Inputs are assumed to be non-negative and
     * already reduced into `[0, m)`—no implicit normalization is performed.
     *
     * @param base base value (caller ensures `0 ≤ base < m`)
     * @param exp exponent (must be non-negative)
     * @param out destination for the result
     * @throws IllegalArgumentException if [exp] is negative
     */
    fun modPow(base: BigIntNumber, exp: Int, out: MutableBigInt) =
        modPow(base, exp.toLong(), out)

    /**
     * Computes `(base^exp) mod m` into [out].
     *
     * Uses Montgomery exponentiation when `m` is odd; otherwise falls back to
     * Barrett-based exponentiation. Inputs are assumed to be non-negative and
     * already reduced into `[0, m)`—no implicit normalization is performed.
     *
     * @param base base value (caller ensures `0 ≤ base < m`)
     * @param exp exponent (must be non-negative)
     * @param out destination for the result
     * @throws IllegalArgumentException if [exp] is negative
     */
    fun modPow(base: BigIntNumber, exp: Long, out: MutableBigInt) =
        montgomery?.modPow(base, exp, out) ?: barrett.modPow(base, exp, out)

    /**
     * Computes `(a / 2) mod m` into [out], assuming an odd modulus.
     *
     * If `a` is odd, adds `m` before the shift so the result remains in `[0, m)`.
     * Caller is responsible for ensuring `0 ≤ a < m`.
     *
     * @param a input value
     * @param out destination for the result
     */
    fun modHalfLucas(a: MutableBigInt, out: MutableBigInt) =
        barrett.modHalfLucas(a, out)

    // modInv scratch for EEA Extended Euclidean Algorithm

    private var invR    = MutableBigInt.withBitCapacityHint(kBits + 1)
    private var invNewR = MutableBigInt.withBitCapacityHint(kBits + 1)
    private var invTmpR  = MutableBigInt.withBitCapacityHint(kBits + 1)
    private var invT    = MutableBigInt.withBitCapacityHint(kBits + 1)
    private var invNewT = MutableBigInt.withBitCapacityHint(kBits + 1)
    private var invTmpT  = MutableBigInt.withBitCapacityHint(kBits + 1)

    private val invQ     = MutableBigInt.withBitCapacityHint(kBits + 1)
    private val invQNewR = MutableBigInt.withBitCapacityHint(kBits + 1)
    private val invQNewT = MutableBigInt.withBitCapacityHint(kBits + 1)

    /**
     * Computes the multiplicative inverse of [a] modulo [m] and writes it into [out],
     * producing a value `x` such that `(a * x) % m == 1`.
     *
     * Requires `0 ≤ a < m`; the caller must normalize inputs beforehand.
     *
     * @param a value to invert within `[0, m)`
     * @param out destination for the inverse
     * @throws IllegalArgumentException if `a !in [0, m)`
     * @throws ArithmeticException if `gcd(a, m) ≠ 1` (no inverse exists)
     */
    fun modInv(a: BigIntNumber, out: MutableBigInt) {
        require(a >= 0 && a < m)

        invR.set(m)
        invNewR.set(a)
        invT.setZero()
        invNewT.setOne()

        while (invNewR.isNotZero()) {
            invQ.setDiv(invR, invNewR)

            invQNewR.setMul(invQ, invNewR)
            invTmpR.setSub(invR, invQNewR)
            val rotateR = invR; invR = invNewR; invNewR = invTmpR; invTmpR = rotateR

            invQNewT.setMul(invQ, invNewT)
            invTmpT.setSub(invT, invQNewT)
            val rotateT = invT; invT = invNewT; invNewT = invTmpT; invTmpT = rotateT
        }

        if (!invR.isOne())
            throw ArithmeticException("not invertible")

        if (invT.isNegative())
            invT += m
        if (invT >= m)
            invT -= m

        out.set(invT)
    }

    /**
     * Implements Barrett reduction and modular arithmetic for a fixed modulus [m].
     *
     * This class precomputes the Barrett constant `mu` and maintains internal
     * scratch buffers to perform fast, allocation-free modular reduction,
     * multiplication, squaring, exponentiation, and related operations.
     *
     * ## Notes
     * - Assumes a fixed, positive modulus `m > 1`.
     * - Uses base `b = 2^32` and limb-based arithmetic.
     * - All methods write results into caller-supplied [MutableBigInt]s.
     * - Internal scratch state makes this class **not thread-safe**.
     *
     * This class is an internal implementation detail of [ModContext].
     *
     * @param m modulus for all operations
     * @param mu precomputed Barrett reciprocal for [m]
     */
    private class Barrett(val m: BigInt,
                          val mu: BigInt
    ) {
        val mSquared = m.sqr()
        val kBits = m.magnitudeBitLen()
        val k = (kBits + 0x1F) ushr 5
        val shiftKMinus1Bits = (k - 1) * 32
        val shiftKPlus1Bits  = (k + 1) * 32
        val bPowKPlus1 = BigInt.Companion.withSetBit(shiftKPlus1Bits)

        // Initial capacities are sized by bitLen to avoid resizing in modPow hot paths
        val q = MutableBigInt.Companion.withBitCapacityHint(2*kBits + 32)
        val r1 = MutableBigInt.Companion.withBitCapacityHint(kBits + 32)
        val r2 = MutableBigInt.Companion.withBitCapacityHint(2*kBits + 32)

        val mulTmp = MutableBigInt.Companion.withBitCapacityHint(2*kBits + 32)
        val baseTmp = MutableBigInt.Companion.withBitCapacityHint(2*kBits + 32)

        companion object {

            /**
             * Creates a Barrett reducer for the given modulus [m].
             */
            operator fun invoke(m: BigInt): Barrett {
                if (m.isNegative() || m <= 1)
                    throw ArithmeticException("Barrett divisor must be >1")
                val mu = calcMu(m)
                return Barrett(m, mu)
            }

            /**
             * Computes the Barrett reciprocal `mu = floor(b^(2k) / m)`.
             */
            private fun calcMu(m: BigInt): BigInt {
                val x = BigInt.withSetBit(2 * m.meta.normLen * 32)
                val mu = x / m
                return mu
            }

        }

        /**
         * Reduces [x] modulo [m] using Barrett reduction.
         *
         * @param x non-negative value with `x < m²`
         * @param out destination accumulator for `x mod m`
         */
        fun reduceInto(x: MutableBigInt, out: MutableBigInt) {
            check(out !== q && out !== r1 && out !== r2 && out !== mulTmp)

            if (x < 0)
                println("snafu!")
            require (x >= 0)
            require (x < mSquared)
            if (x < m) {
                out.set(x)
                return
            }
            val r = out
            // q1 = floor(x / b**(k - 1))
            //val q1 = x ushr ((kLimbs - 1) * 32)
            q.set(x)
            q.mutShr(shiftKMinus1Bits)
            // q2 = q1 * mu
            //val q2 = q1 * muLimbs
            q *= mu
            // q3 = floor(q2 / b**(k + 1))
            //val q3 = q2 ushr ((kLimbs + 1) * 32)
            q.mutShr(shiftKPlus1Bits)

            // r1 = x % b**(k + 1)
            //val r1 = x and BigInt.withBitMask((kLimbs + 1) * 32)
            r1.set(x)
            r1.applyBitMask(shiftKPlus1Bits)
            // r2 = (q3 * m) % b**(k + 1)
            //val r2 = (q3 * m) and BigInt.withBitMask((kLimbs + 1) * 32)
            r2.setMul(q, m)
            r2.applyBitMask(shiftKPlus1Bits)
            //var r = r1 - r2
            r.setSub(r1, r2)
            //if (r.isNegative())
            //    r = r + BigInt.withSetBit((kLimbs + 1) * 32)
            if (r.isNegative())
                r += bPowKPlus1

            if (r >= m) r -= m
            if (r >= m) r -= m
            if (r >= m)
                throw IllegalStateException()
        }

        /**
         * Computes `(a * b) mod m`.
         */
        fun modMul(a: BigInt, b: Int, out: MutableBigInt) {
            check (out !== mulTmp)
            mulTmp.setMul(a, b)
            reduceInto(mulTmp, out)
        }

        /**
         * Computes `(a * b) mod m`.
         */
        fun modMul(a: BigIntNumber, b: BigIntNumber, out: MutableBigInt) {
            check (out !== mulTmp)
            mulTmp.setMul(a, b)
            reduceInto(mulTmp, out)
        }

        /**
         * Computes `(a * b) mod m`.
         */
        fun modMul(a: BigIntNumber, b: Long, out: MutableBigInt) {
            check (a !== mulTmp && out !== mulTmp)
            if (b != 0L) {
                mulTmp.setMul(a, b.absoluteValue.toULong())
                reduceInto(mulTmp, out)
                if (b < 0 && out.isNotZero())
                    out.setSub(m, out)
            } else {
                out.setZero()
            }
        }

        /**
         * Computes `(a * b) mod m`.
         */
        fun modMul(a: BigIntNumber, b: Int, out: MutableBigInt) {
            check (a !== mulTmp && out !== mulTmp)
            if (b != 0) {
                mulTmp.setMul(a, b.absoluteValue.toUInt())
                reduceInto(mulTmp, out)
                if (b < 0 && out.isNotZero())
                    out.setSub(m, out)
            } else {
                out.setZero()
            }
        }

        /**
         * Computes `(a * a) mod m`.
         */
        fun modSqr(a: BigIntNumber, out: MutableBigInt) {
            check (out !== mulTmp)
            mulTmp.setSqr(a)
            reduceInto(mulTmp, out)
        }

        /**
         * Computes `(base^exp) mod m` using square-and-multiply.
         *
         * Uses mutable [MutableBigInt] scratch state to minimize heap allocation.
         *
         * @param base base value
         * @param exp exponent (must be ≥ 0)
         * @param out destination accumulator for the result
         */
        fun modPow(base: BigIntNumber, exp: BigIntNumber, out: MutableBigInt) {
            if (exp < 0)
                throw IllegalArgumentException()
            out.setOne()
            if (exp.isZero())
                return
            // FIXME - why am I checking base here?
            //  should I use modSet ?
            //  is it the caller's responsibility?
            baseTmp.set(base)
            if (base >= m) {
                if (base < mSquared)
                    reduceInto(baseTmp, baseTmp)
                else
                    baseTmp.setRem(base, m)
            }
            out.set(baseTmp)
            val topBitIndex = exp.magnitudeBitLen() - 1
            for (i in topBitIndex - 1 downTo 0) {
                // result = result^2 mod m
                modSqr(out, out)

                if (exp.testBit(i))
                    modMul(out, baseTmp, out)
            }
        }

        fun modPow(base: BigIntNumber, exp: Int, out: MutableBigInt) =
            modPow(base, exp.toLong(), out)

        fun modPow(base: BigIntNumber, exp: Long, out: MutableBigInt) {
            if (exp < 0)
                throw IllegalArgumentException()
            out.setOne()
            if (exp.isZero())
                return
            baseTmp.set(base)
            if (base >= m) {
                if (base < mSquared)
                    reduceInto(baseTmp, baseTmp)
                else
                    baseTmp.setRem(base, m)
            }
            // once for the MSB
            out.set(baseTmp)

            val topBitIndex = exp.magnitudeBitLen() - 1
            for (i in topBitIndex - 1 downTo 0) {
                // result = result^2 mod m
                modSqr(out, out)

                if (exp.testBit(i))
                    modMul(out, baseTmp, out)
            }
        }

        /**
         * Computes `(a / 2) mod m` for an odd modulus.
         *
         * @param a input value
         * @param out destination accumulator for the result
         */
        fun modHalfLucas(a: BigIntNumber, out: MutableBigInt) {
            check (m.isOdd())
            if (out !== a)
                out.set(a)
            if (out.isOdd())
                out += m
            out.mutShr(1)
        }
    }

    class Montgomery(val modulus: BigInt) {
        init { require (modulus >= 1 && modulus.isOdd()) }
        val k = modulus.meta.normLen
        val np = computeNp(modulus.magia[0].toUInt())
        val r2 = BigInt.withSetBit(64*k) % modulus

        companion object {
            fun computeNp(n: UInt): UInt {
                require((n and 1u) == 1u)

                var x = (n * 3u) xor 2u  // 2 good bits
                // repeat(4) {
                //    x *= 2u - n * x      // Newton iteration mod 2^32
                //}
                x *= 2u - n * x
                x *= 2u - n * x
                x *= 2u - n * x
                x *= 2u - n * x
                return (-x.toInt()).toUInt()
            }
        }

        fun toMontgomery(x: BigIntNumber, out: MutableBigInt): MutableBigInt =
            out.setMul(x, r2).montgomeryRedc(modulus, np)

        fun fromMontgomery(xR: MutableBigInt): MutableBigInt =
            xR.montgomeryRedc(modulus, np)

        fun montMul(aR: BigIntNumber, bR: BigIntNumber, out: MutableBigInt) =
            out.setMul(aR, bR).montgomeryRedc(modulus, np)

        fun montSqr(aR: BigIntNumber, out: MutableBigInt) =
            out.setSqr(aR).montgomeryRedc(modulus, np)

        val baseR = MutableBigInt()
        val xR = MutableBigInt()

        fun modPow(base: BigIntNumber, exp: BigIntNumber, out: MutableBigInt) {
            require (! exp.isNegative())

            // Zero exponent → return 1
            if (exp.isZero()) {
                out.set(1)
                return
            }

            // Convert base → Montgomery domain
            //baseR.setMul(base, r2)
            //baseR.montgomeryRedc(modulus, np)
            toMontgomery(base, baseR)

            // xR = 1 in Montgomery space => R mod N
            toMontgomery(BigInt.ONE, xR)

            val w = calcWindowSize(exp)
            precomputeOddPowers(baseR, w)

            // Standard left-to-right binary exponentiation
            val bitLen = exp.magnitudeBitLen()
            var i = bitLen - 1

            // ---- skip initial squarings until first 1 ----
            while (i >= 0 && !exp.testBit(i)) {
                montSqr(xR, xR)
                --i
            }

            while (i >= 0) {
                // we are on a '1' bit: form a window up to width w
                val maxWidth = minOf(w, i + 1)
                var width = 1
                var window = 1  // the top bit is 1

                // absorb following bits while they exist and keep window odd
                var j = 1
                while (j < maxWidth) {
                    val bit = exp.testBit(i - j)
                    if (!bit) break
                    window = (window shl 1) or 1
                    width++
                    j++
                }

                // consume the window bits
                i -= width

                // square width times
                repeat(width) { montSqr(xR, xR) }

                // multiply by odd power
                val idx = window ushr 1   // (2k+1) → k
                montMul(xR, precomputedPowers[idx], xR)

                // now skip zeros until next 1
                while (i >= 0 && !exp.testBit(i)) {
                    montSqr(xR, xR)
                    i--
                }
            }

            // Convert result back from Montgomery
            fromMontgomery(xR)  // → Z-domain = base^exp mod N

            // Move into output
            out.set(xR)
        }

        fun modPow(base: BigIntNumber, exp: Long, out: MutableBigInt) {
            require (! exp.isNegative())

            // Zero exponent → return 1
            if (exp.isZero()) {
                out.set(1)
                return
            }

            // Convert base → Montgomery domain
            //baseR.setMul(base, r2)
            //baseR.montgomeryRedc(modulus, np)
            toMontgomery(base, baseR)

            // xR = 1 in Montgomery space => R mod N
            toMontgomery(BigInt.ONE, xR)

            // Standard left-to-right binary exponentiation
            val bitLen = exp.magnitudeBitLen()
            for (i in bitLen - 1 downTo 0) {
                // xR = xR^2 mod N  (still Montgomery)
                montSqr(xR, xR)

                if (exp.testBit(i)) {
                    // xR = xR * baseR mod N
                    montMul(xR, baseR, xR)
                }
            }

            // Convert result back from Montgomery
            fromMontgomery(xR)  // → Z-domain = base^exp mod N

            // Move into output
            out.set(xR)
        }

        fun calcWindowSize(exp: BigIntNumber): Int {
            val n = exp.magnitudeBitLen()
            return when {
                n < 128  -> 3
                n < 512  -> 4
                n < 2048 -> 5
                else     -> 6
            }
        }

        val MAX_WINDOW_WIDTH = 6
        val precomputedPowers = Array(1 shl MAX_WINDOW_WIDTH) { MutableBigInt() }

        fun precomputeOddPowers(
            baseR: BigIntNumber,
            w: Int,
        ) {
            val numOdd = 1 shl (w - 1)

            // table[0] = baseR^(1)
            precomputedPowers[0].set(baseR)

            // Compute base^2
            val baseSq = MutableBigInt()
            montMul(baseR, baseR, baseSq) // Montgomery square

            // Fill table[i] = table[i-1] * baseSq
            for (i in 1 until numOdd)
                montMul(precomputedPowers[i - 1], baseSq, precomputedPowers[i])

        }


    }
}

/**
 * with these three extension functions the bodies primitive
 * overload functions can be exact clones of the BigInt versions.
 */
private fun Long.isZero() = this == 0L
private fun Long.isNegative() = this < 0L
private fun Long.magnitudeBitLen() = 64 - this.countLeadingZeroBits()
private fun Long.testBit(bitIndex: Int) = (1L shl bitIndex) and this != 0L

