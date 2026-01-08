// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.intrinsic.unsignedMulHi
import kotlin.math.max
import kotlin.math.min

internal object BigIntParsePrint {

    fun toString(x: Magia, xNormLen: Int) = toString(false, x, xNormLen)

    /**
     * Returns the decimal string representation of this BigInt.
     *
     * - Negative values are prefixed with a `-` sign.
     * - Equivalent to calling `java.math.BigInteger.toString()`.
     *
     * @return a decimal string representing the value of this BigInt
     */
    fun toString(meta: Meta, magia: Magia): String =
        toString(meta.isNegative, magia, meta.normLen)

    /**
     * Converts the given unsigned integer magnitude [magia] to its decimal string form.
     *
     * Equivalent to calling [toString] with `isNegative = false`.
     *
     * @param magia the unsigned integer magnitude, least-significant word first.
     * @return the decimal string representation of [magia].
     */
    fun toString(magia: IntArray) = toString(isNegative = false, magia)

    /**
     * Converts a signed magnitude [magia] value into its decimal string representation.
     *
     * Performs a full base-10 conversion. Division and remainder operations
     * are done in chunks of one billion (1 000 000 000) to minimize costly
     * multi-precision divisions. Temporary heap allocation is used for an intermediate
     * quotient array, a temporary UTF-8 buffer, and the final [String] result.
     *
     * The algorithm:
     *  - Estimates the required digit count from [bitLen].
     *  - Copies [magia] into a temporary mutable array.
     *  - Repeatedly divides the number by 1e9 to extract 9-digit chunks.
     *  - Converts each chunk into ASCII digits using [render9DigitsBeforeIndex] and [renderTailDigitsBeforeIndex].
     *  - Prepends a leading ‘-’ if [isNegative] is true.
     *
     * @param isNegative whether to prefix the result with a minus sign.
     * @param magia the magnitude, least-significant word first.
     * @return the decimal string representation of the signed value.
     */
    fun toString(isNegative: Boolean, magia: Magia): String =
        toString(isNegative, magia, normLen(magia))

