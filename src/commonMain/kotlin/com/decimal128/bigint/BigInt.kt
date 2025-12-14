// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.BigInt.Companion.ZERO
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.text.HexFormat

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
    override val meta: Meta,
    override val magia: Magia
) : Comparable<BigInt>, Magian {

    companion object {
        /**
         * The canonical zero value for [BigInt].
         *
         * All representations of zero **must** reference this single instance.
         * This ensures that identity comparisons and optimizations relying on
         * reference equality (`===`) for zero values are valid.
         */
        val ZERO = BigInt(Meta(0), Magus.ZERO)

        val ONE = BigInt(Meta(1), Magus.ONE)

        val NEG_ONE = BigInt(Meta(1, 1), Magus.ONE) // share magia .. but no mutation allowed

        val TEN = BigInt(Meta(4), intArrayOf(10))

        internal operator fun invoke(sign: Sign, magia: Magia): BigInt {
            if (magia.isEmpty())
                return ZERO
            val signBit = sign.bit
            val meta = Meta(signBit, magia)
            return if (meta.normLen > 0) BigInt(meta, magia) else ZERO
        }

        internal operator fun invoke(sign: Boolean, magia: Magia): BigInt {
            if (magia.isEmpty()) {
                check(magia === Magus.ZERO)
                return ZERO
            }
            val signBit = if (sign) 1 else 0
            val meta = Meta(signBit, magia)
            return if (meta.normLen > 0) BigInt(meta, magia) else ZERO
        }

        internal operator fun invoke(magia: Magia): BigInt {
            if (magia.isEmpty()) {
                check (magia === Magus.ZERO)
                return ZERO
            }
            val signBit = 0
            val meta = Meta(signBit, magia)
            return if (meta.normLen > 0) BigInt(meta, magia) else ZERO
        }

        internal operator fun invoke(magia: Magia, normLen: Int): BigInt {
            if (magia.isEmpty()) {
                check (magia === Magus.ZERO)
                return ZERO
            }
            val signBit = 0
            for (i in normLen..<magia.size)
                magia[i] = 0
            val meta = Meta(signBit, normLen)
            return if (meta.normLen > 0) BigInt(meta, magia) else ZERO
        }

        internal operator fun invoke(sign: Boolean, magia: Magia, normLen: Int): BigInt {
            if (magia.isEmpty()) {
                check (magia === Magus.ZERO)
                return ZERO
            }
            val signBit = if (sign) 1 else 0
            for (i in normLen..<magia.size)
                magia[i] = 0
            val meta = Meta(signBit, normLen)
            return if (meta.normLen > 0) BigInt(meta, magia) else ZERO
        }

        internal operator fun invoke(sign: Boolean, biMagnitude: BigInt): BigInt =
            fromLittleEndianIntArray(sign, biMagnitude.magia, biMagnitude.meta.normLen)

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
            n > 0 -> BigInt(intArrayOf(n))
            n < 0 -> BigInt(sign=true, intArrayOf(-n))
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
        fun from(w: UInt) = if (w != 0u) BigInt(intArrayOf(w.toInt())) else ZERO

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
            (dwMagnitude shr 32) == 0uL -> BigInt(sign, intArrayOf(dwMagnitude.toInt()))
            else -> BigInt(sign, intArrayOf(dwMagnitude.toInt(), (dwMagnitude shr 32).toInt()))
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
        private fun from(src: Latin1Iterator): BigInt =
            BigInt(src.peek() == '-', Magus.from(src))

        /**
         * Parse a BigInt thru a standard iterator for different text
         * representations.
         */
        private fun fromHex(src: Latin1Iterator): BigInt =
            BigInt(src.peek() == '-', Magus.fromHex(src))

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
                val magia = Magus.newWithBitLen(maxBitLen)
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
                    !withRandomSign -> BigInt(magia)
                    else -> BigInt(rng.nextBoolean(), magia)
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
                val magia = Magus.newWithBitLen(bitLen)
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
                    BigInt(magia)
                else
                    BigInt(rng.nextBoolean(), magia)
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
                val magia = Magus.fromBinaryBytes(
                    isNegative, isBigEndian, bytes, offset,
                    length
                )
                if (magia !== Magus.ZERO)
                    return BigInt(isNegative, magia)
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
            val magia = Magus.newNormalizedCopy(littleEndianIntArray, len)
            return BigInt(sign, magia)
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
            val magia = Magus.newWithSetBit(bitIndex)
            return BigInt(magia)
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
            val magia = Magus.newWithBitLen(bitLen)
            val loIndex = bitIndex ushr 5
            magia.fill(-1, loIndex)
            val nlz = (magia.size shl 5) - bitLen
            magia[magia.lastIndex] = -1 ushr nlz
            val ctz = bitIndex and 0x1F
            magia[loIndex] = magia[loIndex] and (-1 shl ctz)
            return BigInt(magia)
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
                        val magia = Magus.newWithBitLen(totalBitLen)
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
                        BigInt(sign, magia)
                    }
                }
            }
            throw IllegalArgumentException()
        }

        private val HEX_PREFIX_UTF8_0x = byteArrayOf('0'.code.toByte(), 'x'.code.toByte())
        private val HEX_SUFFIX_UTF8_nada = ByteArray(0)

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

    // <<<<<<<<<<<<<<<<<< BEGINNING OF SHARED TEXT SOURCE CODE >>>>>>>>>>>>>>>>>>>>>>

    /**
     * Returns `true` if this BigInt is negative.
     */
    fun isNegative() = meta.isNegative

    /**
     * Standard signum function.
     *
     * @return -1 if negative, 0 if zero, 1 if positive
     */
    fun signum() = Zoro.signum(meta)

    /**
     * Returns `true` if the magnitude of this BigInt is a power of two
     * (exactly one bit set).
     */
    fun isMagnitudePowerOfTwo(): Boolean = Zoro.isMagnitudePowerOfTwo(meta, magia)

    /**
     * Returns `true` if this value is exactly representable as a 32-bit
     * signed integer (`Int.MIN_VALUE .. Int.MAX_VALUE`).
     *
     * Only values whose magnitude fits in one 32-bit limb (or zero) pass
     * this check.
     */
    fun fitsInt(): Boolean = Zoro.fitsInt(meta, magia)

    /**
     * Returns `true` if this value is non-negative and fits in an unsigned
     * 32-bit integer (`0 .. UInt.MAX_VALUE`).
     */
    fun fitsUInt(): Boolean = Zoro.fitsUInt(meta)

    /**
     * Returns `true` if this value fits in a signed 64-bit integer
     * (`Long.MIN_VALUE .. Long.MAX_VALUE`).
     */
    fun fitsLong(): Boolean = Zoro.fitsLong(meta, magia)

    /**
     * Returns `true` if this value is non-negative and fits in an unsigned
     * 64-bit integer (`0 .. ULong.MAX_VALUE`).
     */
    fun fitsULong(): Boolean = Zoro.fitsULong(meta)

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
    fun toInt() = Zoro.toInt(meta, magia)

    /**
     * Returns this value as a signed `Int`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toInt], this performs a
     * strict range check instead of truncating the upper bits.
     */
    fun toIntExact(): Int = Zoro.toIntExact(meta, magia)

    /**
     * Returns this BigInt as a signed Int, clamped to `Int.MIN_VALUE..Int.MAX_VALUE`.
     *
     * Values greater than `Int.MAX_VALUE` return `Int.MAX_VALUE`.
     * Values less than `Int.MIN_VALUE` return `Int.MIN_VALUE`.
     */
    fun toIntClamped(): Int = Zoro.toIntClamped(meta, magia)

    /**
     * Returns the low 32 bits of this value interpreted as an unsigned
     * two’s-complement `UInt` (i.e., wraps modulo 2³², like `Long.toUInt()`).
     */
    fun toUInt(): UInt = Zoro.toUInt(meta, magia)

    /**
     * Returns this value as a `UInt`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toUInt], this checks
     * that the value is within the unsigned 32-bit range.
     */
    fun toUIntExact(): UInt = Zoro.toUIntExact(meta, magia)

    /**
     * Returns this BigInt as an unsigned UInt, clamped to `0..UInt.MAX_VALUE`.
     *
     * Values greater than `UInt.MAX_VALUE` return `UInt.MAX_VALUE`.
     * Negative values return 0.
     */
    fun toUIntClamped(): UInt = Zoro.toUIntClamped(meta, magia)

    /**
     * Returns the low 64 bits of this value as a signed two’s-complement `Long`.
     *
     * The result is formed from the lowest 64 bits of the magnitude, with the
     * sign applied afterward; upper bits are discarded (wraps modulo 2⁶⁴),
     * matching `Long` conversion behavior.
     */
    fun toLong(): Long = Zoro.toLong(meta, magia)

    /**
     * Returns this value as a `Long`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toLong], this checks
     * that the value lies within the signed 64-bit range.
     */
    fun toLongExact(): Long = Zoro.toLongExact(meta, magia)

    /**
     * Returns this BigInt as a signed Long, clamped to `Long.MIN_VALUE..Long.MAX_VALUE`.
     *
     * Values greater than `Long.MAX_VALUE` return `Long.MAX_VALUE`.
     * Values less than `Long.MIN_VALUE` return `Long.MIN_VALUE`.
     */
    fun toLongClamped(): Long = Zoro.toLongClamped(meta, magia)

    /**
     * Returns the low 64 bits of this value interpreted as an unsigned
     * two’s-complement `ULong` (wraps modulo 2⁶⁴, like `Long.toULong()`).
     */
    fun toULong(): ULong = Zoro.toULong(meta, magia)

    /**
     * Returns this value as a `ULong`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toULong], this checks
     * that the value is within the unsigned 64-bit range.
     */
    fun toULongExact(): ULong = Zoro.toULongExact(meta, magia)

    /**
     * Returns this BigInt as an unsigned ULong, clamped to `0..ULong.MAX_VALUE`.
     *
     * Values greater than `ULong.MAX_VALUE` return `ULong.MAX_VALUE`.
     * Negative values return 0.
     */
    fun toULongClamped(): ULong = Zoro.toULongClamped(meta, magia)

    /**
     * Returns the low 32 bits of the magnitude as a `UInt`
     * (ignores the sign).
     */
    fun toUIntMagnitude(): UInt = Zoro.toUIntMagnitude(meta, magia)

    /**
     * Returns the low 64 bits of the magnitude as a `ULong`
     * (ignores the sign).
     */
    fun toULongMagnitude(): ULong = Zoro.toULongMagnitude(meta, magia)

    /**
     * Extracts a 64-bit unsigned value from the magnitude of this number,
     * starting at the given bit index (0 = least significant bit). Bits
     * beyond the magnitude are treated as zero.
     *
     * @throws IllegalArgumentException if `bitIndex` is negative.
     */
    fun extractULongAtBitIndex(bitIndex: Int): ULong =
        Zoro.extractULongAtBitIndex(meta, magia, bitIndex)

    /**
     * Returns the bit-length of the magnitude of this BigInt.
     *
     * Equivalent to the number of bits required to represent the absolute value.
     */
    fun magnitudeBitLen() = Zoro.magnitudeBitLen(meta, magia)

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
    fun bitLengthBigIntegerStyle(): Int =
        Zoro.bitLengthBigIntegerStyle(meta, magia)

    /**
     * Returns the number of 32-bit integers required to store the binary magnitude.
     */
    fun magnitudeIntArrayLen() =
        Zoro.magnitudeIntArrayLen(meta, magia)

    /**
     * Returns the number of 64-bit longs required to store the binary magnitude.
     */
    fun magnitudeLongArrayLen() =
        Zoro.magnitudeLongArrayLen(meta, magia)

    /**
     * Computes the number of bytes needed to represent this BigInt
     * in two's-complement format.
     *
     * Always returns at least 1 for zero.
     */
    fun calcTwosComplementByteLength(): Int =
        Zoro.calcTwosComplementByteLength(meta, magia)

    /**
     * Returns the index of the rightmost set bit (number of trailing zeros).
     *
     * If this BigInt is ZERO (no bits set), returns -1.
     *
     * Equivalent to `java.math.BigInteger.getLowestSetBit()`.
     *
     * @return bit index of the lowest set bit, or -1 if ZERO
     */
    fun countTrailingZeroBits(): Int = Zoro.countTrailingZeroBits(meta, magia)

    /**
     * Returns the number of bits set in the magnitude, ignoring the sign.
     */
    fun magnitudeCountOneBits(): Int = Zoro.magnitudeCountOneBits(meta, magia)

    /**
     * Tests whether the magnitude bit at [bitIndex] is set.
     *
     * @param bitIndex 0-based, starting from the least-significant bit
     * @return true if the bit is set, false otherwise
     */
    fun testBit(bitIndex: Int): Boolean = Zoro.testBit(meta, magia, bitIndex)

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
    override operator fun compareTo(other: BigInt): Int =
        Zoro.compare(meta, magia, other.meta, other.magia)

    /**
     * Compares this [BigInt] with a [BigIntAccumulator] for
     * numerical order.
     *
     * The comparison is performed according to mathematical value:
     * - A negative number is always less than a positive number.
     * - If both numbers have the same sign, their magnitudes are compared.
     *
     * @param other the [BigIntAccumulator] to compare this value against.
     * @return
     *  * `-1` if this value is less than [other],
     *  * `0` if this value is equal to [other],
     *  * `1` if this value is greater than [other].
     */
    operator fun compareTo(other: BigIntAccumulator): Int =
        Zoro.compare(meta, magia, other.meta, other.magia)

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
    operator fun compareTo(n: Int): Int = Zoro.compare(meta, magia, n)

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
    operator fun compareTo(w: UInt): Int = Zoro.compare(meta, magia, w)

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
    operator fun compareTo(l: Long): Int = Zoro.compare(meta, magia, l)

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
    operator fun compareTo(dw: ULong): Int = Zoro.compare(meta, magia, dw)

    /**
     * Helper for comparing this BigInt to an unsigned 64-bit integer.
     *
     * @param dwSign sign of the ULong operand
     * @param dwMag the ULong magnitude
     * @return -1 if this < ulMag, 0 if equal, 1 if this > ulMag
     */
    fun compareToHelper(dwSign: Boolean, dwMag: ULong): Int =
        Zoro.compareHelper(meta, magia, dwSign, dwMag)


    /**
     * Compares magnitudes, disregarding sign flags.
     *
     * @return -1,0,1
     */
    fun magnitudeCompareTo(other: BigInt) =
        Zoro.magnitudeCompare(meta, magia, other.meta, other.magia)
    fun magnitudeCompareTo(other: BigIntAccumulator) =
        Zoro.magnitudeCompare(meta, magia, other.meta, other.magia)
    fun magnitudeCompareTo(w: UInt) =
        Zoro.magnitudeCompare(meta, magia, w)
    fun magnitudeCompareTo(dw: ULong) =
        Zoro.magnitudeCompare(meta, magia, dw)
    fun magnitudeCompareTo(littleEndianIntArray: IntArray) =
        Zoro.magnitudeCompare(meta, magia, littleEndianIntArray)

    /**
     * Comparison predicate for numerical equality with another [BigInt].
     *
     * @param other the [BigInt] to compare with
     * @return `true` if both have the same sign and identical magnitude, `false` otherwise
     */
    infix fun EQ(other: BigInt): Boolean =
        Zoro.compare(meta, magia, other.meta, other.magia) == 0

    /**
     * Comparison predicate for numerical equality with the current
     * value of a mutable [BigIntAccumulator].
     *
     * @param acc the [BigIntAccumulator] to compare with
     * @return `true` if both have the same sign and identical magnitude, `false` otherwise
     */
    infix fun EQ(acc: BigIntAccumulator): Boolean =
        Zoro.compare(meta, magia, acc.meta, acc.magia) == 0

    /**
     * Comparison predicate for numerical equality with a signed 32-bit integer.
     *
     * @param n the [Int] value to compare with
     * @return `true` if this value equals [n], `false` otherwise
     */
    infix fun EQ(n: Int): Boolean = Zoro.compare(meta, magia, n) == 0

    /**
     * Comparison predicate for numerical equality with an unsigned 32-bit integer.
     *
     * @param w the [UInt] value to compare with
     * @return `true` if this value equals [w], `false` otherwise
     */
    infix fun EQ(w: UInt): Boolean = Zoro.compare(meta, magia, w) == 0

    /**
     * Comparison predicate for numerical equality with a signed 64-bit integer.
     *
     * @param l the [Long] value to compare with
     * @return `true` if this value equals [l], `false` otherwise
     */
    infix fun EQ(l: Long): Boolean = Zoro.compare(meta, magia, l) == 0

    /**
     * Comparison predicate for numerical equality with an unsigned 64-bit integer.
     *
     * @param dw the [ULong] value to compare with
     * @return `true` if this value equals [dw], `false` otherwise
     */
    infix fun EQ(dw: ULong): Boolean = Zoro.compare(meta, magia, dw) == 0

    /**
     * Comparison predicate for numerical inequality with another [BigInt].
     *
     * @param other the [BigInt] to compare with
     * @return `true` if signs differ or magnitudes are unequal, `false` otherwise
     */
    infix fun NE(other: BigInt): Boolean = !EQ(other)

    /**
     * Comparison predicate for numerical inequality with a [BigIntAccumulator].
     *
     * @param acc the [BigIntAccumulator] to compare with
     * @return `true` if signs differ or magnitudes are unequal, `false` otherwise
     */
    infix fun NE(acc: BigIntAccumulator): Boolean = !EQ(acc)

    /**
     * Comparison predicate for numerical inequality with a signed 32-bit integer.
     *
     * @param n the [Int] value to compare with
     * @return `true` if this value does not equal [n], `false` otherwise
     */
    infix fun NE(n: Int): Boolean = !EQ(n)

    /**
     * Comparison predicate for numerical inequality with an unsigned 32-bit integer.
     *
     * @param w the [UInt] value to compare with
     * @return `true` if this value does not equal [w], `false` otherwise
     */
    infix fun NE(w: UInt): Boolean = !EQ(w)

    /**
     * Comparison predicate for numerical inequality with a signed 64-bit integer.
     *
     * @param l the [Long] value to compare with
     * @return `true` if this value does not equal [l], `false` otherwise
     */
    infix fun NE(l: Long): Boolean = !EQ(l)

    /**
     * Comparison predicate for numerical inequality with an unsigned 64-bit integer.
     *
     * @param dw the [ULong] value to compare with
     * @return `true` if this value does not equal [dw], `false` otherwise
     */
    infix fun NE(dw: ULong): Boolean = !EQ(dw)


    /**
     * Comparison predicate for numerical equality with another [BigInt].
     *
     * @param other the [BigInt] to compare with
     * @return `true` if both have the same sign and identical magnitude, `false` otherwise
     */
    infix fun magEQ(other: BigInt): Boolean =
        Zoro.magnitudeCompare(meta, magia, other.meta, other.magia) == 0

    /**
     * Comparison predicate for numerical equality with the current
     * value of a mutable [BigIntAccumulator].
     *
     * @param acc the [BigIntAccumulator] to compare with
     * @return `true` if both have the same sign and identical magnitude, `false` otherwise
     */
    infix fun magEQ(acc: BigIntAccumulator): Boolean =
        Zoro.magnitudeCompare(meta, magia, acc.meta, acc.magia) == 0

    /**
     * Comparison predicate for numerical equality with a signed 32-bit integer.
     *
     * @param n the [Int] value to compare with
     * @return `true` if this value equals [n], `false` otherwise
     */
    infix fun magEQ(n: Int): Boolean =
        Zoro.magnitudeCompare(meta, magia, n.absoluteValue.toUInt()) == 0

    /**
     * Comparison predicate for numerical equality with an unsigned 32-bit integer.
     *
     * @param w the [UInt] value to compare with
     * @return `true` if this value equals [w], `false` otherwise
     */
    infix fun magEQ(w: UInt): Boolean =
        Zoro.magnitudeCompare(meta, magia, w) == 0

    /**
     * Comparison predicate for numerical equality with a signed 64-bit integer.
     *
     * @param l the [Long] value to compare with
     * @return `true` if this value equals [l], `false` otherwise
     */
    infix fun magEQ(l: Long): Boolean =
        Zoro.magnitudeCompare(meta, magia, l.absoluteValue.toULong()) == 0

    /**
     * Comparison predicate for numerical equality with an unsigned 64-bit integer.
     *
     * @param dw the [ULong] value to compare with
     * @return `true` if this value equals [dw], `false` otherwise
     */
    infix fun magEQ(dw: ULong): Boolean =
        Zoro.magnitudeCompare(meta, magia, dw) == 0

    /**
     * Comparison predicate for numerical inequality with another [BigInt].
     *
     * @param other the [BigInt] to compare with
     * @return `true` if signs differ or magnitudes are unequal, `false` otherwise
     */
    infix fun magNE(other: BigInt): Boolean = !magEQ(other)

    /**
     * Comparison predicate for numerical inequality with a [BigIntAccumulator].
     *
     * @param acc the [BigIntAccumulator] to compare with
     * @return `true` if signs differ or magnitudes are unequal, `false` otherwise
     */
    infix fun magNE(acc: BigIntAccumulator): Boolean = !magEQ(acc)

    /**
     * Comparison predicate for numerical inequality with a signed 32-bit integer.
     *
     * @param n the [Int] value to compare with
     * @return `true` if this value does not equal [n], `false` otherwise
     */
    infix fun magNE(n: Int): Boolean = !magEQ(n)

    /**
     * Comparison predicate for numerical inequality with an unsigned 32-bit integer.
     *
     * @param w the [UInt] value to compare with
     * @return `true` if this value does not equal [w], `false` otherwise
     */
    infix fun magNE(w: UInt): Boolean = !magEQ(w)

    /**
     * Comparison predicate for numerical inequality with a signed 64-bit integer.
     *
     * @param l the [Long] value to compare with
     * @return `true` if this value does not equal [l], `false` otherwise
     */
    infix fun magNE(l: Long): Boolean = !magEQ(l)

    /**
     * Comparison predicate for numerical inequality with an unsigned 64-bit integer.
     *
     * @param dw the [ULong] value to compare with
     * @return `true` if this value does not equal [dw], `false` otherwise
     */
    infix fun magNE(dw: ULong): Boolean = !magEQ(dw)


    /**
     * Returns the decimal string representation of this BigInt.
     *
     * - Negative values are prefixed with a `-` sign.
     * - Equivalent to calling `java.math.BigInteger.toString()`.
     *
     * @return a decimal string representing the value of this BigInt
     */
    override fun toString() = Zoro.toString(meta, magia)

    /**
     * Returns the hexadecimal string representation of this BigInt.
     *
     * - The string is prefixed with `0x`.
     * - Uses uppercase hexadecimal characters.
     * - Negative values are prefixed with a `-` sign before `0x`.
     *
     * @return a hexadecimal string representing the value of this BigInt
     */
    fun toHexString(): String = Zoro.toHexString(meta, magia)

    fun toHexString(hexFormat: HexFormat): String =
        Zoro.toHexString(meta, magia, hexFormat)

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
        Zoro.toTwosComplementBigEndianByteArray(meta, magia)

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
        Zoro.toBinaryByteArray(meta, magia, isTwosComplement, isBigEndian)

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
        Zoro.toBinaryBytes(meta, magia,
            isTwosComplement, isBigEndian,
            bytes, offset, requestedLength)

    /**
     * Returns a copy of the magnitude as a little-endian IntArray.
     *
     * - Least significant limb is at index 0.
     * - Sign is ignored; only the magnitude is returned.
     *
     * @return a new IntArray containing the magnitude in little-endian order
     */
    fun magnitudeToLittleEndianIntArray(): IntArray =
        Zoro.magnitudeToLittleEndianIntArray(meta, magia)

    /**
     * Returns a copy of the magnitude as a little-endian LongArray.
     *
     * - Combines every two 32-bit limbs into a 64-bit long.
     * - Least significant bits are in index 0.
     * - Sign is ignored; only the magnitude is returned.
     *
     * @return a new LongArray containing the magnitude in little-endian order
     */
    fun magnitudeToLittleEndianLongArray(): LongArray =
        Zoro.magnitudeToLittleEndianLongArray(meta, magia)

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
    fun isNormalized(): Boolean = Zoro.isNormalized(meta, magia)

    fun isSuperNormalized(): Boolean = Zoro.isSuperNormalized(meta, magia)


    // <<<<<<<<<<<<<<<<<< END OF SHARED TEXT SOURCE CODE >>>>>>>>>>>>>>>>>>>>>>

