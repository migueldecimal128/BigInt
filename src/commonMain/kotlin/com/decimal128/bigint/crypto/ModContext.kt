package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigIntAccumulator
import com.decimal128.bigint.toBigInt
import kotlin.math.absoluteValue

/**
 * Provides a reusable modular-arithmetic context for a fixed modulus [m].
 *
 * `ModContext` precomputes and caches all state required to efficiently perform
 * modular operations modulo [m], including Barrett-reduction parameters and
 * scratch buffers. It is intended for repeated operations with the same modulus,
 * such as in cryptographic algorithms (e.g. modular exponentiation, inverses,
 * Lucas sequences).
 *
 * ## Design notes
 * - The modulus [m] must be ≥ 1 and is immutable for the lifetime of the context.
 * - All operations write their result into a caller-supplied [BigIntAccumulator]
 *   to avoid heap allocation.
 * - Internal scratch accumulators are owned by the context; therefore,
 *   **instances are not thread-safe** and must not be shared across threads
 *   without external synchronization.
 * - Reduction is implemented using Barrett reduction, with capacities sized
 *   from the bit-length of [m] to avoid resizing in hot paths.
 *
 * ## Supported operations
 * - Modular addition and subtraction
 * - Modular multiplication and squaring
 * - Modular exponentiation (`modPow`)
 * - Modular inverse via the extended Euclidean algorithm (`modInv`)
 * - Modular “half” operation for odd moduli (`modHalfLucas`)
 *
 * ## Usage
 * ```
 * val ctx = ModContext(m)
 * val out = BigIntAccumulator()
 *
 * ctx.modMul(a, b, out)
 * ctx.modPow(base, exp, out)
 * ctx.modInv(a, out)
 * ```
 *
 * @param m the modulus for all operations; must be ≥ 1
 * @throws IllegalArgumentException if [m] < 1
 */
class ModContext(val m: BigInt) {
    init {
        if (m < 1)
            throw IllegalArgumentException()
    }
    val kBits = m.magnitudeBitLen()

    private val impl = Barrett(m)

    private val tmp = BigIntAccumulator()

    fun modSet(a: Int, out: BigIntAccumulator) {
        if (a >= 0 && m > a)
            out.set(a)
        else {
            tmp.set(a)
            out.setRem(tmp, m)
            if (out.isNegative())
                out += m
        }
    }

    /**
     * Computes `(a + b) mod m`.
     *
     * @param a first addend
     * @param b second addend
     * @param out destination accumulator for the result
     */
    fun modAdd(a: BigIntAccumulator, b: BigIntAccumulator, out: BigIntAccumulator) {
        out.setAdd(a, b)
        if (out >= m) out -= m
    }

    /**
     * Computes `(a - b) mod m`.
     *
     * @param a minuend
     * @param b subtrahend
     * @param out destination accumulator for the result
     */
    fun modSub(a: BigIntAccumulator, b: BigIntAccumulator, out: BigIntAccumulator) {
        out.setSub(a, b)
        if (out.isNegative()) out += m
    }

    /**
     * Computes `(a * b) mod m`.
     *
     * @param a first multiplicand
     * @param b second multiplicand
     * @param out destination accumulator for the result
     */
    fun modMul(a: BigInt, b: BigInt, out: BigIntAccumulator) =
        impl.modMul(a, b, out)

    /**
     * Computes `(a * b) mod m`.
     *
     * @param a first multiplicand
     * @param b second multiplicand
     * @param out destination accumulator for the result
     */
    fun modMul(a: BigIntAccumulator, b: Int, out: BigIntAccumulator) =
        impl.modMul(a, b, out)

    /**
     * Computes `(a * b) mod m`.
     *
     * @param a first multiplicand
     * @param b second multiplicand
     * @param out destination accumulator for the result
     */
    fun modMul(a: BigIntAccumulator, b: BigIntAccumulator, out: BigIntAccumulator) =
        impl.modMul(a, b, out)

    /**
     * Computes `(a * a) mod m`.
     *
     * @param a value to square
     * @param out destination accumulator for the result
     */
    fun modSqr(a: BigInt, out: BigIntAccumulator) =
        impl.modSqr(a, out)

    /**
     * Computes `(a * a) mod m`.
     *
     * @param a value to square
     * @param out destination accumulator for the result
     */
    fun modSqr(a: BigIntAccumulator, out: BigIntAccumulator) =
        impl.modSqr(a, out)

    /**
     * Computes `(base^exp) mod m`.
     *
     * @param base base value
     * @param exp exponent (must be ≥ 0)
     * @param out destination accumulator for the result
     */
    fun modPow(base: BigInt, exp: BigInt, out: BigIntAccumulator) =
        impl.modPow(base, exp, out)

