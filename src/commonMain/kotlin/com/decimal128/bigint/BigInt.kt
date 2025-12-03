// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.Sign.Companion.NEGATIVE
import com.decimal128.bigint.Sign.Companion.POSITIVE
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random
import kotlin.text.HexFormat

/**
 * Arbitrary-precision signed integers for Kotlin Multiplatform, serving as a
 * lightweight replacement for [java.math.BigInteger].
 *
 * BigInt supports the standard infix arithmetic operators (`+`, `-`, `*`, `/`, `%`)
 * and comparison operators (`<`, `<=`, `==`, `!=`, `>=`, `>`), implemented via
 * Kotlin’s operator-overloading mechanisms. All arithmetic and comparison
 * operations provide overloads that accept primitive integer types
 * (`Int`, `UInt`, `Long`, `ULong`) as the other operand, enabling natural
 * expression syntax when mixing BigInt with primitive values.
 *
 * Internally, BigInt uses a sign-magnitude representation. The canonical zero
 * value is always non-negative.
 *
 * ### Comparison with java.math.BigInteger
 *
 * BigInt is intentionally smaller and simpler than `BigInteger`, and is
 * optimized for values on the order of hundreds of digits rather than
 * tens of thousands. Multiplication uses the schoolbook algorithm, and
 * division uses Knuth’s Algorithm D. BigInt does not include the prime-number
 * generation or primality-testing utilities provided by `java.math.BigInteger`.
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
 * a primitive array on the JVM. Results of operations are not
 * forcibly normalized; the most-significant end may contain unused leading zero
 * limbs. Avoiding reallocation for strict normalization reduces heap churn and
 * is appropriate because most BigInt instances are short-lived.
 *
 * Because BigInt operator functions directly accept primitive types, arguments
 * do not need to be boxed as `BigInteger`. This avoids unnecessary heap
 * allocation and eliminates the need for caches of small integer values, while
 * enabling idiomatic, readable infix arithmetic expressions.
 *
 * The companion type [BigIntAccumulator] provides a mutable, arbitrary-precision
 * accumulator designed for repeated summation and similar in-place operations,
 * significantly reducing heap churn in accumulation-heavy workloads.
 */
class BigInt private constructor(internal val sign: Sign, internal val magia: IntArray) : Comparable<BigInt> {

    companion object {
        /**
         * The canonical zero value for [BigInt].
         *
         * All representations of zero **must** reference this single instance.
         * This ensures that identity comparisons and optimizations relying on
         * reference equality (`===`) for zero values are valid.
         */
        val ZERO = BigInt(POSITIVE, Magia.ZERO)

        val ONE = BigInt(POSITIVE, Magia.ONE)

        val NEG_ONE = BigInt(NEGATIVE, Magia.ONE) // share magia .. but no mutation allowed

        val TEN = BigInt(POSITIVE, intArrayOf(10))

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
            n > 0 -> BigInt(POSITIVE, intArrayOf(n))
            n < 0 -> BigInt(NEGATIVE, intArrayOf(-n))
            else -> ZERO
        }

        /**
         * Converts a 32-bit *unsigned* value, stored in a signed [Int] primitive,
         * into a non-negative [BigInt].
         *
         * @param n the unsigned 32-bit value (stored in an [Int]) to convert.
         * @return a non-negative [BigInt] equivalent to `n.toUInt()`.
         */
        fun fromUnsigned(n: Int) = from(n.toUInt())

        /**
         * Converts a 32-bit unsigned [UInt] into a non-negative [BigInt].
         *
         * The resulting value always has `sign == false`.
         * Zero returns the canonical [ZERO] instance.
         *
         * @param w the unsigned integer to convert.
         * @return the corresponding non-negative [BigInt].
         */
        fun from(w: UInt) = if (w != 0u) BigInt(POSITIVE, intArrayOf(w.toInt())) else ZERO

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
         * Converts a 64-bit *unsigned* value, stored in a signed [Long] primitive,
         * into a non-negative [BigInt].
         *
         * @param l the unsigned 64-bit value (stored in a [Long]) to convert.
         * @return a non-negative [BigInt] equivalent to `l.toULong()`.
         */
        fun fromUnsigned(l: Long) = from(false, l.toULong())

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
            (dwMagnitude shr 32) == 0uL -> BigInt(Sign(sign), intArrayOf(dwMagnitude.toInt()))
            else -> BigInt(Sign(sign), intArrayOf(dwMagnitude.toInt(), (dwMagnitude shr 32).toInt()))
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
        private fun from(src: Latin1Iterator): BigInt {
            val isNegative = src.peek() == '-'
            val magia = Magia.from(src)
            return if (magia.isNotEmpty()) BigInt(Sign(isNegative), magia) else ZERO
        }

