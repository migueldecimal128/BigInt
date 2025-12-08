// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.Sign.Companion.POSITIVE
import com.decimal128.bigint.intrinsic.unsignedMulHi
import kotlin.math.absoluteValue
import kotlin.math.max

/**
 * A mutable arbitrary-precision integer accumulator for efficient series operations.
 *
 * `BigIntAccumulator` provides mutable arithmetic operations optimized for
 * accumulation tasks commonly encountered when processing numerical series.
 * Supported operations include:
 *
 * - Sum
 * - Sum of squares
 * - Sum of absolute values
 * - Product
 *
 * Unlike [BigInt], which is immutable, `BigIntAccumulator` modifies its internal
 * state in place. By reusing internal arrays, it minimizes heap allocation and
 * garbage collection once a steady state is reached, making it well-suited for
 * iterative or streaming computations.
 *
 * Operations accept integer primitives (`Int`, `Long`, `UInt`, `ULong`),
 * [BigInt] instances, or other [BigIntAccumulator] instances as operands.
 *
 * Typical usage example:
 * ```
 * val sumAcc = BigIntAccumulator()
 * val sumSqrAcc = BigIntAccumulator()
 * val sumAbsAcc = BigIntAccumulator()
 * for (value in data) {
 *     sumAcc += value
 *     sumSqrAcc.addSquareOf(value)
 *     sumAbsAcc.addAbsValueOf(value)
 * }
 * val total = sumAcc.toBigInt()
 * ```
 *
 * ### Internal representation
 *
 * The implementation uses a sign–magnitude format, with a [Boolean] sign flag
 * and a little-endian [IntArray] of 32-bit unsigned limbs.
 *
 * - The magnitude array is named `magia` (MAGnitude IntArray).
 * - Its allocated length is always ≥ 4.
 * - The current number of active limbs is stored in `limbLen`.
 * - Zero is represented as `limbLen == 0`.
 * - When nonzero, `limbLen` is normalized so that the most significant limb
 *   (`magia[limbLen − 1]`) is nonzero.
 *
 * [BigIntAccumulator] also maintains an internal temporary buffer `tmp`,
 * used for intermediate operations (for example, squaring a value before
 * summation). In some cases, `tmp` may be swapped with the main `magia` array
 * to minimize additional allocation or data copying.
 *
 * @constructor Creates a new accumulator initialized to zero.
 * Equivalent to calling `BigIntAccumulator()`.
 * @see BigInt for the immutable arbitrary-precision integer implementation.
 */
