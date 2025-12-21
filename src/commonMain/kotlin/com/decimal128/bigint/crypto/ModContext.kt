package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigIntBase
import com.decimal128.bigint.MutableBigInt
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
 * - All operations write their result into a caller-supplied [MutableBigInt]
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
 * val out = MutableBigInt()
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

    private val barrett = Barrett(m)
    private val montgomery: Montgomery? =
        if (m.isOdd()) Montgomery(m) else null

    private val tmp = MutableBigInt()

    fun modSet(a: Int, out: MutableBigInt) {
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
    fun modAdd(a: BigIntBase, b: BigIntBase, out: MutableBigInt) {
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
    fun modSub(a: BigIntBase, b: BigIntBase, out: MutableBigInt) {
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
    fun modMul(a: BigIntBase, b: BigIntBase, out: MutableBigInt) =
        barrett.modMul(a, b, out)

    /**
     * Computes `(a * b) mod m`.
     *
     * @param a first multiplicand
     * @param b second multiplicand
     * @param out destination accumulator for the result
     */
    fun modMul(a: BigIntBase, b: Int, out: MutableBigInt) =
        barrett.modMul(a, b, out)

    /**
     * Computes `(a * a) mod m`.
     *
     * @param a value to square
     * @param out destination accumulator for the result
     */
    fun modSqr(a: BigIntBase, out: MutableBigInt) =
        barrett.modSqr(a, out)

    /**
     * Computes `(base^exp) mod m`.
     *
     * @param base base value
     * @param exp exponent (must be ≥ 0)
     * @param out destination accumulator for the result
     */
    fun modPow(base: BigInt, exp: BigInt, out: MutableBigInt) =
        montgomery?.modPow(base, exp, out) ?: barrett.modPow(base, exp, out)

    /**
     * Computes `(a / 2) mod m` assuming an odd modulus.
     *
     * @param a input value
     * @param out destination accumulator for the result
     */
    fun modHalfLucas(a: MutableBigInt, out: MutableBigInt) =
        barrett.modHalfLucas(a, out)

    // modInv scratch for EEA Extended Euclidean Algorithm

    private var invR    = MutableBigInt.withInitialBitCapacity(kBits + 1)
    private var invNewR = MutableBigInt.withInitialBitCapacity(kBits + 1)
    private var invTmpR  = MutableBigInt.withInitialBitCapacity(kBits + 1)
    private var invT    = MutableBigInt.withInitialBitCapacity(kBits + 1)
    private var invNewT = MutableBigInt.withInitialBitCapacity(kBits + 1)
    private var invTmpT  = MutableBigInt.withInitialBitCapacity(kBits + 1)

    private val invQ     = MutableBigInt.withInitialBitCapacity(kBits + 1)
    private val invQNewR = MutableBigInt.withInitialBitCapacity(kBits + 1)
    private val invQNewT = MutableBigInt.withInitialBitCapacity(kBits + 1)

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
    fun modInv(a: BigInt, out: MutableBigInt) {
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
        val q = MutableBigInt.Companion.withInitialBitCapacity(2*kBits + 32)
        val r1 = MutableBigInt.Companion.withInitialBitCapacity(kBits + 32)
        val r2 = MutableBigInt.Companion.withInitialBitCapacity(2*kBits + 32)

        val mulTmp = MutableBigInt.Companion.withInitialBitCapacity(2*kBits + 32)
        val baseTmp = MutableBigInt.Companion.withInitialBitCapacity(2*kBits + 32)

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
        fun modMul(a: BigIntBase, b: BigIntBase, out: MutableBigInt) {
            check (out !== mulTmp)
            mulTmp.setMul(a, b)
            reduceInto(mulTmp, out)
        }

        /**
         * Computes `(a * b) mod m`.
         */
        fun modMul(a: BigIntBase, b: Int, out: MutableBigInt) {
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
        fun modSqr(a: BigIntBase, out: MutableBigInt) {
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
        fun modPow(base: BigIntBase, exp: BigIntBase, out: MutableBigInt) {
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
        fun modHalfLucas(a: BigIntBase, out: MutableBigInt) {
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


        init { println("foo!") }
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

        fun toMontgomery(x: BigIntBase, out: MutableBigInt): MutableBigInt =
            out.setMul(x, r2).montgomeryRedc(modulus, np)

        fun fromMontgomery(xR: MutableBigInt): MutableBigInt =
            xR.montgomeryRedc(modulus, np)

        val aR = MutableBigInt()
        val bR = MutableBigInt()

        fun modMul(a: BigIntBase, b: BigIntBase, out: MutableBigInt) {
            toMontgomery(a, aR)
            toMontgomery(b, bR)
            out.setMul(aR, bR)
                .montgomeryRedc(modulus, np)
            fromMontgomery(out)
        }

        val tmp = MutableBigInt()

        fun montMul(aR: BigIntBase, bR: BigIntBase, out: MutableBigInt) {
            //out.setMul(aR, bR).montgomeryRedc(modulus, np)
            tmp.setMul(aR, bR)
            tmp.montgomeryRedc(modulus, np)
            out.set(tmp)
            }

        val baseR = MutableBigInt()
        val xR = MutableBigInt()

        fun modPow(base: BigIntBase, exp: BigIntBase, out: MutableBigInt) {
            require (! exp.isNegative())

            // Zero exponent → return 1
            if (exp.isZero()) {
                out.set(1)
                return
            }

            // Convert base → Montgomery domain

            baseR.set(base)
            baseR *= r2
            baseR %= modulus
            baseR.montgomeryRedc(modulus, np)


            // xR = 1 in Montgomery space => R mod N
            xR.set(r2)
            xR.montgomeryRedc(modulus, np) // = R mod N

            // Standard left-to-right binary exponentiation

            println("mod  = ${modulus.toBigInt()}")
            println("r2   = ${r2.toBigInt()}")
            println("baseR= ${baseR.toBigInt()}")
            println("xR   = ${xR.toBigInt()}")

            val test = MutableBigInt().also {
                montMul(xR, xR, it)   // 1_R * 1_R
            }
            println("R*R = ${test.toBigInt()}")

            // Test montMul(1_R, baseR) explicitly:
            val testMul = MutableBigInt()
            montMul(xR, baseR, testMul)
            println("montMul(1_R, baseR) = ${testMul.toBigInt()}")

            val bitLen = exp.magnitudeBitLen()
            for (i in bitLen - 1 downTo 0) {
                // xR = xR^2 mod N  (still Montgomery)
                montMul(xR, xR, xR)

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


    }
}