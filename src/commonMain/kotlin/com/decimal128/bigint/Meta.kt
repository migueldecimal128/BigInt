package com.decimal128.bigint

import kotlin.jvm.JvmInline

/**
 * Compact metadata for a magnitude, packing both the sign and the bit-length
 * into a single 32-bit `Int`.
 *
 * Layout (bit numbering from MSB to LSB):
 *
 *     meta = [ signBit | bitLen (31 bits) ]
 *
 * where:
 *   • signBit = 0 → non-negative
 *   • signBit = 1 → negative
 *   • bitLen  = an unsigned 31-bit magnitude bit-length (>= 0)
 *
 * This type is a `value class`, so it introduces **no runtime allocation**
 * and is represented as a raw `Int` at call sites. Packing sign and bitLen
 * together reduces parameter count and register pressure in arithmetic operations.
 *
 * Construction:
 *   Meta(signBit, bitLen) packs the MSB explicitly.
 *   Meta(signFlag, bitLen) sets the MSB via XOR with `Int.MIN_VALUE`.
 *
 * Invariants:
 *   • bitLen must be >= 0 and fit within 31 bits.
 *   • ZERO is represented by bitLen = 0 and a non-negative sign.
 *
 */
@JvmInline
value class Meta private constructor(val meta: Int) {
    companion object {

        /**
         * Creates a `Meta` value from an explicit sign bit and magnitude bit-length.
         *
         * @param signBit 0 for non-negative, 1 for negative.
         * @param bitLen  non-negative bit-length stored in the lower 31 bits.
         */
        operator fun invoke(signBit: Int, bitLen: Int): Meta {
            check ((signBit shr 1) == 0)
            check (bitLen >= 0)
            return Meta((signBit shl 31) or bitLen)
        }

        /**
         * Creates a `Meta` value from a sign flag and magnitude bit-length.
         *
         * @param signFlag true for negative
         * @param bitLen  non-negative bit-length stored in the lower 31 bits.
         */
        operator fun invoke(signFlag: Boolean, bitLen: Int): Meta {
            check (bitLen >= 0)
            return Meta(bitLen xor (if (signFlag) Int.MIN_VALUE else 0))
        }

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

    /**
     * Returns the negation of the sign with the same bitLen magnitude.
     */
    fun negate() = Meta(meta xor Int.MIN_VALUE)

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

}
