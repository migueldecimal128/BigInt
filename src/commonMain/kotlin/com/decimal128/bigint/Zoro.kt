@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import kotlin.math.absoluteValue
import kotlin.math.max

/**
 * These are operations that are layered above Magia and
 * below [BigInt] and [BigIntAccumulator].
 *
 * Zoro is short for Zoroaster ...
 * - interpreter of metadata
 * - wielder of magia
 */
object Zoro {


    /**
     * Standard signum function.
     *
     * @return -1 if negative, 0 if zero, 1 if positive
     */
    inline fun signum(meta: Meta) = (meta.meta shr 31) or ((-meta.meta) ushr 31)

    /**
     * Returns `true` if this value is exactly representable as a 32-bit
     * signed integer (`Int.MIN_VALUE .. Int.MAX_VALUE`).
     *
     * Only values whose magnitude fits in one 32-bit limb (or zero) pass
     * this check.
     */
    fun fitsInt(meta: Meta, magia: Magia): Boolean {
        if (meta.isZero)
            return true
        if (meta.normLen > 1)
            return false
        val limb = magia[0]
        if (limb >= 0)
            return true
        return meta.isNegative && limb == Int.MIN_VALUE
    }

    /**
     * Returns `true` if this value is non-negative and fits in an unsigned
     * 32-bit integer (`0 .. UInt.MAX_VALUE`).
     */
    fun fitsUInt(meta: Meta) = meta.isPositive && meta.normLen <= 1

    /**
     * Returns `true` if this value fits in a signed 64-bit integer
     * (`Long.MIN_VALUE .. Long.MAX_VALUE`).
     */
    fun fitsLong(meta: Meta, magia: Magia): Boolean {
        return when {
            meta.normLen > 2 -> false
            meta.normLen < 2 -> true
            magia[1] >= 0 -> true
            else -> meta.isNegative && magia[1] == Int.MIN_VALUE && magia[0] == 0
        }
    }

    /**
     * Returns `true` if this value is non-negative and fits in an unsigned
     * 64-bit integer (`0 .. ULong.MAX_VALUE`).
     */
    fun fitsULong(meta: Meta) = meta.isPositive && meta.normLen <= 2


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
    fun toInt(meta: Meta, magia: Magia) =
        if (magia.isEmpty()) 0 else (magia[0] xor meta.signMask) - meta.signMask

    /**
     * Returns this value as a signed `Int`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toInt], this performs a
     * strict range check instead of truncating the upper bits.
     */
    fun toIntExact(meta: Meta, magia: Magia): Int =
        if (fitsInt(meta, magia))
            toInt(meta, magia)
        else
            throw ArithmeticException("BigInt out of Int range")

    /**
     * Returns this BigInt as a signed Int, clamped to `Int.MIN_VALUE..Int.MAX_VALUE`.
     *
     * Values greater than `Int.MAX_VALUE` return `Int.MAX_VALUE`.
     * Values less than `Int.MIN_VALUE` return `Int.MIN_VALUE`.
     */
    fun toIntClamped(meta: Meta, magia: Magia): Int {
        val bitLen = Magus.bitLen(magia)
        if (bitLen == 0)
            return 0
        val mag = magia[0]
        return if (meta.isPositive) {
            if (bitLen <= 31) mag else Int.MAX_VALUE
        } else {
            if (bitLen <= 31) -mag else Int.MIN_VALUE
        }
    }

    /**
     * Returns the low 32 bits of this value interpreted as an unsigned
     * two’s-complement `UInt` (i.e., wraps modulo 2³², like `Long.toUInt()`).
     */
    fun toUInt(meta: Meta, magia: Magia) = toInt(meta, magia).toUInt()

    /**
     * Returns this value as a `UInt`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toUInt], this checks
     * that the value is within the unsigned 32-bit range.
     */
    fun toUIntExact(meta: Meta, magia: Magia): UInt =
        if (fitsUInt(meta))
            toUInt(meta, magia)
        else
            throw ArithmeticException("BigInt out of UInt range")

