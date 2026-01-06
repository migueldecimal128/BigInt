package com.decimal128.bigint

import kotlin.math.absoluteValue
import kotlin.random.Random

// <<<< THESE PORT OVER TO MutableBigInt WITH BigInt => MutableBigInt >>>>
/**
 * Extension operators to enable arithmetic and comparison between primitive integer types
 * (`Int`, `UInt`, `Long`, `ULong`) and `BigInt`.
 *
 * These make expressions like `5 + hugeInt`, `10L * hugeInt`, or `7u % hugeInt` work naturally.
 *
 * Notes:
 * - For division and remainder, the primitive value acts as the numerator and the `BigInt`
 *   as the divisor.
 * - All operations delegate to the internal `BigInt` implementations (e.g. `addSubImpl`, `times`,
 *   `divInverse`, `modInverse`, `compareToHelper`).
 * - Division by zero throws `ArithmeticException`.
 * - Comparisons reverse the order (`a < b` calls `b.compareToHelper(...)` and negates the result)
 *   so that they produce correct signed results when a primitive appears on the left-hand side.
 */
operator fun Int.plus(other: BigInt) =
    other.addImpl32(this < 0, this.absoluteValue.toUInt())
operator fun UInt.plus(other: BigInt) =
    other.addImpl32(false, this)
operator fun Long.plus(other: BigInt) =
    other.addImpl64(signFlipThis = false, this < 0, this.absoluteValue.toULong())
operator fun ULong.plus(other: BigInt) =
    other.addImpl64(signFlipThis = false, false, this)

operator fun Int.minus(other: BigInt) =
    other.addImpl64(signFlipThis = true, this < 0, this.absoluteValue.toUInt().toULong())
operator fun UInt.minus(other: BigInt) =
    other.addImpl64(signFlipThis = true, false, this.toULong())
operator fun Long.minus(other: BigInt) =
    other.addImpl64(signFlipThis = true, this < 0L, this.absoluteValue.toULong())
operator fun ULong.minus(other: BigInt) =
    other.addImpl64(signFlipThis = true, false, this)

operator fun Int.times(other: BigInt) = other.times(this)
operator fun UInt.times(other: BigInt) = other.times(this)
operator fun Long.times(other: BigInt) = other.times(this)
operator fun ULong.times(other: BigInt) = other.times(this)

operator fun Int.div(other: BigInt) = other.divInverse(this)
operator fun UInt.div(other: BigInt) = other.divInverse(this)
operator fun Long.div(other: BigInt) = other.divInverse(this)
operator fun ULong.div(other: BigInt) = other.divInverse(false, this)

operator fun Int.rem(other: BigInt) = other.remInverse(this)
operator fun UInt.rem(other: BigInt) = other.remInverse(this)
operator fun Long.rem(other: BigInt) = other.remInverse(this)
operator fun ULong.rem(other: BigInt) = other.remInverse(false, this)

infix fun Int.mod(other: BigInt) = other.modInverse(this)
infix fun UInt.mod(other: BigInt) = other.modInverse(this)
infix fun Long.mod(other: BigInt) = other.modInverse(this)
infix fun ULong.mod(other: BigInt) = other.modInverse(false, this)


/**
 * Compares this [Int] value with a [BigInt] for numerical equality
 * with compile-time type safety.
 *
 * `EQ` represents *EQuals*, descended from a proud lineage of Fortran
 * and assembly condition mnemonics.
 *
 * @return `true` if both values are numerically equal; `false` otherwise.
 */
infix fun Int.EQ(other: BigInt): Boolean = other.compareTo(this) == 0

/**
 * Compares this [UInt] value with a [BigInt] for numerical equality
 * with compile-time type safety.
 *
 * `EQ` represents *EQuals*, descended from a proud lineage of Fortran
 * and assembly condition mnemonics.
 *
 * @return `true` if both values are numerically equal; `false` otherwise.
 */
infix fun UInt.EQ(other: BigInt): Boolean = other.compareTo(this) == 0

/**
 * Compares this [Long] value with a [BigInt] for numerical equality
 * with compile-time type safety.
 *
 * `EQ` represents *EQuals*, descended from a proud lineage of Fortran
 * and assembly condition mnemonics.
 *
 * @return `true` if both values are numerically equal; `false` otherwise.
 */
infix fun Long.EQ(other: BigInt): Boolean = other.compareTo(this) == 0

