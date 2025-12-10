@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

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

}