        /**
         * Parse a BigInt thru a standard iterator for different text
         * representations.
         */
        private fun fromHex(src: Latin1Iterator): BigInt {
            val isNegative = src.peek() == '-'
            val magia = Magia.fromHex(src)
            return if (magia.isNotEmpty()) BigInt(Sign(isNegative), magia) else ZERO
        }

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
            if (maxBitLen > 0) {
                var zeroTest = 0
                val magia = Magia.newWithBitLen(maxBitLen)
                val topBits = maxBitLen and 0x1F
                var mask = (if (topBits == 0) 0 else (1 shl topBits)) - 1
                for (i in magia.lastIndex downTo 0) {
                    val rand = rng.nextInt() and mask
                    magia[i] = rand
                    zeroTest = zeroTest or rand
                    mask = -1
                }
                return when {
                    zeroTest == 0 -> ZERO
                    !withRandomSign -> BigInt(POSITIVE, magia)
                    else -> BigInt(Sign(rng.nextInt() shr 31), magia)
                }
            }
            if (maxBitLen == 0)
                return ZERO
            throw IllegalArgumentException("bitLen must be > 0")
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
        ): BigInt {
            if (bitLen > 0) {
                var zeroTest = 0
                val magia = Magia.newWithBitLen(bitLen)
                val topBits = bitLen and 0x1F
                var mask = (if (topBits == 0) 0 else (1 shl topBits)) - 1
                for (i in magia.lastIndex downTo 0) {
                    val rand = rng.nextInt() and mask
                    magia[i] = rand
                    zeroTest = zeroTest or rand
                    mask = -1
                }
                val topBitIndex = bitLen - 1
                val limbIndex = topBitIndex ushr 5
                check (limbIndex == magia.size - 1)
                magia[limbIndex] = magia[limbIndex] or (1 shl (topBitIndex and 0x1F))
                return if (!withRandomSign)
                    BigInt(POSITIVE, magia)
                else
                    BigInt(Sign(rng.nextInt() shr 31), magia)
            }
            if (bitLen == 0)
                return ZERO
            throw IllegalArgumentException("bitLen must be >= 0")
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
         * Constructs a [BigInt] from a Big-Endian two’s-complement byte array.
         *
         * This behaves like the `java.math.BigInteger(byte[])` constructor and is the
         * conventional external representation for signed binary integers.
         *
         * The sign is determined by the most significant bit of the first byte.
         * An empty array returns [ZERO].
         *
         * For Little-Endian or unsigned data, use [fromBinaryBytes] directly.
         *
         * @param bytes  The source byte array.
         * @return The corresponding [BigInt] value.
         */
        fun fromTwosComplementBigEndianBytes(bytes: ByteArray): BigInt =
            fromTwosComplementBigEndianBytes(bytes, 0, bytes.size)

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
        fun fromTwosComplementBigEndianBytes(bytes: ByteArray, offset: Int, length: Int): BigInt =
            fromBinaryBytes(isTwosComplement = true, isBigEndian = true, bytes, offset, length)

        /**
         * Creates a [BigInt] from an array of raw binary bytes.
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
         * @return A [BigInt] representing the value of the specified byte range.
         * @throws IllegalArgumentException if the range `[offset, offset + length)` is invalid.
         */
        fun fromBinaryBytes(
            isTwosComplement: Boolean, isBigEndian: Boolean,
            bytes: ByteArray
        ): BigInt =
            fromBinaryBytes(isTwosComplement, isBigEndian, bytes, 0, bytes.size)

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
            bytes: ByteArray, offset: Int, length: Int
        ): BigInt {
            if (offset < 0 || length < 0 || length > bytes.size - offset)
                throw IllegalArgumentException()
            if (length > 0) {
                val ibSign = offset - 1 + (if (isBigEndian) 1 else length)
                val isNegative = isTwosComplement && bytes[ibSign] < 0
                val magia = Magia.fromBinaryBytes(
                    isNegative, isBigEndian, bytes, offset,
                    length
                )
                if (magia !== Magia.ZERO)
                    return BigInt(Sign(isNegative), magia)
            }
            return ZERO
        }

        /**
         * Converts a Little-Endian IntArray to a BigInt with the specified sign.
         */
        fun fromLittleEndianIntArray(sign: Boolean, littleEndianIntArray: IntArray): BigInt =
            fromLittleEndianIntArray(sign, littleEndianIntArray, littleEndianIntArray.size)

        /**
         * Converts a Little-Endian IntArray to a BigInt with the specified sign.
         */
        fun fromLittleEndianIntArray(sign: Boolean, littleEndianIntArray: IntArray, len: Int): BigInt {
            val magia = Magia.newNormalizedCopy(littleEndianIntArray, len)
            return if (magia.isNotEmpty()) BigInt(Sign(sign), magia) else ZERO
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
            val magia = Magia.newWithBitLen(bitIndex + 1)
            magia[magia.lastIndex] = 1 shl (bitIndex and 0x1F)
            val magia2 = Magia.newWithSetBit(bitIndex)
            check (Magia.EQ(magia, magia2))
            return BigInt(POSITIVE, magia)
        }

        /**
         * Constructs a non-negative BigInt with `bitWidth` bits set to 1.
         *
         * The returned value is `2^bitWidth - 1`.
         *
         * @throws IllegalArgumentException if `bitWidth` is negative.
         */
        fun withBitMask(bitWidth: Int): BigInt {
            return when {
                bitWidth > 1 -> {
                    val magia = Magia.newWithBitLen(bitWidth)
                    magia.fill(-1)
                    val leadingZeroCount = (magia.size * 32) - bitWidth
                    magia[magia.lastIndex] = -1 ushr leadingZeroCount
                    BigInt(POSITIVE, magia)
                }

                bitWidth == 1 -> ONE
                bitWidth == 0 -> ZERO
                else -> throw IllegalArgumentException("negative bitWidth:$bitWidth")
            }
        }

        /**
         * Constructs a non-negative BigInt with `bitWidth` bits set to 1, starting at
         * the specified `bitIndex`.
         *
         * The returned value is `(2^bitWidth - 1) << bitIndex`, which is equivalent
         * to `(2^bitWidth - 1) * 2^bitIndex`.
         *
         * @param bitIndex 0-based index of the least significant bit to start from.
         * @param bitWidth number of consecutive bits to set.
         *
         * @throws IllegalArgumentException if `bitIndex` or `bitWidth` is negative.
         */
        fun withIndexedBitMask(bitIndex: Int, bitWidth: Int): BigInt = when {
            bitIndex > 0 && bitWidth > 1 -> {
                val bitLen = bitIndex + bitWidth
                val magia = Magia.newWithBitLen(bitLen)
                var wordIndex = bitIndex ushr 5
                val initialInnerIndex = bitIndex and 0x1F
                val initialBitCount = min(bitWidth, 32 - initialInnerIndex)
                val initialMask = (((1L shl initialBitCount) - 1) shl initialInnerIndex).toInt()
                magia[wordIndex++] = initialMask
                var remainingBitCount = bitWidth - initialBitCount
                if (remainingBitCount > 0) {
                    while (remainingBitCount > 32) {
                        magia[wordIndex++] = -1
                        remainingBitCount -= 32
                    }
                    if (remainingBitCount > 0)
                        magia[wordIndex] = (1 shl remainingBitCount) - 1
                }
                BigInt(POSITIVE, magia)
            }

            bitIndex < 0 || bitWidth < 0 ->
                throw IllegalArgumentException(
                    "illegal negative arg bitIndex:$bitIndex bitCount:$bitWidth"
                )

            bitIndex == 0 -> withBitMask(bitWidth)
            bitWidth == 1 -> withSetBit(bitIndex)
            else -> ZERO
        }

        fun fromULongShiftLeft(dw: ULong, shiftLeftCount: Int, sign: Boolean = false): BigInt {
            if (shiftLeftCount >= 0) {
                val dwBitLen = 64 - dw.countLeadingZeroBits()
                return when {
                    shiftLeftCount == 0 -> from(sign, dw)
                    dwBitLen == 0 -> ZERO
                    dwBitLen == 1 -> withSetBit(shiftLeftCount)
                    else -> {
                        val totalBitLen = dwBitLen + shiftLeftCount
                        val magia = Magia.newWithBitLen(totalBitLen)
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
                        BigInt(Sign(sign), magia)
                    }
                }
            }
            throw IllegalArgumentException()
        }

        fun factorial(n: Int): BigInt {
            if (n >= 0)
                return factorial(n.toUInt())
            throw IllegalArgumentException("factorial of a negative number")
        }

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
            var fLen = 2
            for (i in 21u..w) {
                Magia.setMul(f, f, fLen, i)
                fLen += if (fLen < f.size && f[fLen] != 0) 1 else 0
            }
            return BigInt(POSITIVE, f)
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
            val magia = Magia.gcd(a.magia, b.magia)
            check(magia !== Magia.ZERO)
            return BigInt(POSITIVE, magia)
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
            val gcd = Magia.gcd(a.magia, b.magia)
            val lcm = if (Magia.bitLen(a.magia) < Magia.bitLen(b.magia))
                Magia.newMul(Magia.newDiv(a.magia, gcd), b.magia)
            else
                Magia.newMul(Magia.newDiv(b.magia, gcd), a.magia)
            return BigInt(POSITIVE, lcm)
        }

        private val HEX_PREFIX_UTF8_0x = byteArrayOf('0'.code.toByte(), 'x'.code.toByte())
        private val HEX_SUFFIX_UTF8_nada = ByteArray(0)
    }

    /**
     * Returns `true` if this BigInt is zero.
     *
     * All zero values point to the singleton `BigInt.ZERO`.
     */
    fun isZero() = this === ZERO

    /**
     * Returns `true` if this BigInt is not zero.
     */
    fun isNotZero() = this !== ZERO

    /**
     * Returns `true` if this BigInt is negative.
     */
    fun isNegative() = sign.isNegative

    /**
     * Standard signum function.
     *
     * @return -1 if negative, 0 if zero, 1 if positive
     */
    fun signum() = if (sign.isNegative) -1 else if (isZero()) 0 else 1

    /**
     * Returns `true` if the magnitude of this BigInt is a power of two
     * (exactly one bit set).
     */
    fun isMagnitudePowerOfTwo(): Boolean = Magia.isPowerOfTwo(this.magia, this.magia.size)

    /**
     * Returns `true` if this value is exactly representable as a 32-bit
     * signed integer (`Int.MIN_VALUE .. Int.MAX_VALUE`).
     *
     * Only values whose magnitude fits in one 32-bit limb (or zero) pass
     * this check.
     */
    fun fitsInt(): Boolean {
        if (isZero())
            return true
        val limbLen = Magia.normalizedLimbLen(magia)
        if (limbLen > 1)
            return false
        val limb = magia[0]
        if (limb >= 0)
            return true
        return sign.isNegative && limb == Int.MIN_VALUE
    }

    /**
     * Returns `true` if this value is non-negative and fits in an unsigned
     * 32-bit integer (`0 .. UInt.MAX_VALUE`).
     */
    fun fitsUInt() = sign.isPositive && Magia.normalizedLimbLen(magia) <= 1

    /**
     * Returns `true` if this value fits in a signed 64-bit integer
     * (`Long.MIN_VALUE .. Long.MAX_VALUE`).
     */
    fun fitsLong(): Boolean {
        val limbLen = Magia.normalizedLimbLen(magia)
        return when {
            limbLen > 2 -> false
            limbLen < 2 -> true
            magia[1] >= 0 -> true
            else -> sign.isNegative && magia[1] == Int.MIN_VALUE && magia[0] == 0
        }
    }

    /**
     * Returns `true` if this value is non-negative and fits in an unsigned
     * 64-bit integer (`0 .. ULong.MAX_VALUE`).
     */
    fun fitsULong() = sign.isPositive && Magia.normalizedLimbLen(magia) <= 2

    /**
     * Returns the low 32 bits of this value, interpreted as a signed
     * two’s-complement `Int`.
     *
     * This matches the behavior of Kotlin’s built-in numeric conversions:
     * upper bits are discarded and the result wraps modulo 2³², exactly
     * like `Long.toInt()`.
     *
     * For example: `(-123).toBigInt().toInt() == -123`.
     *
     * See also: `toIntClamped()` for a range-checked conversion.
     */
    fun toInt() = if (magia.isEmpty()) 0 else (magia[0] xor sign.mask) - sign.mask

    /**
     * Returns this value as a signed `Int`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toInt], this performs a
     * strict range check instead of truncating the upper bits.
     */
    fun toIntExact(): Int =
        if (fitsInt())
            toInt()
        else
            throw ArithmeticException("BigInt out of Int range")

    /**
     * Returns this BigInt as a signed Int, clamped to `Int.MIN_VALUE..Int.MAX_VALUE`.
     *
     * Values greater than `Int.MAX_VALUE` return `Int.MAX_VALUE`.
     * Values less than `Int.MIN_VALUE` return `Int.MIN_VALUE`.
     */
    fun toIntClamped(): Int {
        val bitLen = Magia.bitLen(magia)
        if (bitLen == 0)
            return 0
        val mag = magia[0]
        return if (sign.isPositive) {
            if (bitLen <= 31) mag else Int.MAX_VALUE
        } else {
            if (bitLen <= 31) -mag else Int.MIN_VALUE
        }
    }

    /**
     * Returns the low 32 bits of this value interpreted as an unsigned
     * two’s-complement `UInt` (i.e., wraps modulo 2³², like `Long.toUInt()`).
     */
    fun toUInt() = toInt().toUInt()

    /**
     * Returns this value as a `UInt`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toUInt], this checks
     * that the value is within the unsigned 32-bit range.
     */
    fun toUIntExact(): UInt =
        if (fitsUInt())
            toUInt()
        else
            throw ArithmeticException("BigInt out of UInt range")

    /**
     * Returns this BigInt as an unsigned UInt, clamped to `0..UInt.MAX_VALUE`.
     *
     * Values greater than `UInt.MAX_VALUE` return `UInt.MAX_VALUE`.
     * Negative values return 0.
     */
    fun toUIntClamped(): UInt {
        if (sign.isPositive) {
            val bitLen = Magia.bitLen(magia)
            if (bitLen > 0) {
                val magnitude = magia[0]
                return if (bitLen <= 32) magnitude.toUInt() else UInt.MAX_VALUE
            }
        }
        return 0u
    }

    /**
     * Returns the low 64 bits of this value as a signed two’s-complement `Long`.
     *
     * The result is formed from the lowest 64 bits of the magnitude, with the
     * sign applied afterward; upper bits are discarded (wraps modulo 2⁶⁴),
     * matching `Long` conversion behavior.
     */
    fun toLong(): Long {
        val l = when {
            magia.isEmpty() -> 0L
            magia.size == 1 -> magia[0].toUInt().toLong()
            else -> (magia[1].toLong() shl 32) or magia[0].toUInt().toLong()
        }
        val mask = sign.mask.toLong()
        return (l xor mask) - mask
    }

    /**
     * Returns this value as a `Long`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toLong], this checks
     * that the value lies within the signed 64-bit range.
     */
    fun toLongExact(): Long =
        if (fitsLong())
            toLong()
        else
            throw ArithmeticException("BigInt out of Long range")

    /**
     * Returns this BigInt as a signed Long, clamped to `Long.MIN_VALUE..Long.MAX_VALUE`.
     *
     * Values greater than `Long.MAX_VALUE` return `Long.MAX_VALUE`.
     * Values less than `Long.MIN_VALUE` return `Long.MIN_VALUE`.
     */
    fun toLongClamped(): Long {
        val bitLen = Magia.bitLen(magia)
        val magnitude = when (magia.size) {
            0 -> 0L
            1 -> magia[0].toLong() and 0xFFFF_FFFFL
            else -> (magia[1].toLong() shl 32) or (magia[0].toLong() and 0xFFFF_FFFFL)
        }
        return if (sign.isPositive) {
            if (bitLen <= 63) magnitude else Long.MAX_VALUE
        } else {
            if (bitLen <= 63) -magnitude else Long.MIN_VALUE
        }
    }

    /**
     * Returns the low 64 bits of this value interpreted as an unsigned
     * two’s-complement `ULong` (wraps modulo 2⁶⁴, like `Long.toULong()`).
     */
    fun toULong(): ULong = toLong().toULong()

    /**
     * Returns this value as a `ULong`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toULong], this checks
     * that the value is within the unsigned 64-bit range.
     */
    fun toULongExact(): ULong =
        if (fitsULong())
            toULong()
        else
            throw ArithmeticException("BigInt out of ULong range")

    /**
     * Returns this BigInt as an unsigned ULong, clamped to `0..ULong.MAX_VALUE`.
     *
     * Values greater than `ULong.MAX_VALUE` return `ULong.MAX_VALUE`.
     * Negative values return 0.
     */
    fun toULongClamped(): ULong {
        if (sign.isPositive) {
            val bitLen = Magia.bitLen(magia)
            val magnitude = when (magia.size) {
                0 -> 0L
                1 -> magia[0].toLong() and 0xFFFF_FFFFL
                else -> (magia[1].toLong() shl 32) or (magia[0].toLong() and 0xFFFF_FFFFL)
            }
            return if (bitLen <= 64) magnitude.toULong() else ULong.MAX_VALUE
        }
        return 0uL
    }

    /**
     * Returns the low 32 bits of the magnitude as a `UInt`
     * (ignores the sign).
     */
    fun toUIntMagnitude() = if (magia.isEmpty()) 0 else magia[0].toUInt()

    /**
     * Returns the low 64 bits of the magnitude as a `ULong`
     * (ignores the sign).
     */
    fun toULongMagnitude(): ULong {
        return when {
            magia.isEmpty() -> 0uL
            magia.size == 1 -> magia[0].toUInt().toULong()
            else -> (magia[1].toULong() shl 32) or magia[0].toUInt().toULong()
        }
    }

    /**
     * Extracts a 64-bit unsigned value from the magnitude of this number,
     * starting at the given bit index (0 = least significant bit). Bits
     * beyond the magnitude are treated as zero.
     *
     * @throws IllegalArgumentException if `bitIndex` is negative.
     */
    fun extractULongAtBitIndex(bitIndex: Int): ULong {
        if (bitIndex >= 0)
            return Magia.extractULongAtBitIndex(magia, bitIndex)
        throw IllegalArgumentException("invalid bitIndex:$bitIndex")
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
    fun abs() = if (sign.isNegative) BigInt(POSITIVE, magia) else this

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
    fun negate() = if (isNotZero()) BigInt(sign.negate(), magia) else ZERO

    /**
     * Standard plus/minus/times/div/rem operators for BigInt.
     *
     * These overloads support BigInt, Int, UInt, Long, and ULong operands.
     *
     * @return a new BigInt representing the sum or difference.
     */

    operator fun unaryMinus() = negate()
    operator fun unaryPlus() = this

    operator fun plus(other: BigInt): BigInt = this.addSubImpl(false, other)
    operator fun plus(n: Int): BigInt =
        this.addSubImpl(signFlipThis = false, signFlipOther = false, n = n)
    operator fun plus(w: UInt): BigInt =
        this.addSubImpl(signFlipThis = false, otherSign = false, w = w)
    operator fun plus(l: Long): BigInt =
        this.addSubImpl(signFlipThis = false, signFlipOther = false, l = l)
    operator fun plus(dw: ULong): BigInt =
        this.addSubImpl(signFlipThis = false, otherSign = false, dw = dw)

    operator fun minus(other: BigInt): BigInt = this.addSubImpl(true, other)
    operator fun minus(n: Int): BigInt =
        this.addSubImpl(signFlipThis = false, signFlipOther = true, n = n)
    operator fun minus(w: UInt): BigInt =
        this.addSubImpl(signFlipThis = false, otherSign = true, w = w)
    operator fun minus(l: Long): BigInt =
        this.addSubImpl(signFlipThis = false, signFlipOther = true, l = l)
    operator fun minus(dw: ULong): BigInt =
        this.addSubImpl(signFlipThis = false, otherSign = true, dw = dw)

    operator fun times(other: BigInt): BigInt {
        return if (isNotZero() && other.isNotZero())
            BigInt(sign xor other.sign, Magia.newMul(this.magia, other.magia))
        else
            ZERO
    }

    operator fun times(n: Int): BigInt {
        return if (isNotZero() && n != 0)
            BigInt(sign xor (n < 0), Magia.newMul(this.magia, n.absoluteValue.toUInt()))
        else
            ZERO
    }

    operator fun times(w: UInt): BigInt {
        return if (isNotZero() && w != 0u)
            BigInt(this.sign, Magia.newMul(this.magia, w))
        else
            ZERO
    }

    operator fun times(l: Long): BigInt {
        return if (isNotZero() && l != 0L)
            BigInt(this.sign xor (l < 0), Magia.newMul(this.magia, l.absoluteValue.toULong()))
        else
            ZERO
    }

    operator fun times(dw: ULong): BigInt {
        return if (isNotZero() && dw != 0uL)
            BigInt(this.sign, Magia.newMul(this.magia, dw))
        else
            ZERO
    }

    operator fun div(other: BigInt): BigInt {
        if (other.isZero())
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val quot = Magia.newDiv(this.magia, other.magia)
            if (quot.isNotEmpty())
                return BigInt(this.sign xor other.sign, quot)
        }
        return ZERO
    }

    operator fun div(n: Int): BigInt {
        if (n == 0)
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val quot = Magia.newDiv(this.magia, n.absoluteValue.toUInt())
            if (quot.isNotEmpty())
                return BigInt(this.sign xor (n < 0), quot)
        }
        return ZERO
    }

    operator fun div(w: UInt): BigInt {
        if (w == 0u)
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val quot = Magia.newDiv(this.magia, w)
            if (quot.isNotEmpty())
                return BigInt(this.sign, quot)
        }
        return ZERO
    }

    operator fun div(l: Long): BigInt {
        if (l == 0L)
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val quot = Magia.newDiv(this.magia, l.absoluteValue.toULong())
            if (quot.isNotEmpty())
                return BigInt(this.sign xor (l < 0), quot)
        }
        return ZERO
    }

    operator fun div(dw: ULong): BigInt {
        if (dw == 0uL)
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val quot = Magia.newDiv(this.magia, dw)
            if (quot.isNotEmpty())
                return BigInt(this.sign, quot)
        }
        return ZERO
    }

    operator fun rem(other: BigInt): BigInt {
        if (other.isZero())
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val rem = Magia.newMod(this.magia, other.magia)
            if (rem.isNotEmpty())
                return BigInt(this.sign, rem)
        }
        return ZERO
    }

    // note that in java/kotlin, the sign of remainder only depends upon
    // the dividend, so we just take the abs value of the divisor
    operator fun rem(n: Int): BigInt = rem(n.absoluteValue.toUInt())

    operator fun rem(w: UInt): BigInt {
        if (w == 0u)
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val rem = Magia.newMod(this.magia, w)
            if (rem.isNotEmpty())
                return BigInt(this.sign, rem)
        }
        return ZERO
    }

    operator fun rem(l: Long): BigInt = rem(l.absoluteValue.toULong())

    operator fun rem(dw: ULong): BigInt {
        if (dw == 0uL)
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val rem = Magia.newMod(this.magia, dw)
            if (rem.isNotEmpty())
                return BigInt(this.sign, rem)
        }
        return ZERO
    }

    /**
     * Perform an integer division, returning quotient and remainder.
     *
     * @return Pair<quotient: BigInt, remainder: BigInt>
     */
    fun divRem(other: BigInt): Pair<BigInt, BigInt> {
        return when {
            other.isZero() -> throw ArithmeticException("div by zero")
            this.isNotZero() -> divModHelper(other.sign, other.magia)
            else -> ZERO to other
        }
    }

    /** @see divRem(BigInt) */
    fun divRem(n: Int): Pair<BigInt, BigInt> = divModUIntHelper(n < 0, n.absoluteValue.toUInt())
    fun divRem(w: UInt): Pair<BigInt, BigInt> = divModUIntHelper(false, w)
    fun divRem(l: Long): Pair<BigInt, BigInt> = divModULongHelper(l < 0, l.absoluteValue.toULong())
    fun divRem(dw: ULong): Pair<BigInt, BigInt> = divModULongHelper(false, dw)

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
        if (this.magnitudeCompareTo(numerator) > 0)
            return ZERO
        val quotient = numerator / this.toULongMagnitude()
        if (quotient == 0uL)
            return ZERO
        else
            return BigInt(
                this.sign xor signNumerator,
                Magia.newFromULong(quotient)
            )
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
        if (this.magnitudeCompareTo(numerator) > 0)
            return BigInt(Sign(signNumerator), Magia.newFromULong(numerator))
        val remainder = numerator % this.toULongMagnitude()
        if (remainder == 0uL)
            return ZERO
        else
            return BigInt(Sign(signNumerator), Magia.newFromULong(remainder))
    }

    /**
     * Computes the square of this BigInt (i.e., this * this).
     *
     * @return a non-negative BigInt representing the square, or `ZERO` if this is zero.
     */
    fun sqr(): BigInt {
        if (this.isNotZero()) {
            check(Magia.normalizedLimbLen(this.magia) > 0)
            val magiaSqr = Magia.newSqr(this.magia)
            return BigInt(POSITIVE, magiaSqr)
        }
        return ZERO
    }

    /**
     * Raises this BigInt to the power of [n].
     *
     * Special cases are handled for efficiency:
     * - `n == 0` → returns `ONE`
     * - `n == 1` → returns `this`
     * - `n == 2` → uses `sqr()` for efficiency
     * - Magnitude of `1` or `2` → uses precomputed results
     *
     * @param n exponent, must be >= 0
     * @throws IllegalArgumentException if `n < 0`
     * @return BigInt representing this^n with correct sign
     */
    fun pow(n: Int): BigInt {
        val resultSign = this.sign.isNegative && ((n and 1) != 0)
        return when {
            n < 0 -> throw IllegalArgumentException("cannot raise BigInt to negative power:$n")
            n == 0 -> ONE
            n == 1 -> this
            this.isZero() -> ZERO
            Magia.EQ(this.magia, 1) -> if (resultSign) NEG_ONE else ONE
            Magia.EQ(this.magia, 2) -> BigInt(Sign(resultSign), Magia.newWithSetBit(n))
            n == 2 -> sqr()
            else -> {
                val maxBitLen = Magia.bitLen(this.magia) * n
                val maxBitLimbLen = (maxBitLen + 0x1F) ushr 5
                var baseMag = Magia.newCopyWithExactLimbLen(this.magia, maxBitLimbLen)
                var baseLen = Magia.normalizedLimbLen(this.magia)
                var resultMag = IntArray(maxBitLimbLen)
                resultMag[0] = 1
                var resultLen = 1
                var tmpMag = IntArray(maxBitLimbLen)

                var exp = n
                while (true) {
                    if ((exp and 1) != 0) {
                        tmpMag.fill(0, 0, baseLen)
                        resultLen = Magia.setMul(tmpMag, resultMag, resultLen, baseMag, baseLen)
                        val t = tmpMag
                        tmpMag = resultMag
                        resultMag = t
                    }
                    exp = exp ushr 1
                    if (exp == 0)
                        break
                    tmpMag.fill(0, 0, min(tmpMag.size, 2 * baseLen))
                    baseLen = Magia.setSqr(tmpMag, baseMag, baseLen)
                    val t = tmpMag
                    tmpMag = baseMag
                    baseMag = t
                }
                BigInt(Sign(resultSign), resultMag)
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
    fun isqrt(): BigInt {
        if (sign.isNegative)
            throw ArithmeticException("Square root of a negative BigInt")
        val bitLen = Magia.bitLen(magia)
        if (bitLen <= 53) {
            return when {
                bitLen == 0 -> ZERO
                bitLen == 1 -> ONE
                else -> {
                    val dw = Magia.toRawULong(magia)
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
        val top = Magia.extractULongAtBitIndex(magia, topBitsIndex) + 1uL + 1uL
        // a single check to ensure that the initial isqrt estimate >= the actual isqrt
        var topSqrt = ceil(sqrt(top.toDouble())).toULong()
        val crossCheck = topSqrt * topSqrt
        topSqrt += (crossCheck - top) shr 63 // add 1 iff crossCheck < top

        var x = Magia.newWithUIntAtBitIndex(topSqrt.toUInt(), topBitsIndex shr 1)
        var xPrev: IntArray
        do {
            xPrev = x
            x = Magia.newDiv(this.magia, xPrev)
            val carry = Magia.mutateAdd(x, xPrev)
            Magia.mutateShiftRight(x, x.size, 1)
            x[x.size - 1] = x[x.size - 1] or (carry shl 31).toInt()
        } while (Magia.compare(x, xPrev) < 0)
        return BigInt(POSITIVE, xPrev)
    }

    /**
     * Returns the bit-length of the magnitude of this BigInt.
     *
     * Equivalent to the number of bits required to represent the absolute value.
     */
    fun magnitudeBitLen() = Magia.bitLen(magia)

    /**
     * Returns the bit-length in the same style as `java.math.BigInteger.bitLength()`.
     *
     * BigInteger.bitLength() attempts a pseudo-twos-complement answer
     * It is the number of bits required, minus the sign bit.
     * - For non-negative values, it is simply the number of bits in the magnitude.
     * - For negative values, it becomes a little wonky.
     *
     * Example: `BigInteger("-1").bitLength() == 0` ... think about ie :)
     */
    fun bitLengthBigIntegerStyle(): Int = Magia.bitLengthBigIntegerStyle(sign.isNegative, magia)

    /**
     * Returns the number of 32-bit integers required to store the binary magnitude.
     */
    fun magnitudeIntArrayLen() = (Magia.bitLen(magia) + 31) ushr 5

    /**
     * Returns the number of 64-bit longs required to store the binary magnitude.
     */
    fun magnitudeLongArrayLen() = (Magia.bitLen(magia) + 63) ushr 6

    /**
     * Computes the number of bytes needed to represent this BigInt
     * in two's-complement format.
     *
     * Always returns at least 1 for zero.
     */
    fun calcTwosComplementByteLength(): Int {
        if (isZero())
            return 1
        // add one for the sign bit
        val bitLen2sComplement = bitLengthBigIntegerStyle() + 1
        val byteLength = (bitLen2sComplement + 7) ushr 3
        return byteLength
    }

    /**
     * Tests whether the magnitude bit at [bitIndex] is set.
     *
     * @param bitIndex 0-based, starting from the least-significant bit
     * @return true if the bit is set, false otherwise
     */
    fun isBitSet(bitIndex: Int): Boolean = Magia.testBit(this.magia, bitIndex)

    fun setBit(bitIndex: Int): BigInt {
        if (bitIndex >= 0) {
            if (isBitSet(bitIndex))
                return this
            val newBitLen = max(bitIndex + 1, Magia.bitLen(this.magia))
            val magia = Magia.newCopyWithExactBitLen(this.magia, newBitLen)
            val wordIndex = bitIndex ushr 5
            magia[wordIndex] = magia[wordIndex] or (1 shl (bitIndex and 0x1F))
            return BigInt(this.sign, magia)
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns the index of the rightmost set bit (number of trailing zeros).
     *
     * If this BigInt is ZERO (no bits set), returns -1.
     *
     * Equivalent to `java.math.BigInteger.getLowestSetBit()`.
     *
     * @return bit index of the lowest set bit, or -1 if ZERO
     */
    fun countTrailingZeroBits(): Int = Magia.ntz(this.magia)

    /**
     * Returns the number of bits set in the magnitude, ignoring the sign.
     */
    fun magnitudeCountOneBits(): Int = Magia.bitPopulationCount(this.magia)

    /**
     * Returns a new BigInt representing the bitwise AND of the magnitudes,
     * ignoring signs.
     *
     * The result is always non-negative.
     */
    infix fun and(other: BigInt): BigInt {
        val magiaAnd = Magia.newAnd(this.magia, other.magia)
        return if (magiaAnd.isNotEmpty()) BigInt(POSITIVE, magiaAnd) else ZERO
    }

    /**
     * Returns a new BigInt representing the bitwise OR of the magnitudes,
     * ignoring signs.
     *
     * The result is always non-negative.
     */
    infix fun or(other: BigInt): BigInt {
        val magiaOr = Magia.newOr(this.magia, other.magia)
        return if (magiaOr.isNotEmpty()) BigInt(POSITIVE, magiaOr) else ZERO
    }

    /**
     * Returns a new BigInt representing the bitwise XOR of the magnitudes,
     * ignoring signs.
     *
     * The result is always non-negative.
     */
    infix fun xor(other: BigInt): BigInt {
        val magiaXor = Magia.newXor(this.magia, other.magia)
        return if (magiaXor.isNotEmpty()) BigInt(POSITIVE, magiaXor) else ZERO
    }

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
            bitCount > 0 -> {
                val magia = Magia.newShiftRight(this.magia, bitCount)
                if (magia !== Magia.ZERO) BigInt(POSITIVE, magia) else ZERO
            }

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
                var magia = Magia.newShiftRight(this.magia, bitCount)
                when {
                    magia !== Magia.ZERO -> {
                        // Mimic twos-complement rounding down for negative numbers
                        if (sign.isNegative && Magia.testAnyBitInLowerN(this.magia, bitCount))
                            magia = Magia.newOrMutateIncrement(magia)
                        BigInt(this.sign, magia)
                    }

                    sign.isNegative -> NEG_ONE
                    else -> ZERO
                }
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
            bitCount > 0 -> BigInt(
                this.sign,
                Magia.newShiftLeft(this.magia, bitCount)
            )

            else -> throw IllegalArgumentException("bitCount < 0")
        }
    }

    /**
     * Compares this [BigInt] with another [BigInt] for order.
     *
     * The comparison is performed according to mathematical value:
     * - A negative number is always less than a positive number.
     * - If both numbers have the same sign, their magnitudes are compared.
     *
     * @param other the [BigInt] to compare this value against.
     * @return
     *  * `-1` if this value is less than [other],
     *  * `0` if this value is equal to [other],
     *  * `1` if this value is greater than [other].
     */
    override operator fun compareTo(other: BigInt): Int {
        if (this.sign != other.sign)
            return this.sign.neg1or1
        val cmp = Magia.compare(this.magia, other.magia)
        return this.sign.negateIfNegative(cmp)
    }

    /**
     * Compares this [BigInt] with a 32-bit signed integer value.
     *
     * The comparison is based on the mathematical value of both numbers:
     * - Negative values of [n] are treated with a negative sign and compared by magnitude.
     * - Positive values are compared directly by magnitude.
     *
     * @param n the integer value to compare with this [BigInt].
     * @return
     *  * `-1` if this value is less than [n],
     *  * `0` if this value is equal to [n],
     *  * `1` if this value is greater than [n].
     */
    operator fun compareTo(n: Int) = compareToHelper(n < 0, n.absoluteValue.toUInt().toULong())

    /**
     * Compares this [BigInt] with an unsigned 32-bit integer value.
     *
     * The comparison is performed by treating [w] as a non-negative value
     * and comparing magnitudes directly.
     *
     * @param w the unsigned integer to compare with this [BigInt].
     * @return
     *  * `-1` if this value is less than [w],
     *  * `0` if this value is equal to [w],
     *  * `1` if this value is greater than [w].
     */
    operator fun compareTo(w: UInt) = compareToHelper(false, w.toULong())

    /**
     * Compares this [BigInt] with a 64-bit signed integer value.
     *
     * The comparison is based on mathematical value:
     * - If [l] is negative, the comparison accounts for its sign.
     * - Otherwise, magnitudes are compared directly.
     *
     * @param l the signed long value to compare with this [BigInt].
     * @return
     *  * `-1` if this value is less than [l],
     *  * `0` if this value is equal to [l],
     *  * `1` if this value is greater than [l].
     */
    operator fun compareTo(l: Long) = compareToHelper(l < 0, l.absoluteValue.toULong())

    /**
     * Compares this [BigInt] with an unsigned 64-bit integer value.
     *
     * The comparison is performed by treating [dw] as a non-negative value
     * and comparing magnitudes directly.
     *
     * @param dw the unsigned long value to compare with this [BigInt].
     * @return
     *  * `-1` if this value is less than [dw],
     *  * `0` if this value is equal to [dw],
     *  * `1` if this value is greater than [dw].
     */
    operator fun compareTo(dw: ULong) = compareToHelper(false, dw)

    /**
     * Compares magnitudes, disregarding sign flags.
     *
     * @return -1,0,1
     */
    fun magnitudeCompareTo(other: BigInt) = Magia.compare(this.magia, other.magia)
    fun magnitudeCompareTo(un: UInt) = Magia.compare(this.magia, un.toULong())
    fun magnitudeCompareTo(ul: ULong) = Magia.compare(this.magia, ul)
    fun magnitudeCompareTo(littleEndianIntArray: IntArray) =
        Magia.compare(this.magia, littleEndianIntArray)

    /**
     * Comparison predicate for numerical equality with another [BigInt].
     *
     * @param other the [BigInt] to compare with
     * @return `true` if both have the same sign and identical magnitude, `false` otherwise
     */
    infix fun EQ(other: BigInt): Boolean =
        (this.sign == other.sign) && Magia.EQ(this.magia, other.magia)

    /**
     * Comparison predicate for numerical equality with a signed 32-bit integer.
     *
     * @param n the [Int] value to compare with
     * @return `true` if this value equals [n], `false` otherwise
     */
    infix fun EQ(n: Int): Boolean = compareTo(n) == 0

    /**
     * Comparison predicate for numerical equality with an unsigned 32-bit integer.
     *
     * @param w the [UInt] value to compare with
     * @return `true` if this value equals [w], `false` otherwise
     */
    infix fun EQ(w: UInt): Boolean = compareTo(w) == 0

    /**
     * Comparison predicate for numerical equality with a signed 64-bit integer.
     *
     * @param l the [Long] value to compare with
     * @return `true` if this value equals [l], `false` otherwise
     */
    infix fun EQ(l: Long): Boolean = compareTo(l) == 0

    /**
     * Comparison predicate for numerical equality with an unsigned 64-bit integer.
     *
     * @param dw the [ULong] value to compare with
     * @return `true` if this value equals [dw], `false` otherwise
     */
    infix fun EQ(dw: ULong): Boolean = compareTo(dw) == 0

    /**
     * Comparison predicate for numerical inequality with another [BigInt].
     *
     * @param other the [BigInt] to compare with
     * @return `true` if signs differ or magnitudes are unequal, `false` otherwise
     */
    infix fun NE(other: BigInt): Boolean =
        (this.sign != other.sign) || !Magia.EQ(this.magia, other.magia)

    /**
     * Comparison predicate for numerical inequality with a signed 32-bit integer.
     *
     * @param n the [Int] value to compare with
     * @return `true` if this value does not equal [n], `false` otherwise
     */
    infix fun NE(n: Int): Boolean = compareTo(n) != 0

    /**
     * Comparison predicate for numerical inequality with an unsigned 32-bit integer.
     *
     * @param w the [UInt] value to compare with
     * @return `true` if this value does not equal [w], `false` otherwise
     */
    infix fun NE(w: UInt): Boolean = compareTo(w) != 0

    /**
     * Comparison predicate for numerical inequality with a signed 64-bit integer.
     *
     * @param l the [Long] value to compare with
     * @return `true` if this value does not equal [l], `false` otherwise
     */
    infix fun NE(l: Long): Boolean = compareTo(l) != 0

    /**
     * Comparison predicate for numerical inequality with an unsigned 64-bit integer.
     *
     * @param dw the [ULong] value to compare with
     * @return `true` if this value does not equal [dw], `false` otherwise
     */
    infix fun NE(dw: ULong): Boolean = compareTo(dw) != 0

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
     * @param other the object to compare against
     * @return `true` if [other] is a [BigInt] with the same value; `false` otherwise
     */
    override fun equals(other: Any?): Boolean {
        return (other is BigInt) &&
                (this.sign == other.sign) &&
                Magia.EQ(this.magia, other.magia)
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
        var result = sign.isNegative.hashCode()
        result = 31 * result + Magia.normalizedHashCode(magia)
        return result
    }

    /**
     * Returns the decimal string representation of this BigInt.
     *
     * - Negative values are prefixed with a `-` sign.
     * - Equivalent to calling `java.math.BigInteger.toString()`.
     *
     * @return a decimal string representing the value of this BigInt
     */
    override fun toString() = Magia.toString(sign.isNegative, magia)

    /**
     * Returns the hexadecimal string representation of this BigInt.
     *
     * - The string is prefixed with `0x`.
     * - Uses uppercase hexadecimal characters.
     * - Negative values are prefixed with a `-` sign before `0x`.
     *
     * @return a hexadecimal string representing the value of this BigInt
     */
    fun toHexString(): String =
        toHexString(HEX_PREFIX_UTF8_0x, useUpperCase = true, minPrintLength = 1, HEX_SUFFIX_UTF8_nada)

    fun toHexString(hexFormat: HexFormat): String {
        if (hexFormat === HexFormat.UpperCase)
            return toHexString()
        return toHexString(
            hexFormat.number.prefix.encodeToByteArray(),
            hexFormat.upperCase,
            hexFormat.number.minLength,
            hexFormat.number.suffix.encodeToByteArray()
            )
    }

    private fun toHexString(prefixUtf8: ByteArray, useUpperCase: Boolean, minPrintLength: Int, suffixUtf8: ByteArray): String {
        val signCount = sign.bit
        val prefixCount = prefixUtf8.size
        val nybbleCount = max((magnitudeBitLen() + 3) / 4, minPrintLength)
        val suffixCount = suffixUtf8.size
        val totalLen = signCount + prefixCount + nybbleCount + suffixCount
        val utf8 = ByteArray(totalLen)
        utf8[0] = '-'.code.toByte()
        var ich = signCount
        for (b in prefixUtf8) {
            utf8[ich] = b
            ++ich
        }
        Magia.toHexUtf8(magia, utf8, signCount + prefixCount, nybbleCount, useUpperCase)
        ich += nybbleCount
        for (b in suffixUtf8) {
            utf8[ich] = b
            ++ich
        }
        return utf8.decodeToString()
    }

    /**
     * Converts this [BigInt] to a **big-endian two's-complement** byte array.
     *
     * - Negative values use standard two's-complement representation.
     * - The returned array has the minimal length needed to represent the value,
     *   **but always at least 1 byte**.
     * - For other binary formats, see [toBinaryByteArray] or [toBinaryBytes].
     *
     * @return a new [ByteArray] containing the two's-complement representation
     */
    fun toTwosComplementBigEndianByteArray(): ByteArray =
        toBinaryByteArray(isTwosComplement = true, isBigEndian = true)

    /**
     * Converts this [BigInt] to a [ByteArray] in the requested binary format.
     *
     * - The format is determined by [isTwosComplement] and [isBigEndian].
     * - Negative values are represented in two's-complement form if [isTwosComplement] is true.
     * - The returned array has the minimal length needed, **but always at least 1 byte**.
     *
     * @param isTwosComplement whether to use two's-complement representation for negative numbers
     * @param isBigEndian whether the bytes are written in big-endian or little-endian order
     * @return a new [ByteArray] containing the binary representation
     */
    fun toBinaryByteArray(isTwosComplement: Boolean, isBigEndian: Boolean): ByteArray =
        Magia.toBinaryByteArray(sign.isNegative, magia, Magia.normalizedLimbLen(magia), isTwosComplement, isBigEndian)

    /**
     * Writes this [BigInt] into the provided [bytes] array in the requested binary format.
     *
     * - No heap allocation takes place.
     * - If [isTwosComplement] is true, values use two's-complement representation
     *   with the most significant bit indicating the sign.
     * - If [isTwosComplement] is false, the unsigned magnitude is written,
     *   possibly with the most significant bit set.
     * - Bytes are written in big-endian order if [isBigEndian] is true,
     *   otherwise little-endian order.
     * - If [requestedLength] is 0, the minimal number of bytes needed is calculated
     *   and written, **but always at least 1 byte**.
     * - If [requestedLength] > 0, exactly that many bytes will be written:
     *   - If the requested length is greater than the minimum required, the sign will
     *     be extended into the extra bytes.
     *   - If the requested length is insufficient… you will have a bad day.
     * - In all cases, the actual number of bytes written is returned.
     * - May throw [IndexOutOfBoundsException] if the supplied [bytes] array is too small.
     *
     * For a standard **two's-complement big-endian** version, see [toTwosComplementBigEndianByteArray].
     * For a version that allocates a new array automatically, see [toBinaryByteArray].
     *
     * @param isTwosComplement whether to use two's-complement representation for negative numbers
     * @param isBigEndian whether bytes are written in big-endian (true) or little-endian (false) order
     * @param bytes the target array to write into
     * @param offset the start index in [bytes] to begin writing
     * @param requestedLength number of bytes to write (<= 0 means minimal required, but always at least 1)
     * @return the number of bytes actually written
     * @throws IndexOutOfBoundsException if [bytes] is too small
     */
    fun toBinaryBytes(
        isTwosComplement: Boolean, isBigEndian: Boolean,
        bytes: ByteArray, offset: Int = 0, requestedLength: Int = -1
    ): Int =
        Magia.toBinaryBytes(
            this.magia, isTwosComplement && this.sign.isNegative, isBigEndian,
            bytes, offset, requestedLength
        )

    /**
     * Returns a copy of the magnitude as a little-endian IntArray.
     *
     * - Least significant limb is at index 0.
     * - Sign is ignored; only the magnitude is returned.
     *
     * @return a new IntArray containing the magnitude in little-endian order
     */
    fun magnitudeToLittleEndianIntArray(): IntArray = Magia.newNormalizedCopy(magia)

    /**
     * Returns a copy of the magnitude as a little-endian LongArray.
     *
     * - Combines every two 32-bit limbs into a 64-bit long.
     * - Least significant bits are in index 0.
     * - Sign is ignored; only the magnitude is returned.
     *
     * @return a new LongArray containing the magnitude in little-endian order
     */
    fun magnitudeToLittleEndianLongArray(): LongArray {
        val intLen = Magia.normalizedLimbLen(magia)
        val longLen = (intLen + 1) ushr 1
        val z = LongArray(longLen)
        var iw = 0
        var il = 0
        while (intLen - iw >= 2) {
            val lo = magia[iw].toUInt().toLong()
            val hi = magia[iw + 1].toLong() shl 32
            z[il] = hi or lo
            ++il
            iw += 2
        }
        if (iw < intLen)
            z[il] = magia[iw].toUInt().toLong()
        return z
    }

    /**
     * Returns `true` if this value is in normalized form.
     *
     * A `BigInt` is normalized when:
     *  - it is exactly the canonical zero (`BigInt.ZERO`), or
     *  - its magnitude array does not contain unused leading zero limbs
     *    (i.e., the most significant limb is non-zero).
     *
     * Normalization is not required for correctness, but a normalized
     * representation avoids unnecessary high-order zero limbs.
     */
    fun isNormalized() = magia.isEmpty() || magia[magia.lastIndex] != 0

    fun normalize(): BigInt =
        if (isNormalized()) this else BigInt(sign, Magia.newNormalizedCopy(magia))

    /**
     * Internal helper for addition or subtraction between two BigInts.
     *
     * @param isSub true to subtract [other] from this, false to add
     * @param other the BigInt operand
     * @return a new BigInt representing the result
     */
    private fun addSubImpl(isSub: Boolean, other: BigInt): BigInt {
        if (other === ZERO)
            return this
        if (this === ZERO)
            return if (isSub) other.negate() else other
        val otherSign = isSub xor other.sign.isNegative
        if (this.sign.isNegative == otherSign)
            return BigInt(this.sign, Magia.newAdd(this.magia, other.magia))
        val cmp = this.magnitudeCompareTo(other)
        val ret = when {
            cmp > 0 -> BigInt(sign, Magia.newSub(this.magia, other.magia))
            cmp < 0 -> BigInt(Sign(otherSign), Magia.newSub(other.magia, this.magia))
            else -> ZERO
        }
        return ret
    }

    /**
     * Internal helper for addition or subtraction with an Int operand.
     *
     * @param signFlipThis true to flip the sign of this BigInt before operation
     * @param signFlipOther true to flip the sign of the Int operand before operation
     * @param n the Int operand
     * @return a new BigInt representing the result
     */
    fun addSubImpl(signFlipThis: Boolean, signFlipOther: Boolean, n: Int): BigInt {
        val otherSign = n < 0
        val otherMag = n.absoluteValue
        return addSubImpl(signFlipThis, otherSign xor signFlipOther, otherMag.toUInt())
    }

    /**
     * Internal helper for addition or subtraction with a UInt operand.
     *
     * @param signFlipThis true to flip the sign of this BigInt before operation
     * @param otherSign the sign of the UInt operand
     * @param w the UInt operand
     * @return a new BigInt representing the result
     */
    fun addSubImpl(signFlipThis: Boolean, otherSign: Boolean, w: UInt): BigInt {
        if (w == 0u)
            return if (signFlipThis) this.negate() else this
        if (isZero()) {
            val magia = intArrayOf(w.toInt())
            return BigInt(Sign(otherSign), magia)
        }
        val thisSign = this.sign xor signFlipThis
        if (thisSign.isNegative == otherSign)
            return BigInt(thisSign, Magia.newAdd(this.magia, w.toULong()))
        val cmp = this.magnitudeCompareTo(w)
        val ret = when {
            cmp > 0 -> BigInt(thisSign, Magia.newSub(this.magia, w.toULong()))
            cmp < 0 -> BigInt(Sign(otherSign), intArrayOf(w.toInt() - this.magia[0]))
            else -> ZERO
        }
        return ret
    }

    /**
     * Internal helper for addition or subtraction with a Long operand.
     *
     * @param signFlipThis true to flip the sign of this BigInt before operation
     * @param signFlipOther true to flip the sign of the Long operand before operation
     * @param l the Long operand
     * @return a new BigInt representing the result
     */
    fun addSubImpl(signFlipThis: Boolean, signFlipOther: Boolean, l: Long): BigInt {
        val otherSign = l < 0L
        val otherMag = l.absoluteValue
        return addSubImpl(signFlipThis, otherSign xor signFlipOther, otherMag.toULong())
    }

    /**
     * Internal helper for addition or subtraction with a ULong operand.
     *
     * @param signFlipThis true to flip the sign of this BigInt before operation
     * @param otherSign the sign of the ULong operand
     * @param dw the ULong operand
     * @return a new BigInt representing the result
     */
    fun addSubImpl(signFlipThis: Boolean, otherSign: Boolean, dw: ULong): BigInt {
        if ((dw shr 32) == 0uL)
            return addSubImpl(signFlipThis, otherSign, dw.toUInt())
        if (isZero()) {
            val magia = intArrayOf(dw.toInt(), (dw shr 32).toInt())
            return BigInt(Sign(otherSign), magia)
        }
        val thisSign = this.sign xor signFlipThis
        if (thisSign.isNegative == otherSign)
            return BigInt(thisSign, Magia.newAdd(this.magia, dw))
        val cmp = this.magnitudeCompareTo(dw)
        val ret = when {
            cmp > 0 -> BigInt(thisSign, Magia.newSub(this.magia, dw))
            cmp < 0 -> {
                val thisMag = this.toULongMagnitude()
                val diff = dw - thisMag
                BigInt(Sign(otherSign), Magia.newFromULong(diff))
            }

            else -> ZERO
        }
        return ret
    }

    /**
     * Performs division and modulo with another BigInt.
     *
     * @param otherSign sign of the divisor
     * @param otherMagia magnitude of the divisor
     * @return a Pair of BigInts: quotient first, remainder second
     * @throws ArithmeticException if divisor is zero (handled at caller level)
     */
    private fun divModHelper(otherSign: Sign, otherMagia: IntArray): Pair<BigInt, BigInt> {
        val (magiaQuot, magiaRem) = Magia.newDivMod(this.magia, otherMagia)
        val hiQuot = if (magiaQuot.isNotEmpty()) BigInt(this.sign xor otherSign, magiaQuot) else ZERO
        val hiRem = if (magiaRem.isNotEmpty()) BigInt(this.sign, magiaRem) else ZERO
        return hiQuot to hiRem
    }

    /**
     * Performs division and modulo with a 32-bit unsigned integer.
     *
     * @param wSign sign of the divisor
     * @param wMag absolute value of the divisor
     * @return a Pair of BigInts: quotient first, remainder second
     * @throws ArithmeticException if divisor is zero
     */
    private fun divModUIntHelper(wSign: Boolean, wMag: UInt): Pair<BigInt, BigInt> {
        return when {
            wMag == 0u -> throw ArithmeticException("div by zero")
            this.isNotZero() -> {
                val quot = Magia.newNormalizedCopy(this.magia)
                val remN = Magia.mutateDivMod(quot, wMag)
                val hiQuot =
                    if (Magia.normalizedLimbLen(quot) > 0) BigInt(this.sign xor wSign, quot) else ZERO
                val hiRem = if (remN != 0u) BigInt(this.sign, intArrayOf(remN.toInt())) else ZERO
                hiQuot to hiRem
            }

            else -> ZERO to BigInt(Sign(wSign), intArrayOf(wMag.toInt()))
        }
    }

    /**
     * Performs division and modulo with a 64-bit unsigned long integer.
     *
     * Delegates to [divModUIntHelper] if the high 32 bits are zero; otherwise
     * constructs a temporary IntArray to perform the operation via [divModHelper].
     *
     * @param dwSign sign of the divisor
     * @param dwMag absolute value of the divisor
     * @return a Pair of BigInts: quotient first, remainder second
     * @throws ArithmeticException if divisor is zero
     */
    private fun divModULongHelper(dwSign: Boolean, dwMag: ULong): Pair<BigInt, BigInt> {
        val lo = dwMag.toUInt()
        val bi = (dwMag shr 32).toUInt()
        return when {
            dwMag == 0uL -> throw ArithmeticException("div by zero")
            bi == 0u -> divModUIntHelper(dwSign, lo)
            else -> divModHelper(Sign(dwSign), intArrayOf(lo.toInt(), bi.toInt()))
        }
    }

    /**
     * Helper for comparing this BigInt to an unsigned 64-bit integer.
     *
     * @param ulSign sign of the ULong operand
     * @param ulMag the ULong magnitude
     * @return -1 if this < ulMag, 0 if equal, 1 if this > ulMag
     */
    fun compareToHelper(ulSign: Boolean, ulMag: ULong): Int {
        if (this.sign.isNegative != ulSign)
            return this.sign.neg1or1
        val cmp = Magia.compare(this.magia, ulMag)
        return if (!ulSign) cmp else -cmp
    }

    /**
     * Writes the magnitude of this BigInt into the specified [bytes] array
     * in **big-endian two's-complement** format.
     *
     * This is an internal helper used by [toTwosComplementBigEndianByteArray].
     *
     * @param bytes target byte array
     * @param offset starting index in [bytes] to write
     * @param magByteLen number of bytes of the magnitude to write
     *
     * Notes:
     * - Handles both positive and negative values.
     * - For negative numbers, computes two's complement in-place.
     * - Assumes [bytes] has enough space to hold the output.
     * - Does not return a value; writes directly into [bytes].
     */
    private fun writeBigEndianTwosComplementBytesZ(bytes: ByteArray, offset: Int, magByteLen: Int) =
        Magia.toBinaryBytes(this.magia, this.sign.isNegative, isBigEndian = true, bytes, offset, magByteLen)

    private fun writeBigEndianTwosComplementBytes(bytes: ByteArray, offset: Int, magByteLen: Int) {
        val last = offset + magByteLen - 1
        var dest = last
        var remaining = magByteLen
        var i = 0
        bytes[0] = sign.mask.toByte()
        while (remaining > 0 && i < magia.size) {
            var w = magia[i] xor sign.mask
            var j = 4
            do {
                bytes[dest--] = w.toByte()
                w = w ushr 8
                --remaining
                --j
            } while (remaining > 0 && j > 0)
            ++i
        }
        if (sign.isNegative) {
            var carry = 1
            var k = last
            while (carry > 0 && k >= 0) {
                val t = (bytes[k].toInt() and 0xFF) + carry
                bytes[k] = t.toByte()
                carry = t ushr 8
                --k
            }
        }
    }

}

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
    other.addSubImpl(signFlipThis = false, signFlipOther = false, n = this)
operator fun UInt.plus(other: BigInt) =
    other.addSubImpl(signFlipThis = false, otherSign = false, w = this)
operator fun Long.plus(other: BigInt) =
    other.addSubImpl(signFlipThis = false, signFlipOther = false, l = this)
operator fun ULong.plus(other: BigInt) =
    other.addSubImpl(signFlipThis = false, otherSign = false, dw = this)

operator fun Int.minus(other: BigInt) =
    other.addSubImpl(signFlipThis = true, signFlipOther = false, n = this)
operator fun UInt.minus(other: BigInt) =
    other.addSubImpl(signFlipThis = true, otherSign = false, w = this)
operator fun Long.minus(other: BigInt) =
    other.addSubImpl(signFlipThis = true, signFlipOther = false, l = this)
operator fun ULong.minus(other: BigInt) =
    other.addSubImpl(signFlipThis = true, otherSign = false, dw = this)

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

operator fun Int.compareTo(bi: BigInt) =
    -bi.compareToHelper(this < 0, this.absoluteValue.toUInt().toULong())

operator fun UInt.compareTo(bi: BigInt) =
    -bi.compareToHelper(false, this.toULong())

operator fun Long.compareTo(bi: BigInt) =
    -bi.compareToHelper(this < 0, this.absoluteValue.toULong())

operator fun ULong.compareTo(bi: BigInt) =
    -bi.compareToHelper(false, this)

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