/**
 * Compares this [ULong] value with a [BigInt] for numerical equality
 * with compile-time type safety.
 *
 * `EQ` represents *EQuals*, descended from a proud lineage of Fortran
 * and assembly condition mnemonics.
 *
 * @return `true` if both values are numerically equal; `false` otherwise.
 */
infix fun ULong.EQ(other: BigInt): Boolean = other.compareTo(this) == 0


/**
 * Compares this [Int] value with a [BigInt] for numerical inequality
 * with compile-time type safety.
 *
 * `NE` represents *Not Equals*, descended from a proud lineage of Fortran
 * and assembly condition mnemonics.
 *
 * @return `true` if the values are not numerically equal; `false` otherwise.
 */
infix fun Int.NE(other: BigInt): Boolean = other.compareTo(this) != 0

/**
 * Compares this [UInt] value with a [BigInt] for numerical inequality
 * with compile-time type safety.
 *
 * `NE` represents *Not Equals*, descended from a proud lineage of Fortran
 * and assembly condition mnemonics.
 *
 * @return `true` if the values are not numerically equal; `false` otherwise.
 */
infix fun UInt.NE(other: BigInt): Boolean = other.compareTo(this) != 0

/**
 * Compares this [Long] value with a [BigInt] for numerical inequality
 * with compile-time type safety.
 *
 * `NE` represents *Not Equals*, descended from a proud lineage of Fortran
 * and assembly condition mnemonics.
 *
 * @return `true` if the values are not numerically equal; `false` otherwise.
 */
infix fun Long.NE(other: BigInt): Boolean = other.compareTo(this) != 0

/**
 * Compares this [ULong] value with a [BigInt] for numerical inequality
 * with compile-time type safety.
 *
 * `NE` represents *Not Equals*, descended from a proud lineage of Fortran
 * and assembly condition mnemonics.
 *
 * @return `true` if the values are not numerically equal; `false` otherwise.
 */
infix fun ULong.NE(other: BigInt): Boolean = other.compareTo(this) != 0

/** Converts this `Int` to a `BigInt`. */
fun Int.toBigInt() = BigInt.from(this)

/** Converts this `UInt` to a `BigInt`. */
fun UInt.toBigInt() = BigInt.from(this)

/** Converts this `Long` to a `BigInt`. */
fun Long.toBigInt() = BigInt.from(this)

/** Converts this `ULong` to a `BigInt`. */
fun ULong.toBigInt() = BigInt.from(this)

/**
 * Converts this `Double` to a `BigInt`.
 *
 * The conversion is purely numeric: the fractional part is truncated toward zero
 * and the exponent is fully expanded into an integer value.
 *
 * Special cases:
 *  * `NaN`, `+∞`, and `-∞` are converted to `BigInt.ZERO`
 *  * `+0.0` and `-0.0` both return `BigInt.ZERO`
 *
 * Example:
 *  `6.02214076E23` becomes `602214076000000000000000`.
 */
fun Double.toBigInt() = BigInt.from(this)

/** Parses this string as a `BigInt` using `BigInt.from(this)`. */
fun String.toBigInt() = BigInt.from(this)

/** Parses this CharSequence as a `BigInt` using `BigInt.from(this)`. */
fun CharSequence.toBigInt() = BigInt.from(this)

/** Parses this CharArray as a `BigInt` using `BigInt.from(this)`. */
fun CharArray.toBigInt() = BigInt.from(this)

// <<<<<<<<<<< END OF EXTENSION FUNCTIONS >>>>>>>>>>>>>>

/**
 * Returns a random `BigInt` whose magnitude is drawn uniformly from
 * the range `[0, 2^bitCount)`, i.e., each of the `bitCount` low bits
 * has an independent probability of 0.5 of being 0 or 1.
 *
 * If [withRandomSign] is `true`, the sign bit is chosen uniformly at
 * random; otherwise the result is always non-negative.
 *
 * @throws IllegalArgumentException if [bitCount] is negative.
 */
fun Random.nextBigInt(bitCount: Int, withRandomSign: Boolean = false) =
    BigInt.randomWithMaxBitLen(bitCount, this, withRandomSign)

/**
 * core extension functions
 */

fun BigInt.toMutableBigInt() = MutableBigInt(this)

fun Int.toMutableBigInt() = MutableBigInt().set(this)
fun UInt.toMutableBigInt() = MutableBigInt().set(this)
fun Long.toMutableBigInt() = MutableBigInt().set(this)
fun ULong.toMutableBigInt() = MutableBigInt().set(this)

fun String.toMutableBigInt() = this.toBigInt().toMutableBigInt()