    /**
     * Converts a multi-limb integer to its decimal string representation.
     *
     *
     * @param isNegative whether the resulting string should include a leading minus sign.
     * @param x the array of 32-bit limbs representing the integer (least-significant limb first).
     * @param xNormLen the number of significant limbs to consider from `x`.
     * @return the decimal string representation of the integer value.
     */
    fun toString(isNegative: Boolean, x: Magia, xNormLen: Int): String {
        if (xNormLen >= 0 && xNormLen <= x.size) {
            if (!isNormalized(x, xNormLen))
                return "[not-normalized]"
            val bitLen = normBitLen(x, xNormLen)
            if (bitLen < 2) {
                if (bitLen == 0)
                    return "0"
                return if (isNegative) "-1" else "1"
            }
            val maxSignedLen = maxDigitLenFromBitLen(bitLen) + if (isNegative) 1 else 0
            val utf8 = ByteArray(maxSignedLen)
            val limbLen = normLen(x, xNormLen)
            val t = newCopyWithExactLimbLen(x, xNormLen, limbLen)
            val len = destructiveToUtf8BeforeIndex(utf8, utf8.size, isNegative, t, limbLen)
            val startingIndex = utf8.size - len
            verify { startingIndex <= 1 }
            return utf8.decodeToString(startingIndex, utf8.size)
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Returns an upper bound on the number of decimal digits required to
     * represent a non-negative integer with the given bit length.
     *
     * For any positive integer `x`, the exact digit count is:
     *
     *     digits = floor(bitLen * log10(2)) + 1
     *
     * This function computes a tight conservative approximation using a
     * fixed-point 2**32 scaled constant that slightly exceeds `log10(2)`.
     * This function always produces a close safe upper bound on the number
     * of base-10 digits, never overestimating by more than 1 for values
     * with tens of thousands of digits
     *
     * @param bitLen the bit length of the integer (must be ≥ 0)
     * @return an upper bound on the required decimal digit count
     */
    inline fun maxDigitLenFromBitLen(bitLen: Int): Int {
        // LOG10_2_CEIL_SCALE_2_32  = 1292913987uL
        return (bitLen.toULong() * 1292913987uL shr 32).toInt() + 1
    }

    /**
     * Converts the big-integer value in `t` (length `tLen`) into decimal UTF-8 digits.
     *
     * Converts the big-integer value in `t` (length `tLen`) to decimal digits and
     * writes them into `utf8` **right-to-left**. ibMaxx is Max eXclusive, so
     * writing begins at index `ibMaxx - 1` and proceeds to the left.
     *
     * The array `t` is treated as a temporary work area and is **mutated in-place**
     * by repeated Barrett divisions by 1e9. Full 9-digit chunks are written with
     * `renderChunk9`, and the final limb is written with `renderChunkTail`.
     *
     * If `isNegative` is true, a leading '-' is inserted.
     *
     * @param utf8 the destination byte buffer where UTF-8 digits are written.
     * @param ibMaxx the exclusive upper index in `utf8`; writing starts at
     *               `ibMaxx - 1` and proceeds leftward.
     * @param isNegative whether a leading '-' should be inserted.
     * @param tmp a temporary big-integer buffer holding the magnitude; it is
     *            mutated in-place by repeated Barrett reduction divisions.
     * @param tmpLen the number of active limbs in `tmp`; must be ≥ 1, within bounds
     *               and normalized.
     *
     * @return the number of bytes written into `utf8`.
     */
    fun destructiveToUtf8BeforeIndex(utf8: ByteArray, ibMaxx: Int, isNegative: Boolean, tmp: Magia, tmpLen: Int): Int {
        if (tmpLen > 0 && tmpLen <= tmp.size && tmp[tmpLen - 1] != 0 &&
            ibMaxx > 0 && ibMaxx <= utf8.size) {
            var ib = ibMaxx
            var limbsRemaining = tmpLen
            while (limbsRemaining > 1) {
                val newLenAndRemainder = mutateBarrettDivBy1e9(tmp, limbsRemaining)
                val chunk = newLenAndRemainder and 0xFFFF_FFFFuL
                render9DigitsBeforeIndex(chunk, utf8, ib)
                limbsRemaining = (newLenAndRemainder shr 32).toInt()
                ib -= 9
            }
            ib -= renderTailDigitsBeforeIndex(tmp[0].toUInt(), utf8, ib)
            if (isNegative)
                utf8[--ib] = '-'.code.toByte()
            val len = utf8.size - ib
            return len
        } else {
            throw IllegalArgumentException()
        }
    }

    private const val BARRETT_MU_1E9: ULong = 0x44B82FA09uL       // floor(2^64 / 1e9)
    private const val ONE_E_9: ULong = 1_000_000_000uL

    private const val M_U32_DIV_1E1 = 0xCCCCCCCDuL
    private const val S_U32_DIV_1E1 = 35

    private const val M_U32_DIV_1E2 = 0x51EB851FuL
    private const val S_U32_DIV_1E2 = 37

    private const val M_U64_DIV_1E4 = 0x346DC5D63886594BuL
    private const val S_U64_DIV_1E4 = 11 // + 64 high

    // these magic reciprocal constants only work for values up to
// 10**9 / 10**4
    private const val M_1E9_DIV_1E4 = 879_609_303uL
    private const val S_1E9_DIV_1E4 = 43

    private const val LOG2_10_CEIL_32 = 14_267_572_565uL

    /**
     * Renders a single 32-bit unsigned integer [n] into its decimal digits
     * at the end of [utf8], starting from [offMaxx] and moving backward.
     *
     * Digits are emitted least-significant first and written backward into [utf8].
     * Uses reciprocal multiplication by `0xCCCCCCCD` (fixed-point reciprocal of 10)
     * to perform fast division and extract digits.
     *
     * If the value of [w] is zero then a zero digit is written.
     *
     * @param w the integer to render (interpreted as unsigned 32-bit).
     * @param utf8 the UTF-8 byte buffer to write digits into.
     * @param offMaxx the maximum exclusive offset within [utf8];
     *                digits are written backward from `offMaxx - 1`.
     * @return the number of bytes/digits written.
     */
    fun renderTailDigitsBeforeIndex(w: UInt, utf8: ByteArray, offMaxx: Int): Int {
        var t = w.toULong()
        var ib = offMaxx
        while (t >= 1000uL) {
            val t0 = unsignedMulHi(t, M_U64_DIV_1E4) shr S_U64_DIV_1E4
            val abcd = t - (t0 * 10000uL)
            t = t0
            val ab = (abcd * M_U32_DIV_1E2) shr S_U32_DIV_1E2
            val cd = abcd - (ab * 100uL)
            val a = (ab * M_U32_DIV_1E1) shr S_U32_DIV_1E1
            val b = ab - (a * 10uL)
            val c = (cd * M_U32_DIV_1E1) shr S_U32_DIV_1E1
            val d = cd - (c * 10uL)
            if (ib - 4 >= 0 && ib <= utf8.size) {
                utf8[ib - 4] = (a.toInt() + '0'.code).toByte()
                utf8[ib - 3] = (b.toInt() + '0'.code).toByte()
                utf8[ib - 2] = (c.toInt() + '0'.code).toByte()
                utf8[ib - 1] = (d.toInt() + '0'.code).toByte()
                ib -= 4
            } else {
                IllegalArgumentException()
            }
        }
        if (t != 0uL || w == 0u) {
            do {
                val divTen = (t * 0xCCCCCCCDuL) shr 35
                val digit = (t - (divTen * 10uL)).toInt()
                utf8[--ib] = ('0'.code + digit).toByte()
                t = divTen
            } while (t != 0uL)
        }

        return offMaxx - ib
    }

    /**
     * Renders a 9-digit chunk [dw] (0 ≤ [dw] < 1e9) into ASCII digits in [utf8],
     * ending just before [offMaxx].
     *
     * Digits are extracted using reciprocal-multiply division by powers
     * of 10 to avoid slow hardware division instructions.
     *
     * The layout written is:
     * ```
     * utf8[offMaxx - 9] .. utf8[offMaxx - 1] = '0'..'9'
     * ```
     *
     * @param dw the 9-digit unsigned long value to render ... `0..999999999`
     * @param utf8 the output byte buffer for ASCII digits.
     * @param offMaxx the maximum exclusive offset within [utf8];
     * digits occupy the range `offMaxx - 9 .. offMaxx - 1`.
     */
    fun render9DigitsBeforeIndex(dw: ULong, utf8: ByteArray, offMaxx: Int) {
        verify { dw < 1_000_000_000uL }
        //val abcde = unsignedMulHi(dw, M_U64_DIV_1E4) shr S_U64_DIV_1E4
        val abcde = (dw * M_1E9_DIV_1E4) shr S_1E9_DIV_1E4
        val fghi = dw - (abcde * 10000uL)

        val abc = (abcde * M_U32_DIV_1E2) shr S_U32_DIV_1E2
        val de = abcde - (abc * 100uL)

        val fg = (fghi * M_U32_DIV_1E2) shr S_U32_DIV_1E2
        val hi = fghi - (fg * 100uL)

        val a = (abc * M_U32_DIV_1E2) shr S_U32_DIV_1E2
        val bc = abc - (a * 100uL)

        val b = (bc * M_U32_DIV_1E1) shr S_U32_DIV_1E1
        val c = bc - (b * 10uL)

        val d = (de * M_U32_DIV_1E1) shr S_U32_DIV_1E1
        val e = de - (d * 10uL)

        val f = (fg * M_U32_DIV_1E1) shr S_U32_DIV_1E1
        val g = fg - (f * 10uL)

        val h = (hi * M_U32_DIV_1E1) shr S_U32_DIV_1E1
        val i = hi - (h * 10uL)

        // Explicit bounds check to enable elimination of individual checks
        val offMin = offMaxx - 9
        if (offMin >= 0 && offMaxx <= utf8.size) {
            utf8[offMaxx - 9] = (a.toInt() + '0'.code).toByte()
            utf8[offMaxx - 8] = (b.toInt() + '0'.code).toByte()
            utf8[offMaxx - 7] = (c.toInt() + '0'.code).toByte()
            utf8[offMaxx - 6] = (d.toInt() + '0'.code).toByte()
            utf8[offMaxx - 5] = (e.toInt() + '0'.code).toByte()
            utf8[offMaxx - 4] = (f.toInt() + '0'.code).toByte()
            utf8[offMaxx - 3] = (g.toInt() + '0'.code).toByte()
            utf8[offMaxx - 2] = (h.toInt() + '0'.code).toByte()
            utf8[offMaxx - 1] = (i.toInt() + '0'.code).toByte()
        } else {
            throw IndexOutOfBoundsException()
        }
    }

    /**
     * Performs an in-place Barrett division of a multi-limb integer (`magia`) by 1e9.
     *
     * Each limb of [magia] is a 32-bit unsigned value (stored in [Int]),
     * with the most significant limb at index `len - 1`.
     * The function replaces each limb with its quotient and returns both
     * the new effective length and the remainder.
     *
     * This version uses the **qHat + rHat staged Barrett method**:
     * 1. Compute an approximate quotient `qHat` using the precomputed Barrett reciprocal [BARRETT_MU_1E9].
     * 2. Compute the remainder `rHat = combined − qHat × 1e9`.
     * 3. Conditionally increment `qHat` (and subtract 1e9 from `rHat`) if `rHat ≥ 1e9`.
     *    This is a 0-or-1 correction; `qHat` never decreases.
     *
     * The remainder from each limb is propagated to the next iteration.
     *
     * After all limbs are processed, the function computes the new effective length
     * of [magia] (trimming the most significant zero limb, if present) without looping.
     *
     * The new len will usually be one less, but sometimes will be the same. The most
     * significant limb is always written ... meaning that it will be zero-ed out.
     *
     * @param magia the multi-limb integer to divide. Must have `magia[len - 1] != 0`.
     *              Each element represents 32 bits of the number.
     * @param len the number of limbs in [magia] to process.
     * @return a packed [ULong]:
     *   - upper 32 bits: new effective limb count after trimming
     *   - lower 32 bits: remainder of the division by 1e9
     *
     * **Note:** The correction is a 0-or-1 adjustment; `qHat` never decreases.
     * **Correctness:** Guarantees that after each limb, `0 ≤ rHat < 1e9`.
     */
    fun mutateBarrettDivBy1e9(magia: Magia, len: Int): ULong {
        var rem = 0uL
        check(magia[len - 1] != 0)
        for (i in len - 1 downTo 0) {
            val limb = magia[i].toUInt().toULong()
            val combined = (rem shl 32) or limb

            // approximate quotient using Barrett reciprocal
            var qHat = unsignedMulHi(combined, BARRETT_MU_1E9)

            // compute remainder
            var rHat = combined - qHat * ONE_E_9

            // 0-or-1 adjustment: increment qHat if remainder >= 1e9
            // use signed shr to propagate the sign bit
            // adjustMask will have value 0 or -1 (aka 0xFF...FF)
            // if (rHat < ONE_E_9) 0uL else -1uL
            val adjustMask = ((rHat - ONE_E_9).toLong() shr 63).toULong().inv()
            qHat -= adjustMask
            rHat -= ONE_E_9 and adjustMask

            magia[i] = qHat.toInt()
            rem = rHat
        }

        val mostSignificantLimbNonZero = (-magia[len - 1]) ushr 31 // 0 or 1
        val newLen = len - 1 + mostSignificantLimbNonZero

        // pack new length and remainder into a single Long
        return (newLen.toULong() shl 32) or (rem and 0xFFFF_FFFFuL)
    }

    private val HEX_PREFIX_UTF8_0x = byteArrayOf('0'.code.toByte(), 'x'.code.toByte())
    private val HEX_SUFFIX_UTF8_nada = ByteArray(0)

    /**
     * Returns the hexadecimal string representation of this BigInt.
     *
     * - The string is prefixed with `0x`.
     * - Uses uppercase hexadecimal characters.
     * - Negative values are prefixed with a `-` sign before `0x`.
     *
     * @return a hexadecimal string representing the value of this BigInt
     */
    fun toHexString(bi: BigIntNumber): String =
        toHexString(bi, HEX_PREFIX_UTF8_0x, useUpperCase = true, minPrintLength = 1, HEX_SUFFIX_UTF8_nada)

    fun toHexString(bi: BigIntNumber, hexFormat: HexFormat): String {
        if (hexFormat === HexFormat.UpperCase)
            return toHexString(bi)
        return toHexString(bi,
            hexFormat.number.prefix.encodeToByteArray(),
            hexFormat.upperCase,
            hexFormat.number.minLength,
            hexFormat.number.suffix.encodeToByteArray()
        )
    }

    private fun toHexString(bi: BigIntNumber, prefixUtf8: ByteArray, useUpperCase: Boolean, minPrintLength: Int, suffixUtf8: ByteArray): String {
        val signCount = bi.meta.signBit
        val prefixCount = prefixUtf8.size
        val nybbleCount = max((normBitLen(bi.magia, bi.meta.normLen) + 3) / 4, minPrintLength)
        val suffixCount = suffixUtf8.size
        val totalLen = signCount + prefixCount + nybbleCount + suffixCount
        val utf8 = ByteArray(totalLen)
        utf8[0] = '-'.code.toByte()
        var ich = signCount
        for (b in prefixUtf8) {
            utf8[ich] = b
            ++ich
        }
        toHexUtf8(bi, utf8, signCount + prefixCount, nybbleCount, useUpperCase)
        ich += nybbleCount
        for (b in suffixUtf8) {
            utf8[ich] = b
            ++ich
        }
        return utf8.decodeToString()
    }

    fun toHexUtf8(bi: BigIntNumber, utf8: ByteArray, off: Int, digitCount: Int, useUpperCase: Boolean) {
        verify { bi.isNormalized() }
        val alfaBase = if (useUpperCase) 'A' else 'a'
        var ichMax = off + digitCount
        var limbIndex = 0
        var nybblesRemaining = 0
        var w = 0
        while (ichMax > off) {
            if (nybblesRemaining == 0) {
                if (limbIndex == bi.meta.normLen) {
                    // if there are no limbs left then take as
                    // many zero nybbles as you want
                    nybblesRemaining = Int.MAX_VALUE
                    verify { w == 0 }
                } else {
                    w = bi.magia[limbIndex]
                    ++limbIndex
                    nybblesRemaining = 8
                }
            }
            val nybble = w and 0x0F
            --nybblesRemaining
            w = w ushr 4
            val ch = if (nybble < 10) '0' + nybble else alfaBase + (nybble - 10)
            --ichMax
            utf8[ichMax] = ch.code.toByte()
        }
    }

    /**
     * Factory methods for constructing a numeric value from ASCII/Latin-1/UTF-8 encoded input.
     *
     * Each overload accepts a different input source — `String`, `CharSequence`, `CharArray`,
     * or `ByteArray` — and creates a new instance by parsing its contents as an unsigned
     * or signed decimal number (depending on the implementation of `from`).
     *
     * For efficiency, these overloads avoid intermediate string conversions by using
     * specialized iterator types that stream the input data directly.
     *
     * @receiver none
     * @param str the source string to parse.
     * @param csq the character sequence to parse.
     * @param chars the character array to parse.
     * @param bytes the ASCII byte array to parse.
     * @param off the starting offset of the input segment (inclusive).
     * @param len the number of characters or bytes to read from the input.
     * @return a parsed numeric value represented internally by this class.
     *
     * @see StringLatin1Iterator
     * @see CharSequenceLatin1Iterator
     * @see CharArrayLatin1Iterator
     * @see ByteArrayLatin1Iterator
     */
    fun from(str: String) = from(StringLatin1Iterator(str))
    fun from(str: String, off: Int, len: Int) = from(StringLatin1Iterator(str, off, len))
    fun from(csq: CharSequence) = from(CharSequenceLatin1Iterator(csq))
    fun from(csq: CharSequence, off: Int, len: Int) =
        from(CharSequenceLatin1Iterator(csq, off, len))

    fun from(chars: CharArray) = from(CharArrayLatin1Iterator(chars))
    fun from(chars: CharArray, off: Int, len: Int) =
        from(CharArrayLatin1Iterator(chars, off, len))

    fun fromAscii(bytes: ByteArray) = from(ByteArrayLatin1Iterator(bytes))
    fun fromAscii(bytes: ByteArray, off: Int, len: Int) =
        from(ByteArrayLatin1Iterator(bytes, off, len))


    /**
     * Factory methods for constructing a numeric value from a hexadecimal representation
     * in Latin-1 (ASCII) encoded input.
     *
     * Each overload accepts a different input source — `String`, `CharSequence`,
     * `CharArray`, or `ByteArray` — and parses its contents as a hexadecimal number.
     * The input may include digits '0'–'9' and letters 'A'–'F' or 'a'–'f'.
     *
     * These overloads use specialized iterator types to stream input efficiently,
     * avoiding intermediate string allocations.
     *
     * @receiver none
     * @param str the source string containing hexadecimal digits.
     * @param csq the character sequence containing hexadecimal digits.
     * @param chars the character array containing hexadecimal digits.
     * @param bytes the ASCII byte array containing hexadecimal digits.
     * @param off the starting offset of the input segment (inclusive).
     * @param len the number of characters or bytes to read from the input.
     * @return a numeric value parsed from the hexadecimal input.
     *
     * @see StringLatin1Iterator
     * @see CharSequenceLatin1Iterator
     * @see CharArrayLatin1Iterator
     * @see ByteArrayLatin1Iterator
     */
    fun fromHex(str: String) = fromHex(StringLatin1Iterator(str, 0, str.length))
    fun fromHex(str: String, off: Int, len: Int) = fromHex(StringLatin1Iterator(str, off, len))
    fun fromHex(csq: CharSequence) = fromHex(CharSequenceLatin1Iterator(csq, 0, csq.length))
    fun fromHex(csq: CharSequence, off: Int, len: Int) =
        fromHex(CharSequenceLatin1Iterator(csq, off, len))

    fun fromHex(chars: CharArray) = fromHex(CharArrayLatin1Iterator(chars, 0, chars.size))
    fun fromHex(chars: CharArray, off: Int, len: Int) =
        fromHex(CharArrayLatin1Iterator(chars, off, len))
    fun fromAsciiHex(bytes: ByteArray) =
        fromHex(ByteArrayLatin1Iterator(bytes, 0, bytes.size))
    fun fromAsciiHex(bytes: ByteArray, off: Int, len: Int) =
        fromHex(ByteArrayLatin1Iterator(bytes, off, len))

    /**
     * Determines whether a character is valid in a textual hexadecimal representation.
     *
     * Valid characters include:
     * - Digits '0'–'9'
     * - Letters 'A'–'F' and 'a'–'f'
     * - Underscore '_'
     *
     * Underscores are commonly allowed as digit separators in numeric literals.
     *
     * This function uses a bitmask to efficiently check if the character is one
     * of the allowed hexadecimal characters or an underscore.
     *
     * @param c the character to test
     * @return `true` if [c] is a valid hexadecimal digit or underscore, `false` otherwise
     */
    private inline fun isHexAsciiCharOrUnderscore(c: Char): Boolean {
        // if a bit is turned on, then it is a valid char in
        // hex representation.
        // this means [0-9A-Fa-f_]
        val hexDigitAndUnderscoreMask = 0x007E_8000_007E_03FFL
        val idx = c.code - '0'.code
        return (idx >= 0) and (idx <= 'f'.code - '0'.code) and
                (((hexDigitAndUnderscoreMask ushr idx) and 1L) != 0L)
    }


    /**
     * Parses an unsigned decimal integer from a [Latin1Iterator] into a new [Magia] representing its magnitude.
     *
     * This layer ignores any optional leading sign characters ('+' or '-') and processes only the magnitude.
     * Leading zeros and underscores ('_') are handled according to numeric literal conventions:
     * - Leading zeros are skipped.
     * - Underscores are ignored between digits.
     * - Hexadecimal input prefixed with "0x" or "0X" is delegated to [fromHex].
     *
     * The function accumulates decimal digits in blocks of 9 for efficiency, using
     * [mutateFmaPow10] to multiply and add into the resulting array.
     *
     * Storage is allocated based upon a close estimate, so the returned [Magia] may
     * have leading zero limbs.
     *
     * @param src the input iterator providing characters in Latin-1 encoding.
     * @return a new non-normalized [Magia] representing the magnitude of the parsed integer.
     * @throws IllegalArgumentException if the input does not contain a valid decimal integer.
     */
    fun from(src: Latin1Iterator): Magia {
        invalid_syntax@
        do {
            val bitLen = prefixDetermineBitLen(src)
            when {
                bitLen < 0 -> break@invalid_syntax
                bitLen == 0 -> return MAGIA_ZERO
                bitLen == Int.MAX_VALUE -> return fromHex(src.reset())
            }
            val z = newWithBitLen(bitLen)
            if (parseHelper(src, z, z.size))
                return z
        } while (false)
        throw IllegalArgumentException("integer parse error:$src")
    }

    fun tryParseText(src: Latin1Iterator, mbi: MutableBigInt): Boolean {
        val isNeg = src.peek() == '-'
        val bitLen = prefixDetermineBitLen(src)
        when {
            bitLen < 0 -> return false
            bitLen == 0 -> {
                mbi.setZero()
                return true
            }
            bitLen == Int.MAX_VALUE -> return tryParseHexText(src, mbi)
        }
        val limbLen = limbLenFromBitLen(bitLen)
        mbi.updateMeta(Meta(0))
        mbi.ensureMagiaCapacityCopyZeroExtend(limbLen)
        if (! parseHelper(src, mbi.magia, limbLen))
            return false
        val normLen = normLen(mbi.magia, limbLen)
        mbi.updateMeta(Meta(isNeg, normLen))
        return true
    }

    fun tryParseHexText(src: Latin1Iterator, mbi: MutableBigInt): Boolean {
        val sign = src.peek() == '-'
        val nybbleCount = hexNybbleCount(src)
        if (nybbleCount >= 0) {
            if (nybbleCount == 0) {
                mbi.setZero()
                return true
            }
            mbi.updateMeta(Meta(0))
            val limbLen = (nybbleCount + 7) ushr 3
            mbi.ensureMagiaCapacityDiscard(limbLen)
            parseHexHelper(src, nybbleCount, mbi.magia, limbLen)
            mbi.updateMeta(Meta(sign, limbLen))
            return true
        }
        return false
    }

    /**
     * Scans the textual prefix of a decimal (or hexadecimal) integer literal and
     * determines how many bits of storage are required for its magnitude.
     *
     * This function performs **lightweight prefix validation and sizing only**.
     * It does **not** consume the full literal; on success it leaves the iterator
     * positioned at the first significant digit.
     *
     * Parsing rules:
     * - An optional leading '+' or '-' sign is ignored.
     * - Leading zeros are skipped.
     * - Underscores ('_') are permitted *only after* at least one leading zero
     *   has been seen; a leading underscore is rejected.
     * - A prefix of `"0x"` or `"0X"` signals hexadecimal input and is delegated
     *   to the hex parser.
     *
     * Return values:
     * - `-1` if the prefix is syntactically invalid.
     * - `0` if the value is exactly zero.
     * - `Int.MAX_VALUE` if hexadecimal parsing is required.
     * - Otherwise, a conservative upper bound on the number of bits required
     *   to store the decimal magnitude.
     *
     * The returned bit length is computed from the remaining decimal digit count
     * using a fixed-point ⌈log₂(10)⌉ approximation. The estimate may slightly
     * over-allocate but is guaranteed to be sufficient.
     *
     * On success, the iterator is rewound by one character so that the first
     * non-prefix digit will be re-read by the main parser.
     *
     * @param src the input iterator positioned at the start of the literal
     * @return sizing sentinel or estimated bit length as described above
     */
    private fun prefixDetermineBitLen(src: Latin1Iterator): Int {
        var leadingZeroSeen = false
        var ch = src.nextChar()
        if (ch == '-' || ch == '+') // discard leading sign
            ch = src.nextChar()
        if (ch == '0') { // discard leading zero
            ch = src.nextChar()
            if (ch == 'x' || ch == 'X')
                return Int.MAX_VALUE
            leadingZeroSeen = true
        }
        while (ch == '0' || ch == '_') {
            if (ch == '_' && !leadingZeroSeen)
                return -1
            leadingZeroSeen = leadingZeroSeen or (ch == '0')
            ch = src.nextChar() // discard all leading zeros
        }
        val remainingLen = src.remainingLen() + if (ch == '\u0000') 0 else 1
        // val bitLen = (remainingLen * 13607 + 4095) ushr 12
        val roundUp32 = (1uL shl 32) - 1uL
        val bitLen =
            ((remainingLen.toULong() * LOG2_10_CEIL_32 + roundUp32) shr 32).toInt()
        if (bitLen == 0) {
            if (leadingZeroSeen)
                return 0
            return -1
        }
        src.prevChar() // back up one
        return bitLen
    }

    /**
     * Parses the decimal digit sequence of an unsigned integer literal into the
     * provided magnitude buffer.
     *
     * The iterator is expected to be positioned at the first significant digit
     * (after any prefix handling). Only base-10 digits and underscores are accepted:
     *
     * - Digits `'0'..'9'` are accumulated.
     * - Underscores (`'_'`) are ignored and may appear between digits.
     * - Parsing stops at the first non-digit, non-underscore character.
     *
     * Digits are processed in blocks of up to 9 and folded into `z` using
     * repeated fused multiply-add operations via [mutateFmaPow10].
     *
     * Success conditions:
     * - The input must terminate (`'\u0000'`) after the digit sequence.
     * - The final character must not be an underscore.
     *
     * On success, `z` contains the parsed magnitude (not normalized), and
     * `true` is returned. On failure, `z` is left partially modified and
     * `false` is returned.
     *
     * This function performs **no sign handling, no normalization, and no
     * trailing validation** beyond the digit sequence itself.
     *
     * @param src the input iterator positioned at the first digit
     * @param z the target magnitude buffer (pre-allocated)
     * @return `true` if parsing completed successfully, `false` otherwise
     */
    private fun parseHelper(src: Latin1Iterator, z: Magia, zLen: Int): Boolean {
        var accumulator = 0u
        var accumulatorDigitCount = 0
        var ch = '\u0000'
        var chLast = '\u0000'
        while (true) {
            chLast = ch
            ch = src.nextChar()
            if (ch == '_')
                continue
            if (ch !in '0'..'9')
                break
            val n = ch - '0'
            accumulator = accumulator * 10u + n.toUInt()
            ++accumulatorDigitCount
            if (accumulatorDigitCount < 9)
                continue
            mutateFmaPow10(z, zLen, 9, accumulator)
            accumulator = 0u
            accumulatorDigitCount = 0
        }
        if (ch == '\u0000' && chLast != '_') {
            if (accumulatorDigitCount > 0)
                mutateFmaPow10(z, zLen, accumulatorDigitCount, accumulator)
            return true
        }
        return false;
    }

    /**
     * Parses a hexadecimal integer from [src] and returns its magnitude as a normalized limb array.
     *
     * The input may include an optional leading sign, an optional `0x`/`0X` prefix, and `_` digit
     * separators. Leading zeros are permitted. The result is always returned in canonical form:
     * the top limb is non-zero for non-zero values, and zero is returned as [MAGIA_ZERO].
     *
     * @param src the input iterator providing characters in Latin-1 encoding.
     * @return a new normalized [Magia] representing the magnitude of the parsed integer.
     * @throws IllegalArgumentException if the input has invalid syntax or contains no digits.
     */
    internal fun fromHex(src: Latin1Iterator): Magia {
        val nybbleCount = hexNybbleCount(src)
        if (nybbleCount >= 0) {
            if (nybbleCount == 0)
                return MAGIA_ZERO
            val z = newWithBitLen(nybbleCount shl 2)
            parseHexHelper(src, nybbleCount, z, z.size)
            return z
        }
        throw IllegalArgumentException("hex integer parse error")
    }



    internal fun hexNybbleCount(src: Latin1Iterator): Int {
        var leadingZeroSeen = false
        var ch = src.nextChar()
        if (ch == '+' || ch == '-')
            ch = src.nextChar()
        if (ch == '0') {
            ch = src.nextChar()
            if (ch == 'x' || ch == 'X')
                ch = src.nextChar()
            else
                leadingZeroSeen = true
        }
        while (ch == '0' || ch == '_') {
            if (ch == '_' && !leadingZeroSeen)
                return -1
            leadingZeroSeen = leadingZeroSeen or (ch == '0')
            ch = src.nextChar()
        }
        if (ch != '\u0000')
            src.prevChar() // back up one
        var nybbleCount = 0
        while (src.hasNext()) {
            ch = src.nextChar()
            if (!isHexAsciiCharOrUnderscore(ch))
                return -1
            nybbleCount += if (ch == '_') 0 else 1
        }
        if (ch == '_') // last char seen was '_'
            return -1
        if (nybbleCount == 0) {
            if (leadingZeroSeen)
                return 0
            return -1
        }
        return nybbleCount
    }

    private fun parseHexHelper(src: Latin1Iterator, nybbleCount: Int, z: Magia, zNormLen: Int) {
        var nybblesLeft = nybbleCount
        for (k in 0..<z.size) {
            var w = 0
            val stepCount = min(nybblesLeft, 8)
            repeat(stepCount) { n ->
                var ch: Char
                do {
                    ch = src.prevChar()
                } while (ch == '_')
                val nybble = when (ch) {
                    in '0'..'9' -> ch - '0'
                    in 'A'..'F' -> ch - 'A' + 10
                    in 'a'..'f' -> ch - 'a' + 10
                    else -> throw IllegalStateException()
                }
                w = w or (nybble shl (n shl 2))
            }
            z[k] = w // compiler knows 0 <= k < zLen <= z.size, bounds check can be eliminated
            nybblesLeft -= stepCount
        }
    }
}