package com.decimal128.bigint

import com.decimal128.bigint.Mago.normLen
import kotlin.math.absoluteValue

sealed class BigIntBase(
    internal var _meta: Meta,
    internal var _magia: Magia
) {
    internal val meta:Meta get() = _meta
    internal val magia:Magia get() = _magia

    companion object {
        internal val ERR_MSG_DIV_BY_ZERO = "div by zero"
        internal val ERR_MSG_MOD_NEG_DIVISOR = "modulus with a negative divisor is undefined"
        internal val ERR_MSG_NEG_BITCOUNT = "negative bitCount"
        internal val ERR_MSG_BITLEN_LE_0 = "invalid bitLen <= 0"
        internal val ERR_MSG_INVALID_BITLEN_RANGE = "invalid bitLen range: 0 <= bitLenMin <= bitLenMax"
        internal val ERR_MSG_NEG_BITINDEX = "negative bitIndex"
    }
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
     * Returns `true` if this MutableBigInt currently is One
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
     * Zero is considered even, not odd.
     */
    fun isOdd() = isNotZero() && (magia[0] and 1) != 0

    /**
     * Returns `true` if the magnitude of this BigInt is a power of two
     * (exactly one bit set).
     */
    fun isMagnitudePowerOfTwo(): Boolean = Mago.isPowerOfTwo(magia, meta.normLen)

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
    internal fun isNormalized(): Boolean =
        Mago.isNormalized(magia, meta.normLen) && meta._meta != Int.MIN_VALUE

    internal fun isSuperNormalized(): Boolean =
        isNormalized() && meta.normLen == magia.size

    /**
     * Returns `true` if this value is exactly representable as a 32-bit
     * signed integer (`Int.MIN_VALUE .. Int.MAX_VALUE`).
     *
     * Only values whose magnitude fits in one 32-bit limb (or zero) pass
     * this check.
     */
    fun fitsInt(): Boolean {
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
    fun fitsUInt(): Boolean = meta.isPositive && meta.normLen <= 1

    /**
     * Returns `true` if this value fits in a signed 64-bit integer
     * (`Long.MIN_VALUE .. Long.MAX_VALUE`).
     */
    fun fitsLong(): Boolean {
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
    fun fitsULong(): Boolean = meta.isPositive && meta.normLen <= 2

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
    fun toInt() =
        if (isZero()) 0 else (magia[0] xor meta.signMask) - meta.signMask

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
    fun toIntClamped(): Int = when {
        magnitudeBitLen() <= 31 -> toInt()
        meta.isPositive -> Int.MAX_VALUE
        else -> Int.MIN_VALUE
    }

    /**
     * Returns the low 32 bits of this value interpreted as an unsigned
     * two’s-complement `UInt` (i.e., wraps modulo 2³², like `Long.toUInt()`).
     */
    fun toUInt(): UInt = toInt().toUInt()

    /**
     * Returns this value as a `UInt`, throwing an [ArithmeticException]
     * if it cannot be represented exactly. Unlike [toUInt], this checks
     * that the value is within the unsigned 32-bit range.
     */
    fun toUIntExact(): UInt =
        if (fitsUInt())
            toUInt()
        else
            throw ArithmeticException("out of UInt range")

    /**
     * Returns this BigInt as an unsigned UInt, clamped to `0..UInt.MAX_VALUE`.
     *
     * Values greater than `UInt.MAX_VALUE` return `UInt.MAX_VALUE`.
     * Negative values return 0.
     */
    fun toUIntClamped(): UInt = when {
        meta.isNegative -> 0u
        magnitudeBitLen() <= 32 -> toUInt()
        else -> UInt.MAX_VALUE
    }

    /**
     * Returns the low 64 bits of this value as a signed two’s-complement `Long`.
     *
     * The result is formed from the lowest 64 bits of the magnitude, with the
     * sign applied afterward; upper bits are discarded (wraps modulo 2⁶⁴),
     * matching `Long` conversion behavior.
     */
    fun toLong(): Long {
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
    fun toLongExact(): Long =
        if (fitsLong())
            toLong()
        else
            throw ArithmeticException("out of Long range")

    /**
     * Returns this BigInt as a signed Long, clamped to `Long.MIN_VALUE..Long.MAX_VALUE`.
     *
     * Values greater than `Long.MAX_VALUE` return `Long.MAX_VALUE`.
     * Values less than `Long.MIN_VALUE` return `Long.MIN_VALUE`.
     */
    fun toLongClamped(): Long = when {
        magnitudeBitLen() <= 63 -> toLong()
        meta.isPositive -> Long.MAX_VALUE
        else -> Long.MIN_VALUE
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
            throw ArithmeticException("out of ULong range")

    /**
     * Returns this BigInt as an unsigned ULong, clamped to `0..ULong.MAX_VALUE`.
     *
     * Values greater than `ULong.MAX_VALUE` return `ULong.MAX_VALUE`.
     * Negative values return 0.
     */
    fun toULongClamped(): ULong = when {
        meta.isNegative -> 0uL
        magnitudeBitLen() <= 64 -> toULong()
        else -> ULong.MAX_VALUE
    }

    /**
     * Returns the low 32 bits of the magnitude as a `UInt`
     * (ignores the sign).
     */
    fun toUIntMagnitude(): UInt =
        if (meta.normLen == 0) 0u else magia[0].toUInt()

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
    fun extractULongAtBitIndex(bitIndex: Int): ULong =
        Mago.extractULongAtBitIndex(magia, meta.normLen, bitIndex)

    /**
     * Returns the bit-length of the magnitude of this BigInt.
     *
     * Equivalent to the number of bits required to represent the absolute value.
     */
    fun magnitudeBitLen() = Mago.bitLen(magia, meta.normLen)

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
        Mago.bitLengthBigIntegerStyle(meta.signFlag, magia, meta.normLen)

    /**
     * Returns the number of 32-bit integers required to store the binary magnitude.
     */
    fun magnitudeIntArrayLen() = (magnitudeBitLen() + 31) ushr 5

    /**
     * Returns the number of 64-bit longs required to store the binary magnitude.
     */
    fun magnitudeLongArrayLen() = (magnitudeBitLen() + 63) ushr 6

    /**
     * Computes the number of bytes needed to represent this BigInt
     * in two's-complement format.
     *
     * Always returns at least 1 for zero.
     */
    fun calcTwosComplementByteLength(): Int {
        if (meta.isZero)
            return 1
        // add one for the sign bit ...
        // ... since bitLengthBigIntegerStyle does not include the sign bit
        val bitLen2sComplement = bitLengthBigIntegerStyle() + 1
        val byteLength = (bitLen2sComplement + 7) ushr 3
        return byteLength
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
    fun countTrailingZeroBits(): Int = Mago.ctz(magia, meta.normLen)

    /**
     * Counts the number of set bits (population count) in the normalized magnitude.
     *
     * @return the total number of set bits in the magnitude.
     *
     * @throws IllegalStateException if `meta.normLen` is out of bounds.
     */
    fun magnitudeCountOneBits(): Int {
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
    fun testBit(bitIndex: Int): Boolean = Mago.testBit(magia, meta.normLen, bitIndex)

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
    operator fun compareTo(other: BigIntBase): Int {
        if (meta.signMask != other.meta.signMask)
            return meta.signMask or 1
        val cmp = Mago.compare(magia, meta.normLen, other.magia, other.meta.normLen)
        return meta.negateIfNegative(cmp)
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
    operator fun compareTo(n: Int): Int = compareToHelper(n < 0, n.absoluteValue.toUInt().toULong())

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
    operator fun compareTo(w: UInt): Int = compareToHelper(false, w.toULong())

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
    operator fun compareTo(l: Long): Int = compareToHelper(l < 0, l.absoluteValue.toULong())

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
    operator fun compareTo(dw: ULong): Int = compareToHelper(false, dw)

    /**
     * Helper for comparing this BigInt to an unsigned 64-bit integer.
     *
     * @param dwSign sign of the ULong operand
     * @param dwMag the ULong magnitude
     * @return -1 if this < ulMag, 0 if equal, 1 if this > ulMag
     */
    fun compareToHelper(dwSign: Boolean, dwMag: ULong): Int {
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
    fun magnitudeCompareTo(n: Int) =
        Mago.compare(magia, meta.normLen, n.absoluteValue.toUInt().toULong())
    fun magnitudeCompareTo(w: UInt) =
        Mago.compare(magia, meta.normLen, w.toULong())
    fun magnitudeCompareTo(l: Long) =
        Mago.compare(magia, meta.normLen, l.absoluteValue.toULong())
    fun magnitudeCompareTo(dw: ULong) =
        Mago.compare(magia, meta.normLen, dw)
    fun magnitudeCompareTo(littleEndianIntArray: IntArray, length: Int = littleEndianIntArray.size) =
        Mago.compare(magia, meta.normLen, littleEndianIntArray, Mago.normLen(littleEndianIntArray, length))
    fun magnitudeCompareTo(other: BigIntBase): Int =
        Mago.compare(magia, meta.normLen, other.magia, other.meta.normLen)
    internal fun magnitudeCompareTo(otherMeta: Meta, otherMagia: Magia) =
        Mago.compare(magia, meta.normLen, otherMagia, otherMeta.normLen)

    /**
     * Comparison predicate for numerical equality with another [BigInt] or
     * [MutableBigInt].
     *
     * @param other the [BigInt] or [MutableBigInt] to compare with
     * @return `true` if both have the same sign and identical magnitude, `false` otherwise
     */
    infix fun EQ(other: BigIntBase): Boolean =
        compareTo(other) == 0

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
    infix fun NE(other: BigInt): Boolean = !EQ(other)

    /**
     * Comparison predicate for numerical inequality with a [MutableBigInt].
     *
     * @param mbi the [MutableBigInt] to compare with
     * @return `true` if signs differ or magnitudes are unequal, `false` otherwise
     */
    infix fun NE(mbi: MutableBigInt): Boolean = !EQ(mbi)

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
     * Comparison predicate for numerical equality with another [BigInt] or
     * [MutableBigInt]
     *
     * @param other the [BigInt] or [MutableBigInt] to compare with
     * @return `true` if both have the same sign and identical magnitude, `false` otherwise
     */
    infix fun magEQ(other: BigIntBase): Boolean = magnitudeCompareTo(other) == 0

    /**
     * Comparison predicate for numerical equality with a signed 32-bit integer.
     *
     * @param n the [Int] value to compare with
     * @return `true` if this value equals [n], `false` otherwise
     */
    infix fun magEQ(n: Int): Boolean = magnitudeCompareTo(n) == 0

    /**
     * Comparison predicate for numerical equality with an unsigned 32-bit integer.
     *
     * @param w the [UInt] value to compare with
     * @return `true` if this value equals [w], `false` otherwise
     */
    infix fun magEQ(w: UInt): Boolean = magnitudeCompareTo(w) == 0

    /**
     * Comparison predicate for numerical equality with a signed 64-bit integer.
     *
     * @param l the [Long] value to compare with
     * @return `true` if this value equals [l], `false` otherwise
     */
    infix fun magEQ(l: Long): Boolean = magnitudeCompareTo(l) == 0

    /**
     * Comparison predicate for numerical equality with an unsigned 64-bit integer.
     *
     * @param dw the [ULong] value to compare with
     * @return `true` if this value equals [dw], `false` otherwise
     */
    infix fun magEQ(dw: ULong): Boolean = magnitudeCompareTo(dw) == 0

    /**
     * Comparison predicate for numerical inequality with another [BigInt].
     *
     * @param other the [BigInt] to compare with
     * @return `true` if signs differ or magnitudes are unequal, `false` otherwise
     */
    infix fun magNE(other: BigInt): Boolean = !magEQ(other)

    /**
     * Comparison predicate for numerical inequality with a [MutableBigInt].
     *
     * @param mbi the [MutableBigInt] to compare with
     * @return `true` if signs differ or magnitudes are unequal, `false` otherwise
     */
    infix fun magNE(mbi: MutableBigInt): Boolean = !magEQ(mbi)

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
    override fun toString() = BigIntParsePrint.toString(meta.isNegative, magia, meta.normLen)

    /**
     * Returns the hexadecimal string representation of this BigInt.
     *
     * - The string is prefixed with `0x`.
     * - Uses uppercase hexadecimal characters.
     * - Negative values are prefixed with a `-` sign before `0x`.
     *
     * @return a hexadecimal string representing the value of this BigInt
     */
    fun toHexString(): String = BigIntParsePrint.toHexString(this)

    fun toHexString(hexFormat: HexFormat): String =
        BigIntParsePrint.toHexString(this, hexFormat)

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
        BigIntSerde.toTwosComplementBigEndianByteArray(this)

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
        BigIntSerde.toBinaryByteArray(this, isTwosComplement, isBigEndian)

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
        BigIntSerde.toBinaryBytes(this,
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
        BigIntSerde.magnitudeToLittleEndianIntArray(this)

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
        BigIntSerde.magnitudeToLittleEndianLongArray(this)

    /**
     * Returns the integer square root of this value.
     *
     * The result `r` satisfies `r*r ≤ this < (r+1)*(r+1)`.
     */
    fun isqrt(): BigInt = BigIntAlgorithms.isqrt(this)

    /**
     * Returns `true` if this value is a perfect square.
     *
     * This is equivalent to checking whether `isqrt().square() == this`.
     */
    fun isPerfectSquare(): Boolean = BigIntAlgorithms.isPerfectSquare(this)

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
    fun magnitudeHashCode(): Int {
        check (isNormalized())
        var h = 0
        var i = 0
        while (i + 3 < meta.normLen) {
            h = 31 * 31 * 31 * 31 * h +
                    31 * 31 * 31 * magia[i] +
                    31 * 31 * magia[i + 1] +
                    31 * magia[i + 2] +
                    magia[i + 3]
            i += 4
        }
        while (i < meta.normLen) {
            h = 31 * h + magia[i]
            ++i
        }
        return h
    }

    override fun equals(other: Any?): Boolean =
        throw UnsupportedOperationException()

    override fun hashCode(): Int =
        throw UnsupportedOperationException()

    abstract fun toBigInt(): BigInt

}
