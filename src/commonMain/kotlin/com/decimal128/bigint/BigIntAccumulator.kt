// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

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
    meta: Meta,
    magia: Magia,
) : BigIntBase(meta, magia) {
    constructor() : this(Meta(0), Magia(4))

    internal var tmp1: Magia = Mago.ZERO
    internal var tmp2: Magia = Mago.ZERO

    companion object {

        private inline fun limbLenFromBitLen(bitLen: Int) = (bitLen + 0x1F) ushr 5

        fun withInitialBitCapacity(initialBitCapacity: Int): BigIntAccumulator {
            if (initialBitCapacity >= 0) {
                val initialLimbCapacity = max(4, limbLenFromBitLen(initialBitCapacity))
                return BigIntAccumulator(
                    Meta(0),
                    Mago.newWithFloorLen(initialLimbCapacity)
                )
            }
            throw IllegalArgumentException()
        }

        private fun from(meta: Meta, magia: Magia): BigIntAccumulator {
            return BigIntAccumulator(
                Meta(0),
                Mago.newWithFloorLen(meta.normLen)
            ).set(meta, magia)
        }

        fun from(bi: BigInt) = from(bi.meta, bi.magia)

    }

    val normLen: Int
        get() = meta.normLen

    val signBit: Int
        get() = meta.signBit

    val signFlag: Boolean
        get() = meta.signFlag


    private fun validate() {
        check(
            normLen <= magia.size &&
                    magia.size >= 4 &&
                    (normLen == 0 || magia[normLen - 1] != 0)
        )
    }

    // <<<<<<<<<<< BEGIN STORAGE MANAGEMENT FUNCTIONS >>>>>>>>>>>>

    /**
     * Resizes the internal limb storage, discarding any existing value.
     *
     * Capacity policy:
     * - If the current backing array is the initial fixed-size storage (4 limbs),
     *   allocate a new array with capacity **at least** [minLimbLen].
     * - Otherwise, allocate with additional headroom (~50%) to reduce the number
     *   of future reallocations.
     *
     * The final capacity is rounded up to the allocator’s heap quantum
     * (e.g., 16 bytes / 4 ints).
     *
     * @param minLimbLen the minimum number of limbs required; must exceed the
     *        current capacity and the inline storage size.
     */
    private fun resizeDiscard(minLimbLen: Int) {
        check (minLimbLen > 4 && minLimbLen > magia.size)
        // if the existing magia.size == 4 then this is the first resizing.
        // if this is the first resizing then give them requested size.
        // otherwise, we are in a growth pattern, so give them 50% more.
        val headRoom = (minLimbLen ushr 1) and ((4 - magia.size) shr 31)
        // newWithFloorLen rounds up to heap quantum, 16 bytes, 4 ints
        _magia = Mago.newWithFloorLen(minLimbLen + headRoom)
    }

    /**
     * Resizes the internal limb storage while preserving the current value.
     *
     * Capacity policy:
     * - If the current backing array is the initial fixed-size storage (4 limbs),
     *   allocate a new array with capacity **at least** [minLimbLen].
     * - Otherwise, allocate with additional headroom (~50%) to reduce the number
     *   of future reallocations.
     *
     * The final capacity is rounded up to the allocator’s heap quantum.
     * Only the normalized limbs ([meta.normLen]) are copied; any additional limbs
     * in the new storage remain zero-initialized.
     *
     * @param minLimbLen the minimum number of limbs required; must exceed the
     *        current capacity and the inline storage size.
     */
    private fun resizeCopy(minLimbLen: Int) {
        check (minLimbLen > 4 && minLimbLen > magia.size)
        val t = _magia
        val headRoom = (minLimbLen ushr 1) and ((4 - magia.size) shr 31)
        _magia = Mago.newWithFloorLen(minLimbLen + headRoom)
        t.copyInto(magia, 0, 0, meta.normLen)
    }

    /**
     * Resizes the `tmp1` temporary buffer.
     *
     * Temporary buffers start with zero capacity. On the first allocation,
     * the buffer is grown to a capacity **at least** [minLimbLen]. On subsequent
     * resizes, additional headroom (~50%) is added to reduce reallocation.
     *
     * The final capacity is rounded up to the allocator’s heap quantum.
     * Existing contents, if any, are discarded.
     *
     * @param minLimbLen the minimum number of limbs required; must exceed the
     *        current capacity of the temporary buffer.
     */
    private fun resizeTmp1(minLimbLen: Int) {
        check (minLimbLen > tmp1.size)
        // tmp arrays start off with zero size
        // if this is the first resize then give them what they want
        // otherwise, give them 50% more
        val headRoom = (minLimbLen ushr 1) and (-tmp1.size shr 31)
        tmp1 = Mago.newWithFloorLen(minLimbLen + headRoom)
    }

    /**
     * Resizes the `tmp2` temporary buffer.
     *
     * Temporary buffers start with zero capacity. On the first allocation,
     * the buffer is grown to a capacity **at least** [minLimbLen]. On subsequent
     * resizes, additional headroom (~50%) is added to reduce reallocation.
     *
     * The final capacity is rounded up to the allocator’s heap quantum.
     * Existing contents, if any, are discarded.
     *
     * @param minLimbLen the minimum number of limbs required; must exceed the
     *        current capacity of the temporary buffer.
     */
    private fun resizeTmp2(minLimbLen: Int) {
        check (minLimbLen > tmp2.size)
        // tmp arrays start off with zero size
        // if this is the first resize then give them what they want
        // otherwise, give them 50% more
        val headRoom = (minLimbLen ushr 1) and (-tmp2.size shr 31)
        tmp2 = Mago.newWithFloorLen(minLimbLen + headRoom)
    }

    /**
     * Ensures that the backing limb array has capacity **at least** [minLimbLen].
     *
     * If the current array is too small, it is replaced with a new zero-initialized
     * array whose capacity is at least [minLimbLen] (rounded up to the allocator’s
     * heap quantum). Any existing value is discarded.
     *
     * Note that discarding the contents can cause confusion in the debugger
     * because the remaining value in the debugger is not normalized ... triggering
     * issues with toString()
     *
     * @param minLimbLen the minimum number of limbs required.
     */
    private inline fun ensureCapacityDiscard(minLimbLen: Int) {
        if (magia.size < minLimbLen)
            resizeDiscard(minLimbLen)
    }

    /**
     * Ensures that the backing limb array has capacity **at least** [minLimbLen].
     *
     * If the current array is too small, it is replaced with a new zero-initialized
     * array whose capacity is at least [minLimbLen] (rounded up to the allocator’s
     * heap quantum). Only the normalized limbs are copied into the new storage,
     * and any additional limbs remain zero.
     *
     * @param minLimbLen the minimum number of limbs required.
     */
    private inline fun ensureCapacityCopy(minLimbLen: Int) {
        if (magia.size < minLimbLen)
            resizeCopy(minLimbLen)
    }

    /**
     * Ensures that the backing limb array has capacity **at least** [newLimbLen],
     * and that all limbs in the range `[meta.normLen, newLimbLen)` are zero.
     *
     * If the current array is large enough, any existing garbage limbs in that
     * range are explicitly cleared in place. If the array is too small, it is
     * replaced with a new zero-initialized array whose capacity is at least
     * [newLimbLen] (rounded up to the allocator’s heap quantum), and the normalized
     * limbs are copied.
     *
     * Existing normalized limbs (`[0, meta.normLen)`) are always preserved.
     *
     * @param newLimbLen the required limb length to be zeroed.
     */
    private inline fun ensureCapacityZeroed(newLimbLen: Int) {
        if (newLimbLen <= magia.size) {
            if (newLimbLen > meta.normLen)
                magia.fill(0, meta.normLen, newLimbLen)
        } else {
            // resize allocates new clean zeroed storage
            resizeCopy(newLimbLen)
        }
    }

    /**
     * Ensures capacity for representing at least [minBitLen] bits, discarding any
     * existing value.
     *
     * The bit-length requirement is converted to a minimum limb count
     * (`ceil(minBitLen / 32)`) and delegated to [ensureCapacityDiscard].
     *
     * @param minBitLen the minimum number of bits required.
     */
    private inline fun ensureBitCapacityDiscard(minBitLen: Int) =
        ensureCapacityDiscard((minBitLen + 0x1F) ushr 5)

    /**
     * Ensures capacity for representing at least [minBitLen] bits while preserving
     * the existing value.
     *
     * The bit-length requirement is converted to a minimum limb count
     * (`ceil(minBitLen / 32)`) and delegated to [ensureCapacityCopy].
     *
     * @param minBitLen the minimum number of bits required.
     */
    private inline fun ensureBitCapacityCopy(minBitLen: Int) =
        ensureCapacityCopy((minBitLen + 0x1F) ushr 5)

    /**
     * Ensures that the temporary limb buffer `tmp1` has capacity **at least**
     * [minLimbLen].
     *
     * If `tmp1` is too small, it is replaced with a new zero-initialized array whose
     * capacity is at least [minLimbLen] (rounded up to the allocator’s heap quantum).
     * Any existing contents are discarded.
     *
     * @param minLimbLen the minimum number of limbs required.
     */
    private inline fun ensureTmp1Capacity(minLimbLen: Int) {
        if (minLimbLen > tmp1.size)
            resizeTmp1(minLimbLen)
    }

    /**
     * Ensures that the temporary limb buffer `tmp2` has capacity **at least**
     * [minLimbLen].
     *
     * If `tmp2` is too small, it is replaced with a new zero-initialized array whose
     * capacity is at least [minLimbLen] (rounded up to the allocator’s heap quantum).
     * Any existing contents are discarded.
     *
     * @param minLimbLen the minimum number of limbs required.
     */
    private inline fun ensureTmp2Capacity(minLimbLen: Int) {
        if (minLimbLen > tmp2.size)
            resizeTmp2(minLimbLen)
    }

    /**
     * Ensures that the temporary limb buffer `tmp1` has capacity **at least**
     * [newLimbLen], and that all limbs in the range `[0, newLimbLen)` are zero.
     *
     * If `tmp1` is already large enough, it is zero-cleared up to `newLimbLen`.
     * If it is too small, it is replaced with a new zero-initialized array
     * whose capacity is at least [newLimbLen] (rounded up to the allocator’s heap
     * quantum).
     *
     * Any existing contents are discarded.
     *
     * @param newLimbLen the required limb length to be zeroed.
     */
    private inline fun ensureTmp1CapacityZeroed(newLimbLen: Int) {
        if (newLimbLen <= tmp1.size)
            tmp1.fill(0, 0, newLimbLen)
        else
            resizeTmp1(newLimbLen)
    }

    /**
     * Swaps the temporary limb buffer `tmp1` with the primary backing array `magia`.
     *
     * This is a pointer swap with no allocation or copying.
     * After the swap, the previous contents of `magia` become `tmp1`, and
     * the previous contents of `tmp1` become the active backing storage.
     */
    private inline fun swapTmp1() {
        val t = tmp1; tmp1 = _magia; _magia = t
    }

    /**
     * Swaps the temporary limb buffer `tmp1` with the primary backing array `magia`,
     * then copies the normalized limbs back into the active storage.
     *
     * After the swap, the previous contents of `tmp1` become the active backing
     * array. The normalized limbs (`[0, meta.normLen)`) are then copied from
     * `tmp1` into `magia`, preserving the current value while allowing the
     * temporary buffer to be reused.
     *
     * No allocation occurs.
     */
    private inline fun swapTmp1Copy() {
        val t = tmp1; tmp1 = _magia; _magia = t
        tmp1.copyInto(magia, 0, 0, meta.normLen)
    }


    // <<<<<<<<<<< END STORAGE MANAGEMENT FUNCTIONS >>>>>>>>>>>>

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
        _meta = Meta(0)
        return this
    }

    fun setOne(signFlag: Boolean = false): BigIntAccumulator {
        validate()
        _meta = Meta(signFlag, 1)
        magia[0] = 1
        validate()
        return this
    }

    fun mutAbs(): BigIntAccumulator {
        _meta = _meta.abs()
        return this
    }

    fun mutNegate(): BigIntAccumulator {
        _meta = _meta.negate()
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
     * Sets this accumulator’s value from another [BigIntAccumulator].
     *
     * The accumulator copies the sign, and magnitude of the source accumulator.
     * Internal storage of the destination is reused when possible.
     *
     * @param bi the source [BigIntAccumulator].
     * @return this accumulator instance, for call chaining.
     */
    fun set(bi: BigIntBase): BigIntAccumulator = set(bi.meta, bi.magia)

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
        _meta = Meta(sign, normLen)
        // limbLen = if (dw == 0uL) 0 else if ((dw shr 32) == 0uL) 1 else 2
        magia[0] = dw.toInt()
        magia[1] = (dw shr 32).toInt()
        return this
    }

    fun set(sign: Boolean, dwHi: ULong, dwLo: ULong): BigIntAccumulator {
        val bitLen = if (dwHi == 0uL)
            64 - dwLo.countLeadingZeroBits()
        else
            128 - dwHi.countLeadingZeroBits()
        val normLen = (bitLen + 0x1F) ushr 5
        _meta = Meta(sign, normLen)
        // limbLen = if (dw == 0uL) 0 else if ((dw shr 32) == 0uL) 1 else 2
        _magia[0] = dwLo.toInt()
        _magia[1] = (dwLo shr 32).toInt()
        _magia[2] = dwHi.toInt()
        _magia[3] = (dwHi shr 32).toInt()
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
    private fun set(yMeta: Meta, y: Magia): BigIntAccumulator {
        ensureCapacityDiscard(yMeta.normLen)
        _meta = yMeta
        y.copyInto(magia, 0, 0, yMeta.normLen)
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
        BigInt.fromLittleEndianIntArray(meta.signFlag, magia, meta.normLen)

    fun setAdd(x: BigIntBase, n: Int) =
        setAddImpl(x.meta, x.magia, n < 0, n.absoluteValue.toUInt().toULong())
    fun setAdd(x: BigIntBase, w: UInt) =
        setAddImpl(x.meta, x.magia, false, w.toULong())
    fun setAdd(x: BigIntBase, l: Long) =
        setAddImpl(x.meta, x.magia, l < 0, l.absoluteValue.toULong())
    fun setAdd(x: BigIntBase, dw: ULong) =
        setAddImpl(x.meta, x.magia, false, dw)
    fun setAdd(x: BigIntBase, y: BigIntBase) =
        setAddImpl(x.meta, x.magia, y.meta, y.magia)

    fun setSub(x: BigIntBase, n: Int) =
        setAddImpl(x.meta, x.magia, n >= 0, n.absoluteValue.toUInt().toULong())
    fun setSub(x: BigIntBase, w: UInt) =
        setAddImpl(x.meta, x.magia, true, w.toULong())
    fun setSub(x: BigIntBase, l: Long) =
        setAddImpl(x.meta, x.magia, l >= 0L, l.absoluteValue.toULong())
    fun setSub(x: BigIntBase, dw: ULong) =
        setAddImpl(x.meta, x.magia, true, dw)
    fun setSub(x: BigIntBase, y: BigIntBase) =
        setAddImpl(x.meta, x.magia, y.meta.negate(), y.magia)

    private fun setAddImpl(xMeta: Meta, xMagia: Magia, ySign: Boolean, yDw: ULong): BigIntAccumulator {
        check (Mago.isNormalized(xMagia, xMeta.normLen))
        when {
            yDw == 0uL -> set(xMeta, xMagia)
            xMeta.isZero -> set(ySign, yDw)
            xMeta.signFlag == ySign -> {
                ensureCapacityDiscard(max(xMeta.normLen, 2) + 1)
                _meta = Meta(
                    xMeta.signBit,
                    Mago.setAdd64(magia, xMagia, xMeta.normLen, yDw)
                )
            }
            else -> {
                val cmp: Int = Mago.compare(xMagia, xMeta.normLen, yDw)
                when {
                    cmp > 0 -> {
                        ensureCapacityDiscard(xMeta.normLen)
                        _meta = Meta(
                            xMeta.signBit,
                            Mago.setSub64(magia, xMagia, xMeta.normLen, yDw)
                        )
                    }
                    cmp < 0 -> set(ySign, yDw - toRawULong())
                    else -> setZero()
                }
            }
        }
        return this
    }

    private fun setAddImpl(xMeta: Meta, x: Magia, yMeta: Meta, y: Magia): BigIntAccumulator {
        check (Mago.isNormalized(x, xMeta.normLen))
        check (Mago.isNormalized(y, yMeta.normLen))
        when {
            yMeta.isZero -> set(xMeta, x)
            xMeta.isZero -> set(yMeta, y)
            xMeta.signBit == yMeta.signBit -> {
                ensureCapacityDiscard(max(xMeta.normLen, yMeta.normLen) + 1)
                _meta = Meta(
                    xMeta.signBit,
                    Mago.setAdd(magia, x, xMeta.normLen, y, yMeta.normLen)
                )
            }

            else -> {
                val cmp: Int = Mago.compare(x, xMeta.normLen, y, yMeta.normLen)
                when {
                    cmp > 0 -> {
                        ensureCapacityDiscard(xMeta.normLen)
                        _meta = Meta(
                            xMeta.signBit,
                            Mago.setSub(magia, x, xMeta.normLen, y, yMeta.normLen)
                        )
                    }

                    cmp < 0 -> {
                        ensureCapacityDiscard(yMeta.normLen)
                        _meta = Meta(
                            yMeta.signBit,
                            Mago.setSub(magia, y, yMeta.normLen, x, xMeta.normLen)
                        )
                    }

                    else -> setZero()
                }
            }
        }
        return this
    }

    fun setMul(x: BigIntBase, n: Int) =
        setMulImpl(x.meta, x.magia, n < 0, n.absoluteValue.toUInt())
    fun setMul(x: BigIntBase, w: UInt) =
        setMulImpl(x.meta, x.magia, false, w)
    fun setMul(x: BigIntBase, l: Long) =
        setMulImpl(x.meta, x.magia, l < 0, l.absoluteValue.toULong())
    fun setMul(x: BigIntBase, dw: ULong) =
        setMulImpl(x.meta, x.magia, false, dw)
    fun setMul(x: BigIntBase, y: BigIntBase) =
        setMulImpl(x.meta, x.magia, y.meta, y.magia)

    private fun setMulImpl(xMeta: Meta, x: Magia, wSign: Boolean, w: UInt): BigIntAccumulator {
        val xNormLen = xMeta.normLen
        ensureTmp1Capacity(xNormLen + 1)
        _meta = Meta(
            xMeta.signFlag xor wSign,
            Mago.setMul32(tmp1, x, xNormLen, w)
        )
        swapTmp1()
        return this
    }

    private fun setMulImpl(xMeta: Meta, x: Magia, wSign: Boolean, dw: ULong): BigIntAccumulator {
        val xNormLen = xMeta.normLen
        ensureTmp1Capacity(xNormLen + 2)
        _meta = Meta(
            xMeta.signFlag xor wSign,
            Mago.setMul64(tmp1, x, xNormLen, dw)
        )
        swapTmp1()
        return this
    }

    private fun setMulImpl(xMeta: Meta, x: Magia, yMeta: Meta, y: Magia): BigIntAccumulator {
        val xNormLen = xMeta.normLen
        val yNormLen = yMeta.normLen
        ensureTmp1Capacity(xNormLen + yNormLen)
        _meta = Meta(
            xMeta.signBit xor yMeta.signBit,
            Mago.setMul(tmp1, x, xNormLen, y, yNormLen)
        )
        swapTmp1()
        return this
    }

    fun setSqr(n: Int): BigIntAccumulator = setSqr(n.absoluteValue.toUInt())

    fun setSqr(w: UInt): BigIntAccumulator {
        val abs = w.toULong()
        return set(abs * abs)
    }

    fun setSqr(l: Long): BigIntAccumulator = setSqr(l.absoluteValue.toULong())

    fun setSqr(dw: ULong): BigIntAccumulator {
        val lo = dw * dw
        val hi = unsignedMulHi(dw, dw)
        return set(false, hi, lo)
    }

    fun setSqr(x: BigIntBase): BigIntAccumulator =
        setSqrImpl(x.meta, x.magia)

    private fun setSqrImpl(xMeta: Meta, x: Magia): BigIntAccumulator {
        check(Mago.isNormalized(x, xMeta.normLen))
        val xNormLen = xMeta.normLen
        ensureTmp1CapacityZeroed(xNormLen + xNormLen)
        _meta = Meta(
            0,
            Mago.setSqr(tmp1, x, xNormLen)
        )
        swapTmp1()
        return this
    }

    fun setDiv(x: BigIntAccumulator, n: Int): BigIntAccumulator =
        setDivImpl(x.meta, x.magia , n < 0, n.absoluteValue.toUInt().toULong())
    fun setDiv(x: BigIntAccumulator, w: UInt): BigIntAccumulator =
        setDivImpl(x.meta, x.magia , false, w.toULong())
    fun setDiv(x: BigIntAccumulator, l: Long): BigIntAccumulator =
        setDivImpl(x.meta, x.magia , l < 0L, l.absoluteValue.toULong())
    fun setDiv(x: BigIntAccumulator, dw: ULong): BigIntAccumulator =
        setDivImpl(x.meta, x.magia , false, dw)
    fun setDiv(x: BigIntBase, y: BigIntBase): BigIntAccumulator =
        setDivImpl(x.meta, x.magia, y.meta, y.magia)

    private fun setDivImpl(xMeta: Meta, xMagia: Magia, ySign: Boolean, yDw: ULong): BigIntAccumulator {
        ensureCapacityDiscard(xMeta.normLen - 1 + 1) // yDw might represent a single limb
        if (trySetDivFastPath64(xMeta, xMagia, ySign, yDw))
            return this
        ensureTmp1Capacity(xMeta.normLen + 1)
        val normLen = Mago.setDiv64(magia, xMagia, xMeta.normLen, tmp1, yDw)
        _meta = Meta(xMeta.signFlag xor ySign, normLen)
        return this
    }

    private fun setDivImpl(xMeta: Meta, x: Magia, yMeta: Meta, y: Magia): BigIntAccumulator {
        ensureCapacityDiscard(xMeta.normLen - yMeta.normLen + 1)
        if (trySetDivFastPath(xMeta, x, yMeta, y))
            return this
        ensureTmp1Capacity(xMeta.normLen + 1)
        ensureTmp2Capacity(yMeta.normLen)
        _meta = Meta(xMeta.signBit xor yMeta.signBit,
            Mago.setDiv(magia, x, xMeta.normLen, tmp1, y, yMeta.normLen, tmp2))
        return this
    }

    private fun trySetDivFastPath(xMeta: Meta, xMagia: Magia, yMeta: Meta, yMagia: Magia): Boolean {
        val qSignFlag = xMeta.signFlag xor yMeta.signFlag
        val qNormLen = Mago.trySetDivFastPath(this.magia, xMagia, xMeta.normLen, yMagia, yMeta.normLen)
        if (qNormLen < 0)
            return false
        _meta = Meta(qSignFlag, qNormLen)
        return true
    }

    private fun trySetDivFastPath64(xMeta: Meta, xMagia: Magia, ySign: Boolean, yDw: ULong): Boolean {
        val qSignFlag = xMeta.signFlag xor ySign
        val qNormLen = Mago.trySetDivFastPath64(this.magia, xMagia, xMeta.normLen, yDw)
        if (qNormLen < 0)
            return false
        _meta = Meta(qSignFlag, qNormLen)
        return true
    }

    private fun trySetRemFastPath(xMeta: Meta, xMagia: Magia, yMeta: Meta, yMagia: Magia): Boolean {
        val rSignFlag = xMeta.signFlag
        val rNormLen = Mago.trySetRemFastPath(this.magia, xMagia, xMeta.normLen, yMagia, yMeta.normLen)
        if (rNormLen < 0)
            return false
        _meta = Meta(rSignFlag, rNormLen)
        return true
    }

    fun setRem(x: BigIntBase, n: Int): BigIntAccumulator =
        setRemImpl(x, n.absoluteValue.toUInt().toULong())
    fun setRem(x: BigIntBase, w: UInt): BigIntAccumulator =
        setRemImpl(x, w.toULong())
    fun setRem(x: BigIntBase, l: Long): BigIntAccumulator =
        setRemImpl(x, l.absoluteValue.toULong())
    fun setRem(x: BigIntBase, dw: ULong): BigIntAccumulator =
        setRemImpl(x, dw)
    fun setRem(x: BigIntBase, y: BigIntBase): BigIntAccumulator {
        ensureCapacityCopy(min(x.meta.normLen, y.meta.normLen))
        if (trySetRemFastPath(x.meta, x.magia, y.meta, y.magia))
            return this
        if (y.meta.normLen == 2)
            return setRemImpl(x, (y.magia[1].toULong() shl 32) or (y.magia[0].toUInt().toULong()))
        ensureTmp1Capacity(x.meta.normLen + 1)
        ensureTmp2Capacity(y.meta.normLen)
        val rNormLen = Mago.setRem(magia, x.magia, x.meta.normLen, tmp1, y.magia, y.meta.normLen, tmp2)
        _meta = Meta(x.meta.signBit, rNormLen)
        return this
    }


    private fun setRemImpl(x: BigIntBase, yDw: ULong): BigIntAccumulator {
        ensureTmp1Capacity(x.meta.normLen + 1)
        val rem = Mago.calcRem64(x.magia, x.meta.normLen, tmp1, yDw)
        return set(x.meta.signFlag, rem)
    }

    fun setMod(x: BigIntBase, n: Int): BigIntAccumulator =
        setModImpl(x, n < 0, n.absoluteValue.toUInt().toULong())
    fun setMod(x: BigIntBase, w: UInt): BigIntAccumulator =
        setModImpl(x, false, w.toULong())
    fun setMod(x: BigIntBase, l: Long): BigIntAccumulator =
        setModImpl(x, l < 0, l.absoluteValue.toULong())
    fun setMod(x: BigIntBase, dw: ULong): BigIntAccumulator =
        setModImpl(x, false, dw)
    fun setMod(x: BigIntBase, y: BigIntBase): BigIntAccumulator {
        if (y.meta.isNegative)
            throw ArithmeticException("cannot take modulus of a negative number")
        setRem(x, y)
        if (isNegative())
            setAdd(this, y)
        return this
    }

    private fun setModImpl(x: BigIntBase, ySign: Boolean, yDw: ULong): BigIntAccumulator {
        if (ySign)
            throw ArithmeticException("cannot take modulus of a negative number")
        setRem(x, yDw)
        if (isNegative())
            setAdd(this, yDw)
        return this
    }

    /**
     * Adds the given Int value to this accumulator.
     *
     * @param n the value to add.
     * @see plusAssign(Long)
     */
    operator fun plusAssign(n: Int) { setAdd(this, n) }

    /**
     * Adds the given UInt value to this accumulator.
     *
     * @param w the value to add.
     * @see plusAssign(Long)
     */
    operator fun plusAssign(w: UInt) { setAdd(this, w) }

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
    operator fun plusAssign(l: Long) { setAdd(this, l) }

    /**
     * Adds the given ULong value to this accumulator.
     *
     * @param dw the value to add.
     * @see plusAssign(Long)
     */
    operator fun plusAssign(dw: ULong) { setAdd(this, dw) }

    /**
     * Adds the given BigInt value to this accumulator.
     *
     * @param bi the value to add.
     * @see plusAssign(Long)
     */
    operator fun plusAssign(bi: BigIntBase) { setAdd(this, bi) }

    /**
     * Subtracts the given Int value from this accumulator.
     *
     * @param n the value to subtract.
     * @see minusAssign(Long)
     */
    operator fun minusAssign(n: Int) { setSub(this, n) }

    /**
     * Subtracts the given UInt value from this accumulator.
     *
     * @param w the value to subtract.
     * @see minusAssign(Long)
     */
    operator fun minusAssign(w: UInt) { setSub(this, w) }

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
    operator fun minusAssign(l: Long) { setSub(this, l) }

    /**
     * Subtracts the given ULong value from this accumulator.
     *
     * @param dw the value to subtract.
     * @see minusAssign(Long)
     */
    operator fun minusAssign(dw: ULong) { setSub(this, dw) }

    /**
     * Subtracts the given BigInt value from this accumulator.
     *
     * @param bi the value to subtract.
     * @see minusAssign(Long)
     */
    operator fun minusAssign(bi: BigIntBase) { setSub(this, bi) }

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
     * val acc = BigIntAccumulator().setOne() // must start at 1 for multiplication
     * acc *= 10
     * acc *= anotherBigInt
     * ```
     *
     * @param n the value to multiply by.
     * @see timesAssign(Long)
     *
     */
    operator fun timesAssign(n: Int) { setMul(this, n) }

    /**
     * Multiplies this accumulator by the given UInt value.
     *
     * @param w the value to multiply by.
     * @see timesAssign(Long)
     */
    operator fun timesAssign(w: UInt) { setMul(this, w) }

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
     * val acc = BigIntAccumulator().setOne() // must start at 1 for multiplication
     * acc *= 10L
     * ```
     *
     * @param l the value to multiply by.
     */
    operator fun timesAssign(l: Long) { setMul(this, l) }

    /**
     * Multiplies this accumulator by the given ULong value.
     *
     * @param dw the value to multiply by.
     * @see timesAssign(Long)
     */
    operator fun timesAssign(dw: ULong) { setMul(this, dw) }

    /**
     * Multiplies this accumulator by the given [BigInt]
     * or [BigIntAccumulator] value.
     *
     * @param bi the value to multiply by.
     * @see timesAssign(Long)
     */
    operator fun timesAssign(bi: BigIntBase) { setMul(this, bi) }

    operator fun divAssign(n: Int) { setDiv(this, n) }

    operator fun divAssign(w: UInt) { setDiv(this, w) }

    operator fun divAssign(l: Long) { setDiv(this, l) }

    operator fun divAssign(dw: ULong) = mutateDivImpl(false, dw)

    operator fun divAssign(bi: BigInt) { setDiv(this, bi) }

    operator fun divAssign(acc: BigIntAccumulator) { setDiv(this, acc) }

    operator fun remAssign(n: Int) = mutateRemImpl(n.absoluteValue.toUInt())

    operator fun remAssign(w: UInt) = mutateRemImpl(w)

    operator fun remAssign(l: Long) = mutateRemImpl(l.absoluteValue.toULong())

    operator fun remAssign(dw: ULong) = mutateRemImpl(dw)

    operator fun remAssign(bi: BigInt) { setRem(this, bi) }

    operator fun remAssign(acc: BigIntAccumulator) { setRem(this, acc) }

    /**
     * Mutates accumulator `this <<= bitCount`.
     * Sign remains the same.
     * Throws if [bitCount] is negative.
     */
    fun mutShl(bitCount: Int): BigIntAccumulator = setShl(this, bitCount)

    /**
     * Sets this accumulator to `x << bitCount`. Allocates space for the
     * resulting bit length. Throws if [bitCount] is negative.
     */
    fun setShl(x: BigIntAccumulator, bitCount: Int): BigIntAccumulator = when {
        bitCount < 0 -> throw IllegalArgumentException("negative bitCount")
        bitCount == 0 || x.isZero() -> set(x)
        else -> {
            val xMagia = x.magia
            ensureBitCapacityDiscard(x.magnitudeBitLen() + bitCount)
            _meta = Meta(meta.signBit,
                Mago.setShiftLeft(magia, xMagia, x.meta.normLen, bitCount))
            this
        }
    }

    /**
     * Mutates this accumulator `this >>>= bitCount`.
     *
     * The sign of this is ignored and the resulting value is the
     * non-negative magnitude.
     *
     * Throws if [bitCount] is negative.
     */
    fun mutUshr(bitCount: Int): BigIntAccumulator = setUshr(this, bitCount)

    /**
     * Sets this accumulator to `x >>> bitCount`.
     *
     * The sign of `x` is ignored and the resulting value is the
     * non-negative magnitude.
     *
     * Throws if [bitCount] is negative.
     */
    fun setUshr(x: BigIntBase, bitCount: Int): BigIntAccumulator {
        val zBitLen = x.magnitudeBitLen() - bitCount
        return when {
            bitCount < 0 -> throw IllegalArgumentException("negative bitCount")
            bitCount == 0 -> set(x)
            zBitLen <= 0 -> setZero()
            else -> {
                // if aliasing then we are shrinking
                ensureBitCapacityDiscard(zBitLen)
                _meta = Meta(
                    0,
                    Mago.setShiftRight(magia, x.magia, x.meta.normLen, bitCount))
                this
            }
        }
    }

    /**
     * Mutates this accumulator `x >>= bitCount`.
     *
     * Follows arithmetic shift right semantics ...
     * effectively treating the resulting value as if
     * `this` were stored in twos-complement.
     *
     * Throws if [bitCount] is negative.
     */
    fun mutShr(bitCount: Int): BigIntAccumulator = setShr(this, bitCount)

    /**
     * Sets this accumulator to `x >> bitCount`.
     *
     * Follows arithmetic shift right semantics if `x` is negative,
     * effectively treating the resulting value as if it were
     * stored in twos-complement.
     *
     * Throws if [bitCount] is negative.
     */
    fun setShr(x: BigIntBase, bitCount: Int): BigIntAccumulator {
        val zBitLen = x.magnitudeBitLen() - bitCount
        when {
            bitCount < 0 -> throw IllegalArgumentException("bitCount < 0")
            bitCount == 0 -> set(x)
            zBitLen <= 0 && x.meta.isNegative -> set(-1)
            zBitLen <= 0 -> setZero()
            else -> {
                val needsIncrement = x.meta.isNegative && Mago.testAnyBitInLowerN(x.magia, bitCount)
                // if aliasing then we are shrinking
                ensureBitCapacityDiscard(zBitLen)
                var normLen = Mago.setShiftRight(magia, x.magia, x.meta.normLen, bitCount)
                check (normLen > 0)
                if (needsIncrement) {
                    ensureBitCapacityCopy(zBitLen + 1)
                    normLen = Mago.setAdd64(magia, magia, normLen, 1u)
                }
                _meta = Meta(x.meta.signFlag, normLen)
            }
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
            ensureCapacityZeroed(wordIndex + 1)
            magia[wordIndex] = isolatedBit
            _meta = Meta(meta.signBit, wordIndex + 1)
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
                _meta = Meta(meta.signBit, Mago.normLen(magia, meta.normLen))
            }
            return this
        }
        throw IllegalArgumentException()
    }

    /**
     * Applies a bit mask of `bitWidth` consecutive 1-bits starting at `bitIndex`
     * to this accumulator, clearing all bits outside that range. The sign is
     * always cleared to non-negative. Operates in place and returns this.
     *
     * Equivalent to:
     *
     *     this = abs(this) & ((2^bitWidth - 1) << bitIndex)
     *
     * @throws IllegalArgumentException if `bitWidth` or `bitIndex` is negative.
     */
    fun applyBitMask(bitWidth: Int, bitIndex: Int = 0): BigIntAccumulator {
        check (isNormalized())
        val myBitLen = magnitudeBitLen()
        when {
            bitIndex < 0 || bitWidth < 0 ->
                throw IllegalArgumentException(
                    "illegal negative arg bitIndex:$bitIndex bitCount:$bitWidth")
            bitWidth == 0 || bitIndex >= myBitLen -> return setZero()
            bitWidth == 1 && !testBit(bitIndex) -> return setZero()
            bitWidth == 1 -> {
                val limbIndex = (bitIndex ushr 5)
                magia.fill(0, 0, limbIndex)
                magia[limbIndex] = 1 shl (bitIndex and 0x1F)
                _meta = Meta(limbIndex + 1)
                check (isNormalized())
                return this
            }
        }
        // more than 1 bit wide and some overlap
        val clampedBitLen = min(bitWidth + bitIndex, myBitLen)
        val normLen0 = (clampedBitLen + 0x1F) ushr 5
        val nlz = (normLen0 shl 5) - clampedBitLen
        magia[normLen0 - 1] = magia[normLen0 - 1] and (-1 ushr nlz)
        val loIndex = bitIndex ushr 5
        magia.fill(0, 0, loIndex)
        val ctz = bitIndex and 0x1F
        magia[loIndex] = magia[loIndex] and (-1 shl ctz)
        val normLen = Mago.normLen(magia, normLen0)
        _meta = Meta(normLen)
        check (isNormalized())
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
            setAdd(this, lo64)
            return
        }
        val hi64 = unsignedMulHi(dw, dw)
        if (tmp1.size < 4)
            tmp1 = Magia(4)
        tmp1[0] = lo64.toInt()
        tmp1[1] = (lo64 shr 32).toInt()
        tmp1[2] = hi64.toInt()
        tmp1[3] = (hi64 shr 32).toInt()
        val normLen = Mago.normLen(tmp1, 4)
        setAddImpl(meta, magia, Meta(0, normLen), tmp1)
    }

    /**
     * Adds the square of the given BigInt value to this accumulator.
     *
     * @param bi the value to square and add.
     * @see addSquareOf(ULong)
     */
    fun addSquareOf(bi: BigInt) = addSquareOfImpl(bi.magia, bi.meta.normLen)

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
     * Adds the absolute value of the given [BigInt] or
     * [BigIntAccumulator] to this accumulator.
     *
     * @param hi the value to add.
     * @see addAbsValueOf(Long)
     */
    fun addAbsValueOf(bi: BigIntBase) =
        setAddImpl(meta, magia, bi.meta.abs(), bi.magia)

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
            tmp1 = Mago.newWithFloorLen(sqrLenMax)
        else
            tmp1.fill(0, 0, sqrLenMax)
        val normLenSqr = Mago.setSqr(tmp1, y, yNormLen)
        setAddImpl(this.meta, this.magia, Meta(0, normLenSqr), tmp1)
        validate()
    }

    private fun mutateDivImpl(wSign: Boolean, w: UInt) {
        validate()
        val normLen = Mago.setDiv32(magia, magia, meta.normLen, w)
        _meta = Meta(meta.signFlag xor wSign, normLen)
        validate()
    }

    private fun mutateDivImpl(wSign: Boolean, dw: ULong) {
        validate()
        ensureTmp1Capacity(max(1, meta.normLen)) // dw might be a single limb
        val normLen = Mago.setDiv64(tmp1, magia, meta.normLen, unBuf=null, dw)
        swapTmp1()
        _meta = Meta(meta.signFlag xor wSign, normLen)
        validate()
    }

    private fun mutateRemImpl(w: UInt) {
        validate()
        val normLen = Mago.setRem32(magia, magia, meta.normLen, w)
        _meta = Meta(meta.signFlag, normLen)
        validate()
    }

    private fun mutateRemImpl(dw: ULong) {
        validate()
        val normLen = Mago.setRem64(magia, magia, meta.normLen, dw)
        _meta = Meta(meta.signFlag, normLen)
        validate()
    }

    /**
     * Value comparison for computational use.
     *
     * Equality is intentionally **asymmetric**:
     * - Compares by numeric value against [BigIntAccumulator], [BigInt], and
     *   selected integer primitives.
     * - `other.equals(this)` is **not** guaranteed to return the same result.
     *
     * This type is mutable and **not a value type**:
     * - Must not be used in hash-based collections.
     * - [hashCode] is unsupported and always throws.
     *
     * Intended for internal arithmetic and testing, not for generic equality checks.
     */
    override fun equals(other: Any?): Boolean {
        return when (other) {
            is BigIntAccumulator -> this EQ other
            is BigInt -> this EQ other
            is Int -> this EQ other
            is Long -> this EQ other
            is UInt -> this EQ other
            is ULong -> this EQ other
            else -> false
        }
    }

    /**
     * Always throws.
     *
     * `BigIntAccumulator` is mutable and must never be used as a key in hash-based
     * collections (`HashMap`, `HashSet`, etc.). Calling `hashCode()` is therefore
     * unsupported and results in an exception.
     */
    override fun hashCode(): Int =
        throw UnsupportedOperationException(
            "mutable BigIntAccumulator is an invalid key in collections")

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
