package com.decimal128.bigint

import kotlin.math.absoluteValue

sealed class BigIntBase(
    internal var _meta: Meta,
    internal var _magia: Magia
) {
    internal val meta:Meta get() = _meta
    internal val magia:Magia get() = _magia

    /**
     * Returns `true` if this BigInt is zero.
     *
     * All zero values point to the singleton `BigInt.ZERO`.
     */
    fun isZero() = meta.normLen == 0

    /**
     * Returns `true` if this BigInt is not zero.
     */
    fun isNotZero() = meta.normLen > 0

    /**
     * Returns `true` if this BigIntAccumulator currently is One
     */
    fun isOne() = meta._meta == 1 && magia[0] == 1

    /**
     * Returns `true` if this BigInt is negative.
     */
    fun isNegative() = meta.isNegative

    /**
     * returns `true` if this BigInt is non-negative.
     */
    fun isPositive() = meta.isPositive

    /**
     * Standard signum function.
     *
     * @return -1 if negative, 0 if zero, 1 if positive
     */
    fun signum(meta: Meta) = (meta._meta shr 31) or ((-meta._meta) ushr 31)

    /**
     * Returns `true` if this value is even.
     *
     * Zero is considered even.
     */
    fun isEven() = isZero() || (magia[0] and 1) == 0

    /**
     * Returns `true` if this value is odd.
     *
     * Zero is not considered odd.
     */
    fun isOdd() = isNotZero() && (magia[0] and 1) != 0

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
    operator fun compareTo(other: BigInt): Int =
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

}