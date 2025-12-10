package com.decimal128.bigint

import kotlin.jvm.JvmInline

/**
 * Compact metadata for a magnitude, packing both the sign and the bit-length
 * into a single 32-bit `Int`.
 *
 * Layout (bit numbering from MSB to LSB):
 *
 *     meta = [ signBit | normLen (31 bits) ]
 *
 * where:
 *   • signBit = 0 → non-negative
 *   • signBit = 1 → negative
 *   • normLen  = an unsigned 31-bit magnitude normalized limb length (>= 0)
 *
 * This type is a `value class`, so it introduces **no runtime allocation**
 * and is represented as a raw `Int` at call sites. Packing sign and normLen
 * together reduces parameter count and register pressure in arithmetic operations.
 *
 * Construction:
 *   Meta(signBit, normLen) packs the MSB explicitly.
 *   Meta(signFlag, normLen) sets the MSB via XOR with `Int.MIN_VALUE`.
 *
 * Invariants:
 *   • normLen must be >= 0 and fit within 31 bits.
 *   • ZERO is represented by normLen == 0 and a non-negative sign.
 *
 */
@JvmInline
value class Meta internal constructor(val meta: Int) {
    companion object {

        /**
         * Creates a `Meta` value from an explicit sign bit and magnitude normalized limb length.
         *
         * @param signBit 0 for non-negative, 1 for negative.
         * @param normLen  non-negative normalizedLength stored in the lower 31 bits.
         */
        operator fun invoke(signBit: Int, normLen: Int): Meta {
            check ((signBit shr 1) == 0)
            check (normLen >= 0)
            return Meta(((signBit shl 31) or normLen) and (-normLen shr 31))
        }

        /**
         * Creates a `Meta` value from a sign flag and magnitude bit-length.
         *
         * @param signFlag true for negative
         * @param limbLen  non-negative limb-length stored in the lower 31 bits.
         */
        operator fun invoke(signFlag: Boolean, normLen: Int): Meta {
            check (normLen >= 0)
            return Meta(normLen xor (if (signFlag) Int.MIN_VALUE else 0))
        }

        operator fun invoke(signFlag: Boolean, x: IntArray, xLen: Int): Meta =
            Meta(signFlag, Magus.normLen(x, xLen))

        operator fun invoke(signFlag: Boolean, x: IntArray): Meta {
            var normLen = x.size
            while (normLen > 0 && x[normLen-1] == 0)
                --normLen
            return Meta(signFlag, normLen)
        }

        operator fun invoke(signBit: Int, x: IntArray): Meta {
            check ((signBit ushr 1) == 0)
            var normLen = x.size
            while (normLen > 0 && x[normLen-1] == 0)
                --normLen
            return Meta(signBit, normLen)
        }

        operator fun invoke(x: IntArray): Meta = invoke(0, x)

    }

    /** Returns `true` if the sign bit is set (i.e., the value is negative). */
    val signFlag: Boolean
        get() = meta < 0
    /**
     * Returns the sign as a single bit: **0** for non-negative,
     * **1** for negative.
     */
    val signBit: Int
        get() = meta ushr 31

    /** Sign mask: 0 for non-negative, -1 for negative.
     *
     * Useful for masking and negating.
     */
    val signMask: Int
        get() = meta shr 31

    /**
     * Returns `true` if the sign is negative.
     */
    val isNegative: Boolean
        get() = meta < 0

    /**
     * Returns `true` if the sign is positive ... or at least non-negative.
     */
    val isPositive: Boolean
        get() = meta >= 0

    val isZero: Boolean
        get() = meta == 0

    /**
     * Returns the negation of the sign with the same normLen magnitude.
     */
    fun negate() = Meta(meta xor Int.MIN_VALUE)

    /**
     * Returns a meta with non-negative sign and the same normLen magnitude.
     */
    fun abs() = Meta(meta and Int.MAX_VALUE)

    /**
     * Negates the parameter x if this `Meta` is negative.
     * Used in comparison operations.
     */
    fun negateIfNegative(x: Int) = (x xor signMask) - signMask

    /**
     * Returns the sign as -1 or 1.
     *
     * Used in comparison operations.
     */
    val signNeg1or1: Int
        get() = signMask or 1

    val normLen: Int
        get() = meta and Int.MAX_VALUE

}
