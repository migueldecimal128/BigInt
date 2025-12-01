package com.decimal128.bigint

import kotlin.jvm.JvmInline

/**
 * Represents a sign value encoded canonically as **0** (non-negative)
 * or **-1** (negative).
 *
 * This encoding matches the two’s-complement sign mask convention:
 * the value is either all-zero bits or all-one bits. The representation
 * is stable across all Kotlin targets and enables efficient, branch-free
 * sign manipulation.
 *
 * Instances can only be created through the fake constructor
 * `Sign(zeroOrNeg1)`, which enforces that the underlying value is
 * exactly 0 or -1.
 *
 * The type is a `value class` and therefore has no runtime allocation
 * overhead.
 */
@JvmInline
value class Sign private constructor(val zeroOrNeg1: Int) {
    companion object {

        /**
         * Fake constructor: enforces canonical sign encoding.
         *
         * @param zeroOrNeg1 must be **0** for a non-negative sign
         * or **-1** for a negative sign.
         * @throws IllegalArgumentException if the argument is not 0 or -1
         */
        operator fun invoke(zeroOrNeg1: Int): Sign {
            check (zeroOrNeg1 == 0 || zeroOrNeg1 == -1)
            return Sign(zeroOrNeg1)
        }

        operator fun invoke(isNeg: Boolean) = if (isNeg) NEGATIVE else POSITIVE

        internal val NEGATIVE: Sign
            get() = Sign(-1)

        internal val POSITIVE: Sign
            get() = Sign(0)
    }

    /**
     * The raw mask value: always **0** or **-1**.
     *
     * Intended for two's complement negation
     * `(foo xor sign.mask) - sign.mask`
     */
    val mask: Int
        get() = zeroOrNeg1

    /**
     * Returns `true` if the sign is negative (i.e. underlying value is -1).
     */
    val isNegative: Boolean
        get() = zeroOrNeg1 < 0

    /**
     * Returns `true` if the sign is positive ... or at least non-negative
     * (i.e. underlying value is 0).
     */
    val isPositive: Boolean
        get() = zeroOrNeg1 == 0

    /**
     * Returns the sign as a single bit: **0** for non-negative,
     * **1** for negative.
     *
     * Equivalent to `(zeroOrNeg1 >>> 31)`.
     */
    val bit: Int
        get() = zeroOrNeg1 ushr 31

    fun negate() = Sign(zeroOrNeg1.inv())

    fun negateIfNegative(x: Int) = (x xor zeroOrNeg1) - zeroOrNeg1

    val neg1or1: Int
        get() = zeroOrNeg1 shl 1 + 1

    infix fun xor(other: Sign) = Sign(zeroOrNeg1 xor other.zeroOrNeg1)

    infix fun xor(condition: Boolean) = Sign(zeroOrNeg1 xor (if (condition) -1 else 0))
}
