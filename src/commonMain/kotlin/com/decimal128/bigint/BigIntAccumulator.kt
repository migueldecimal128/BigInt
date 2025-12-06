// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.Sign.Companion.POSITIVE
import com.decimal128.bigint.intrinsic.unsignedMulHi
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
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
    var sign: Sign,
    var magia: IntArray,
    var limbLen: Int,
    var tmp1: IntArray) {
    constructor() : this(Sign.POSITIVE, IntArray(4), 0, Magia.ZERO)

    private inline fun validate() {
        check (limbLen >= 0 &&
                       limbLen <= magia.size &&
                       magia.size >= 4 &&
                       (limbLen == 0 || magia[limbLen - 1] != 0))
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
        sign = POSITIVE
        limbLen = 0
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
    fun set(hi: BigInt): BigIntAccumulator = set(hi.sign.isNegative, hi.magia, Magia.normLen(hi.magia))

    /**
     * Sets this accumulator’s value from another [BigIntAccumulator].
     *
     * The accumulator copies the sign, and magnitude of the source accumulator.
     * Internal storage of the destination is reused when possible.
     *
     * @param hia the source [BigIntAccumulator].
     * @return this accumulator instance, for call chaining.
     */
    fun set(hia: BigIntAccumulator): BigIntAccumulator = set(hia.sign.isNegative, hia.magia, hia.limbLen)

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
        this.sign = Sign(sign)
        // limbLen = if (dw == 0uL) 0 else if ((dw shr 32) == 0uL) 1 else 2
        limbLen = (64 - dw.countLeadingZeroBits() + 31) shr 5
        magia[0] = dw.toInt()
        magia[1] = (dw shr 32).toInt()
        return this
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
    private fun set(ySign: Boolean, y: IntArray, yLen: Int): BigIntAccumulator {
        if (magia.size < yLen)
            magia = Magia.newWithFloorLen(yLen)
        sign = Sign(ySign)
        limbLen = yLen
        //System.arraycopy(y, 0, magia, 0, yLen)
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
        BigInt.fromLittleEndianIntArray(sign.isNegative, magia, limbLen)

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
        mutateAddImpl(hi.sign.isNegative, hi.magia, Magia.normLen(hi.magia))

    /**
     * Adds the given BigIntAccumulator value to this accumulator.
     *
     * @param acc the value to add.
     * @see plusAssign(Long)
     */
    operator fun plusAssign(acc: BigIntAccumulator) =
        mutateAddImpl(acc.sign.isNegative, acc.magia, acc.limbLen)

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
        mutateAddImpl(hi.sign.isPositive, hi.magia, Magia.normLen(hi.magia))

    /**
     * Subtracts the given BigIntAccumulator value from this accumulator.
     *
     * @param acc the value to subtract.
     * @see minusAssign(Long)
     */
    operator fun minusAssign(acc: BigIntAccumulator) =
        mutateAddImpl(acc.sign.isPositive, acc.magia, acc.limbLen)

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
        mutateMulImpl(hi.sign.isNegative, hi.magia, Magia.normLen(hi.magia))

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
            mutateMulImpl(acc.sign.isNegative, acc.magia, acc.limbLen)
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
        mutateAddMagImpl(tmp1, Magia.normLen(tmp1, 4))
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
        if (other.limbLen > 0)
            addSquareOfImpl(other.magia, other.limbLen)
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
        mutateAddMagImpl(hi.magia, Magia.normLen(hi.magia))

    /**
     * Adds the absolute value of the given BigIntAccumulator to this accumulator.
     *
     * @param acc the value to add.
     * @see addAbsValueOf(Long)
     */
    fun addAbsValueOf(acc: BigIntAccumulator) =
        mutateAddMagImpl(acc.magia, acc.limbLen)

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
        return when {
            limbLen == 1 -> dw32(magia[0])
            limbLen >= 2 -> (dw32(magia[1]) shl 32) or dw32(magia[0])
            else -> 0uL
        }
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
            this.sign.isNegative == otherSign -> mutateAddMagImpl(dw)
            limbLen == 0 -> set(otherSign, dw)
            limbLen > 2 || rawULong > dw -> {
                Magia.mutateSub(magia, limbLen, dw)
                limbLen = Magia.normLen(magia, limbLen)
                sign = Sign(sign.isNegative and (limbLen > 0))
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
    private fun mutateAddImpl(ySign: Boolean, y: IntArray, yLen: Int) {
        validate()
        when {
            yLen <= 2 -> {
                when {
                    yLen == 2 -> mutateAddImpl(ySign, (dw32(y[1]) shl 32) or dw32(y[0]))
                    yLen == 1 -> mutateAddImpl(ySign, y[0].toUInt().toULong())
                }
                // if yLen == 0 do nothing
                return
            }

            limbLen == 0 -> { set(ySign, y, yLen); return }
            this.sign.isNegative == ySign -> { mutateAddMagImpl(y, yLen); return }
        }
        val cmp = Magia.compare(magia, limbLen, y, yLen)
        when {
            cmp > 0 -> {
                Magia.mutateSub(magia, limbLen, y, yLen)
                limbLen = Magia.normLen(magia, limbLen)
                sign = Sign(sign.isNegative and (limbLen > 0))
            }
            cmp < 0 -> {
                if (magia.size < yLen)
                    magia = Magia.newCopyWithFloorLen(magia, yLen)
                if (limbLen < yLen)
                    magia.fill(0, limbLen, yLen)
                Magia.mutateReverseSub(magia, yLen, y, yLen)
                limbLen = Magia.normLen(magia, yLen)
                sign = Sign(ySign)
            }
            else -> {
                setZero()
            }
        }
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
        val carry = Magia.mutateAdd(magia, limbLen, dw)
        if (carry == 0uL)
            return
        val newLen = limbLen + if ((carry shr 32) == 0uL) 1 else 2
        if (newLen > magia.size)
            magia = Magia.newCopyWithFloorLen(magia, newLen)
        magia[newLen - 1] = (carry shr 32).toInt() // overwritten when carry hi word == 0
        magia[limbLen] = carry.toInt()
        limbLen = newLen
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
    private fun mutateAddMagImpl(y: IntArray, yLen: Int) {
        if (yLen > magia.size)
            magia = Magia.newCopyWithFloorLen(magia, yLen + 1)
        var xLen = limbLen
        while (xLen < yLen) {
            magia[xLen] = 0
            ++xLen
        }
        val carry: UInt = Magia.mutateAdd(magia, xLen, y, yLen)
        if (carry == 0u) {
            limbLen = xLen
            return
        }
        if (xLen == magia.size)
            magia = Magia.newCopyWithFloorLen(magia, xLen + 1)
        magia[xLen] = 1
        limbLen = xLen + 1
    }

    /**
     * Adds the square of a multi-limb integer to this accumulator in place.
     *
     * This is a low-level internal helper used by `addSquareOf` for [BigInt]
     * and [BigIntAccumulator] operands. The operation squares the first [yLen]
     * limbs of [y] and adds the result to this accumulator’s value.
     *
     * The array [y] is interpreted as little-endian (least significant limb first),
     * and only the first [yLen] limbs are considered. This method is safe to call
     * even if the source array belongs to this accumulator.
     *
     * @param y the array of limbs representing the operand's magnitude.
     * @param yLen the number of active limbs in [y] to square and add.
     */
    private inline fun addSquareOfImpl(y: IntArray, yLen: Int) {
        val sqrLenMax = yLen * 2
        if (tmp1.size < sqrLenMax)
            tmp1 = Magia.newWithFloorLen(sqrLenMax)
        else
            tmp1.fill(0, 0, sqrLenMax)
        Magia.setSqr(tmp1, y, yLen)
        val sqrLen = sqrLenMax - if (tmp1[sqrLenMax - 1] == 0) 1 else 0
        mutateAddMagImpl(tmp1, sqrLen)
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
        if (w == 0u || limbLen == 0) {
            setZero()
            return
        }
        val newLimbLenMax = limbLen + 1
        if (magia.size < newLimbLenMax)
            magia = Magia.newCopyWithFloorLen(magia, newLimbLenMax)
        magia[limbLen] = 0
        Magia.setMul(magia, magia, limbLen, w)
        sign = sign xor wSign
        limbLen += if (magia[limbLen] == 0) 0 else 1
    }

    /**
     * Multiplies this accumulator in place by a single 64-bit value.
     *
     * This is a low-level internal helper used by the public `timesAssign` operators.
     * The operation multiplies the accumulator by [dw], taking into account the
     * logical sign of the operand specified by [wSign].
     *
     * Internally, the multiplication always modifies the accumulator in place,
     * updating its magnitude and sign as needed.
     *
     * @param wSign `true` if the operand is negative, `false` if positive.
     * @param dw the unsigned 64-bit magnitude to multiply by.
     */
    private fun mutateMulImpl(wSign: Boolean, dw: ULong) {
        validate()
        if ((dw shr 32) == 0uL) {
            mutateMulImpl(wSign, dw.toUInt())
            return
        }
        if (limbLen == 0)
            return
        if (magia.size < limbLen + 2)
            magia = Magia.newCopyWithFloorLen(magia, limbLen + 2)
        limbLen = Magia.setMul(magia, magia, limbLen, dw)
        sign = sign xor wSign
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
    private fun mutateMulImpl(ySign: Boolean, y: IntArray, yLen: Int) {
        validate()
        if (limbLen == 0 || yLen == 0) {
            setZero()
            return
        }
        val m = if (limbLen >= yLen) magia else y
        val mLen = max(limbLen, yLen)
        val n = if (limbLen >= yLen) y else magia
        val nLen = min(limbLen, yLen)
        val pLen = mLen + nLen
        if (tmp1.size < pLen)
            tmp1 = Magia.newWithFloorLen(pLen)
        else
            tmp1.fill(0, 0, pLen)
        Magia.setMul(tmp1, magia, limbLen, y, yLen)
        val t = magia
        magia = tmp1
        tmp1 = t
        limbLen = pLen - if (magia[pLen - 1] == 0) 1 else 0
        sign = sign xor ySign
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
        if (limbLen > 0) {
            val newLimbLenMax = limbLen * 2
            if (tmp1.size < newLimbLenMax)
                tmp1 = Magia.newWithFloorLen(newLimbLenMax)
            else
                tmp1.fill(0, 0, newLimbLenMax)
            val t = magia
            magia = tmp1
            tmp1 = t
            limbLen = Magia.setSqr(magia, t, limbLen)
            sign = POSITIVE
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
    override fun toString(): String = Magia.toString(this.sign.isNegative, this.magia, this.limbLen)

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