    /**
     * Returns this BigInt as an unsigned UInt, clamped to `0..UInt.MAX_VALUE`.
     *
     * Values greater than `UInt.MAX_VALUE` return `UInt.MAX_VALUE`.
     * Negative values return 0.
     */
    fun toUIntClamped(meta: Meta, magia: Magia): UInt {
        if (meta.isPositive) {
            val bitLen = Magus.bitLen(magia)
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
    fun toLong(meta: Meta, magia: Magia): Long {
        val l = when (meta.normLen) {
            0 -> 0L
            1 -> magia[0].toUInt().toLong()
            else -> (magia[1].toLong() shl 32) or magia[0].toUInt().toLong()
        }
        val mask = meta.signMask.toLong()
        return (l xor mask) - mask
    }

    /**
     * Returns this value as a `Long`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toLong], this checks
     * that the value lies within the signed 64-bit range.
     */
    fun toLongExact(meta: Meta, magia: Magia): Long =
        if (fitsLong(meta, magia))
            toLong(meta, magia)
        else
            throw ArithmeticException("BigInt out of Long range")

    /**
     * Returns this BigInt as a signed Long, clamped to `Long.MIN_VALUE..Long.MAX_VALUE`.
     *
     * Values greater than `Long.MAX_VALUE` return `Long.MAX_VALUE`.
     * Values less than `Long.MIN_VALUE` return `Long.MIN_VALUE`.
     */
    fun toLongClamped(meta: Meta, magia: Magia): Long {
        val bitLen = Magus.bitLen(magia)
        val magnitude = when (magia.size) {
            0 -> 0L
            1 -> magia[0].toLong() and 0xFFFF_FFFFL
            else -> (magia[1].toLong() shl 32) or (magia[0].toLong() and 0xFFFF_FFFFL)
        }
        return if (meta.isPositive) {
            if (bitLen <= 63) magnitude else Long.MAX_VALUE
        } else {
            if (bitLen <= 63) -magnitude else Long.MIN_VALUE
        }
    }

    /**
     * Returns the low 64 bits of this value interpreted as an unsigned
     * two’s-complement `ULong` (wraps modulo 2⁶⁴, like `Long.toULong()`).
     */
    fun toULong(meta: Meta, magia: Magia): ULong = toLong(meta, magia).toULong()

    /**
     * Returns this value as a `ULong`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toULong], this checks
     * that the value is within the unsigned 64-bit range.
     */
    fun toULongExact(meta: Meta, magia: Magia): ULong =
        if (fitsULong(meta))
            toULong(meta, magia)
        else
            throw ArithmeticException("BigInt out of ULong range")

    /**
     * Returns this BigInt as an unsigned ULong, clamped to `0..ULong.MAX_VALUE`.
     *
     * Values greater than `ULong.MAX_VALUE` return `ULong.MAX_VALUE`.
     * Negative values return 0.
     */
    fun toULongClamped(meta: Meta, magia: Magia): ULong {
        if (meta.isPositive) {
            val bitLen = Magus.bitLen(magia)
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
    fun toUIntMagnitude(meta: Meta, magia: Magia): UInt =
        if (meta.normLen == 0) 0u else magia[0].toUInt()

    /**
     * Returns the low 64 bits of the magnitude as a `ULong`
     * (ignores the sign).
     */
    fun toULongMagnitude(meta: Meta, magia: Magia): ULong {
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
     * This works on the magnitude ... the sign is ignored.
     *
     * @throws IllegalArgumentException if `bitIndex` is negative.
     */
    fun extractULongAtBitIndex(meta: Meta, magia: Magia, bitIndex: Int): ULong {
        if (bitIndex >= 0)
            return Magus.extractULongAtBitIndex(magia, meta.normLen, bitIndex)
        throw IllegalArgumentException("invalid bitIndex:$bitIndex")
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
    fun compare(xMeta: Meta, xMagia: Magia, yMeta: Meta, yMagia: Magia): Int {
        if (xMeta.signMask != yMeta.signMask)
            return xMeta.signMask or 1
        val cmp = Magus.compare(xMagia, xMeta.normLen, yMagia, yMeta.normLen)
        return xMeta.negateIfNegative(cmp)
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
    fun compare(xMeta: Meta, xMagia: Magia, n: Int) =
        compareHelper(xMeta, xMagia, n < 0, n.absoluteValue.toUInt().toULong())

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
    fun compare(xMeta: Meta, xMagia: Magia, w: UInt) =
        compareHelper(xMeta, xMagia, false, w.toULong())

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
    fun compare(xMeta: Meta, xMagia: Magia, l: Long) =
        compareHelper(xMeta, xMagia, l < 0, l.absoluteValue.toULong())

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
    fun compare(xMeta: Meta, xMagia: Magia, dw: ULong) =
        compareHelper(xMeta, xMagia, false, dw)

    /**
     * Helper for comparing this BigInt to an unsigned 64-bit integer.
     *
     * @param dwSign sign of the ULong operand
     * @param dwMag the ULong magnitude
     * @return -1 if this < ulMag, 0 if equal, 1 if this > ulMag
     */
    private fun compareHelper(meta: Meta, magia: Magia, dwSign: Boolean, dwMag: ULong): Int {
        if (meta.isNegative != dwSign)
            return meta.signMask or 1
        val cmp = Magus.compare(magia, meta.normLen, dwMag)
        return if (dwSign) -cmp else cmp
    }



    /**
     * Compares magnitudes, disregarding sign flags.
     *
     * @return -1,0,1
     */
    fun magnitudeCompare(xMeta: Meta, xMagia: Magia, yMeta: Meta, yMagia: Magia) =
        Magus.compare(xMagia, xMeta.normLen, yMagia, yMeta.normLen)
    fun magnitudeCompare(xMeta: Meta, xMagia: Magia, w: UInt) =
        Magus.compare(xMagia, xMeta.normLen, w.toULong())
    fun magnitudeCompare(xMeta: Meta, xMagia: Magia, dw: ULong) =
        Magus.compare(xMagia, xMeta.normLen, dw)
    fun magnitudeCompare(xMeta: Meta, xMagia: Magia, littleEndianIntArray: IntArray) =
        Magus.compare(xMagia, xMeta.normLen,
            littleEndianIntArray, Magus.normLen(littleEndianIntArray))

    /**
     * Comparison predicate for numerical equality between two
     * [Meta]/[Magia] pairs
     */
    fun EQ(xMeta: Meta, xMagia: Magia, yMeta: Meta, yMagia: Magia): Boolean =
        (xMeta.meta == yMeta.meta) &&
                Magus.compare(xMagia, xMeta.normLen, yMagia, yMeta.normLen) == 0

    /**
     * Comparison predicate for numerical equality with a signed 32-bit integer.
     *
     * @param n the [Int] value to compare with
     * @return `true` if this value equals [n], `false` otherwise
     */
    fun EQ(xMeta: Meta, xMagia: Magia, n: Int): Boolean = compare(xMeta, xMagia, n) == 0

    /**
     * Comparison predicate for numerical equality with an unsigned 32-bit integer.
     *
     * @param w the [UInt] value to compare with
     * @return `true` if this value equals [w], `false` otherwise
     */
    fun EQ(xMeta: Meta, xMagia: Magia, w: UInt): Boolean = compare(xMeta, xMagia, w) == 0

    /**
     * Comparison predicate for numerical equality with a signed 64-bit integer.
     *
     * @param l the [Long] value to compare with
     * @return `true` if this value equals [l], `false` otherwise
     */
    fun EQ(xMeta: Meta, xMagia: Magia, l: Long): Boolean = compare(xMeta, xMagia, l) == 0

    /**
     * Comparison predicate for numerical equality with an unsigned 64-bit integer.
     *
     * @param dw the [ULong] value to compare with
     * @return `true` if this value equals [dw], `false` otherwise
     */
    fun EQ(xMeta: Meta, xMagia: Magia, dw: ULong): Boolean = compare(xMeta, xMagia, dw) == 0

    /**
     * Returns the decimal string representation of this BigInt.
     *
     * - Negative values are prefixed with a `-` sign.
     * - Equivalent to calling `java.math.BigInteger.toString()`.
     *
     * @return a decimal string representing the value of this BigInt
     */
    fun toString(meta: Meta, magia: Magia): String =
        Magus.toString(meta.isNegative, magia, meta.normLen)

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
    fun toHexString(meta: Meta, magia: Magia): String =
        toHexString(meta, magia, HEX_PREFIX_UTF8_0x, useUpperCase = true, minPrintLength = 1, HEX_SUFFIX_UTF8_nada)

    fun toHexString(meta: Meta, magia: Magia, hexFormat: HexFormat): String {
        if (hexFormat === HexFormat.UpperCase)
            return toHexString(meta, magia)
        return toHexString(meta, magia,
            hexFormat.number.prefix.encodeToByteArray(),
            hexFormat.upperCase,
            hexFormat.number.minLength,
            hexFormat.number.suffix.encodeToByteArray()
        )
    }

    private fun toHexString(meta: Meta, magia: Magia, prefixUtf8: ByteArray, useUpperCase: Boolean, minPrintLength: Int, suffixUtf8: ByteArray): String {
        val signCount = meta.signBit
        val prefixCount = prefixUtf8.size
        val nybbleCount = max((magnitudeBitLen(meta, magia) + 3) / 4, minPrintLength)
        val suffixCount = suffixUtf8.size
        val totalLen = signCount + prefixCount + nybbleCount + suffixCount
        val utf8 = ByteArray(totalLen)
        utf8[0] = '-'.code.toByte()
        var ich = signCount
        for (b in prefixUtf8) {
            utf8[ich] = b
            ++ich
        }
        Magus.toHexUtf8(magia, meta.normLen, utf8, signCount + prefixCount, nybbleCount, useUpperCase)
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
    fun toTwosComplementBigEndianByteArray(meta: Meta, magia: Magia): ByteArray =
        toBinaryByteArray(meta, magia, isTwosComplement = true, isBigEndian = true)

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
    fun toBinaryByteArray(meta: Meta, magia: Magia, isTwosComplement: Boolean, isBigEndian: Boolean): ByteArray =
        Magus.toBinaryByteArray(meta.isNegative, magia, meta.normLen, isTwosComplement, isBigEndian)

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
    fun toBinaryBytes(meta: Meta, magia: Magia,
        isTwosComplement: Boolean, isBigEndian: Boolean,
        bytes: ByteArray, offset: Int = 0, requestedLength: Int = -1
    ): Int =
        Magus.toBinaryBytes(
            magia, isTwosComplement && meta.isNegative, isBigEndian,
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
    fun magnitudeToLittleEndianIntArray(meta: Meta, magia: Magia): IntArray = Magus.newNormalizedCopy(magia, meta.normLen)

    /**
     * Returns a copy of the magnitude as a little-endian LongArray.
     *
     * - Combines every two 32-bit limbs into a 64-bit long.
     * - Least significant bits are in index 0.
     * - Sign is ignored; only the magnitude is returned.
     *
     * @return a new LongArray containing the magnitude in little-endian order
     */
    fun magnitudeToLittleEndianLongArray(meta: Meta, magia: Magia): LongArray {
        val intLen = meta.normLen
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
     * Returns the bit-length of the magnitude of this BigInt.
     *
     * Equivalent to the number of bits required to represent the absolute value.
     */
    fun magnitudeBitLen(meta: Meta, magia: Magia): Int =
        Magus.bitLen(magia, meta.normLen)

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
    // FIXME - move bitLengthBigIntegerStyle out of Magus and into Zoro
    fun bitLengthBigIntegerStyle(meta: Meta, magia: Magia): Int =
        Magus.bitLengthBigIntegerStyle(meta.isNegative, magia)

    /**
     * Returns the number of 32-bit integers required to store the binary magnitude.
     */
    fun magnitudeIntArrayLen(meta: Meta, magia: Magia) =
        (magnitudeBitLen(meta, magia) + 31) ushr 5

    /**
     * Returns the number of 64-bit longs required to store the binary magnitude.
     */
    fun magnitudeLongArrayLen(meta: Meta, magia: Magia) =
        (magnitudeBitLen(meta, magia) + 63) ushr 6


}
