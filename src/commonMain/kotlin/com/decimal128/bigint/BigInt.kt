// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.BigInt.Companion.ZERO
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Arbitrary-precision signed integers for Kotlin Multiplatform, serving as a
 * high-performance lightweight replacement for [java.math.BigInteger].
 *
 * BigInt supports the standard infix arithmetic operators (`+`, `-`, `*`, `/`, `%`)
 * and comparison operators (`<`, `<=`, `==`, `!=`, `>=`, `>`), implemented via
 * Kotlin’s operator-overloading mechanisms. All arithmetic and comparison
 * operations provide overloads that accept primitive integer types
 * (`Int`, `UInt`, `Long`, `ULong`) as the other operand, enabling natural
 * expression syntax when mixing BigInt with primitive values.
 *
 * Internally, BigInt uses a sign-magnitude representation with a stored
 * normalized limb length. The canonical zero value is always non-negative.
 *
 * ### Comparison with java.math.BigInteger
 *
 * BigInt is intentionally smaller and simpler than `BigInteger`, and is
 * optimized for values on the order of hundreds of digits rather than
 * tens of thousands. Multiplication uses the schoolbook algorithm, and
 * division uses Knuth’s Algorithm D.
 *
 * BigInt also differs from `BigInteger` in its handling of bit-level and
 * boolean operations. These operations act only on the magnitude, ignore the
 * sign, and generally return non-negative results. This contrasts with
 * BigInteger’s specification:
 *
 *    _“All operations behave as if BigIntegers were represented in two’s-complement
 *    notation (like Java’s primitive integer types).”_
 *
 * ### Interoperability and Performance Considerations
 *
 * BigInt stores its magnitude as little-endian 32-bit limbs in an `IntArray`;
 * a primitive array on the JVM. The normalized limb length is stored along with
 * the sign.
 *
 * Because BigInt operator functions directly accept integer primitive operands,
 * arguments do not need to be boxed as `BigInteger`. This avoids unnecessary
 * heap allocation and eliminates the need for caches of small integer values,
 * while enabling idiomatic, readable infix arithmetic expressions.
 *
 * The companion type [BigIntAccumulator] provides mutable arbitrary-precision
 * integer support. Reuse of mutable arrays significantly reduces heap churn
 * and increases cache coherency for statistical summations on large data sets
 * and for compute-heavy crypto calculations.
 */
