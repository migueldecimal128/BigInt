@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.Mago.isNormalized
import com.decimal128.bigint.Mago.normLen
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
internal object Zoro {


    /**
     * Standard signum function.
     *
     * @return -1 if negative, 0 if zero, 1 if positive
     */
    inline fun signum(meta: Meta) = (meta.meta shr 31) or ((-meta.meta) ushr 31)

    /**
     * Returns `true` if the magnitude of this BigInt is a power of two
     * (exactly one bit set).
     */
    fun isMagnitudePowerOfTwo(meta: Meta, magia: Magia): Boolean =
        Mago.isPowerOfTwo(magia, meta.normLen)

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
        val bitLen = magnitudeBitLen(meta, magia)
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
            val bitLen = magnitudeBitLen(meta, magia)
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
        val bitLen = magnitudeBitLen(meta, magia)
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
            val bitLen = magnitudeBitLen(meta, magia)
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
            return Mago.extractULongAtBitIndex(magia, meta.normLen, bitIndex)
        throw IllegalArgumentException("invalid bitIndex:$bitIndex")
    }

    /**
     * Counts trailing zero bits in the magnitude defined by [meta] and [magia].
     *
     * Scans limbs `0 ..< meta.normLen` in little-endian order and returns the index of the
     * first set bit, or `-1` if all inspected limbs are zero.
     *
     * @param meta metadata containing the normalized limb length.
     * @param magia magnitude limb array in little-endian order.
     * @return the count of trailing zero bits, or `-1` if the magnitude is zero.
     *
     * @throws IllegalArgumentException if `meta.normLen` is out of bounds.
     */
    internal fun countTrailingZeroBits(meta: Meta, magia: Magia): Int =
        Mago.ctz(magia, meta.normLen)

    /**
     * Counts the number of set bits (population count) in the normalized magnitude.
     *
     * Sums `countOneBits()` over limbs `0 ..< meta.normLen`. Limbs are interpreted in
     * little-endian order. Returns `0` if the magnitude is zero.
     *
     * @param meta metadata containing the normalized limb length.
     * @param magia magnitude limb array in little-endian order.
     * @return the total number of set bits in the magnitude.
     *
     * @throws IllegalStateException if `meta.normLen` is out of bounds.
     */
    fun magnitudeCountOneBits(meta: Meta, magia: Magia): Int {
        if (meta.normLen >= 0 && meta.normLen <= magia.size) { // BCE
            var popCount = 0
            for (i in 0..<meta.normLen)
                popCount += magia[i].countOneBits()
            return popCount
        }
        throw IllegalStateException()
    }


    /**
     * Tests whether the magnitude bit at [bitIndex] is set.
     *
     * @param bitIndex 0-based, starting from the least-significant bit
     * @return true if the bit is set, false otherwise
     */
    fun testBit(meta: Meta, magia: Magia, bitIndex: Int): Boolean =
        Mago.testBit(magia, meta.normLen, bitIndex)


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
        val cmp = Mago.compare(xMagia, xMeta.normLen, yMagia, yMeta.normLen)
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
    fun compareHelper(meta: Meta, magia: Magia, dwSign: Boolean, dwMag: ULong): Int {
        if (meta.isNegative != dwSign)
            return meta.signMask or 1
        val cmp = Mago.compare(magia, meta.normLen, dwMag)
        return if (dwSign) -cmp else cmp
    }



    /**
     * Compares magnitudes, disregarding sign flags.
     *
     * @return -1,0,1
     */
    fun magnitudeCompare(xMeta: Meta, xMagia: Magia, yMeta: Meta, yMagia: Magia) =
        Mago.compare(xMagia, xMeta.normLen, yMagia, yMeta.normLen)
    fun magnitudeCompare(xMeta: Meta, xMagia: Magia, w: UInt) =
        Mago.compare(xMagia, xMeta.normLen, w.toULong())
    fun magnitudeCompare(xMeta: Meta, xMagia: Magia, dw: ULong) =
        Mago.compare(xMagia, xMeta.normLen, dw)
    fun magnitudeCompare(xMeta: Meta, xMagia: Magia, littleEndianIntArray: IntArray) =
        Mago.compare(xMagia, xMeta.normLen,
            littleEndianIntArray, Mago.normLen(littleEndianIntArray))

    /**
     * Returns the decimal string representation of this BigInt.
     *
     * - Negative values are prefixed with a `-` sign.
     * - Equivalent to calling `java.math.BigInteger.toString()`.
     *
     * @return a decimal string representing the value of this BigInt
     */
    fun toString(meta: Meta, magia: Magia): String =
        Mago.toString(meta.isNegative, magia, meta.normLen)

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
        Mago.toHexUtf8(magia, meta.normLen, utf8, signCount + prefixCount, nybbleCount, useUpperCase)
        ich += nybbleCount
        for (b in suffixUtf8) {
            utf8[ich] = b
            ++ich
        }
        return utf8.decodeToString()
    }

    /**
     * Converts the value described by [meta] and [magia] to a big-endian two’s-complement
     * byte array.
     *
     * The result uses the minimal number of bytes required to represent the value,
     * but is always at least one byte long. Negative values are encoded using standard
     * two’s-complement form.
     *
     * @param meta metadata providing the sign and normalized limb length.
     * @param magia magnitude limb array in little-endian order.
     * @return a big-endian two’s-complement byte array.
     */
    fun toTwosComplementBigEndianByteArray(meta: Meta, magia: Magia): ByteArray =
        toBinaryByteArray(meta, magia, isTwosComplement = true, isBigEndian = true)

    /**
     * Converts the value described by [meta] and [magia] to a binary [ByteArray].
     *
     * The output format is controlled by [isTwosComplement] and [isBigEndian]. The returned
     * array uses the minimal number of bytes required to represent the value, but is always
     * at least one byte long.
     *
     * @param meta metadata providing the sign and normalized limb length.
     * @param magia magnitude limb array in little-endian order.
     * @param isTwosComplement whether to encode negative values using two’s-complement form.
     * @param isBigEndian whether the output byte order is big-endian (`true`) or little-endian (`false`).
     * @return a [ByteArray] containing the binary representation of the value.
     *
     * @throws IllegalArgumentException if the magnitude is not normalized.
     */
    fun toBinaryByteArray(meta: Meta, magia: Magia, isTwosComplement: Boolean, isBigEndian: Boolean): ByteArray {
        check (isNormalized(magia, meta.normLen))
        if (meta.normLen >= 0 && meta.normLen <= magia.size) {
            val bitLen =
                if (isTwosComplement) bitLengthBigIntegerStyle(meta, magia) + 1
                else max(magnitudeBitLen(meta, magia), 1)
            val byteLen = (bitLen + 7) ushr 3
            val bytes = ByteArray(byteLen)
            toBinaryBytes(meta, magia, isTwosComplement, isBigEndian, bytes, 0, byteLen)
            return bytes
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Writes the value described by [meta] and [magia] into [bytes] using the requested
     * binary format, without allocating.
     *
     * The encoding is controlled by [isTwosComplement] and [isBigEndian]. If
     * [requestedLen] ≤ 0, the minimal number of bytes required is written (always at
     * least one). If [requestedLen] > 0, exactly that many bytes are written, with
     * sign-extension applied if needed.
     *
     * @param meta metadata providing the sign and normalized limb length.
     * @param magia magnitude limb array in little-endian order.
     * @param isTwosComplement whether to encode negative values using two’s-complement form.
     * @param isBigEndian whether bytes are written in big-endian (`true`) or little-endian (`false`) order.
     * @param bytes destination array to write into.
     * @param offset start index in [bytes].
     * @param requestedLen number of bytes to write, or ≤ 0 to write the minimal length.
     * @return the number of bytes written.
     *
     * @throws IndexOutOfBoundsException if [bytes] is too small.
     * @throws IllegalStateException if [meta] normLen does not fit size of [magia]
     */
    fun toBinaryBytes(meta: Meta, magia: Magia,
        isTwosComplement: Boolean, isBigEndian: Boolean,
        bytes: ByteArray, offset: Int = 0, requestedLen: Int = -1
    ): Int {
        check (isNormalized(magia, meta.normLen))
        if (meta.normLen >= 0 && meta.normLen <= magia.size &&
            offset >= 0 && (requestedLen <= 0 || requestedLen <= bytes.size - offset)) {

            val actualLen = if (requestedLen > 0) requestedLen else {
                val bitLen = if (isTwosComplement)
                    bitLengthBigIntegerStyle(meta, magia) + 1
                else
                    max(magnitudeBitLen(meta, magia), 1)
                (bitLen + 7) ushr 3
            }

            // calculate offsets and stepping direction for BE BigEndian vs LE LittleEndian
            val offB1 = if (isBigEndian) -1 else 1 // BE == -1, LE ==  1
            val offB2 = offB1 shl 1                // BE == -2, LE ==  2
            val offB3 = offB1 + offB2              // BE == -3, LE ==  3
            val step1LoToHi = offB1                // BE == -1, LE ==  1
            val step4LoToHi = offB1 shl 2          // BE == -4, LE ==  4

            val ibLast = offset + actualLen - 1
            val ibLsb = if (isBigEndian) ibLast else offset // index Least significant byte
            val ibMsb = if (isBigEndian) offset else ibLast // index Most significant byte

            val negativeMask = if (meta.isNegative) -1 else 0

            var remaining = actualLen

            var ib = ibLsb
            var iw = 0

            var carry = -negativeMask.toLong() // if (isNegative) then carry = 1 else 0

            while (remaining >= 4 && iw < meta.normLen) {
                val v = magia[iw++]
                carry += (v xor negativeMask).toLong() and 0xFFFF_FFFFL
                val w = carry.toInt()
                carry = carry shr 32

                val b3 = (w shr 24).toByte()
                val b2 = (w shr 16).toByte()
                val b1 = (w shr 8).toByte()
                val b0 = (w).toByte()

                bytes[ib + offB3] = b3
                bytes[ib + offB2] = b2
                bytes[ib + offB1] = b1
                bytes[ib] = b0

                ib += step4LoToHi
                remaining -= 4
            }
            if (remaining > 0) {
                val v = if (iw < meta.normLen) magia[iw++] else 0
                var w = (v xor negativeMask).toLong() + carry.toInt()
                do {
                    bytes[ib] = w.toByte()
                    ib += step1LoToHi
                    w = w shr 8
                } while (--remaining > 0)
            }
            check (iw == meta.normLen)
            check(ib - step1LoToHi == ibMsb)
            return actualLen
        }
        throw IllegalStateException()
    }

    /**
     * Constructs a [Mago] magnitude from a sequence of raw binary bytes.
     *
     * The input bytes represent a non-negative magnitude if [isNegative] is `false`,
     * or a two’s-complement negative number if [isNegative] is `true`. In the latter case,
     * the bytes are complemented and incremented during decoding to produce the corresponding
     * positive magnitude. The sign itself is handled by the caller.
     *
     * The bytes may be in either big-endian or little-endian order, as indicated by [isBigEndian].
     *
     * The return value will be canonical ZERO or a normalized Magia
     *
     * @param isNegative  `true` if bytes encode a negative value in two’s-complement form.
     * @param isBigEndian `true` if bytes are in big-endian order, `false` for little-endian.
     * @param bytes       Source byte array.
     * @param off         Starting offset in [bytes].
     * @param len         Number of bytes to read.
     * @return a normalized [Magia] or ZERO
     * @throws IllegalArgumentException if the range `[off, off + len)` is invalid.
     */
    internal fun fromBinaryBytes(isNegative: Boolean, isBigEndian: Boolean,
                                 bytes: ByteArray, off: Int, len: Int): Magia {
        if (off < 0 || len < 0 || len > bytes.size - off)
            throw IllegalArgumentException()
        if (len == 0)
            return Mago.ZERO

        // calculate offsets and stepping direction for BE BigEndian vs LE LittleEndian
        val offB1 = if (isBigEndian) -1 else 1 // BE == -1, LE ==  1
        val offB2 = offB1 shl 1                // BE == -2, LE ==  2
        val offB3 = offB1 + offB2              // BE == -3, LE ==  3
        val step1HiToLo = - offB1              // BE ==  1, LE == -1
        val step4LoToHi = offB1 shl 2          // BE == -4, LE ==  4

        val ibLast = off + len - 1
        val ibLsb = if (isBigEndian) ibLast else off // index Least significant byte
        var ibMsb = if (isBigEndian) off else ibLast // index Most significant byte

        val negativeMask = if (isNegative) -1 else 0

        // Leading sign-extension bytes (0x00 for non-negative, 0xFF for negative) are flushed
        // If all bytes are flush bytes, the result is [ZERO] or [ONE], depending on [isNegative].
        val leadingFlushByte = negativeMask
        var remaining = len
        while (bytes[ibMsb].toInt() == leadingFlushByte) {
            ibMsb += step1HiToLo
            --remaining
            if (remaining == 0)
                return if (isNegative) Mago.ONE else Mago.ZERO
        }

        val magia = Magia((remaining + 3) ushr 2)

        var ib = ibLsb
        var iw = 0

        var carry = -negativeMask.toLong() // if (isNegative) then carry = 1 else 0

        while (remaining >= 4) {
            val b3 = bytes[ib + offB3].toInt() and 0xFF
            val b2 = bytes[ib + offB2].toInt() and 0xFF
            val b1 = bytes[ib + offB1].toInt() and 0xFF
            val b0 = bytes[ib        ].toInt() and 0xFF
            val w = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            carry += (w xor negativeMask).toLong() and 0xFFFF_FFFFL
            magia[iw++] = carry.toInt()
            carry = carry shr 32
            check ((carry shr 1) == 0L)
            ib += step4LoToHi
            remaining -= 4
        }
        if (remaining > 0) {
            val b3 = negativeMask and 0xFF
            val b2 = (if (remaining == 3) bytes[ib + offB2].toInt() else negativeMask) and 0xFF
            val b1 = (if (remaining >= 2) bytes[ib + offB1].toInt() else negativeMask) and 0xFF
            val b0 = bytes[ib].toInt() and 0xFF
            val w = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            magia[iw++] = (w xor negativeMask) + carry.toInt()
        }
        check(iw == magia.size)
        return magia
    }




    /**
     * Returns a copy of the magnitude as a little-endian IntArray.
     *
     * - Least significant limb is at index 0.
     * - Sign is ignored; only the magnitude is returned.
     *
     * @return a new IntArray containing the magnitude in little-endian order
     */
    fun magnitudeToLittleEndianIntArray(meta: Meta, magia: Magia): IntArray = Mago.newNormalizedCopy(magia, meta.normLen)

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
        Mago.bitLen(magia, meta.normLen)

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

    /**
     * Computes the number of bytes needed to represent this BigInt
     * in two's-complement format.
     *
     * Always returns at least 1 for zero.
     */
    fun calcTwosComplementByteLength(meta: Meta, magia: Magia): Int {
        if (meta.isZero)
            return 1
        // add one for the sign bit ...
        // ... since bitLengthBigIntegerStyle does not include the sign bit
        val bitLen2sComplement = bitLengthBigIntegerStyle(meta, magia) + 1
        val byteLength = (bitLen2sComplement + 7) ushr 3
        return byteLength
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
    fun isNormalized(meta: Meta, magia: Magia): Boolean =
        Mago.isNormalized(magia, meta.normLen)

    /**
     * A superNormal value is one where there are no unused limbs in the
     * magia.
     *
     * That is, the normalized length == magia.size && magia[magia.lastIndex] != 0
     *
     * This is really only used internally in some unit tests to help confirm
     * that normalization is working correctly.
     */
    fun isSuperNormalized(meta: Meta, magia: Magia): Boolean =
        meta.normLen == magia.size && Mago.isNormalized(magia, meta.normLen)


    /**
     * Computes a hash code for the magnitude [x], ignoring any leading
     * zero limbs. The effective length is determined by [normLen],
     * ensuring that numerically equal magnitudes with different limb
     * capacities produce the same hash.
     *
     * The hash is a standard polynomial hash using multiplier 31,
     * identical to applying:
     *
     *     h = 31 * h + limb
     *
     * for each non-zero limb in order.
     *
     * The loop over limbs is manually unrolled in groups of four solely
     * for performance. The result is **bit-for-bit identical** to the
     * non-unrolled version.
     *
     * This function is used by [BigInt.hashCode] so that the hash depends
     * only on the numeric value, not on redundant leading zero limbs or
     * array capacity.
     *
     * @param x the magnitude array in little-endian limb order
     * @return a hash code consistent with numeric equality of magnitudes
     */
    fun normalizedHashCode(x: Magia, xNormLen: Int): Int {
        check (isNormalized(x, xNormLen))
        var h = 0
        var i = 0
        while (i + 3 < xNormLen) {
            h = 31 * 31 * 31 * 31 * h +
                    31 * 31 * 31 * x[i] +
                    31 * 31 * x[i + 1] +
                    31 * x[i + 2] +
                    x[i + 3]
            i += 4
        }
        while (i < xNormLen) {
            h = 31 * h + x[i]
            ++i
        }
        return h
    }

    /**
     * Returns true if the value represented by the lower [xLen] limbs of [x]
     * is an exact power of two.
     *
     * A power of two has exactly one bit set in its binary representation.
     * Limbs above [xLen] are ignored.
     *
     * @param x the array of limbs, in little-endian order.
     * @param xLen the number of valid limbs to examine.
     * @return true if the value is a power of two; false otherwise.
     */
    fun isPowerOfTwo(x: Magia, xNormLen: Int): Boolean {
        check (isNormalized(x, xNormLen))
        if (xNormLen >= 0 && xNormLen <= x.size) {
            var bitSeen = false
            for (i in 0..<xNormLen) {
                val w = x[i]
                if (w != 0) {
                    if (bitSeen || (w and (w - 1)) != 0)
                        return false
                    bitSeen = true
                }
            }
            return bitSeen
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Computes the bit length using Java `BigInteger` semantics.
     *
     * Returns the number of bits required to represent the value in two’s-complement
     * form, excluding the sign bit. For negative values that are exact powers of two,
     * the result is one less than the magnitude bit length.
     *
     * @param meta metadata providing the sign and normalized limb length.
     * @param magia magnitude limb array in little-endian order.
     * @return the bit length following `BigInteger.bitLength()` semantics.
     *
     * @throws IllegalStateException if the magnitude is not normalized.
     */
    fun bitLengthBigIntegerStyle(meta: Meta, magia: Magia): Int {
        check (isNormalized(magia, meta.normLen))
        if (meta.normLen >= 0 && meta.normLen <= magia.size) {
            val bitLen = magnitudeBitLen(meta, magia)
            val isNegPowerOfTwo = meta.signFlag && isPowerOfTwo(magia, meta.normLen)
            val bitLengthBigIntegerStyle = bitLen - if (isNegPowerOfTwo) 1 else 0
            return bitLengthBigIntegerStyle
        }
        throw IllegalStateException()
    }

}