    /**
     * Computes `(a / 2) mod m` assuming an odd modulus.
     *
     * @param a input value
     * @param out destination accumulator for the result
     */
    fun modHalfLucas(a: BigIntAccumulator, out: BigIntAccumulator) =
        impl.modHalfLucas(a, out)

    // modInv scratch for EEA Extended Euclidean Algorithm

    private var invR    = BigIntAccumulator.withInitialBitCapacity(kBits + 1)
    private var invNewR = BigIntAccumulator.withInitialBitCapacity(kBits + 1)
    private var invTmpR  = BigIntAccumulator.withInitialBitCapacity(kBits + 1)
    private var invT    = BigIntAccumulator.withInitialBitCapacity(kBits + 1)
    private var invNewT = BigIntAccumulator.withInitialBitCapacity(kBits + 1)
    private var invTmpT  = BigIntAccumulator.withInitialBitCapacity(kBits + 1)

    private val invQ     = BigIntAccumulator.withInitialBitCapacity(kBits + 1)
    private val invQNewR = BigIntAccumulator.withInitialBitCapacity(kBits + 1)
    private val invQNewT = BigIntAccumulator.withInitialBitCapacity(kBits + 1)

    /**
     * Computes the modular multiplicative inverse of [a] modulo [m].
     *
     * On success, writes `x` to [out] such that `(a * x) % m == 1`.
     * Uses an allocation-free extended Euclidean algorithm with
     * internal scratch state owned by this [ModContext].
     *
     * @param a value to invert, must satisfy `0 ≤ a < m`
     * @param out destination accumulator for the inverse
     * @throws IllegalArgumentException if `a` is out of range
     * @throws ArithmeticException if the inverse does not exist
     */
    fun modInv(a: BigInt, out: BigIntAccumulator) {
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
     * - All methods write results into caller-supplied [BigIntAccumulator]s.
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
        val q = BigIntAccumulator.Companion.withInitialBitCapacity(2*kBits + 32)
        val r1 = BigIntAccumulator.Companion.withInitialBitCapacity(kBits + 32)
        val r2 = BigIntAccumulator.Companion.withInitialBitCapacity(2*kBits + 32)

        val mulTmp = BigIntAccumulator.Companion.withInitialBitCapacity(2*kBits + 32)
        val baseTmp = BigIntAccumulator.Companion.withInitialBitCapacity(2*kBits + 32)

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
        fun reduceInto(x: BigIntAccumulator, out: BigIntAccumulator) {
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
        fun modMul(a: BigInt, b: Int, out: BigIntAccumulator) {
            check (out !== mulTmp)
            mulTmp.setMul(a, b)
            reduceInto(mulTmp, out)
        }

        /**
         * Computes `(a * b) mod m`.
         */
        fun modMul(a: BigInt, b: BigInt, out: BigIntAccumulator) {
            check (out !== mulTmp)
            mulTmp.setMul(a, b)
            reduceInto(mulTmp, out)
        }

        /**
         * Computes `(a * b) mod m`.
         */
        fun modMul(a: BigIntAccumulator, b: Int, out: BigIntAccumulator) {
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
         * Computes `(a * b) mod m`.
         */
        fun modMul(a: BigIntAccumulator, b: BigIntAccumulator, out: BigIntAccumulator) {
            check (a !== mulTmp && b !== mulTmp && out !== mulTmp)
            mulTmp.setMul(a, b)
            reduceInto(mulTmp, out)
        }

        /**
         * Computes `(a * a) mod m`.
         */
        fun modSqr(a: BigInt, out: BigIntAccumulator) {
            check (out !== mulTmp)
            mulTmp.setSqr(a)
            reduceInto(mulTmp, out)
        }

        /**
         * Computes `(a * a) mod m`.
         */
        fun modSqr(a: BigIntAccumulator, out: BigIntAccumulator) {
            check (a !== mulTmp && out !== mulTmp)
            mulTmp.setSqr(a)
            reduceInto(mulTmp, out)
        }

        /**
         * Computes `(base^exp) mod m` using square-and-multiply.
         *
         * Uses mutable [BigIntAccumulator] scratch state to minimize heap allocation.
         *
         * @param base base value
         * @param exp exponent (must be ≥ 0)
         * @param out destination accumulator for the result
         */
        fun modPow(base: BigInt, exp: BigInt, out: BigIntAccumulator) {
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
        fun modHalfLucas(a: BigIntAccumulator, out: BigIntAccumulator) {
            check (m.isOdd())
            if (out !== a)
                out.set(a)
            if (out.isOdd())
                out += m
            out.mutShr(1)
        }
    }
}