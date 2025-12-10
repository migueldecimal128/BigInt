// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigInt.Companion.NEG_ONE
import com.decimal128.bigint.BigInt.Companion.ZERO
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
     * @param bi the source [BigInt].
     * @return this accumulator instance, for call chaining.
     */
    fun set(bi: BigInt): BigIntAccumulator = set(Meta(bi.meta.isNegative, bi.magia), bi.magia)

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
        val normLen = (64 - dw.countLeadingZeroBits() + 31) ushr 5
        meta = Meta(sign, normLen)
        // limbLen = if (dw == 0uL) 0 else if ((dw shr 32) == 0uL) 1 else 2
        magia[0] = dw.toInt()
        magia[1] = (dw shr 32).toInt()
        return this
    }

    /**
     * Ensures that the backing limb array has at least [minLimbLen] capacity.
     *
     * If the current array is too small, a new zero-initialized array of the
     * required size is allocated and the previous contents are discarded.
     * Existing contents are **not** preserved.
     */
    private inline fun ensureCapacityDiscard(minLimbLen: Int) {
        if (magia.size < minLimbLen)
            magia = Magia.newWithFloorLen(minLimbLen)
    }

    /**
     * Ensures the limb array has at least [minLimbLen] capacity.
     *
     * If the array is too small, a larger one is allocated and the
     * existing contents are copied into it.
     */
    private inline fun ensureCapacityCopy(minLimbLen: Int) {
        if (magia.size < minLimbLen)
            magia = Magia.newCopyWithFloorLen(magia, minLimbLen)
    }

    /**
     * Ensures capacity for at least [minBitLen] bits, discarding any existing data.
     * Converts the bit requirement to limb capacity and delegates to
     * `ensureCapacityDiscard`.
     */
    private inline fun ensureBitCapacityDiscard(minBitLen: Int) =
        ensureCapacityDiscard((minBitLen + 0x1F) ushr 5)

    /**
     * Ensures capacity for at least [minBitLen] bits, preserving existing data.
     * Converts the bit requirement to limb capacity and delegates to
     * `ensureCapacityCopy`.
     */
    private inline fun ensureBitCapacityCopy(minBitLen: Int) =
        ensureCapacityCopy((minBitLen + 0x1F) ushr 5)


    /**
     * Ensures the backing array has at least [newLimbLen] limbs and that any
     * newly-exposed limbs are zero-initialized.
     *
     * - If [newLimbLen] ≤ current `normLen`, nothing is done.
     * - If [newLimbLen] ≤ current capacity, the unused limbs are zeroed
     *   from the current `meta.normLen` up to [newLimbLen].
     * - Otherwise a new zeroed array with minimum [newLimbLen] is
     *   allocated and existing limbs up to `meta.normLen` are copied.
     *
     * This adjusts physical storage only; callers remain responsible for updating
     * `meta.normLen` as needed.
     */
    private fun ensureLimbLen(newLimbLen: Int) {
        if (newLimbLen <= meta.normLen)
            return
        if (newLimbLen <= magia.size) {
            magia.fill(0, meta.normLen, newLimbLen)
            return
        }
        magia = Magia.newCopyWithFloorLen(magia, meta.normLen, newLimbLen)
    }

    /**
     * Ensures backing storage for at least [newBitLen] bits, zero-initializing
     * any newly added limbs. Does not modify `meta.normLen`.
     */
    private fun ensureBitLen(newBitLen: Int) = ensureLimbLen((newBitLen + 0x1F) ushr 5)


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


    fun setAdd(x: BigInt, y: BigInt) =
        setAddImpl(x.meta, x.magia, y.meta, y.magia)
    fun setAdd(x: BigInt, y: BigIntAccumulator) =
        setAddImpl(x.meta, x.magia, y.meta, y.magia)
    fun setAdd(x: BigIntAccumulator, y: BigInt) =
        setAddImpl(x.meta, x.magia, y.meta, y.magia)
    fun setAdd(x: BigIntAccumulator, y: BigIntAccumulator) =
        setAddImpl(x.meta, x.magia, y.meta, y.magia)

    fun setSub(x: BigInt, y: BigInt) =
        setAddImpl(x.meta, x.magia, y.meta.negate(), y.magia)
    fun setSub(x: BigInt, y: BigIntAccumulator) =
        setAddImpl(x.meta, x.magia, y.meta.negate(), y.magia)
    fun setSub(x: BigIntAccumulator, y: BigInt) =
        setAddImpl(x.meta, x.magia, y.meta.negate(), y.magia)
    fun setSub(x: BigIntAccumulator, y: BigIntAccumulator) =
        setAddImpl(x.meta, x.magia, y.meta.negate(), y.magia)

    private fun setAddImpl(xMeta: Meta, x: IntArray, yMeta: Meta, y: IntArray): BigIntAccumulator {
        when {
            yMeta.isZero -> set(xMeta, x)
            xMeta.isZero -> set(yMeta, y)
            xMeta.signBit == yMeta.signBit -> {
                ensureCapacityDiscard(xMeta.normLen + yMeta.normLen + 1)
                meta = Meta(xMeta.signBit,
                    Magia.setAdd(magia, x, xMeta.normLen, y, yMeta.normLen))
            }
            else -> {
                val cmp: Int = Magia.compare(x, xMeta.normLen, y, yMeta.normLen)
                when {
                    cmp > 0 -> {
                        ensureCapacityDiscard(xMeta.normLen)
                        meta = Meta(xMeta.signBit,
                            Magia.setSub(magia, x, xMeta.normLen, y, yMeta.normLen))
                    }
                    cmp < 0 -> {
                        ensureCapacityDiscard(yMeta.normLen)
                        meta = Meta(yMeta.signBit,
                            Magia.setSub(magia, y, yMeta.normLen, x, xMeta.normLen))
                    }
                    else -> setZero()
                }
            }
        }
        return this
    }

    fun setMul(x: BigInt, y: BigInt) =
        setMulImpl(x.meta, x.magia, y.meta, y.magia)
    fun setMul(x: BigInt, y: BigIntAccumulator) =
        setMulImpl(x.meta, x.magia, y.meta, y.magia)
    fun setMul(x: BigIntAccumulator, y: BigInt) =
        setMulImpl(x.meta, x.magia, y.meta, y.magia)
    fun setMul(x: BigIntAccumulator, y: BigIntAccumulator) =
        setMulImpl(x.meta, x.magia, y.meta, y.magia)

    private fun setMulImpl(xMeta: Meta, x: IntArray, yMeta: Meta, y: IntArray): BigIntAccumulator {
        swapTmp1()
        val xNormLen = xMeta.normLen
        val yNormLen = yMeta.normLen
        ensureCapacityDiscard(xNormLen + yNormLen)
        meta = Meta(xMeta.signBit xor yMeta.signBit,
            Magia.setMul(magia, x, xNormLen, y, yNormLen))
        return this
    }

    fun setDiv(x: BigInt, y: BigInt) =
        setDivImpl(x.meta, x.magia, y.meta, y.magia)
    fun setDiv(x: BigInt, y: BigIntAccumulator) =
        setDivImpl(x.meta, x.magia, y.meta, y.magia)
    fun setDiv(x: BigIntAccumulator, y: BigInt) =
        setDivImpl(x.meta, x.magia, y.meta, y.magia)
    fun setDiv(x: BigIntAccumulator, y: BigIntAccumulator) =
        setDivImpl(x.meta, x.magia, y.meta, y.magia)

    private fun setDivImpl(xMeta: Meta, x: IntArray, yMeta: Meta, y: IntArray): BigIntAccumulator {
        val xNormLen = xMeta.normLen
        val yNormLen = yMeta.normLen
        if (yNormLen == 0)
            throw ArithmeticException("div by zero")
        swapTmp1()
        ensureCapacityDiscard(xNormLen)
        meta = Meta(xMeta.signBit xor yMeta.signBit,
            Magia.setDiv(magia, x, xNormLen, y, yNormLen))
        return this
    }

    fun setRem(x: BigInt, y: BigInt) =
        setRemImpl(x.meta, x.magia, y.meta, y.magia)
    fun setRem(x: BigInt, y: BigIntAccumulator) =
        setRemImpl(x.meta, x.magia, y.meta, y.magia)
    fun setRem(x: BigIntAccumulator, y: BigInt) =
        setRemImpl(x.meta, x.magia, y.meta, y.magia)
    fun setRem(x: BigIntAccumulator, y: BigIntAccumulator) =
        setRemImpl(x.meta, x.magia, y.meta, y.magia)

    private fun setRemImpl(xMeta: Meta, x: IntArray, yMeta: Meta, y: IntArray): BigIntAccumulator {
        val xNormLen = xMeta.normLen
        val yNormLen = yMeta.normLen
        if (yNormLen == 0)
            throw ArithmeticException("div by zero")
        swapTmp1()
        ensureCapacityDiscard(xNormLen)
        meta = Meta(xMeta.signBit,
            Magia.setRem(magia, x, xNormLen, y, yNormLen))
        return this
    }


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
     * @param bi the value to add.
     * @see plusAssign(Long)
     */
    operator fun plusAssign(bi: BigInt) =
        mutateAddImpl(Meta(bi.meta.isNegative, bi.magia), bi.magia)

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
     * @param bi the value to subtract.
     * @see minusAssign(Long)
     */
    operator fun minusAssign(bi: BigInt) =
        mutateAddImpl(Meta(bi.meta.isPositive, bi.magia), bi.magia)

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
     * @param bi the value to multiply by.
     * @see timesAssign(Long)
     */
    operator fun timesAssign(bi: BigInt) =
        mutateMulImpl(Meta(bi.meta.isNegative, bi.magia), bi.magia)

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
     * Sets this accumulator to `x << bitCount`. Allocates space for the
     * resulting bit length. Throws if [bitCount] is negative.
     */
    fun setShl(x: BigInt, bitCount: Int): BigIntAccumulator =
        setShlImpl(x.meta, x.magia, bitCount)

    /**
     * Sets this accumulator to `x << bitCount`. Allocates space for the
     * resulting bit length. Throws if [bitCount] is negative.
     */
    fun setShl(x: BigIntAccumulator, bitCount: Int): BigIntAccumulator =
        setShlImpl(x.meta, x.magia, bitCount)

    private fun setShlImpl(xMeta: Meta, x: IntArray, bitCount: Int): BigIntAccumulator {
        return when {
            bitCount < 0 -> throw IllegalArgumentException("negative bitCount")
            bitCount == 0 -> set(xMeta, x)
            xMeta.isZero -> setZero()
            else -> {
                val xBitLen = Magia.bitLen(x, xMeta.normLen)
                val zBitLen = xBitLen + bitCount
                ensureBitCapacityDiscard(zBitLen)
                meta = Meta(meta.signBit,
                    Magia.setShiftLeft(magia, x, xMeta.normLen, bitCount)
                    )
                return this
            }
        }
    }

    /**
     * Sets this accumulator to `x >>> bitCount`.
     *
     * The sign of `x` is ignored and the resulting value is the
     * non-negative magnitude.
     *
     * Throws if [bitCount] is negative.
     */
    fun setUshr(x: BigInt, bitCount: Int): BigIntAccumulator =
        setUshrImpl(x.meta, x.magia, bitCount)

    /**
     * Sets this accumulator to `x >>> bitCount`.
     *
     * The sign of `x` is ignored and the resulting value is the
     * non-negative magnitude.
     *
     * Throws if [bitCount] is negative.
     */
    fun setUshr(x: BigIntAccumulator, bitCount: Int): BigIntAccumulator =
        setUshrImpl(x.meta, x.magia, bitCount)

    private fun setUshrImpl(xMeta: Meta, x: IntArray, bitCount: Int): BigIntAccumulator {
        val xBitLen = Magia.bitLen(x, xMeta.normLen)
        val zBitLen = xBitLen - bitCount
        return when {
            bitCount < 0 -> throw IllegalArgumentException("negative bitCount")
            bitCount == 0 -> set(xMeta, x)
            zBitLen <= 0 -> setZero()
            else -> {
                ensureBitCapacityDiscard(zBitLen)
                meta = Meta(0,
                    Magia.setShiftRight(magia, x, xMeta.normLen, bitCount))
                this
            }
        }
    }

    /**
     * Sets this accumulator to `x >> bitCount`.
     *
     * Follows arithmetic shift right semantics if `x` is negative,
     * effectively treating the resulting value as if it were
     * stored in twos-complement.
     *
     * Throws if [bitCount] is negative.
     */
    fun setShr(x: BigInt, bitCount: Int): BigIntAccumulator =
        setShrImpl(x.meta, x.magia, bitCount)

    /**
     * Sets this accumulator to `x >> bitCount`.
     *
     * Follows arithmetic shift right semantics if `x` is negative,
     * effectively treating the resulting value as if it were
     * stored in twos-complement.
     *
     * Throws if [bitCount] is negative.
     */
    fun setShr(x: BigIntAccumulator, bitCount: Int): BigIntAccumulator =
        setShrImpl(x.meta, x.magia, bitCount)

    private fun setShrImpl(xMeta: Meta, x: IntArray, bitCount: Int): BigIntAccumulator {
        when {
            bitCount > 0 -> {
                val bitLen = Magia.bitLen(x, xMeta.normLen)
                val zBitLen = bitLen - bitCount
                if (zBitLen <= 0)
                    return if (xMeta.isNegative) set (-1) else setZero()
                val needsIncrement = xMeta.isNegative && Magia.testAnyBitInLowerN(x, bitCount)
                ensureBitCapacityDiscard(zBitLen)
                var normLen = Magia.setShiftRight(magia, x, xMeta.normLen, bitCount)
                check (normLen > 0)
                if (needsIncrement) {
                    ensureBitCapacityCopy(zBitLen + 1)
                    normLen = Magia.setAdd(magia, magia, normLen, 1u)
                }
                meta = Meta(xMeta.signFlag, normLen)
            }
            bitCount == 0 -> {}
            else -> throw IllegalArgumentException("bitCount < 0")
        }
        return this
    }

    /**
     * Tests whether the magnitude bit at [bitIndex] is set.
     *
     * @param bitIndex 0-based, starting from the least-significant bit
     * @return true if the bit is set, false otherwise
     */
    fun testBit(bitIndex: Int): Boolean = Magia.testBit(this.magia, this.meta.normLen, bitIndex)

    /**
     * Sets the bit at [bitIndex] in the magnitude, growing the limb array if needed.
     *
     * If the bit lies within the current normalized limb range, the limb is updated
     * in place. Otherwise the array is extended and the new highest limb set.
     *
     * @throws IllegalArgumentException if [bitIndex] is negative
     */
    fun setBit(bitIndex: Int): BigIntAccumulator {
        if (bitIndex >= 0) {
            val wordIndex = bitIndex ushr 5
            val isolatedBit = (1 shl (bitIndex and 0x1F))
            if (wordIndex < meta.normLen) {
                magia[wordIndex] = magia[wordIndex] or isolatedBit
                return this
            }
            ensureLimbLen(wordIndex + 1)
            magia[wordIndex] = isolatedBit
            meta = Meta(meta.signBit, wordIndex + 1)
            return this
        }
        throw IllegalArgumentException()
    }

    /**
     * Clears the bit at [bitIndex] in the magnitude. If the cleared bit was in the
     * most-significant used limb, the normalized length is reduced accordingly.
     *
     * @throws IllegalArgumentException if [bitIndex] is negative
     */
    fun clearBit(bitIndex: Int): BigIntAccumulator {
        if (bitIndex >= 0) {
            val wordIndex = bitIndex ushr 5
            if (wordIndex < meta.normLen) {
                val isolatedBitMask = (1 shl (bitIndex and 0x1F)).inv()
                magia[wordIndex] = magia[wordIndex] and isolatedBitMask
                meta = Meta(meta.signBit, Magia.normLen(magia, meta.normLen))
            }
            return this
        }
        throw IllegalArgumentException()
    }

    private fun performBitOp(bitIndex: Int, isSetOp: Boolean): BigIntAccumulator {
        if (bitIndex >= 0) {
            val newBitLen = max(bitIndex + 1, Magia.bitLen(this.magia, this.meta.normLen))
            ensureBitLen(newBitLen)
            val wordIndex = bitIndex ushr 5
            val isolatedBit = (1 shl (bitIndex and 0x1F))
            val limb = magia[wordIndex]
            magia[wordIndex] =
                if (isSetOp)
                    limb or isolatedBit
                else
                    limb and isolatedBit.inv()
            meta = Meta(meta.signBit, max(meta.normLen, wordIndex + 1))
            check (Magia.isNormalized(magia, meta.normLen))
            return this
        }
        throw IllegalArgumentException()
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
        val normLen = Magia.setAdd(magia, magia, normLen, dw)
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