class BigIntAccumulator private constructor (
    var meta: Meta,
    var magia: IntArray,
    var tmp1: IntArray) {
    constructor() : this(Meta(0), IntArray(4), Magia.ZERO)

    val normLen: Int
        get() = meta.normLen

    val signBit: Int
        get() = meta.signBit

    val signFlag: Boolean
        get() = meta.signFlag


    private inline fun validate() {
        check (normLen <= magia.size &&
                magia.size >= 4 &&
                (normLen == 0 || magia[normLen - 1] != 0))
    }

    /**
     * Resets this accumulator to zero.
     *
     * This method clears the current value by setting the internal length to zero
     * and resetting the sign to non-negative. The internal buffer remains allocated,
     * allowing future operations to reuse it without incurring new heap allocations.
     *
     * @return this accumulator instance, for call chaining.
     */
    fun setZero(): BigIntAccumulator {
        validate()
        meta = Meta(0)
        return this
    }

    /**
     * Sets this accumulator’s value from a signed 32-bit integer.
     *
     * The accumulator’s sign and magnitude are updated to match the given value.
     *
     * @param n the source value.
     * @return this accumulator instance, for call chaining.
     */
    fun set(n: Int) = set(n < 0, n.absoluteValue.toUInt().toULong())

    /**
     * Sets this accumulator’s value from an unsigned 32-bit integer.
     *
     * @param w the source value.
     * @return this accumulator instance, for call chaining.
     */
    fun set(w: UInt) = set(false, w.toULong())

    /**
     * Sets this accumulator’s value from a signed 64-bit integer.
     *
     * The accumulator’s sign and magnitude are updated to match the given value.
     *
     * @param l the source value.
     * @return this accumulator instance, for call chaining.
     */
    fun set(l: Long) = set(l < 0, l.absoluteValue.toULong())

    /**
     * Sets this accumulator’s value from an unsigned 64-bit integer.
     *
     * @param dw the source value.
     * @return this accumulator instance, for call chaining.
     */
    fun set(dw: ULong) = set(false, dw)

    /**
     * Sets this accumulator’s value from a [BigInt].
     *
     * The accumulator adopts the sign and magnitude of the given [BigInt].
     * Internal storage is reused where possible.
     *
     * @param hi the source [BigInt].
     * @return this accumulator instance, for call chaining.
     */
    fun set(hi: BigInt): BigIntAccumulator = set(Meta(hi.sign.isNegative, hi.magia), hi.magia)

    /**
     * Sets this accumulator’s value from another [BigIntAccumulator].
     *
     * The accumulator copies the sign, and magnitude of the source accumulator.
     * Internal storage of the destination is reused when possible.
     *
     * @param hia the source [BigIntAccumulator].
     * @return this accumulator instance, for call chaining.
     */
    fun set(hia: BigIntAccumulator): BigIntAccumulator = set(hia.meta, hia.magia)

    /**
     * Sets this accumulator’s value from a raw sign and 64-bit magnitude.
     *
     * This is the fundamental low-level setter used by the other `set()` overloads.
     * It updates the accumulator’s sign and replaces its magnitude with the
     * provided value.
     *
     * @param sign `true` if the value is negative; `false` otherwise.
     * @param dw the magnitude as an unsigned 64-bit integer.
     * @return this accumulator instance, for call chaining.
     */
    fun set(sign: Boolean, dw: ULong): BigIntAccumulator {
        val normLen = (64 - dw.countLeadingZeroBits() + 31) shr 5
        meta = Meta(sign, normLen)
        // limbLen = if (dw == 0uL) 0 else if ((dw shr 32) == 0uL) 1 else 2
        magia[0] = dw.toInt()
        magia[1] = (dw shr 32).toInt()
        return this
    }

    private inline fun ensureCapacityDiscard(minLimbLen: Int) {
        if (magia.size < minLimbLen)
            magia = Magia.newWithFloorLen(minLimbLen)
    }

    private inline fun ensureCapacityCopy(minLimbLen: Int) {
        if (magia.size < minLimbLen)
            magia = Magia.newCopyWithFloorLen(magia, minLimbLen)
    }

    private inline fun swapTmp1() {
        val t = tmp1; tmp1 = magia; magia = t
    }

    /**
     * Sets this accumulator’s value from a raw limb array.
     *
     * This internal method copies the sign and magnitude from the provided limb
     * array into this accumulator. The active limb count is set to [yLen], and the
     * internal buffer is reused or expanded as needed to accommodate the data.
     *
     * The input array [y] is not modified.
     *
     * @param ySign `true` if the value is negative; `false` otherwise.
     * @param y the source limb array containing the magnitude in little-endian limb order.
     * @param yLen the number of significant limbs in [y] to copy.
     * @return this accumulator instance, for call chaining.
     */
    private fun set(yMeta: Meta, y: IntArray): BigIntAccumulator {
        ensureCapacityDiscard(yMeta.normLen)
        meta = yMeta
        y.copyInto(magia)
        return this
    }

    /**
     * Creates an immutable [BigInt] representing the current value of this accumulator.
     *
     * The returned [BigInt] is a snapshot of the accumulator’s current sign and
     * magnitude. Subsequent modifications to this [BigIntAccumulator] do not affect
     * the returned [BigInt], and vice versa.
     *
     * This conversion performs a copy of the active limbs (`magia[0 until limbLen]`)
     * into the new [BigInt] instance.
     *
     * @return a new [BigInt] containing the current value of this accumulator.
     */
    fun toBigInt(): BigInt =
        BigInt.fromLittleEndianIntArray(signFlag, magia, normLen)

    /*

    fun setAdd(x: BigInt, y: BigInt) =
        setAddImpl(x.sign, x.magia, y.sign, y.magia)
    fun setAdd(x: BigInt, y: BigIntAccumulator) =
        setAddImpl(x.sign, x.magia, y.sign, y.magia)
    fun setAdd(x: BigIntAccumulator, y: BigInt) =
        setAddImpl(x.sign, x.magia, y.sign, y.magia)
    fun setAdd(x: BigIntAccumulator, y: BigIntAccumulator) =
        setAddImpl(x.sign, x.magia, y.sign, y.magia)

    fun setSub(x: BigInt, y: BigInt) =
        setAddImpl(x.sign, x.magia, y.sign.negate(), y.magia)
    fun setSub(x: BigInt, y: BigIntAccumulator) =
        setAddImpl(x.sign, x.magia, y.sign.negate(), y.magia)
    fun setSub(x: BigIntAccumulator, y: BigInt) =
        setAddImpl(x.sign, x.magia, y.sign.negate(), y.magia)
    fun setSub(x: BigIntAccumulator, y: BigIntAccumulator) =
        setAddImpl(x.sign, x.magia, y.sign.negate(), y.magia)

    fun setMul(x: BigInt, y: BigInt) =
        setMulImpl(x.sign, x.magia, y.sign, y.magia)
    fun setMul(x: BigInt, y: BigIntAccumulator) =
        setMulImpl(x.sign, x.magia, y.sign, y.magia)
    fun setMul(x: BigIntAccumulator, y: BigInt) =
        setMulImpl(x.sign, x.magia, y.sign, y.magia)
    fun setMul(x: BigIntAccumulator, y: BigIntAccumulator) =
        setMulImpl(x.sign, x.magia, y.sign, y.magia)

    fun setDiv(x: BigInt, y: BigInt) =
        setDivImpl(x.sign, x.magia, y.sign, y.magia)
    fun setDiv(x: BigInt, y: BigIntAccumulator) =
        setDivImpl(x.sign, x.magia, y.sign, y.magia)
    fun setDiv(x: BigIntAccumulator, y: BigInt) =
        setDivImpl(x.sign, x.magia, y.sign, y.magia)
    fun setDiv(x: BigIntAccumulator, y: BigIntAccumulator) =
        setDivImpl(x.sign, x.magia, y.sign, y.magia)

    fun setRem(x: BigInt, y: BigInt) =
        setRemImpl(x.sign, x.magia, y.sign, y.magia)
    fun setRem(x: BigInt, y: BigIntAccumulator) =
        setRemImpl(x.sign, x.magia, y.sign, y.magia)
    fun setRem(x: BigIntAccumulator, y: BigInt) =
        setRemImpl(x.sign, x.magia, y.sign, y.magia)
    fun setRem(x: BigIntAccumulator, y: BigIntAccumulator) =
        setRemImpl(x.sign, x.magia, y.sign, y.magia)

     */

    /**
     * Adds the given Int value to this accumulator.
     *
     * @param n the value to add.
     * @see plusAssign(Long)
     */
    operator fun plusAssign(n: Int) = mutateAddImpl(n < 0, n.absoluteValue.toUInt().toULong())

    /**
     * Adds the given UInt value to this accumulator.
     *
     * @param w the value to add.
     * @see plusAssign(Long)
     */
    operator fun plusAssign(w: UInt) = mutateAddImpl(false, w.toULong())

    /**
     * Adds the given Long value to this accumulator in place.
     *
     * This is the canonical overload for the `+=` operator. The accumulator is
     * updated by adding the operand, with the sign handled automatically.
     *
     * For `BigInt`-style operands, the accumulator adopts the operand’s sign
     * and magnitude for the addition.
     *
     * Example usage:
     * ```
     * val acc = BigIntAccumulator()
     * acc += 42L
     * ```
     *
     * @param l the value to add.
     */
    operator fun plusAssign(l: Long) = mutateAddImpl(l < 0, l.absoluteValue.toULong())

    /**
     * Adds the given ULong value to this accumulator.
     *
     * @param dw the value to add.
     * @see plusAssign(Long)
     */
    operator fun plusAssign(dw: ULong) = mutateAddImpl(false, dw)

    /**
     * Adds the given BigInt value to this accumulator.
     *
     * @param hi the value to add.
     * @see plusAssign(Long)
     */
    operator fun plusAssign(hi: BigInt) =
        mutateAddImpl(Meta(hi.sign.isNegative, hi.magia), hi.magia)

    /**
     * Adds the given BigIntAccumulator value to this accumulator.
     *
     * @param acc the value to add.
     * @see plusAssign(Long)
     */
    operator fun plusAssign(acc: BigIntAccumulator) =
        mutateAddImpl(acc.meta, acc.magia)

    /**
     * Subtracts the given Int value from this accumulator.
     *
     * @param n the value to subtract.
     * @see minusAssign(Long)
     */
    operator fun minusAssign(n: Int) = mutateAddImpl(n > 0, n.absoluteValue.toUInt().toULong())

    /**
     * Subtracts the given UInt value from this accumulator.
     *
     * @param w the value to subtract.
     * @see minusAssign(Long)
     */
    operator fun minusAssign(w: UInt) = mutateAddImpl(w > 0u, w.toULong())

    /**
     * Subtracts the given Long value from this accumulator in place.
     *
     * This is the canonical overload for the `-=` operator. The accumulator is
     * updated by subtracting the absolute value of the operand, with sign handled
     * automatically.
     *
     * Example usage:
     * ```
     * val acc = BigIntAccumulator()
     * acc -= 42L
     * ```
     *
     * @param l the value to subtract.
     */
    operator fun minusAssign(l: Long) = mutateAddImpl(l > 0L, l.absoluteValue.toULong())

    /**
     * Subtracts the given ULong value from this accumulator.
     *
     * @param dw the value to subtract.
     * @see minusAssign(Long)
     */
    operator fun minusAssign(dw: ULong) = mutateAddImpl(dw > 0uL, dw)

    /**
     * Subtracts the given BigInt value from this accumulator.
     *
     * @param hi the value to subtract.
     * @see minusAssign(Long)
     */
    operator fun minusAssign(hi: BigInt) =
        mutateAddImpl(Meta(hi.sign.isPositive, hi.magia), hi.magia)

    /**
     * Subtracts the given BigIntAccumulator value from this accumulator.
     *
     * @param acc the value to subtract.
     * @see minusAssign(Long)
     */
    operator fun minusAssign(acc: BigIntAccumulator) =
        mutateAddImpl(acc.meta.negate(), acc.magia)

    /**
     * Multiplies this accumulator by the given value in place.
     *
     * The `timesAssign` operators (`*=`) mutate this accumulator by multiplying
     * it with the operand. Supported operand types include:
     * - [Int], [Long], [UInt], [ULong]
     * - [BigInt]
     * - [BigIntAccumulator]
     *
     * Sign handling is automatically applied.
     *
     * When multiplying by another `BigIntAccumulator` that is the same instance
     * (`this === other`), a specialized squaring routine is used to prevent aliasing
     * issues and improve performance.
     *
     * Example usage:
     * ```
     * val acc = BigIntAccumulator().set(1) // must start at 1 for multiplication
     * acc *= 10
     * acc *= anotherBigInt
     * ```
     *
     * @param n the value to multiply by.
     * @see timesAssign(Long)
     *
     */
    operator fun timesAssign(n: Int) = mutateMulImpl(n < 0, n.absoluteValue.toUInt())

    /**
     * Multiplies this accumulator by the given UInt value.
     *
     * @param w the value to multiply by.
     * @see timesAssign(Long)
     */
    operator fun timesAssign(w: UInt) = mutateMulImpl(false, w)

    /**
     * Multiplies this accumulator by the given Long value in place.
     *
     * This is the canonical overload for the `*=` operator. The accumulator is
     * updated by multiplying with the operand. Sign is handled automatically.
     *
     * When multiplying by the same instance (`this *= this`), a specialized
     * squaring routine is used to prevent aliasing issues and improve performance.
     *
     * Example usage:
     * ```
     * val acc = BigIntAccumulator().set(1) // must start at 1 for multiplication
     * acc *= 10L
     * ```
     *
     * @param l the value to multiply by.
     */
    operator fun timesAssign(l: Long) = mutateMulImpl(l < 0, l.absoluteValue.toULong())

    /**
     * Multiplies this accumulator by the given ULong value.
     *
     * @param dw the value to multiply by.
     * @see timesAssign(Long)
     */
    operator fun timesAssign(dw: ULong) = mutateMulImpl(false, dw)

    /**
     * Multiplies this accumulator by the given BigInt value.
     *
     * @param hi the value to multiply by.
     * @see timesAssign(Long)
     */
    operator fun timesAssign(hi: BigInt) =
        mutateMulImpl(Meta(hi.sign.isNegative, hi.magia), hi.magia)

    /**
     * Multiplies this accumulator by the given BigIntAccumulator value.
     *
     * If `this === other`, a specialized squaring routine is used to avoid aliasing
     * issues and improve performance.
     *
     * @param acc the value to multiply by.
     * @see timesAssign(Long)
     */
    operator fun timesAssign(acc: BigIntAccumulator) {
        if (this === acc)
            mutateSquare()  // prevent aliasing problems & improve performance
        else
            mutateMulImpl(acc.meta, acc.magia)
    }

    /**
     * Adds the square of the given value to this accumulator in place.
     *
     * The `addSquareOf` methods efficiently compute the square of the operand
     * and add it to this accumulator. Supported operand types include:
     * - [Int], [Long], [UInt], [ULong]
     * - [BigInt]
     * - [BigIntAccumulator]
     *
     * The magnitude is squared before addition. The internal tmp buffer
     * is reused to minimize heap allocation during the operation.
     *
     * These methods mutate the accumulator in place. They are safe to use even
     * when the source is the same instance as the accumulator (`this`), as
     * squaring is performed into temporary storage before addition.
     *
     * Example usage:
     * ```
     * val sumSqr = BigIntAccumulator()
     * for (v in data) {
     *     sumSqr.addSquareOf(v)
     * }
     * val totalSquares = sumSqr.toBigInt()
     * ```
     *
     * @param n the integer value to square and add.
     */
    fun addSquareOf(n: Int) = addSquareOf(n.absoluteValue.toUInt())

    /**
     * Adds the square of the given UInt value to this accumulator.
     *
     * @param w the value to square and add.
     * @see addSquareOf(ULong)
     */
    fun addSquareOf(w: UInt) = plusAssign(w.toULong() * w.toULong())

    /**
     * Adds the square of the given Long value to this accumulator.
     *
     * @param l the value to square and add.
     * @see addSquareOf(ULong)
     */
    fun addSquareOf(l: Long) = addSquareOf(l.absoluteValue.toULong())

    /**
     * Adds the square of the given ULong value to this accumulator.
     *
     * This method is the canonical implementation for adding squares. It handles
     * internal limb arithmetic efficiently and updates the accumulator in place.
     *
     * @param dw the value to square and add.
     */
    fun addSquareOf(dw: ULong) {
        val lo64 = dw * dw
        if ((dw shr 32) == 0uL) {
            mutateAddMagImpl(lo64)
            return
        }
        val hi64 = unsignedMulHi(dw, dw)
        if (tmp1.size < 4)
            tmp1 = IntArray(4)
        tmp1[0] = lo64.toInt()
        tmp1[1] = (lo64 shr 32).toInt()
        tmp1[2] = hi64.toInt()
        tmp1[3] = (hi64 shr 32).toInt()
        val normLen = Magia.normLen(tmp1, 4)
        mutateAddMagImpl(Meta(0, normLen), tmp1)
    }

    /**
     * Adds the square of the given BigInt value to this accumulator.
     *
     * @param hi the value to square and add.
     * @see addSquareOf(ULong)
     */
    fun addSquareOf(hi: BigInt) = addSquareOfImpl(hi.magia, Magia.normLen(hi.magia))

    /**
     * Adds the square of the given BigIntAccumulator value to this accumulator.
     *
     * @param other the value to square and add.
     * @see addSquareOf(ULong)
     */
    fun addSquareOf(other: BigIntAccumulator) {
        // this works OK when this == other because
        // addSquareOfImpl multiplies into tmp1 before the add operation
        if (other.normLen > 0)
            addSquareOfImpl(other.magia, other.normLen)
    }

    /**
     * Adds the absolute value of the given Int to this accumulator.
     *
     * @param n the value to add.
     * @see addAbsValueOf(Long)
     */
    fun addAbsValueOf(n: Int) = plusAssign(n.absoluteValue.toUInt())

    /**
     * Adds the absolute value of the given operand to this accumulator in place.
     *
     * Supported operand types include integer primitives ([Int], [Long]) and
     * arbitrary-precision values ([BigInt], [BigIntAccumulator]).
     *
     * This operation does not support unsigned types since they are always
     * non-negative ... use `+=`
     *
     * Example usage:
     * ```
     * val sumAbs = BigIntAccumulator()
     * for (v in data) {
     *     sumAbs.addAbsValueOf(v)
     * }
     * val totalAbs = sumAbs.toBigInt()
     * ```
     *
     * This is the canonical overload for absolute values.
     *
     * @param l the value to add.
     */
    fun addAbsValueOf(l: Long) = plusAssign(l.absoluteValue.toULong())

    /**
     * Adds the absolute value of the given BigInt to this accumulator.
     *
     * @param hi the value to add.
     * @see addAbsValueOf(Long)
     */
    fun addAbsValueOf(hi: BigInt) =
        mutateAddMagImpl(Meta(0, hi.magia), hi.magia)

    /**
     * Adds the absolute value of the given BigIntAccumulator to this accumulator.
     *
     * @param acc the value to add.
     * @see addAbsValueOf(Long)
     */
    fun addAbsValueOf(acc: BigIntAccumulator) =
        mutateAddMagImpl(acc.meta.abs(), acc.magia)

    /**
     * Returns the current value of this accumulator as a raw unsigned 64-bit value.
     *
     * This method is intended for internal use. It converts the accumulator
     * to a single [ULong], assuming the value fits within 64 bits.
     * If the magnitude exceeds 64 bits, the result will only include the least
     * significant 64 bits.
     *
     * @return the value of this accumulator as a [ULong], truncated if necessary.
     */
    private inline fun toRawULong(): ULong {
        //return when {
        //    limbLen == 1 -> dw32(magia[0])
        //    limbLen >= 2 -> (dw32(magia[1]) shl 32) or dw32(magia[0])
        //    else -> 0uL
        //}
        val dw = (dw32(magia[1]) shl 32) or dw32(magia[0])
        val nonZeroMask = ((-normLen).toLong() shr 63).toULong()
        val gt32Mask = ((1 - normLen) shr 31).toLong().toULong() or 0xFFFF_FFFFuL
        return dw and gt32Mask and nonZeroMask
    }

    /**
     * Adds a value to this accumulator in place, taking the operand’s logical sign into account.
     *
     * This is a low-level internal helper used by the public `plusAssign` and
     * `minusAssign` operators. Internally, the operation always performs addition,
     * and [otherSign] determines whether the operand is effectively positive or negative.
     *
     * @param otherSign `true` if the operand is negative, `false` if positive.
     * @param dw the magnitude of the operand as an unsigned 64-bit value.
     */
    private fun mutateAddImpl(otherSign: Boolean, dw: ULong) {
        val rawULong = toRawULong()
        when {
            dw == 0uL -> {}
            meta.signFlag == otherSign -> mutateAddMagImpl(dw)
            meta.normLen == 0 -> set(otherSign, dw)
            meta.normLen > 2 || rawULong > dw -> {
                //Magia.mutateSub(magia, meta.normLen, dw)
                //val normLen = Magia.normLen(magia, meta.normLen)
                val normLen = Magia.setSub(magia, magia, meta.normLen, dw)
                meta = Meta(signFlag, normLen)
            }
            rawULong < dw -> set(otherSign, dw - rawULong)
            else -> setZero()
        }
    }

    /**
     * Adds a multi-limb integer to this accumulator in place, considering its sign.
     *
     * This is a low-level internal helper used by public operators such as
     * `plusAssign` and `minusAssign`. The operation always performs addition
     * internally, while [ySign] determines the logical sign of the operand.
     *
     * Only the first [yLen] limbs of [y] are used. [y] represents the magnitude
     * in little-endian order (least significant limb first).
     *
     * @param ySign `true` if the operand is negative, `false` if positive.
     * @param y the array of limbs representing the operand's magnitude.
     * @param yLen the number of active limbs in [y] to consider.
     */
    private fun mutateAddImpl(yMeta: Meta, y: IntArray) {
        validate()
        when {
            yMeta.normLen <= 2 -> {
                when {
                    yMeta.normLen == 2 -> mutateAddImpl(yMeta.signFlag, (dw32(y[1]) shl 32) or dw32(y[0]))
                    yMeta.normLen == 1 -> mutateAddImpl(yMeta.signFlag, y[0].toUInt().toULong())
                }
                // if yLen == 0 do nothing
                return
            }

            normLen == 0 -> { set(yMeta, y); validate(); return }
            signBit == yMeta.signBit -> { mutateAddMagImpl(yMeta, y); validate(); return }
        }
        val cmp = Magia.compare(magia, normLen, y, yMeta.normLen)
        when {
            cmp > 0 -> meta = Meta(signBit,
                Magia.setSub(magia, magia, normLen, y, yMeta.normLen))
            cmp < 0 -> {
                ensureCapacityCopy(yMeta.normLen)
                meta = Meta(yMeta.signBit,
                    Magia.setSub(magia, y, yMeta.normLen, magia, normLen))
            }
            else -> setZero()
        }
        validate()
    }

    /**
     * Adds the given magnitude to this accumulator in place.
     *
     * This is a low-level internal helper that assumes the operand is non-negative
     * and represented as a single unsigned 64-bit value ([dw]). The operation
     * directly updates the accumulator’s internal magnitude.
     *
     * This method does not consider any sign; it is intended for internal use
     * where the operand’s sign has already been handled by the caller.
     *
     * @param dw the unsigned magnitude to add.
     */
    private fun mutateAddMagImpl(dw: ULong) {
        ensureCapacityCopy(normLen + 2)
        val normLen = Magia.mutateAdd(magia, normLen, dw)
        meta = Meta(signBit, normLen)
        validate()
    }

    /**
     * Adds a multi-limb magnitude to this accumulator in place.
     *
     * This is a low-level internal helper that assumes the operand is non-negative.
     * The operation directly updates the accumulator’s internal magnitude without
     * considering any sign; the caller is responsible for handling sign logic.
     *
     * Only the first [yLen] limbs of [y] are used. The array [y] is interpreted
     * as little-endian, with the least significant limb at index 0.
     *
     * @param y the array of limbs representing the operand's magnitude.
     * @param yLen the number of active limbs in [y] to add.
     */
    private fun mutateAddMagImpl(yMeta: Meta, y: IntArray) {
        ensureCapacityCopy(yMeta.normLen + 1)
        meta = Meta(signBit,
            Magia.setAdd(magia, magia, normLen, y, yMeta.normLen))
        validate()
    }

    /**
     * Adds the square of a multi-limb integer to this accumulator in place.
     *
     * This is a low-level internal helper used by `addSquareOf` for [BigInt]
     * and [BigIntAccumulator] operands. The operation squares the first [yNormLen]
     * limbs of [y] and adds the result to this accumulator’s value.
     *
     * The array [y] is interpreted as little-endian (least significant limb first),
     * and only the first [yNormLen] limbs are considered. This method is safe to call
     * even if the source array belongs to this accumulator.
     *
     * @param y the array of limbs representing the operand's magnitude.
     * @param yNormLen the number of active limbs in [y] to square and add.
     */
    private inline fun addSquareOfImpl(y: IntArray, yNormLen: Int) {
        val sqrLenMax = yNormLen * 2
        if (tmp1.size < sqrLenMax)
            tmp1 = Magia.newWithFloorLen(sqrLenMax)
        else
            tmp1.fill(0, 0, sqrLenMax)
        val normLenSqr = Magia.setSqr(tmp1, y, yNormLen)
        mutateAddMagImpl(Meta(0, normLenSqr), tmp1)
        validate()
    }

    /**
     * Multiplies this accumulator in place by a single-limb value.
     *
     * This is a low-level internal helper used by the public `timesAssign` operators.
     * The operation multiplies the accumulator by [w], taking into account the
     * logical sign of the operand specified by [wSign].
     *
     * Internally, the multiplication always modifies the accumulator in place,
     * updating its magnitude and sign as needed.
     *
     * @param wSign `true` if the operand is negative, `false` if positive.
     * @param w the unsigned 32-bit magnitude to multiply by.
     */
    private fun mutateMulImpl(wSign: Boolean, w: UInt) {
        validate()
        if (w == 0u || normLen == 0) {
            setZero()
            return
        }
        ensureCapacityCopy(normLen + 1)
        meta = Meta(signFlag xor wSign, Magia.setMul(magia, magia, normLen, w))
        validate()
    }

    /**
     * Multiplies this accumulator in place by a single 64-bit value.
     *
     * This is a low-level internal helper used by the public `timesAssign` operators.
     * The operation multiplies the accumulator by [dw], taking into account the
     * logical sign of the operand specified by [dwSign].
     *
     * Internally, the multiplication always modifies the accumulator in place,
     * updating its magnitude and sign as needed.
     *
     * @param dwSign `true` if the operand is negative, `false` if positive.
     * @param dw the unsigned 64-bit magnitude to multiply by.
     */
    private fun mutateMulImpl(dwSign: Boolean, dw: ULong) {
        validate()
        if ((dw shr 32) == 0uL) {
            mutateMulImpl(dwSign, dw.toUInt())
            return
        }
        if (normLen == 0)
            return
        ensureCapacityCopy(normLen + 2)
        meta = Meta(signFlag xor dwSign, Magia.setMul(magia, magia, normLen, dw))
        validate()
    }

    /**
     * Multiplies this accumulator in place by a multi-limb value.
     *
     * This is a low-level internal helper used by the public `timesAssign` operators.
     * The operation multiplies the first [yLen] limbs of [y] with this accumulator,
     * taking into account the logical sign of the operand specified by [ySign].
     *
     * Only the first [yLen] limbs are referenced. The operation uses the internal
     * `tmp` associated with `this` and effectively updates the value in-place
     * while minimizing or eliminating heap allocation.
     *
     * @param ySign `true` if the operand is negative, `false` if positive.
     * @param y the array of limbs representing the operand's magnitude.
     * @param yLen the number of active limbs in [y] to multiply by.
     */
    private fun mutateMulImpl(yMeta: Meta, y: IntArray) {
        validate()
        if (normLen == 0 || yMeta.normLen == 0) {
            setZero()
            return
        }
        swapTmp1()
        ensureCapacityCopy(normLen + yMeta.normLen + 1)
        val normLen = Magia.setMul(magia, tmp1, normLen, y, yMeta.normLen)
        meta = Meta(signBit xor yMeta.signBit, normLen)
        validate()
    }

    /**
     * Squares this accumulator in place.
     *
     * This is a low-level internal helper used when multiplying an accumulator
     * by itself (for example, `acc *= acc`).
     *
     * This method modifies the accumulator in place and is optimized to handle
     * aliasing safely when the source and destination are the same object.
     */
    private fun mutateSquare() {
        if (normLen > 0) {
            val newLimbLenMax = normLen * 2
            if (tmp1.size < newLimbLenMax)
                tmp1 = Magia.newWithFloorLen(newLimbLenMax)
            else
                tmp1.fill(0, 0, newLimbLenMax)
            swapTmp1()
            meta = Meta(0,
                Magia.setSqr(magia, tmp1, normLen))
        }
    }

    /**
     * Returns the string representation of this accumulator.
     *
     * The value is formatted as a decimal number, using the current sign and
     * magnitude of the accumulator. This provides a human-readable form of
     * the arbitrary-precision integer.
     *
     * @return the decimal string representation of this accumulator.
     */
    override fun toString(): String = Magia.toString(meta.isNegative, magia, normLen)

    fun toStringX(): String = Magia.toString(meta.isNegative, magia, normLen)

}

/**
 * Converts a 32-bit [Int] to a 64-bit [ULong] with zero-extension.
 *
 * This method treats the input [n] as an unsigned 32-bit value and
 * returns it as a [ULong] where the upper 32 bits are zero. In
 * this context it is used for consistently extracting 64-bit limbs
 * from signed [IntArray] elements.
 *
 * @param n the 32-bit integer to convert.
 * @return the zero-extended 64-bit unsigned value.
 */
private inline fun dw32(n: Int) = n.toUInt().toULong()