class BigInt private constructor(
    meta: Meta,
    magia: Magia
) : Comparable<BigInt>, BigIntBase(meta, magia) {

    companion object {
        /**
         * The canonical zero value for [BigInt].
         *
         * All representations of zero **must** reference this single instance.
         * This ensures that identity comparisons and optimizations relying on
         * reference equality (`===`) for zero values are valid.
         */
        val ZERO = BigInt(Meta(0), Mago.ZERO)

        val ONE = BigInt(Meta(1), Mago.ONE)

        val NEG_ONE = BigInt(Meta(1, 1), Mago.ONE) // share magia .. but no mutation allowed

        val TEN = BigInt(Meta(4), intArrayOf(10))

        internal operator fun invoke(sign: Boolean, magia: Magia): BigInt {
            if (magia.isEmpty()) {
                check(magia === Mago.ZERO)
                return ZERO
            }
            val signBit = if (sign) 1 else 0
            val normLen = Mago.normLen(magia)
            if (normLen == 0)
                return ZERO
            check (injectPoison(magia, normLen))
            val meta = Meta(signBit, normLen)
            return BigInt(meta, magia)
        }

        internal operator fun invoke(magia: Magia): BigInt {
            if (magia.isNotEmpty()) {
                val signBit = 0
                val meta = Meta(signBit, magia)
                if (meta.normLen > 0) {
                    check(injectPoison(magia, meta.normLen))
                    return BigInt(meta, magia)
                }
            }
            return ZERO
        }

        internal operator fun invoke(magia: Magia, normLen: Int): BigInt {
            if (magia.isNotEmpty()) {
                check(Mago.isNormalized(magia, normLen))
                val meta = Meta(0, normLen)
                if (meta.normLen > 0) {
                    check(injectPoison(magia, normLen))
                    return BigInt(meta, magia)
                }
            }
            return ZERO
        }

        internal operator fun invoke(sign: Boolean, magia: Magia, normLen: Int): BigInt {
            if (normLen > 0) {
                check (Mago.isNormalized(magia, normLen))
                val signBit = if (sign) 1 else 0
                val meta = Meta(signBit, normLen)
                check (injectPoison(magia, normLen))
                return BigInt(meta, magia)
            }
            return ZERO
        }

        internal operator fun invoke(sign: Boolean, biMagnitude: BigInt): BigInt =
            fromLittleEndianIntArray(sign, biMagnitude.magia, biMagnitude.meta.normLen)

        internal fun fromNormalizedNonZero(magia: Magia): BigInt =
            fromNormalizedNonZero(false, magia)

        internal fun fromNormalizedNonZero(sign: Boolean, magia: Magia): BigInt {
            check (magia.isNotEmpty())
            check (magia[magia.lastIndex] != 0)
            return BigInt(Meta(sign, magia.size), magia)
        }

        internal fun fromNormalizedOrZero(magia: Magia): BigInt =
            fromNormalizedOrZero(false, magia)

        internal fun fromNormalizedOrZero(sign: Boolean, magia: Magia): BigInt {
            if (magia.isEmpty()) {
                check (magia === Mago.ZERO)
                return ZERO
            }
            check (magia.isNotEmpty())
            check (magia[magia.lastIndex] != 0)
            return BigInt(Meta(sign, magia.size), magia)
        }

        internal fun fromNonNormalizedNonZero(magia: Magia): BigInt =
            fromNonNormalizedNonZero(false, magia)

        internal fun fromNonNormalizedNonZero(sign: Boolean, magia: Magia): BigInt {
            val normLen = Mago.normLen(magia)
            check (normLen > 0)
            return BigInt(Meta(sign, normLen), magia)
        }

        internal fun fromNonNormalizedOrZero(magia: Magia): BigInt =
            fromNonNormalizedOrZero(false, magia)

        internal fun fromNonNormalizedOrZero(sign: Boolean, magia: Magia): BigInt {
            val normLen = Mago.normLen(magia)
            if (normLen > 0)
                return BigInt(Meta(sign, normLen), magia)
            return ZERO
        }

        /**
         * Inject `0xDEAD` poison into high, unused limbs of a BigInt.
         *
         * Used during development and debugging to help ensure correct
         * normalization.
         *
         * Used with `assert (injectPoison(x, xNormLen)` so that it will
         * go away when one is not debugging on JVM and the equiv for
         * debug vs fast Native libraries.
         *
         * @return true so that this can be wrapped in an assert or equiv
         */
        fun injectPoison(magia: Magia, normLen: Int): Boolean {
            for (i in normLen..<magia.size)
                magia[i] = 0xDEAD
            return true
        }

        /**
         * Converts a 32-bit signed [Int] into a signed [BigInt].
         *
         * Positive values produce a non-negative (positive) [BigInt],
         * and negative values produce a [BigInt] with `sign == true`.
         * Zero values return the canonical [ZERO] instance.
         *
         * @param n the signed 32-bit integer to convert.
         * @return the corresponding [BigInt] representation.
         */
        fun from(n: Int): BigInt = when {
            // FIXME - consider a small cache of small values
            //  that is lazily filled in
            n > 0 -> fromNormalizedNonZero(intArrayOf(n))
            n < 0 -> fromNormalizedNonZero(sign=true, intArrayOf(-n))
            else -> ZERO
        }

        /**
         * Converts a 32-bit unsigned [UInt] into a non-negative [BigInt].
         *
         * The resulting value always has `sign == false`.
         * Zero returns the canonical [ZERO] instance.
         *
         * @param w the unsigned integer to convert.
         * @return the corresponding non-negative [BigInt].
         */
        fun from(w: UInt) = if (w != 0u) fromNormalizedNonZero(intArrayOf(w.toInt())) else ZERO

        /**
         * Converts a 64-bit signed [Long] into a signed [BigInt].
         *
         * Positive values produce a non-negative (positive) [BigInt],
         * and negative values produce a [BigInt] with `sign == true`.
         * Zero values return the canonical [ZERO] instance.
         *
         * @param l the signed 64-bit integer to convert.
         * @return the corresponding [BigInt] representation.
         */
        fun from(l: Long) = from (l < 0, (l.absoluteValue).toULong())

        /**
         * Converts a 64-bit unsigned [ULong] into a non-negative [BigInt].
         *
         * The resulting value always has `sign == false`.
         * Zero returns the canonical [ZERO] instance.
         *
         * @param dw the unsigned long integer to convert.
         * @return the corresponding non-negative [BigInt].
         */
        fun from(dw: ULong) = from(false, dw)

        fun from(sign: Boolean, dwMagnitude: ULong) = when {
            dwMagnitude == 0uL -> ZERO
            (dwMagnitude shr 32) == 0uL -> fromNormalizedNonZero(sign, intArrayOf(dwMagnitude.toInt()))
            else -> fromNormalizedNonZero(sign, intArrayOf(dwMagnitude.toInt(), (dwMagnitude shr 32).toInt()))
        }

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
        fun from(dbl: Double): BigInt {
            // 1 + 12 + 52 == 64
            val longBits = dbl.toBits()
            val sign = longBits < 0
            val signMask = longBits shr 63
            val biasedExp = ((longBits ushr 52) and 0x7FF).toInt()
            val exp = biasedExp - 1023
            if (exp < 0 || // +0, -0, fractional amounts
                biasedExp == 0x7FF) // fractional values + NaN + Infinity ... sorry
                return ZERO
            // we are left with finiteNonZero
            val significand53 = (longBits and ((1L shl 52) - 1L)) or (1L shl 52)
            val shift = exp - 52
            if (shift <= 0) {
                val magnitude = significand53 ushr -shift
                val l = (magnitude xor signMask) - signMask
                return from(l)
            }
            return fromULongShiftLeft(significand53.toULong(), shift, sign)
        }

        /**
         * Constructs a new [BigInt] from a [BigIntAccumulator] or another [BigInt].
         */
        fun from(other: BigIntBase): BigInt = BigInt(other.meta, other.magia.copyOf(other.meta.normLen))

        /**
         * Parses a [String] representation of an integer into a [BigInt].
         *
         * Supported syntax:
         * - Standard decimal notation, with an optional leading `'+'` or `'-'` sign.
         * - Embedded underscores (e.g. `978_654_321`) are allowed as digit separators.
         * - Hexadecimal form is supported when prefixed with `0x` or `0X`
         *   after an optional sign, e.g. `-0xDEAD_BEEF`.
         *
         * Not supported:
         * - Trailing decimal points (e.g. `123.`)
         * - Scientific notation (e.g. `6.02E23`)
         *
         * @param str the string to parse.
         * @return the parsed [BigInt].
         * @throws IllegalArgumentException if the string is empty or contains invalid characters.
         */
        fun from(str: String) =
            from(StringLatin1Iterator(str, 0, str.length))

        /**
         * Parses a substring range of a [String] into a [BigInt].
         *
         * Supports the same syntax rules as the full-string overload:
         * - Optional sign (`'+'` or `'-'`)
         * - Decimal or `0x`/`0X` hexadecimal digits
         * - Optional underscores as digit separators
         * - No fractional or scientific notation
         *
         * @param str the source string.
         * @param offset the starting index of the substring.
         * @param length the number of characters to parse.
         * @return the parsed [BigInt].
         * @throws IllegalArgumentException if the range is empty, invalid, or contains invalid characters.
         */
        fun from(str: String, offset: Int, length: Int) =
            from(StringLatin1Iterator(str, offset, length))

        /**
         * Parses a [CharSequence] representation of an integer into a [BigInt].
         *
         * Supported syntax:
         * - Standard decimal notation with optional `'+'` or `'-'`
         * - Optional hexadecimal prefix `0x` or `0X`
         * - Embedded underscores allowed as separators
         * - No fractional or scientific notation
         *
         * @param csq the character sequence to parse.
         * @return the parsed [BigInt].
         * @throws IllegalArgumentException if the input is empty or contains invalid characters.
         */
        fun from(csq: CharSequence) =
            from(CharSequenceLatin1Iterator(csq, 0, csq.length))

        /**
         * Parses a range of a [CharSequence] into a [BigInt].
         *
         * Accepts the same syntax as the full-sequence overload:
         * - Optional leading `'+'` or `'-'`
         * - Decimal or `0x`/`0X` hexadecimal digits
         * - Optional underscores as separators
         *
         * @param csq the source character sequence.
         * @param offset the index of the range to parse.
         * @param length the number of characters in the range.
         * @return the parsed [BigInt].
         * @throws IllegalArgumentException if the specified range is empty, invalid, or contains invalid characters.
         */
        fun from(csq: CharSequence, offset: Int, length: Int) =
            from(CharSequenceLatin1Iterator(csq, offset, length))

        /**
         * Parses a [CharArray] representation of an integer into a [BigInt].
         *
         * Supported syntax:
         * - Decimal or `0x`/`0X` hexadecimal notation
         * - Optional `'+'` or `'-'` sign at the start
         * - Optional underscores as separators
         * - No scientific or fractional forms
         *
         * @param chars the character array to parse.
         * @return the parsed [BigInt].
         * @throws IllegalArgumentException if the array is empty or contains invalid characters.
         */
        fun from(chars: CharArray) =
            from(CharArrayLatin1Iterator(chars, 0, chars.size))

        /**
         * Parses a range of a [CharArray] into a [BigInt].
         *
         * Uses the same syntax rules as other text-based overloads:
         * - Optional leading `'+'` or `'-'`
         * - Decimal or hexadecimal digits
         * - Underscores allowed between digits
         *
         * @param chars the source array.
         * @param offset the index of the range to parse.
         * @param length the number of characters in the range.
         * @return the parsed [BigInt].
         * @throws IllegalArgumentException if the specified range is invalid or contains invalid characters.
         */
        fun from(chars: CharArray, offset: Int, length: Int) =
            from(CharArrayLatin1Iterator(chars, offset, length))

        /**
         * Parses an ASCII/Latin-1/UTF-8 encoded [ByteArray] into a [BigInt].
         *
         * Supported syntax:
         * - Decimal or `0x`/`0X` hexadecimal digits
         * - Optional `'+'` or `'-'` sign at the start
         * - Optional underscores between digits
         *
         * The bytes must represent only ASCII digits, signs, or hexadecimal letters.
         * Multi-byte UTF-8 sequences are not supported.
         *
         * @param bytes the byte array containing ASCII-encoded digits.
         * @return the parsed [BigInt].
         * @throws IllegalArgumentException if the array is empty or contains non-ASCII bytes.
         */
        fun fromAscii(bytes: ByteArray) =
            from(ByteArrayLatin1Iterator(bytes, 0, bytes.size))

        /**
         * Parses a range of an ASCII/Latin-1/UTF-8–encoded [ByteArray] into a [BigInt].
         *
         * Uses the same syntax rules as the full-array overload:
         * - Optional `'+'` or `'-'` sign
         * - Decimal or hexadecimal digits (`0x`/`0X`)
         * - Optional underscores as separators
         *
         * Only ASCII characters are supported.
         *
         * @param bytes the source byte array.
         * @param offset the index of the range to parse.
         * @param length the number of bytes in the rang.
         * @return the parsed [BigInt].
         * @throws IllegalArgumentException if the specified range is invalid or contains non-ASCII bytes.
         */
        fun fromAscii(bytes: ByteArray, offset: Int, length: Int) =
            from(ByteArrayLatin1Iterator(bytes, offset, length))

        /**
         * Parses a hexadecimal [String] into a [BigInt].
         *
         * Supported syntax:
         * - Optional leading `'+'` or `'-'`
         * - Optional `0x` or `0X` prefix
         * - Hex digits `[0-9A-Fa-f]`
         * - Embedded underscores allowed (e.g., `DEAD_BEEF`)
         *
         * @param str the string to parse
         * @return the parsed [BigInt]
         * @throws IllegalArgumentException if the string is empty or contains invalid characters
         */
        fun fromHex(str: String) = fromHex(str, 0, str.length)


        /**
         * Parses a range of a hexadecimal [String] into a [BigInt].
         *
         * Supported syntax:
         * - Optional leading `'+'` or `'-'`
         * - Optional `0x` or `0X` prefix
         * - Hex digits `[0-9A-Fa-f]`
         * - Embedded underscores allowed
         *
         * @param str the source string
         * @param offset the starting index of the range
         * @param length the number of characters in the range
         * @return the parsed [BigInt]
         * @throws IllegalArgumentException if the range is empty, invalid, or contains invalid characters
         */
        fun fromHex(str: String, offset: Int, length: Int) =
            fromHex(StringLatin1Iterator(str, offset, length))


        /**
         * Parses a hexadecimal [CharSequence] into a [BigInt].
         *
         * Syntax rules are identical to the string overloads.
         *
         * @param csq the character sequence to parse
         * @return the parsed [BigInt]
         * @throws IllegalArgumentException if the sequence is empty or contains invalid characters
         */
        fun fromHex(csq: CharSequence) = fromHex(csq, 0, csq.length)


        /**
         * Parses a range of a hexadecimal [CharSequence] into a [BigInt].
         *
         * @param csq the source character sequence
         * @param offset the starting index of the range
         * @param length the number of characters in the range
         * @return the parsed [BigInt]
         * @throws IllegalArgumentException if the range is empty, invalid, or contains invalid characters
         */
        fun fromHex(csq: CharSequence, offset: Int, length: Int) =
            fromHex(CharSequenceLatin1Iterator(csq, offset, length))


        /**
         * Parses a hexadecimal [CharArray] into a [BigInt].
         *
         * Syntax rules are identical to the string overloads.
         *
         * @param chars the character array to parse
         * @return the parsed [BigInt]
         * @throws IllegalArgumentException if the array is empty or contains invalid characters
         */
        fun fromHex(chars: CharArray) = fromHex(chars, 0, chars.size)


        /**
         * Parses a range of a hexadecimal [CharArray] into a [BigInt].
         *
         * @param chars the source character array
         * @param offset the starting index of the range
         * @param length the number of characters in the range
         * @return the parsed [BigInt]
         * @throws IllegalArgumentException if the range is empty, invalid, or contains invalid characters
         */
        fun fromHex(chars: CharArray, offset: Int, length: Int) =
            fromHex(CharArrayLatin1Iterator(chars, offset, length))


        /**
         * Parses a UTF-8/ASCII [ByteArray] of hexadecimal characters into a [BigInt].
         *
         * Only ASCII characters are supported. Syntax rules are identical to string overloads.
         *
         * @param bytes the byte array to parse
         * @return the parsed [BigInt]
         * @throws IllegalArgumentException if the array is empty or contains non-ASCII or invalid characters
         */
        fun fromHexAscii(bytes: ByteArray) = fromHexAscii(bytes, 0, bytes.size)


        /**
         * Parses a range of a UTF-8/ASCII [ByteArray] of hexadecimal characters into a [BigInt].
         *
         * Only ASCII characters are supported. Syntax rules are identical to string overloads.
         *
         * @param bytes the source byte array
         * @param offset the starting index of the range
         * @param length the number of bytes in the range
         * @return the parsed [BigInt]
         * @throws IllegalArgumentException if the range is empty, invalid, or contains non-ASCII or invalid characters
         */
        fun fromHexAscii(bytes: ByteArray, offset: Int, length: Int) =
            fromHex(ByteArrayLatin1Iterator(bytes, offset, length))

        /**
         * Parse a BigInt thru a standard iterator for different text
         * representations.
         */
        private fun from(src: Latin1Iterator): BigInt =
            fromNonNormalizedOrZero(src.peek() == '-', BigIntParsePrint.from(src))

        /**
         * Parse a BigInt thru a standard iterator for different text
         * representations.
         */
        private fun fromHex(src: Latin1Iterator): BigInt =
            fromNormalizedOrZero(src.peek() == '-', BigIntParsePrint.fromHex(src))

        /**
         * Generates a random `BigInt` whose magnitude is uniformly sampled from
         * the range `0 .. (2^maxBitLen - 1)`.
         *
         * ## Magnitude distribution
         * Each limb is filled with uniformly random bits. For the highest limb,
         * only the lowest `maxBitLen mod 32` bits are used; if `maxBitLen` is a
         * multiple of 32, all 32 bits of the top limb are random.
         *
         * This produces a magnitude `m` satisfying:
         *
         *     0 ≤ m < 2^maxBitLen
         *
         * The actual bit length of the result may be **less than** `maxBitLen`
         * if leading bits happen to be zero. In fact, every bit position is
         * independently set with probability `1/2`.
         *
         * ## Sign handling
         * When `withRandomSign == false` (default), the result is always
         * non-negative.
         *
         * When `withRandomSign == true`, the sign of any **non-zero** result is
         * chosen uniformly from `{ +, – }`. Zero is always returned as the
         * canonical `BigInt.ZERO`, so zero occurs with **twice the probability**
         * of any particular non-zero magnitude.
         *
         * ## Parameters
         * @param maxBitLen the maximum number of bits in the generated magnitude;
         *                  must be ≥ 0.
         * @param rng the random number generator used for both magnitude and sign.
         * @param withRandomSign if `true`, allows a random sign for non-zero values;
         *                       otherwise the result is always non-negative.
         *
         * ## Returns
         * A uniformly random `BigInt` in the range `[−(2^maxBitLen−1), +(2^maxBitLen−1)]`
         * when `withRandomSign == true`, and in the range `[0, 2^maxBitLen)` otherwise.
         *
         * @throws IllegalArgumentException if `maxBitLen < 0`.
         */
        fun randomWithMaxBitLen(
            maxBitLen: Int,
            rng: Random = Random.Default,
            withRandomSign: Boolean = false
        ): BigInt {
            return when {
                maxBitLen > 0 -> {
                    var zeroTest = 0
                    val magia = Mago.newWithBitLen(maxBitLen)
                    val topBits = maxBitLen and 0x1F
                    // note parenthesization on this mask creation ... 0 - 1
                    var mask = ((-topBits ushr 31) shl topBits) - 1
                    for (i in magia.lastIndex downTo 0) {
                        val rand = rng.nextInt() and mask
                        magia[i] = rand
                        zeroTest = zeroTest or rand
                        mask = -1
                    }
                    val sign = withRandomSign && rng.nextBoolean()
                    if (zeroTest == 0) ZERO else fromNonNormalizedNonZero(sign, magia)
                }
                maxBitLen == 0 -> ZERO
                else -> throw IllegalArgumentException("bitLen must be > 0")
            }

        }

        /**
         * Generates a random `BigInt` with **exactly** the specified bit length.
         *
         * The magnitude is sampled uniformly from the range:
         *
         *     2^(bitLen - 1) ≤ m < 2^bitLen
         *
         * The most significant bit (bit `bitLen - 1`) is always set, ensuring that the
         * result has *exactly* `bitLen` bits. All lower bits are chosen independently
         * with probability 0.5, so the distribution over valid magnitudes is uniform.
         *
         * ## Sign handling
         * When `withRandomSign == false` (default), the result is always non-negative.
         *
         * When `withRandomSign == true`, the sign of the result is chosen uniformly
         * from `{ +, – }`. Zero is never produced, because the magnitude is strictly
         * positive for all `bitLen > 0`.
         *
         * ## Parameters
         * @param bitLen the exact bit length of the generated magnitude; must be ≥ 0.
         * @param rng the random number generator used for both magnitude and sign.
         * @param withRandomSign if `true`, assigns a random sign; otherwise the result
         *                       is always non-negative.
         *
         * ## Returns
         * A `BigInt` whose magnitude has exactly `bitLen` bits (or zero if `bitLen == 0`).
         *
         * @throws IllegalArgumentException if `bitLen < 0`.
         */
        fun randomWithBitLen(
            bitLen: Int,
            rng: Random = Random.Default,
            withRandomSign: Boolean = false
        ): BigInt = when {
            bitLen > 0 -> {
                val magia = Mago.newWithBitLen(bitLen)
                val topBits = bitLen and 0x1F
                var mask = ((-topBits ushr 31) shl topBits) - 1
                for (i in magia.lastIndex downTo 0) {
                    val rand = rng.nextInt() and mask
                    magia[i] = rand
                    mask = -1
                }
                val topBitIndex = bitLen - 1
                val limbIndex = topBitIndex ushr 5
                check (limbIndex == magia.size - 1)
                magia[limbIndex] = magia[limbIndex] or (1 shl (topBitIndex and 0x1F))
                val sign = withRandomSign && rng.nextBoolean()
                fromNormalizedNonZero(sign, magia)
            }
            bitLen == 0 -> ZERO
            else -> throw IllegalArgumentException("bitLen must be >= 0")
        }

        /**
         * Generates a random `BigInt` whose bit length is chosen uniformly from the
         * range `0 .. maxBitLen`.
         *
         * First, a bit length `L` is drawn uniformly from:
         *
         *     L ∈ { 0, 1, 2, …, maxBitLen }
         *
         * Then a random magnitude is generated exactly as in
         * [`randomWithMaxBitLen(L, rng, withRandomSign)`], producing a uniformly
         * random value in the range:
         *
         *     0 ≤ m < 2^L
         *
         * This yields a **log-uniform** distribution over magnitudes: smaller values
         * occur more frequently than larger values, with each possible bit length
         * equally likely.
         *
         * ## Sign handling
         * When `withRandomSign == false` (default), the result is always non-negative.
         *
         * When `withRandomSign == true`, the sign of any non-zero result is chosen
         * uniformly from `{ +, – }`. Zero is always returned as the canonical
         * `BigInt.ZERO`, so its probability reflects the chance that `L == 0` or that
         * a sampled magnitude happens to be zero.
         *
         * ## Parameters
         * @param maxBitLen the maximum possible bit length; must be ≥ 0.
         * @param rng the random number generator used for both bit-length selection
         *            and magnitude generation.
         * @param withRandomSign if `true`, assigns a random sign to non-zero values;
         *                       otherwise the result is always non-negative.
         *
         * ## Returns
         * A random `BigInt` whose bit length is uniformly chosen in
         * `0 .. maxBitLen`, and whose magnitude is uniformly distributed within
         * the corresponding power-of-two range.
         *
         * @throws IllegalArgumentException if `maxBitLen < 0`.
         */
        fun randomWithRandomBitLen(
            maxBitLen: Int,
            rng: Random = Random.Default,
            withRandomSign: Boolean = false
        ): BigInt = randomWithMaxBitLen(rng.nextInt(maxBitLen + 1), rng, withRandomSign)

        /**
         * Generates a random `BigInt` uniformly distributed in the range `0 .. max-1`.
         *
         * The function repeatedly samples random values `x` from
         * `0 .. (2^bitLen - 1)`, where `bitLen = max.magnitudeBitLen()`, until a
         * value satisfying `x < max` is obtained. This yields a uniform distribution
         * over the interval `[0, max)`.
         *
         * The sign of the result is always non-negative.
         *
         * ## Parameters
         * @param max the exclusive upper bound; must be > 0.
         * @param rnd the random number generator.
         *
         * ## Returns
         * A uniformly random `BigInt` `x` satisfying `0 ≤ x < max`.
         *
         * @throws IllegalArgumentException if `max ≤ 0`.
         */
        fun randomBelow(max: BigInt, rnd: Random = Random.Default): BigInt {
            require(max > BigInt.ZERO) {
                "max must be > 0 for randomBelow(max)"
            }
            val bitLen = max.magnitudeBitLen()
            while (true) {
                val x = randomWithMaxBitLen(bitLen, rnd)
                if (x < max) return x
            }
        }

        /**
         * Generates a random `BigInt` whose bit length is chosen uniformly from the
         * closed interval `[bitLenMin .. bitLenMax]`, and whose magnitude is uniformly
         * sampled from `0 .. (2^bitLen - 1)` once that bit length is selected.
         *
         * The magnitude is produced exactly as in the single-argument
         * `fromRandom(bitLen, ...)`: each bit is chosen independently with
         * probability 0.5, so the true bit length of the value may be smaller than
         * the selected `bitLen` if the leading sampled bits are zero.
         *
         * Zero is always returned as the unique `BigInt.ZERO` object, so—when
         * `withRandomSign == true`—zero occurs with **twice** the probability of any
         * specific non-zero magnitude (because zero never receives a sign).
         *
         * The sign behavior is identical to the fixed-bit-length overload:
         *  - When `withRandomSign == false`, the result is always non-negative.
         *  - When `withRandomSign == true`, non-zero magnitudes receive a random
         *    sign with equal probability.
         *
         * @param bitLenMin the minimum bit length (inclusive); must be ≥ 0.
         * @param bitLenMax the maximum bit length (inclusive); must be ≥ `bitLenMin`.
         * @param rng the random number generator used for selecting the bit length,
         *            magnitude, and (optionally) sign.
         * @param withRandomSign if `true`, assigns a random sign to non-zero values;
         *                       otherwise the result is always non-negative.
         * @return a random `BigInt` with a bit length selected from
         *         `[bitLenMin .. bitLenMax]`.
         * @throws IllegalArgumentException if `bitLenMin <= 0` or `bitLenMax < bitLenMin`.
         */
        fun randomWithMaxBitLen(
            bitLenMin: Int,
            bitLenMax: Int,
            rng: Random = Random.Default,
            withRandomSign: Boolean = false
        ): BigInt {
            val range = bitLenMax - bitLenMin
            if (bitLenMin < 0 || range < 0)
                throw IllegalArgumentException("invalid bitLen range: 0 <= bitLenMin <= bitLenMax")
            val bitLen = bitLenMin + rng.nextInt(range + 1)
            return randomWithMaxBitLen(bitLen, rng, withRandomSign)
        }

        /**
         * Constructs a [BigInt] from a subrange of a Big-Endian two’s-complement byte array.
         *
         * This behaves like the `java.math.BigInteger(byte[], offset, length)` pattern.
         * The sign is determined by the most significant bit of the first byte in the range.
         * An empty range returns [ZERO].
         *
         * For Little-Endian or unsigned data, use [fromBinaryBytes] directly.
         *
         * @param bytes   The source byte array.
         * @param offset  The starting index within [bytes].
         * @param length  The number of bytes to read.
         * @return The corresponding [BigInt] value.
         * @throws IllegalArgumentException if [offset] or [length] specify an invalid range.
         */
        fun fromTwosComplementBigEndianBytes(bytes: ByteArray,
                                             offset: Int = 0, length: Int = bytes.size): BigInt =
            fromBinaryBytes(isTwosComplement = true, isBigEndian = true, bytes, offset, length)

        /**
         * Creates a [BigInt] from a sequence of raw binary bytes.
         *
         * The input bytes represent either an unsigned integer or a two’s-complement
         * signed integer, depending on [isTwosComplement]. If [isTwosComplement] is `true`,
         * the most significant bit of the first byte determines the sign, and the bytes are
         * interpreted according to two’s-complement encoding. If [isTwosComplement] is `false`,
         * the bytes are treated as a non-negative unsigned value.
         *
         * The byte order is determined by [isBigEndian].
         *
         * @param isTwosComplement `true` if bytes use two’s-complement encoding, `false` if unsigned.
         * @param isBigEndian `true` if bytes are in big-endian order, `false` for little-endian.
         * @param bytes Source byte array containing the integer representation.
         * @param offset Starting offset within [bytes].
         * @param length Number of bytes to read.
         * @return A [BigInt] representing the value of the specified byte range.
         * @throws IllegalArgumentException if the range `[offset, offset + length)` is invalid.
         */
        fun fromBinaryBytes(
            isTwosComplement: Boolean, isBigEndian: Boolean,
            bytes: ByteArray, offset: Int = 0, length: Int = bytes.size
        ): BigInt {
            if (offset < 0 || length < 0 || length > bytes.size - offset)
                throw IllegalArgumentException()
            if (length > 0) {
                val ibSign = offset - 1 + (if (isBigEndian) 1 else length)
                val isNegative = isTwosComplement && bytes[ibSign] < 0
                val magia = BigIntSerde.fromBinaryBytes(
                    isNegative, isBigEndian, bytes, offset,
                    length
                )
                if (magia !== Mago.ZERO)
                    return fromNormalizedNonZero(isNegative, magia)
            }
            return ZERO
        }

        /**
         * Converts a Little-Endian IntArray to a BigInt with the specified sign.
         */
        fun fromLittleEndianIntArray(sign: Boolean, littleEndianIntArray: IntArray,
                                     len: Int = littleEndianIntArray.size): BigInt {
            if (len >= 0 && len <= littleEndianIntArray.size) {
                val normLen = Mago.normLen(littleEndianIntArray, len)
                if (normLen > 0)
                    return fromNormalizedNonZero(sign, littleEndianIntArray.copyOf(normLen))
                return ZERO
            }
            throw IllegalArgumentException()
        }

        /**
         * Constructs a positive BigInt with a single bit turned on at the zero-based bitIndex.
         *
         * The returned BigInt value will be 2**bitIndex
         *
         * @throws kotlin.IllegalArgumentException for a negative bitIndex
         */
        fun withSetBit(bitIndex: Int): BigInt {
            if (bitIndex < 0)
                throw IllegalArgumentException("negative bitIndex:$bitIndex")
            if (bitIndex == 0)
                return ONE
            val magia = Mago.newWithBitLen(bitIndex + 1)
            magia[magia.lastIndex] = 1 shl (bitIndex and 0x1F)
            return fromNormalizedNonZero(magia)
        }

        /**
         * Returns a non-negative `BigInt` whose value is a bit mask of `bitWidth`
         * consecutive 1-bits starting at bit position `bitIndex`.
         *
         * Formally, this returns:
         *
         *     (2^bitWidth - 1) << bitIndex
         *
         * which is a run of `bitWidth` ones shifted left by `bitIndex` bits.
         *
         * @param bitWidth number of consecutive 1-bits in the mask.
         * @param bitIndex index (0-based) of the least significant bit of the mask.
         *
         * @throws IllegalArgumentException if either argument is negative.
         */
        fun withBitMask(bitWidth: Int, bitIndex: Int = 0): BigInt {
            when {
                bitIndex < 0 || bitWidth < 0 ->
                    throw IllegalArgumentException(
                        "illegal negative arg bitIndex:$bitIndex bitCount:$bitWidth")
                bitWidth == 0 -> return ZERO
                bitWidth == 1 -> return withSetBit(bitIndex)
            }
            // non-zero and more than 1 bit wide
            val bitLen = bitWidth + bitIndex
            val magia = Mago.newWithBitLen(bitLen)
            val loIndex = bitIndex ushr 5
            magia.fill(-1, loIndex)
            val nlz = (magia.size shl 5) - bitLen
            magia[magia.lastIndex] = -1 ushr nlz
            val ctz = bitIndex and 0x1F
            magia[loIndex] = magia[loIndex] and (-1 shl ctz)
            return fromNormalizedNonZero(magia)
        }

        /**
         * Constructs a `BigInt` equal to `(dw << shiftLeftCount)` with the given sign.
         *
         * The 64-bit unsigned value [dw] is shifted left by [shiftLeftCount] bits to form the
         * magnitude. The result is created without intermediate normalization; the generated
         * limb array is canonical for all non-zero values. If [dw] is zero, [ZERO] is returned
         * regardless of [shiftLeftCount] or [sign].
         *
         * @param dw the unsigned 64-bit value to shift.
         * @param shiftLeftCount number of bits to shift left; must be non-negative.
         * @param sign whether the resulting value is negative.
         *
         * @return a `BigInt` representing `(dw << shiftLeftCount)` with the specified sign.
         *
         * @throws IllegalArgumentException if [shiftLeftCount] is negative.
         */
        fun fromULongShiftLeft(dw: ULong, shiftLeftCount: Int, sign: Boolean = false): BigInt {
            if (shiftLeftCount >= 0) {
                val dwBitLen = 64 - dw.countLeadingZeroBits()
                return when {
                    shiftLeftCount == 0 -> from(sign, dw)
                    dwBitLen == 0 -> ZERO
                    dwBitLen == 1 -> withSetBit(shiftLeftCount)
                    else -> {
                        val totalBitLen = dwBitLen + shiftLeftCount
                        val magia = Mago.newWithBitLen(totalBitLen)
                        val innerShift = shiftLeftCount and 0x1F
                        val lo = (dw shl innerShift).toInt()
                        if (innerShift == 0) {
                            val hi0 = (dw shr 32).toInt()
                            if (hi0 == 0) {
                                magia[magia.size - 1] = lo
                            } else {
                                magia[magia.size - 1] = hi0
                                magia[magia.size - 2] = lo
                            }
                        } else {
                            val mid = (dw shr (32 - innerShift)).toInt()
                            val hi = (dw shr (64 - innerShift)).toInt()
                            when {
                                hi != 0 -> {
                                    magia[magia.size - 1] = hi
                                    magia[magia.size - 2] = mid
                                    magia[magia.size - 3] = lo
                                }

                                mid != 0 -> {
                                    magia[magia.size - 1] = mid
                                    magia[magia.size - 2] = lo

                                }

                                else -> magia[magia.size - 1] = lo
                            }
                        }
                        fromNormalizedNonZero(sign, magia)
                    }
                }
            }
            throw IllegalArgumentException()
        }

        /**
         * Returns `n!` as a [BigInt].
         *
         * `n` must be non-negative. Uses an optimized multiplication tree and
         * fast paths for small `n`, and returns `ONE` for `n == 0` or `1`.
         */
        fun factorial(n: Int): BigInt = BigIntAlgorithms.factorial(n)

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
        fun gcd(a: BigInt, b: BigInt): BigInt = BigIntAlgorithms.gcd(a, b)

        /**
         * Returns the least common multiple (LCM) of [a] and [b].
         *
         * If either argument is zero, the result is `BigInt.ZERO`. Otherwise the LCM is
         * defined as `|a / gcd(a, b)| * |b|` and is always non-negative.
         *
         * This implementation divides the smaller magnitude by the GCD to minimize the
         * cost of multiprecision division, then multiplies by the larger magnitude.
         */
        fun lcm(a: BigInt, b: BigInt): BigInt = BigIntAlgorithms.lcm(a, b)

    }


// Note: `magia` is shared with `negate` and `abs`.
// No mutation of `magia` is allowed.

    /**
     * Java/C-style function for the absolute value of this BigInt.
     *
     * If already non-negative, returns `this`.
     *
     *@see absoluteValue
     */
    fun abs() = if (isNegative()) BigInt(meta.abs(), magia) else this

    /**
     * Kotlin-style property for the absolute value of this BigInt.
     *
     * If already non-negative, returns `this`.
     */
    public val absoluteValue: BigInt = abs()

    /**
     * Returns a BigInt with the opposite sign and the same magnitude.
     *
     * Zero always returns the singleton `BigInt.ZERO`.
     */
    fun negate() = if (isZero()) ZERO else BigInt(meta.negate(), magia)

    override fun toBigInt() = this

    /**
     * Standard plus/minus/times/div/rem operators for BigInt.
     *
     * These overloads support BigInt, Int, UInt, Long, and ULong operands.
     *
     * @return a new BigInt representing the sum or difference.
     */

    operator fun unaryMinus() = negate()
    operator fun unaryPlus() = this

    operator fun plus(other: BigInt): BigInt = this.addImpl(false, other)
    operator fun plus(n: Int): BigInt =
        this.addImpl(signFlipThis = false, n < 0, n.absoluteValue.toUInt().toULong())
    operator fun plus(w: UInt): BigInt =
        this.addImpl(signFlipThis = false, false, w.toULong())
    operator fun plus(l: Long): BigInt =
        this.addImpl(signFlipThis = false, l < 0, l.absoluteValue.toULong())
    operator fun plus(dw: ULong): BigInt =
        this.addImpl(signFlipThis = false, false, dw)

    operator fun minus(other: BigInt): BigInt = this.addImpl(true, other)
    operator fun minus(n: Int): BigInt =
        this.addImpl(signFlipThis = false, n >= 0, n.absoluteValue.toUInt().toULong())
    operator fun minus(w: UInt): BigInt =
        this.addImpl(signFlipThis = false, true, w.toULong())
    operator fun minus(l: Long): BigInt =
        this.addImpl(signFlipThis = false, l >= 0, l.absoluteValue.toULong())
    operator fun minus(dw: ULong): BigInt =
        this.addImpl(signFlipThis = false, true, dw)

    operator fun times(other: BigInt): BigInt = mulImpl(other)

    operator fun times(n: Int): BigInt = mulImpl(n < 0, n.absoluteValue.toUInt().toULong())

    operator fun times(w: UInt): BigInt = mulImpl(false, w.toULong())

    operator fun times(l: Long): BigInt = mulImpl(l < 0, l.absoluteValue.toULong())

    operator fun times(dw: ULong): BigInt = mulImpl(false, dw)

    operator fun div(other: BigInt): BigInt = divImpl(other)

    operator fun div(n: Int): BigInt = divImpl(n < 0, n.absoluteValue.toUInt().toULong())

    operator fun div(w: UInt): BigInt = divImpl(false, w.toULong())

    operator fun div(l: Long): BigInt = divImpl(l < 0, l.absoluteValue.toULong())

    operator fun div(dw: ULong): BigInt = divImpl(false, dw)

    operator fun rem(other: BigInt): BigInt = remImpl(other)

    // note that in java/kotlin, the sign of remainder only depends upon
    // the dividend, so we just take the abs value of the divisor
    operator fun rem(n: Int): BigInt = remImpl(n.absoluteValue.toUInt().toULong())

    operator fun rem(w: UInt): BigInt = remImpl(w.toULong())

    operator fun rem(l: Long): BigInt = remImpl(l.absoluteValue.toULong())

    operator fun rem(dw: ULong): BigInt = remImpl(dw)

    infix fun mod(n: Int): BigInt = mod(n.absoluteValue.toUInt())

    infix fun mod(w: UInt): BigInt {
        val rem = Mago.calcRem32(magia, meta.normLen, w)
        if (meta.isPositive || rem == 0u)
            return from(rem)
        // negative: return w - rem
        return from(w - rem)
    }

    infix fun mod(l: Long): BigInt = mod(l.absoluteValue.toULong())

    infix fun mod(dw: ULong): BigInt {
        val rem = Mago.calcRem64(magia, meta.normLen, unBuf = null, dw)
        if (meta.isPositive || rem == 0uL)
            return from(rem)
        // negative: return dw - rem
        return from(dw - rem)
    }

    infix fun mod(other: BigInt): BigInt {
        val rem = Mago.newRem(magia, meta.normLen, other.magia, other.meta.normLen)
        if (rem === Mago.ZERO)
            return ZERO
        val mod =
            if (meta.isPositive)
                rem
            else
                Mago.newSub(other.magia, other.meta.normLen, rem, Mago.normLen(rem))
        return fromNonNormalizedNonZero(mod)
    }

    /**
     * Divides the given [numerator] (primitive type) by this BigInt and returns the quotient.
     *
     * This is used for expressions like `5 / hugeInt`, where the primitive is the numerator
     * and the BigInt is the divisor.
     *
     * @param numerator the value to divide (absolute value used; see `signNumerator`)
     * @param signNumerator the sign of the numerator (ignored in primitive overloads)
     * @throws ArithmeticException if this BigInt is zero
     * @return the quotient as a BigInt, zero if |numerator| < |this|
     */
    fun divInverse(numerator: Int) = divInverse(numerator.toLong())
    fun divInverse(numerator: UInt) = divInverse(false, numerator.toULong())
    fun divInverse(numerator: Long) = divInverse(numerator < 0, numerator.absoluteValue.toULong())
    fun divInverse(signNumerator: Boolean, numerator: ULong): BigInt {
        if (this.isZero())
            throw ArithmeticException("div by zero")
        val cmp = this.magnitudeCompareTo(numerator)
        val qSign = this.meta.signFlag xor signNumerator
        return when {
            cmp > 0 -> ZERO
            cmp == 0 && qSign -> NEG_ONE
            cmp == 0 -> ONE
            else -> {
                val qMag = numerator / this.toULongMagnitude()
                from(qSign, qMag)
            }
        }
    }

    /**
     * Computes the remainder of dividing the given [numerator] (primitive type) by this BigInt.
     *
     * This is used for expressions like `5 % hugeInt`, where the primitive is the numerator
     * and the BigInt is the divisor.
     *
     * @param numerator the value to divide (absolute value used; see `signNumerator`)
     * @param signNumerator the sign of the numerator (ignored in primitive overloads)
     * @throws ArithmeticException if this BigInt is zero
     * @return the remainder as a BigInt, zero if numerator is a multiple of this BigInt
     */
    fun remInverse(numerator: Int) = remInverse(numerator.toLong())
    fun remInverse(numerator: UInt) = remInverse(false, numerator.toULong())
    fun remInverse(numerator: Long) = remInverse(numerator < 0, numerator.absoluteValue.toULong())
    fun remInverse(signNumerator: Boolean, numerator: ULong): BigInt {
        if (this.isZero())
            throw ArithmeticException("div by zero")
        val cmp = this.magnitudeCompareTo(numerator)
        return when {
            cmp > 0 -> from(signNumerator, numerator)
            cmp == 0 -> ZERO
            else -> {
                val remainder = numerator % this.toULongMagnitude()
                from(signNumerator, remainder)
            }
        }
    }

    /**
     * Computes the square of this BigInt (i.e., this * this).
     *
     * @return a non-negative BigInt representing the square, or `ZERO` if this is zero.
     */
    fun sqr(): BigInt {
        if (this.isNotZero())
            return fromNonNormalizedNonZero(Mago.newSqr(this.magia, this.meta.normLen))
        return ZERO
    }


    fun withSetBit(bitIndex: Int): BigInt = withBitOp(bitIndex, isSetOp = true)

    fun withClearBit(bitIndex: Int): BigInt = withBitOp(bitIndex, isSetOp = false)

    private fun withBitOp(bitIndex: Int, isSetOp: Boolean): BigInt {
        if (bitIndex >= 0) {
            if (! (testBit(bitIndex) xor isSetOp))
                return this
            if (isMagnitudePowerOfTwo() && magnitudeBitLen() - 1 == bitIndex) {
                // if we were setting a power of 2 then that was just
                // handled
                // this is only for clearing
                check (!isSetOp)
                return ZERO
            }
            val newBitLen = max(bitIndex + 1, Mago.bitLen(this.magia, this.meta.normLen))
            val magia = Mago.newCopyWithExactBitLen(this.magia, this.meta.normLen, newBitLen)
            val wordIndex = bitIndex ushr 5
            val isolatedBit = (1 shl (bitIndex and 0x1F))
            val limb = magia[wordIndex]
            magia[wordIndex] =
                if (isSetOp)
                    limb or isolatedBit
                else
                    limb and isolatedBit.inv()
            return fromNonNormalizedNonZero(this.meta.signFlag, magia)
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new BigInt representing the bitwise AND of the magnitudes,
     * ignoring signs.
     *
     * The result is always non-negative.
     */
    infix fun and(other: BigInt): BigInt =
        fromNormalizedOrZero(
            Mago.newAnd(this.magia, this.meta.normLen,
                other.magia, other.meta.normLen))

    /**
     * Returns a new BigInt representing the bitwise OR of the magnitudes,
     * ignoring signs.
     *
     * The result is always non-negative.
     */
    infix fun or(other: BigInt): BigInt =
        fromNormalizedOrZero(
            Mago.newOr(this.magia, this.meta.normLen,
                other.magia, other.meta.normLen))

    /**
     * Returns a new BigInt representing the bitwise XOR of the magnitudes,
     * ignoring signs.
     *
     * The result is always non-negative.
     */
    infix fun xor(other: BigInt): BigInt =
        fromNonNormalizedOrZero(
            Mago.newXor(this.magia, this.meta.normLen,
                other.magia, other.meta.normLen))

    /**
     * Performs an unsigned right shift (logical shift) of the magnitude.
     *
     * Sign is ignored and the result is always non-negative.
     *
     * @param bitCount number of bits to shift, must be >= 0
     * @throws IllegalArgumentException if bitCount < 0
     */
    infix fun ushr(bitCount: Int): BigInt {
        return when {
            bitCount > 0 ->
                fromNormalizedOrZero(Mago.newShiftRight(this.magia, this.meta.normLen, bitCount))
            bitCount == 0 -> abs()
            else -> throw IllegalArgumentException("bitCount < 0")
        }
    }

    /**
     * Performs a signed right shift (arithmetic shift) of the magnitude.
     *
     * Mimics twos-complement behavior for negative values.
     *
     * @param bitCount number of bits to shift, must be >= 0
     * @throws IllegalArgumentException if bitCount < 0
     */
    infix fun shr(bitCount: Int): BigInt {
        return when {
            bitCount > 0 -> {
                val bitLen = magnitudeBitLen()
                if (bitLen <= bitCount)
                    return if (meta.isNegative) NEG_ONE else ZERO
                val willNeedIncrement = meta.isNegative && Mago.testAnyBitInLowerN(magia, bitCount)
                var magia = Mago.newShiftRight(this.magia, this.meta.normLen, bitCount)
                check (Mago.normLen(magia) > 0)
                if (willNeedIncrement)
                    magia = Mago.newOrMutateIncrement(magia)
                return fromNormalizedNonZero(meta.signFlag, magia)
            }

            bitCount == 0 -> this
            else -> throw IllegalArgumentException("bitCount < 0")
        }
    }

    /**
     * Performs a left shift of the magnitude, retaining the sign.
     *
     * @param bitCount number of bits to shift, must be >= 0
     * @throws IllegalArgumentException if bitCount < 0
     */
    infix fun shl(bitCount: Int): BigInt {
        return when {
            isZero() || bitCount == 0 -> this
            bitCount > 0 ->
                fromNormalizedNonZero(this.meta.signFlag,
                    Mago.newShiftLeft(magia, meta.normLen, bitCount))
            else -> throw IllegalArgumentException("bitCount < 0")
        }
    }

    /**
     * Returns this value masked to the `bitWidth` consecutive bits starting at
     * `bitIndex`. Bits outside the mask range are cleared.
     *
     * The sign of the returned value is always non-negative.
     *
     * This is equivalent to:
     *
     *     result = abs(this) & ((2**bitWidth - 1) << bitIndex)
     *
     * @throws IllegalArgumentException if `bitWidth` or `bitIndex` is negative.
     */
    fun withBitMask(bitWidth: Int, bitIndex: Int = 0): BigInt {
        val myBitLen = magnitudeBitLen()
        when {
            bitIndex < 0 || bitWidth < 0 ->
                throw IllegalArgumentException(
                    "illegal negative arg bitIndex:$bitIndex bitCount:$bitWidth")
            bitWidth == 0 || bitIndex >= myBitLen -> return ZERO
            bitWidth == 1 && !testBit(bitIndex) -> return ZERO
            bitWidth == 1 -> return BigInt.withSetBit(bitIndex)
        }
        // more than 1 bit wide and some overlap
        val clampedBitLen = min(bitWidth + bitIndex, myBitLen)
        val ret = Mago.newCopyWithExactBitLen(magia, meta.normLen, clampedBitLen)
        val nlz = (ret.size shl 5) - clampedBitLen
        ret[ret.lastIndex] = ret[ret.lastIndex] and (-1 ushr nlz)
        val loIndex = bitIndex ushr 5
        ret.fill(0, 0, loIndex)
        val ctz = bitIndex and 0x1F
        ret[loIndex] = ret[loIndex] and (-1 shl ctz)
        return fromNonNormalizedOrZero(ret)
    }

    /**
     * Compares this [BigInt] with another object for numerical equality.
     *
     * Two [BigInt] instances are considered equal if they have the same sign
     * and identical magnitude arrays.
     *
     * Prefer using the infix predicates [EQ] and [NE] instead of `==` and `!=`,
     * since the `equals(Any?)` signature permits unintended comparisons with
     * unrelated types that will compile quietly but will always evaluate to
     * `false` at runtime.
     *
     * Note that for convenience `BigInt.equals(BigIntAccumulator)` is accepted
     * and will compare the values for numeric equality. However, this behavior
     * cannot break collections since [BigIntAccumulator] cannot be stored
     * in a collection. To enforce this, [BigIntAccumulator.hashCode()] throws
     * an Exception.
     *
     * @param other the object to compare against
     * @return `true` if [other] is a [BigInt] or [BigIntAccumulator] with the
     *         same numeric value; `false` otherwise
     */
    override fun equals(other: Any?): Boolean {
        return when (other) {
            is BigInt -> this EQ other
            is BigIntAccumulator -> this EQ other
            else -> false
        }
    }

    /**
     * Returns a hash code for this BigInt.
     *
     * Combines the sign and the magnitude array to ensure consistency
     * with [equals], so that equal BigInts have the same hash code.
     *
     * @return hash code of this BigInt
     */
    override fun hashCode(): Int {
        var result = meta.isNegative.hashCode()
        result = 31 * result + magnitudeHashCode()
        return result
    }

    override fun compareTo(other: BigInt): Int = super.compareTo(other)


    /**
     * Internal helper for addition or subtraction with a ULong operand.
     *
     * @param signFlipThis true to flip the sign of this BigInt before operation
     * @param otherSign the sign of the ULong operand
     * @param dw the ULong operand
     * @return a new BigInt representing the result
     */
    fun addImpl(signFlipThis: Boolean, otherSign: Boolean, dw: ULong): BigInt {
        val thisSign = this.meta.signFlag xor signFlipThis
        when {
            dw == 0uL && signFlipThis -> return this.negate()
            dw == 0uL -> return this
            this.isZero() && (dw shr 32) == 0uL->
                return BigInt(otherSign, intArrayOf(dw.toInt()))
            this.isZero() ->
                return BigInt(otherSign, intArrayOf(dw.toInt(), (dw shr 32).toInt()))
            thisSign == otherSign ->
                return BigInt(thisSign, Mago.newAdd(this.magia, this.meta.normLen, dw))
        }
        val cmp = this.magnitudeCompareTo(dw)
        val ret = when {
            cmp > 0 -> BigInt(thisSign, Mago.newSub(this.magia, this.meta.normLen, dw))
            cmp < 0 -> {
                val thisMag = this.toULongMagnitude()
                val diff = dw - thisMag
                BigInt(otherSign, Mago.newFromULong(diff))
            }
            else -> BigInt.ZERO
        }
        return ret
    }

    /**
     * Internal helper for addition or subtraction between two BigInts.
     *
     * @param isSub true to subtract [other] from this, false to add
     * @param other the BigInt operand
     * @return a new BigInt representing the result
     */
    fun addImpl(isSub: Boolean, other: BigInt): BigInt {
        val otherSign = isSub xor other.meta.isNegative
        when {
            other.isZero() -> return this
            this.isZero() && isSub -> return other.negate()
            this.isZero() -> other
            this.meta.signFlag == otherSign ->
                return BigInt(this.meta.signFlag,
                    Mago.newAdd(this.magia, this.meta.normLen,
                        other.magia, other.meta.normLen))
        }
        val cmp = this.magnitudeCompareTo(other)
        val ret = when {
            cmp > 0 -> BigInt(this.meta.signFlag,
                Mago.newSub(this.magia, this.meta.normLen, other.magia, other.meta.normLen))
            cmp < 0 -> BigInt(otherSign,
                Mago.newSub(other.magia, other.meta.normLen, this.magia, this.meta.normLen))
            else -> BigInt.ZERO
        }
        return ret
    }

    fun mulImpl(dwSign: Boolean, dw: ULong): BigInt =
        BigInt.fromNonNormalizedOrZero(this.meta.signFlag xor dwSign,
            Mago.newMul(this.magia, this.meta.normLen, dw))

    fun mulImpl(other: BigIntBase): BigInt =
        BigInt.fromNonNormalizedOrZero(meta.signFlag xor other.meta.signFlag,
            Mago.newMul(this.magia, this.meta.normLen, other.magia, other.meta.normLen))

    fun divImpl(dwSign: Boolean, dw: ULong): BigInt =
        BigInt.fromNonNormalizedOrZero(this.meta.signFlag xor dwSign,
            Mago.newDiv(magia, meta.normLen, dw))

    fun divImpl(other: BigIntBase): BigInt =
        BigInt.fromNonNormalizedOrZero(meta.signFlag xor other.meta.signFlag,
            Mago.newDiv(this.magia, this.meta.normLen, other.magia, other.meta.normLen))

    fun remImpl(dw: ULong): BigInt =
        BigInt.fromNormalizedOrZero(this.meta.signFlag,
            Mago.newRem(magia, meta.normLen, dw))

    fun remImpl(other: BigIntBase): BigInt =
        BigInt.fromNonNormalizedOrZero(meta.signFlag,
            Mago.newRem(this.magia, this.meta.normLen, other.magia, other.meta.normLen))

}