// Note: `magia` is shared with `negate` and `abs`.
// No mutation of `magia` is allowed.

    /**
     * Java/C-style function for the absolute value of this BigInt.
     *
     * If already non-negative, returns `this`.
     *
     *@see absoluteValue
     */
    fun abs() = if (meta.isNegative) BigInt(magia) else this

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
    fun negate() = if (isNotZero()) BigInt(meta.negate(), magia) else ZERO

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
        this.addImpl(signFlipThis = false, signFlipOther = false, n = n)
    operator fun plus(w: UInt): BigInt =
        this.addImpl(signFlipThis = false, otherSign = false, w = w)
    operator fun plus(l: Long): BigInt =
        this.addImpl(signFlipThis = false, signFlipOther = false, l = l)
    operator fun plus(dw: ULong): BigInt =
        this.addImpl(signFlipThis = false, otherSign = false, dw = dw)

    operator fun minus(other: BigInt): BigInt = this.addImpl(true, other)
    operator fun minus(n: Int): BigInt =
        this.addImpl(signFlipThis = false, signFlipOther = true, n = n)
    operator fun minus(w: UInt): BigInt =
        this.addImpl(signFlipThis = false, otherSign = true, w = w)
    operator fun minus(l: Long): BigInt =
        this.addImpl(signFlipThis = false, signFlipOther = true, l = l)
    operator fun minus(dw: ULong): BigInt =
        this.addImpl(signFlipThis = false, otherSign = true, dw = dw)

    operator fun times(other: BigInt): BigInt {
        return if (isNotZero() && other.isNotZero())
            BigInt(meta.signFlag xor other.meta.signFlag, Magus.newMul(this.magia, other.magia))
        else
            ZERO
    }

    operator fun times(n: Int): BigInt {
        return if (isNotZero() && n != 0)
            BigInt(this.meta.signFlag xor (n < 0), Magus.newMul(this.magia, n.absoluteValue.toUInt()))
        else
            ZERO
    }

    operator fun times(w: UInt): BigInt {
        return if (isNotZero() && w != 0u)
            BigInt(this.meta.signFlag, Magus.newMul(this.magia, w))
        else
            ZERO
    }

    operator fun times(l: Long): BigInt {
        return if (isNotZero() && l != 0L)
            BigInt(this.meta.signFlag xor (l < 0), Magus.newMul(this.magia, l.absoluteValue.toULong()))
        else
            ZERO
    }

    operator fun times(dw: ULong): BigInt {
        return if (isNotZero() && dw != 0uL)
            BigInt(this.meta.signFlag, Magus.newMul(this.magia, dw))
        else
            ZERO
    }

    operator fun div(other: BigInt): BigInt {
        if (other.isZero())
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val quot = Magus.newDiv(this.magia, this.meta.normLen, other.magia, other.meta.normLen)
            if (quot.isNotEmpty())
                return BigInt(this.meta.signFlag xor other.meta.signFlag, quot)
        }
        return ZERO
    }

    operator fun div(n: Int): BigInt {
        if (n == 0)
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val quot = Magus.newDiv(magia, meta.normLen, n.absoluteValue.toUInt())
            if (quot.isNotEmpty())
                return BigInt(this.meta.signFlag xor (n < 0), quot)
        }
        return ZERO
    }

    operator fun div(w: UInt): BigInt {
        if (w == 0u)
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val quot = Magus.newDiv(magia, meta.normLen, w)
            if (quot.isNotEmpty())
                return BigInt(this.meta.signFlag, quot)
        }
        return ZERO
    }

    operator fun div(l: Long): BigInt {
        if (l == 0L)
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val quot = Magus.newDiv(magia, meta.normLen, l.absoluteValue.toULong())
            if (quot.isNotEmpty())
                return BigInt(this.meta.signFlag xor (l < 0), quot)
        }
        return ZERO
    }

    operator fun div(dw: ULong): BigInt {
        if (dw == 0uL)
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val quot = Magus.newDiv(magia, meta.normLen, dw)
            if (quot.isNotEmpty())
                return BigInt(this.meta.signFlag, quot)
        }
        return ZERO
    }

    operator fun rem(other: BigInt): BigInt {
        if (other.isZero())
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val rem = Magus.newRem(this.magia, this.meta.normLen, other.magia, other.meta.normLen)
            if (rem.isNotEmpty())
                return BigInt(this.meta.signFlag, rem)
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
            val rem = Magus.newRem(this.magia, this.meta.normLen, w)
            if (rem.isNotEmpty())
                return BigInt(this.meta.signFlag, rem)
        }
        return ZERO
    }

    operator fun rem(l: Long): BigInt = rem(l.absoluteValue.toULong())

    operator fun rem(dw: ULong): BigInt {
        if (dw == 0uL)
            throw ArithmeticException("div by zero")
        if (isNotZero()) {
            val rem = Magus.newRem(this.magia, this.meta.normLen, dw)
            if (rem.isNotEmpty())
                return BigInt(this.meta.signFlag, rem)
        }
        return ZERO
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
        if (this.magnitudeCompareTo(numerator) > 0)
            return ZERO
        val quotient = numerator / this.toULongMagnitude()
        if (quotient == 0uL)
            return ZERO
        else
            return BigInt(
                this.meta.signFlag xor signNumerator,
                Magus.newFromULong(quotient)
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
            return BigInt(signNumerator, Magus.newFromULong(numerator))
        val remainder = numerator % this.toULongMagnitude()
        if (remainder == 0uL)
            return ZERO
        else
            return BigInt(signNumerator, Magus.newFromULong(remainder))
    }

    /**
     * Computes the square of this BigInt (i.e., this * this).
     *
     * @return a non-negative BigInt representing the square, or `ZERO` if this is zero.
     */
    fun sqr(): BigInt {
        if (this.isNotZero()) {
            check(Magus.normLen(this.magia) > 0)
            val magiaSqr = Magus.newSqr(this.magia)
            return BigInt(magiaSqr)
        }
        return ZERO
    }


    fun withSetBit(bitIndex: Int): BigInt = withBitOp(bitIndex, isSetOp = true)

    fun withClearBit(bitIndex: Int): BigInt = withBitOp(bitIndex, isSetOp = false)

    private fun withBitOp(bitIndex: Int, isSetOp: Boolean): BigInt {
        if (bitIndex >= 0) {
            if (! (testBit(bitIndex) xor isSetOp))
                return this
            val newBitLen = max(bitIndex + 1, Magus.bitLen(this.magia, this.meta.normLen))
            val magia = Magus.newCopyWithExactBitLen(this.magia, this.meta.normLen, newBitLen)
            val wordIndex = bitIndex ushr 5
            val isolatedBit = (1 shl (bitIndex and 0x1F))
            val limb = magia[wordIndex]
            magia[wordIndex] =
                if (isSetOp)
                    limb or isolatedBit
                else
                    limb and isolatedBit.inv()
            return BigInt(this.meta.signFlag, magia)
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new BigInt representing the bitwise AND of the magnitudes,
     * ignoring signs.
     *
     * The result is always non-negative.
     */
    infix fun and(other: BigInt): BigInt {
        val magiaAnd = Magus.newAnd(this.magia, this.meta.normLen, other.magia, other.meta.normLen)
        return if (magiaAnd.isNotEmpty()) BigInt(magiaAnd) else ZERO
    }

    /**
     * Returns a new BigInt representing the bitwise OR of the magnitudes,
     * ignoring signs.
     *
     * The result is always non-negative.
     */
    infix fun or(other: BigInt): BigInt {
        val magiaOr =
            Magus.newOr(this.magia, this.meta.normLen, other.magia, other.meta.normLen)
        return if (magiaOr.isNotEmpty()) BigInt(magiaOr) else ZERO
    }

    /**
     * Returns a new BigInt representing the bitwise XOR of the magnitudes,
     * ignoring signs.
     *
     * The result is always non-negative.
     */
    infix fun xor(other: BigInt): BigInt {
        val magiaXor = Magus.newXor(this.magia, this.meta.normLen, other.magia, other.meta.normLen)
        return if (magiaXor.isNotEmpty()) BigInt(magiaXor) else ZERO
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
                val magia = Magus.newShiftRight(this.magia, bitCount)
                if (magia !== Magus.ZERO) BigInt(magia) else ZERO
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
                val bitLen = magnitudeBitLen()
                if (bitLen <= bitCount)
                    return if (meta.isPositive) ZERO else NEG_ONE
                val needsIncrement = meta.isNegative && Magus.testAnyBitInLowerN(magia, bitCount)
                var magia = Magus.newShiftRight(this.magia, bitCount)
                check (Magus.normLen(magia) > 0)
                if (needsIncrement)
                    magia = Magus.newOrMutateAdd(magia, 1u)
                return BigInt(meta.signFlag, magia)
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
                this.meta.signFlag,
                Magus.newShiftLeft(this.magia, bitCount)
            )

            else -> throw IllegalArgumentException("bitCount < 0")
        }
    }

    /**
     * Returns this value masked to the `bitWidth` consecutive bits starting at
     * `bitIndex`. Bits outside the mask range are cleared. The sign of this value
     * is preserved.
     *
     * This is equivalent to:
     *
     *     result = this & ((2^bitWidth - 1) << bitIndex)
     *
     * @throws IllegalArgumentException if `bitWidth` or `bitIndex` is negative.
     */
    fun withBitMask(bitWidth: Int, bitIndex: Int = 0): BigInt {
        val myBitLen = magnitudeBitLen()
        when {
            bitIndex < 0 || bitWidth < 0 ->
                throw IllegalArgumentException(
                    "illegal negative arg bitIndex:$bitIndex bitCount:$bitWidth")
            bitWidth == 0 ||
                    bitIndex >= myBitLen ||
                    bitWidth == 1 && !testBit(bitIndex) -> return ZERO
            bitWidth == 1 -> {
                val magia = Magus.newWithSetBit(bitIndex)
                return BigInt(meta.signFlag, magia)
            }
        }
        // more than 1 bit wide and some overlap
        val clampedBitLen = min(bitWidth + bitIndex, myBitLen)
        val ret = Magus.newCopyWithExactBitLen(magia, meta.normLen, clampedBitLen)
        val nlz = (ret.size shl 5) - clampedBitLen
        ret[ret.lastIndex] = ret[ret.lastIndex] and (-1 ushr nlz)
        val loIndex = bitIndex ushr 5
        ret.fill(0, 0, loIndex)
        val ctz = bitIndex and 0x1F
        ret[loIndex] = ret[loIndex] and (-1 shl ctz)
        return BigInt(meta.signFlag, ret)
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
        result = 31 * result + Zoro.normalizedHashCode(magia, meta.normLen)
        return result
    }

    // FIXME
    //  who is calling this?
    fun normalize(): BigInt =
        if (isNormalized()) this else BigInt(meta.signFlag, Magus.newNormalizedCopy(magia, meta.normLen))

    /**
     * Internal helper for addition or subtraction between two BigInts.
     *
     * @param isSub true to subtract [other] from this, false to add
     * @param other the BigInt operand
     * @return a new BigInt representing the result
     */
    private fun addImpl(isSub: Boolean, other: BigInt): BigInt {
        if (other === ZERO)
            return this
        if (this === ZERO)
            return if (isSub) other.negate() else other
        val otherSign = isSub xor other.meta.isNegative
        if (this.meta.isNegative == otherSign)
            return BigInt(this.meta.signFlag,
                Magus.newAdd(this.magia, this.meta.normLen, other.magia, other.meta.normLen))
        val cmp = this.magnitudeCompareTo(other)
        val ret = when {
            cmp > 0 -> BigInt(this.meta.signFlag,
                Magus.newSub(this.magia, this.meta.normLen, other.magia, other.meta.normLen))
            cmp < 0 -> BigInt(otherSign,
                Magus.newSub(other.magia, other.meta.normLen, this.magia, this.meta.normLen))
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
    fun addImpl(signFlipThis: Boolean, signFlipOther: Boolean, n: Int): BigInt {
        val otherSign = n < 0
        val otherMag = n.absoluteValue
        return addImpl(signFlipThis, otherSign xor signFlipOther, otherMag.toUInt())
    }

    /**
     * Internal helper for addition or subtraction with a UInt operand.
     *
     * @param signFlipThis true to flip the sign of this BigInt before operation
     * @param otherSign the sign of the UInt operand
     * @param w the UInt operand
     * @return a new BigInt representing the result
     */
    fun addImpl(signFlipThis: Boolean, otherSign: Boolean, w: UInt): BigInt {
        if (w == 0u)
            return if (signFlipThis) this.negate() else this
        if (isZero()) {
            val magia = intArrayOf(w.toInt())
            return BigInt(otherSign, magia)
        }
        val thisSign = this.meta.signFlag xor signFlipThis
        if (thisSign == otherSign)
            return BigInt(thisSign, Magus.newAdd(this.magia, this.meta.normLen, w.toULong()))
        val cmp = this.magnitudeCompareTo(w)
        val ret = when {
            cmp > 0 -> BigInt(thisSign, Magus.newSub(this.magia, this.meta.normLen, w.toULong()))
            cmp < 0 -> BigInt(otherSign, intArrayOf(w.toInt() - this.magia[0]))
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
    fun addImpl(signFlipThis: Boolean, signFlipOther: Boolean, l: Long): BigInt {
        val otherSign = l < 0L
        val otherMag = l.absoluteValue
        return addImpl(signFlipThis, otherSign xor signFlipOther, otherMag.toULong())
    }

    /**
     * Internal helper for addition or subtraction with a ULong operand.
     *
     * @param signFlipThis true to flip the sign of this BigInt before operation
     * @param otherSign the sign of the ULong operand
     * @param dw the ULong operand
     * @return a new BigInt representing the result
     */
    fun addImpl(signFlipThis: Boolean, otherSign: Boolean, dw: ULong): BigInt {
        if ((dw shr 32) == 0uL)
            return addImpl(signFlipThis, otherSign, dw.toUInt())
        if (isZero()) {
            val magia = intArrayOf(dw.toInt(), (dw shr 32).toInt())
            return BigInt(otherSign, magia)
        }
        val thisSign = this.meta.signFlag xor signFlipThis
        if (thisSign == otherSign)
            return BigInt(thisSign, Magus.newAdd(this.magia, this.meta.normLen, dw))
        val cmp = this.magnitudeCompareTo(dw)
        val ret = when {
            cmp > 0 -> BigInt(thisSign, Magus.newSub(this.magia, this.meta.normLen, dw))
            cmp < 0 -> {
                val thisMag = this.toULongMagnitude()
                val diff = dw - thisMag
                BigInt(otherSign, Magus.newFromULong(diff))
            }

            else -> ZERO
        }
        return ret
    }

}

// <<<< THESE PORT OVER TO BigIntAccumulator WITH BigInt => BigIntAccumulator >>>>
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
    other.addImpl(signFlipThis = false, signFlipOther = false, n = this)
operator fun UInt.plus(other: BigInt) =
    other.addImpl(signFlipThis = false, otherSign = false, w = this)
operator fun Long.plus(other: BigInt) =
    other.addImpl(signFlipThis = false, signFlipOther = false, l = this)
operator fun ULong.plus(other: BigInt) =
    other.addImpl(signFlipThis = false, otherSign = false, dw = this)

operator fun Int.minus(other: BigInt) =
    other.addImpl(signFlipThis = true, signFlipOther = false, n = this)
operator fun UInt.minus(other: BigInt) =
    other.addImpl(signFlipThis = true, otherSign = false, w = this)
operator fun Long.minus(other: BigInt) =
    other.addImpl(signFlipThis = true, signFlipOther = false, l = this)
operator fun ULong.minus(other: BigInt) =
    other.addImpl(signFlipThis = true, otherSign = false, dw = this)

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

/**
 * Computes `this**exp` using fast binary exponentiation.
 *
 * Delegates to [BigIntAlgorithms.pow].
 *
 * @throws IllegalArgumentException if [exp] is negative
 * @see BigIntAlgorithms.pow
 */
fun BigInt.pow(exp: Int): BigInt = BigIntAlgorithms.pow(this, exp)

/**
 * Returns ⌊√this⌋, the integer square root.
 *
 * @see BigIntAlgorithms.isqrt
 */
fun BigInt.isqrt(): BigInt = BigIntAlgorithms.isqrt(this)



