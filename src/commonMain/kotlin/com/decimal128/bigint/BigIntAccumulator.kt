// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.intrinsic.unsignedMulHi
import kotlin.math.absoluteValue
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
    override var meta: Meta,
    override var magia: Magia,
    internal var tmp1: Magia
) : Magian {
    constructor() : this(Meta(0), Magia(4), Magus.ZERO)

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
     * Returns `true` if this BigIntAccumulator currently is zero.
     */
    fun isZero() = meta.normLen == 0

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
            magia = Magus.newWithFloorLen(minLimbLen)
    }

    /**
     * Ensures the limb array has at least [minLimbLen] capacity.
     *
     * If the array is too small, a larger one is allocated and the
     * existing contents are copied into it.
     */
    private inline fun ensureCapacityCopy(minLimbLen: Int) {
        if (magia.size < minLimbLen)
            magia = Magus.newCopyWithFloorLen(magia, minLimbLen)
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
        magia = Magus.newCopyWithFloorLen(magia, meta.normLen, newLimbLen)
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
    private fun set(yMeta: Meta, y: Magia): BigIntAccumulator {
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

    private fun setAddImpl(xMeta: Meta, x: Magia, yMeta: Meta, y: Magia): BigIntAccumulator {
        when {
            yMeta.isZero -> set(xMeta, x)
            xMeta.isZero -> set(yMeta, y)
            xMeta.signBit == yMeta.signBit -> {
                ensureCapacityDiscard(xMeta.normLen + yMeta.normLen + 1)
                meta = Meta(xMeta.signBit,
                    Magus.setAdd(magia, x, xMeta.normLen, y, yMeta.normLen))
            }
            else -> {
                val cmp: Int = Magus.compare(x, xMeta.normLen, y, yMeta.normLen)
                when {
                    cmp > 0 -> {
                        ensureCapacityDiscard(xMeta.normLen)
                        meta = Meta(xMeta.signBit,
                            Magus.setSub(magia, x, xMeta.normLen, y, yMeta.normLen))
                    }
                    cmp < 0 -> {
                        ensureCapacityDiscard(yMeta.normLen)
                        meta = Meta(yMeta.signBit,
                            Magus.setSub(magia, y, yMeta.normLen, x, xMeta.normLen))
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

    private fun setMulImpl(xMeta: Meta, x: Magia, yMeta: Meta, y: Magia): BigIntAccumulator {
        swapTmp1()
        val xNormLen = xMeta.normLen
        val yNormLen = yMeta.normLen
        ensureCapacityDiscard(xNormLen + yNormLen)
        meta = Meta(xMeta.signBit xor yMeta.signBit,
            Magus.setMul(magia, x, xNormLen, y, yNormLen))
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

    private fun setDivImpl(xMeta: Meta, x: Magia, yMeta: Meta, y: Magia): BigIntAccumulator {
        val xNormLen = xMeta.normLen
        val yNormLen = yMeta.normLen
        if (yNormLen == 0)
            throw ArithmeticException("div by zero")
        swapTmp1()
        ensureCapacityDiscard(xNormLen)
        meta = Meta(xMeta.signBit xor yMeta.signBit,
            Magus.setDiv(magia, x, xNormLen, y, yNormLen))
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

    private fun setRemImpl(xMeta: Meta, x: Magia, yMeta: Meta, y: Magia): BigIntAccumulator {
        val xNormLen = xMeta.normLen
        val yNormLen = yMeta.normLen
        if (yNormLen == 0)
            throw ArithmeticException("div by zero")
        swapTmp1()
        ensureCapacityDiscard(xNormLen)
        meta = Meta(xMeta.signBit,
            Magus.setRem(magia, x, xNormLen, y, yNormLen))
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

    private fun setShlImpl(xMeta: Meta, x: Magia, bitCount: Int): BigIntAccumulator {
        return when {
            bitCount < 0 -> throw IllegalArgumentException("negative bitCount")
            bitCount == 0 -> set(xMeta, x)
            xMeta.isZero -> setZero()
            else -> {
                val xBitLen = Magus.bitLen(x, xMeta.normLen)
                val zBitLen = xBitLen + bitCount
                ensureBitCapacityDiscard(zBitLen)
                meta = Meta(meta.signBit,
                    Magus.setShiftLeft(magia, x, xMeta.normLen, bitCount)
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

    private fun setUshrImpl(xMeta: Meta, x: Magia, bitCount: Int): BigIntAccumulator {
        val xBitLen = Magus.bitLen(x, xMeta.normLen)
        val zBitLen = xBitLen - bitCount
        return when {
            bitCount < 0 -> throw IllegalArgumentException("negative bitCount")
            bitCount == 0 -> set(xMeta, x)
            zBitLen <= 0 -> setZero()
            else -> {
                ensureBitCapacityDiscard(zBitLen)
                meta = Meta(0,
                    Magus.setShiftRight(magia, x, xMeta.normLen, bitCount))
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

    private fun setShrImpl(xMeta: Meta, x: Magia, bitCount: Int): BigIntAccumulator {
        when {
            bitCount > 0 -> {
                val bitLen = Magus.bitLen(x, xMeta.normLen)
                val zBitLen = bitLen - bitCount
                if (zBitLen <= 0)
                    return if (xMeta.isNegative) set (-1) else setZero()
                val needsIncrement = xMeta.isNegative && Magus.testAnyBitInLowerN(x, bitCount)
                ensureBitCapacityDiscard(zBitLen)
                var normLen = Magus.setShiftRight(magia, x, xMeta.normLen, bitCount)
                check (normLen > 0)
                if (needsIncrement) {
                    ensureBitCapacityCopy(zBitLen + 1)
                    normLen = Magus.setAdd(magia, magia, normLen, 1u)
                }
                meta = Meta(xMeta.signFlag, normLen)
            }
            bitCount == 0 -> {}
            else -> throw IllegalArgumentException("bitCount < 0")
        }
        return this
    }

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
                meta = Meta(meta.signBit, Magus.normLen(magia, meta.normLen))
            }
            return this
        }
        throw IllegalArgumentException()
    }

    /**
     * Applies a bit mask of `bitWidth` consecutive 1-bits starting at `bitIndex`
     * to this accumulator, clearing all bits outside that range. The sign is
     * preserved. Operates in place and returns this.
     *
     * Equivalent to:
     *
     *     this = sign(this) * (abs(this) & ((2^bitWidth - 1) << bitIndex))
     *
     * @throws IllegalArgumentException if `bitWidth` or `bitIndex` is negative.
     */
    fun applyBitMask(bitWidth: Int, bitIndex: Int = 0): BigIntAccumulator {
        val myBitLen = magnitudeBitLen()
        when {
            bitIndex < 0 || bitWidth < 0 ->
                throw IllegalArgumentException(
                    "illegal negative arg bitIndex:$bitIndex bitCount:$bitWidth")
            bitWidth == 0 ||
                    bitIndex >= myBitLen ||
                    bitWidth == 1 && !testBit(bitIndex) -> return setZero()
            bitWidth == 1 -> {
                val limbIndex = (bitIndex ushr 5)
                magia.fill(0, 0, limbIndex)
                magia[limbIndex] = 1 shl (bitIndex and 0x1F)
                meta = Meta(meta.signBit, limbIndex + 1)
                return this
            }
        }
        // more than 1 bit wide and some overlap
        val clampedBitLen = min(bitWidth + bitIndex, myBitLen)
        val normLen = (clampedBitLen + 0x1F) ushr 5
        val nlz = (normLen shl 5) - clampedBitLen
        magia[normLen - 1] = magia[normLen - 1] and (-1 ushr nlz)
        val loIndex = bitIndex ushr 5
        magia.fill(0, 0, loIndex)
        val ctz = bitIndex and 0x1F
        magia[loIndex] = magia[loIndex] and (-1 shl ctz)
        meta = Meta(meta.signBit, normLen)
        return this
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
            tmp1 = Magia(4)
        tmp1[0] = lo64.toInt()
        tmp1[1] = (lo64 shr 32).toInt()
        tmp1[2] = hi64.toInt()
        tmp1[3] = (hi64 shr 32).toInt()
        val normLen = Magus.normLen(tmp1, 4)
        mutateAddMagImpl(Meta(0, normLen), tmp1)
    }

    /**
     * Adds the square of the given BigInt value to this accumulator.
     *
     * @param hi the value to square and add.
     * @see addSquareOf(ULong)
     */
    fun addSquareOf(hi: BigInt) = addSquareOfImpl(hi.magia, Magus.normLen(hi.magia))

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
                val normLen = Magus.setSub(magia, magia, meta.normLen, dw)
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
    private fun mutateAddImpl(yMeta: Meta, y: Magia) {
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
        val cmp = Magus.compare(magia, normLen, y, yMeta.normLen)
        when {
            cmp > 0 -> meta = Meta(signBit,
                Magus.setSub(magia, magia, normLen, y, yMeta.normLen))
            cmp < 0 -> {
                ensureCapacityCopy(yMeta.normLen)
                meta = Meta(yMeta.signBit,
                    Magus.setSub(magia, y, yMeta.normLen, magia, normLen))
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
        val normLen = Magus.setAdd(magia, magia, normLen, dw)
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
    private fun mutateAddMagImpl(yMeta: Meta, y: Magia) {
        ensureCapacityCopy(yMeta.normLen + 1)
        meta = Meta(signBit,
            Magus.setAdd(magia, magia, normLen, y, yMeta.normLen))
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
    private inline fun addSquareOfImpl(y: Magia, yNormLen: Int) {
        val sqrLenMax = yNormLen * 2
        if (tmp1.size < sqrLenMax)
            tmp1 = Magus.newWithFloorLen(sqrLenMax)
        else
            tmp1.fill(0, 0, sqrLenMax)
        val normLenSqr = Magus.setSqr(tmp1, y, yNormLen)
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
        meta = Meta(signFlag xor wSign, Magus.setMul(magia, magia, normLen, w))
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
        meta = Meta(signFlag xor dwSign, Magus.setMul(magia, magia, normLen, dw))
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
    private fun mutateMulImpl(yMeta: Meta, y: Magia) {
        validate()
        if (normLen == 0 || yMeta.normLen == 0) {
            setZero()
            return
        }
        swapTmp1()
        ensureCapacityCopy(normLen + yMeta.normLen + 1)
        val normLen = Magus.setMul(magia, tmp1, normLen, y, yMeta.normLen)
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
                tmp1 = Magus.newWithFloorLen(newLimbLenMax)
            else
                tmp1.fill(0, 0, newLimbLenMax)
            swapTmp1()
            meta = Meta(0,
                Magus.setSqr(magia, tmp1, normLen))
        }
    }

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
        Zoro.EQ(meta, magia, other.meta, other.magia)

    /**
     * Comparison predicate for numerical equality with the current
     * value of a mutable [BigIntAccumulator].
     *
     * @param acc the [BigIntAccumulator] to compare with
     * @return `true` if both have the same sign and identical magnitude, `false` otherwise
     */
    infix fun EQ(acc: BigIntAccumulator): Boolean =
        Zoro.EQ(meta, magia, acc.meta, acc.magia)

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

/**
 * Converts a 32-bit [Int] to a 64-bit [ULong] with zero-extension.
 *
 * This method treats the input [n] as an unsigned 32-bit value and
 * returns it as a [ULong] where the upper 32 bits are zero. In
 * this context it is used for consistently extracting 64-bit
 * zero-extended limbs from signed [IntArray] elements.
 *
 * @param n the 32-bit integer to convert.
 * @return the zero-extended 64-bit unsigned value.
 */
private inline fun dw32(n: Int) = n.toUInt().toULong()
