// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.intrinsic.unsignedMulHi
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/**
 * [MutableBigInt] provides mutable arithmetic operations for compute-heavy
 * calculations where heap-allocated storage of intermediate values
 * impacts runtime performance.
 *
 * Unlike immutable [BigInt] objects,
 * [MutableBigInt] objects modify their internal state in-place by growing
 * internal arrays as needed. Reuse of internal arrays minimizes heap churn
 * and maintains cache coherency. It is well-suited for long-running numerical
 * calculations such as crypto and collecting statistics on very large
 * datasets.
 *
 * Operations accept integer primitives (`Int`, `Long`, `UInt`, `ULong`),
 * [BigInt] instances, or other [MutableBigInt] instances as operands.
 *
 * Use of [MutableBigInt] for accumulation of series values is simple and
 * straightforward through the use of built-in operations for:
 * - Sum
 * - Sum of squares
 * - Sum of absolute values
 * - Product
 *
 * Typical statistical usage example:
 * ```
 * val sumAcc = MutableBigInt()
 * val sumSqrAcc = MutableBigInt()
 * val sumAbsAcc = MutableBigInt()
 * for (value in data) {
 *     sumAcc += value
 *     sumSqrAcc.addSquareOf(value)
 *     sumAbsAcc.addAbsValueOf(value)
 * }
 * val total = sumAcc.toBigInt()
 * ```
 *
 * Beyond the use case for gathering statistics, use of [MutableBigInt] is
 * much more complicated and dangerous. Algorithms should **always** be
 * implemented, tested, and thoroughly understood using regular [BigInt].
 * Deriving a [MutableBigInt] implementation from an existing [BigInt]
 * implementation is a separate task that should be undertaken only if
 * necessary.
 *
 * If the goal becomes elimination of heap allocation of [BigInt]
 * intermediate values then one must be very conscious of the order
 * and manner that operations are performed.
 *
 * In order to avoid heap churn all [MutableBigInt] values and
 * temporaries need to be allocated before starting the core
 * iterations of your computation.
 *
 * Operations effectively
 * become restricted to one mutation operation per line:
 * - `+=` `-=` `*=` `/=` `%=`
 * - `setAdd(a, b)` `setSub(c, d)` `setMul(e, f)` `setDiv(g, h)`
 *   `setRem(i, j)` `setMod(k, l)`
 * - `setShl(a, n)` `setUshr(b, m)` `setShr(c, k)`
 * - `withBitMask(bitWidth, bitIndex)`
 *
 * This combination of static `register` allocation and one operation
 * per `instruction` feels very much like assembly-level programming,
 * with each [MutableBigInt] acting like a CPU register.
 * The process of converting an existing algorithm it brings a new
 * appreciation of the power of nested infix expressions.
 *
 * The good news is that [MutableBigInt] makes sure that you are
 * not churning through heap storage. The bad news is that the
 * process is still surprisingly painful.
 *
 * ### Internal representation
 *
 * The implementation uses a sign–magnitude format with the magnitude held
 * in a little-endian [IntArray] of 32-bit unsigned limbs. The normalized
 * current length `normLen` and the `sign` are held in a [Meta] value
 * class whose representation is a single [Int].
 *
 * - The magnitude array is named `magia` (MAGnitude IntArray).
 * - Its allocated length is always ≥ 4.
 * - The current number of active limbs is stored in `meta.normLen`,
 *   ensuring that the most significant limb at `magia[meta.normLen - 1]`
 *   is nonzero.
 * - Zero is represented as `meta.normLen == 0`.
 * - The current `sign` is stored as `meta.signBit`
 *
 * Each [MutableBigInt] also has the ability to store two internal
 * temporary buffers `tmp1` and `tmp2`. The tmps are initialized to
 * a canonical empty array and get resized/grown if/when needed.
 * Long multiplication and squaring use only `tmp1`.
 * Long division uses both tmps. Since these operations
 * are happening as part of an iterative loop (otherwise you wouldn't be
 * converting to [MutableBigInt]) the temps will get allocated on the
 * first pass and reused on subsequent iterations.
 *
 * All allocations of [IntArray] limb storage (for the primary `magia`
 * and the tmps) are rounded up to eliminate internal fragmentation
 * and make all allocated space available for use.
 * The current generation of JVMs allocates on 16-byte boundaries, so
 * all [IntArray] allocations are rounded up to a multiple of 4.
 *
 * For a given [MutableBigInt] instance, the first reallocation
 * gives exactly the size requested (rounded up to the heap quantum).
 * Subsequent reallocations increase the requested size by 50%, on
 * the assumption that if we have reallocated twice then we will
 * keep growing.
 *
 * @constructor Creates a new accumulator initialized to zero.
 * Equivalent to calling `MutableBigInt()`.
 * @see BigInt for the immutable arbitrary-precision integer implementation.
 */
class MutableBigInt private constructor (
    meta: Meta,
    magia: Magia,
) : BigIntBase(meta, magia) {
    constructor() : this(Meta(0), Magia(4))

    internal var tmp1: Magia = Mago.ZERO
    internal var tmp2: Magia = Mago.ZERO

    companion object {

        private inline fun limbLenFromBitLen(bitLen: Int) = (bitLen + 0x1F) ushr 5

        fun withInitialBitCapacity(initialBitCapacity: Int): MutableBigInt {
            if (initialBitCapacity >= 0) {
                val initialLimbCapacity = max(4, limbLenFromBitLen(initialBitCapacity))
                return MutableBigInt(
                    Meta(0),
                    Mago.newWithFloorLen(initialLimbCapacity)
                )
            }
            throw IllegalArgumentException()
        }

        private fun from(meta: Meta, magia: Magia): MutableBigInt {
            return MutableBigInt(
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
    private inline fun ensureCapacityCopyZeroExtend(newLimbLen: Int) {
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
    fun setZero(): MutableBigInt {
        validate()
        _meta = Meta(0)
        return this
    }

    fun setOne(signFlag: Boolean = false): MutableBigInt {
        validate()
        _meta = Meta(signFlag, 1)
        magia[0] = 1
        validate()
        return this
    }

    fun mutAbs(): MutableBigInt {
        _meta = _meta.abs()
        return this
    }

    fun mutNegate(): MutableBigInt {
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
     * Sets this accumulator’s value from another [MutableBigInt].
     *
     * The accumulator copies the sign, and magnitude of the source accumulator.
     * Internal storage of the destination is reused when possible.
     *
     * @param bi the source [MutableBigInt].
     * @return this accumulator instance, for call chaining.
     */
    fun set(bi: BigIntBase): MutableBigInt = set(bi.meta, bi.magia)

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
    fun set(sign: Boolean, dw: ULong): MutableBigInt {
        val normLen = (64 - dw.countLeadingZeroBits() + 31) ushr 5
        _meta = Meta(sign, normLen)
        // limbLen = if (dw == 0uL) 0 else if ((dw shr 32) == 0uL) 1 else 2
        magia[0] = dw.toInt()
        magia[1] = (dw shr 32).toInt()
        return this
    }

    fun set(sign: Boolean, dwHi: ULong, dwLo: ULong): MutableBigInt {
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
    private fun set(yMeta: Meta, y: Magia): MutableBigInt {
        ensureCapacityDiscard(yMeta.normLen)
        _meta = yMeta
        y.copyInto(magia, 0, 0, yMeta.normLen)
        return this
    }

    /**
     * Creates an immutable [BigInt] representing the current value of this accumulator.
     *
     * The returned [BigInt] is a snapshot of the accumulator’s current sign and
     * magnitude. Subsequent modifications to this [MutableBigInt] do not affect
     * the returned [BigInt], and vice versa.
     *
     * This conversion performs a copy of the active limbs (`magia[0 until limbLen]`)
     * into the new [BigInt] instance.
     *
     * @return a new [BigInt] containing the current value of this accumulator.
     */
    override fun toBigInt(): BigInt = BigInt.from(this)

    fun setAdd(x: BigIntBase, n: Int) =
        setAddImpl(x, n < 0, n.absoluteValue.toUInt().toULong())
    fun setAdd(x: BigIntBase, w: UInt) =
        setAddImpl(x, false, w.toULong())
    fun setAdd(x: BigIntBase, l: Long) =
        setAddImpl(x, l < 0, l.absoluteValue.toULong())
    fun setAdd(x: BigIntBase, dw: ULong) =
        setAddImpl(x, false, dw)
    fun setAdd(x: BigIntBase, y: BigIntBase) =
        setAddImpl(x, y.meta, y.magia)

    fun setSub(x: BigIntBase, n: Int) =
        setAddImpl(x, n >= 0, n.absoluteValue.toUInt().toULong())
    fun setSub(x: BigIntBase, w: UInt) =
        setAddImpl(x, true, w.toULong())
    fun setSub(x: BigIntBase, l: Long) =
        setAddImpl(x, l >= 0L, l.absoluteValue.toULong())
    fun setSub(x: BigIntBase, dw: ULong) =
        setAddImpl(x, true, dw)
    fun setSub(x: BigIntBase, y: BigIntBase) =
        setAddImpl(x, y.meta.negate(), y.magia)

    private fun setAddImpl(x: BigIntBase, ySign: Boolean, yDw: ULong): MutableBigInt {
        check (x.isNormalized())
        val xMagia = x.magia // use only xMagia in here because of aliasing
        when {
            yDw == 0uL -> set(x)
            x.isZero() -> set(ySign, yDw)
            x.meta.signFlag == ySign -> {
                ensureCapacityDiscard(max(x.meta.normLen, 2) + 1)
                _meta = Meta(
                    x.meta.signBit,
                    Mago.setAdd64(magia, xMagia, x.meta.normLen, yDw)
                )
            }
            else -> {
                val cmp: Int = x.magnitudeCompareTo(yDw)
                when {
                    cmp > 0 -> {
                        ensureCapacityDiscard(x.meta.normLen)
                        _meta = Meta(
                            x.meta.signBit,
                            Mago.setSub64(magia, xMagia, x.meta.normLen, yDw)
                        )
                    }
                    cmp < 0 -> set(ySign, yDw - toRawULong())
                    else -> setZero()
                }
            }
        }
        return this
    }

    private fun setAddImpl(x: BigIntBase, yMeta: Meta, yMagia: Magia): MutableBigInt {
        check (x.isNormalized())
        check (Mago.isNormalized(yMagia, yMeta.normLen))
        val xMagia = x.magia // save for aliasing
        when {
            yMeta.isZero -> set(x)
            x.isZero() -> set(yMeta, yMagia)
            x.meta.signFlag == yMeta.signFlag -> {
                ensureCapacityDiscard(max(x.meta.normLen, yMeta.normLen) + 1)
                _meta = Meta(
                    x.meta.signBit,
                    Mago.setAdd(magia, xMagia, x.meta.normLen, yMagia, yMeta.normLen)
                )
            }

            else -> {
                val cmp: Int = x.magnitudeCompareTo(yMeta, yMagia)
                when {
                    cmp > 0 -> {
                        ensureCapacityDiscard(x.meta.normLen)
                        _meta = Meta(
                            x.meta.signBit,
                            Mago.setSub(magia, xMagia, x.meta.normLen, yMagia, yMeta.normLen))
                    }

                    cmp < 0 -> {
                        ensureCapacityDiscard(yMeta.normLen)
                        _meta = Meta(
                            yMeta.signBit,
                            Mago.setSub(magia, yMagia, yMeta.normLen, xMagia, x.meta.normLen))
                    }

                    else -> setZero()
                }
            }
        }
        return this
    }

    fun setMul(x: BigIntBase, n: Int) =
        setMulImpl(x, n < 0, n.absoluteValue.toUInt())
    fun setMul(x: BigIntBase, w: UInt) =
        setMulImpl(x, false, w)
    fun setMul(x: BigIntBase, l: Long) =
        setMulImpl(x, l < 0, l.absoluteValue.toULong())
    fun setMul(x: BigIntBase, dw: ULong) =
        setMulImpl(x, false, dw)
    fun setMul(x: BigIntBase, y: BigIntBase) =
        setMulImpl(x.meta, x.magia, y.meta, y.magia)

    private fun setMulImpl(x: BigIntBase, wSign: Boolean, w: UInt): MutableBigInt {
        val xMagia = x.magia
        ensureCapacityDiscard(x.meta.normLen + 1)
        _meta = Meta(
            x.meta.signFlag xor wSign,
            Mago.setMul32(magia, xMagia, x.meta.normLen, w)
        )
        return this
    }

    private fun setMulImpl(x: BigIntBase, dwSign: Boolean, dw: ULong): MutableBigInt {
        val xMagia = x.magia
        ensureCapacityDiscard(x.meta.normLen + 2)
        _meta = Meta(
            x.meta.signFlag xor dwSign,
            Mago.setMul64(magia, xMagia, x.meta.normLen, dw)
        )
        return this
    }

    private fun setMulImpl(xMeta: Meta, x: Magia, yMeta: Meta, y: Magia): MutableBigInt {
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

    fun setSqr(n: Int): MutableBigInt = setSqr(n.absoluteValue.toUInt())

    fun setSqr(w: UInt): MutableBigInt {
        val abs = w.toULong()
        return set(abs * abs)
    }

    fun setSqr(l: Long): MutableBigInt = setSqr(l.absoluteValue.toULong())

    fun setSqr(dw: ULong): MutableBigInt {
        val lo = dw * dw
        val hi = unsignedMulHi(dw, dw)
        return set(false, hi, lo)
    }

    fun setSqr(x: BigIntBase): MutableBigInt =
        setSqrImpl(x.meta, x.magia)

    private fun setSqrImpl(xMeta: Meta, x: Magia): MutableBigInt {
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

    fun setDiv(x: MutableBigInt, n: Int): MutableBigInt =
        setDivImpl(x, n < 0, n.absoluteValue.toUInt().toULong())
    fun setDiv(x: MutableBigInt, w: UInt): MutableBigInt =
        setDivImpl(x, false, w.toULong())
    fun setDiv(x: MutableBigInt, l: Long): MutableBigInt =
        setDivImpl(x, l < 0L, l.absoluteValue.toULong())
    fun setDiv(x: MutableBigInt, dw: ULong): MutableBigInt =
        setDivImpl(x, false, dw)
    fun setDiv(x: BigIntBase, y: BigIntBase): MutableBigInt {
        ensureCapacityDiscard(x.meta.normLen - y.meta.normLen + 1)
        if (trySetDivFastPath(x, y))
            return this
        ensureTmp1Capacity(x.meta.normLen + 1)
        ensureTmp2Capacity(y.meta.normLen)
        _meta = Meta(x.meta.signBit xor y.meta.signBit,
            Mago.setDiv(magia, x.magia, x.meta.normLen, tmp1, y.magia, y.meta.normLen, tmp2))
        return this
    }

    private fun setDivImpl(x: BigIntBase, ySign: Boolean, yDw: ULong): MutableBigInt {
        ensureCapacityDiscard(x.meta.normLen - 1 + 1) // yDw might represent a single limb
        if (trySetDivFastPath64(x, ySign, yDw))
            return this
        ensureTmp1Capacity(x.meta.normLen + 1)
        val normLen = Mago.setDiv64(magia, x.magia, x.meta.normLen, tmp1, yDw)
        _meta = Meta(x.meta.signFlag xor ySign, normLen)
        return this
    }


    private fun trySetDivFastPath(x: BigIntBase, y: BigIntBase): Boolean {
        val qSignFlag = x.meta.signFlag xor y.meta.signFlag
        val qNormLen = Mago.trySetDivFastPath(this.magia, x.magia, x.meta.normLen, y.magia, y.meta.normLen)
        if (qNormLen < 0)
            return false
        _meta = Meta(qSignFlag, qNormLen)
        return true
    }

    private fun trySetDivFastPath64(x: BigIntBase, ySign: Boolean, yDw: ULong): Boolean {
        val qSignFlag = x.meta.signFlag xor ySign
        val qNormLen = Mago.trySetDivFastPath64(this.magia, x.magia, x.meta.normLen, yDw)
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

    fun setRem(x: BigIntBase, n: Int): MutableBigInt =
        setRemImpl(x, n.absoluteValue.toUInt().toULong())
    fun setRem(x: BigIntBase, w: UInt): MutableBigInt =
        setRemImpl(x, w.toULong())
    fun setRem(x: BigIntBase, l: Long): MutableBigInt =
        setRemImpl(x, l.absoluteValue.toULong())
    fun setRem(x: BigIntBase, dw: ULong): MutableBigInt =
        setRemImpl(x, dw)
    fun setRem(x: BigIntBase, y: BigIntBase): MutableBigInt {
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


    private fun setRemImpl(x: BigIntBase, yDw: ULong): MutableBigInt {
        ensureTmp1Capacity(x.meta.normLen + 1)
        val rem = Mago.calcRem64(x.magia, x.meta.normLen, tmp1, yDw)
        return set(x.meta.signFlag, rem)
    }

    fun setMod(x: BigIntBase, n: Int): MutableBigInt =
        setModImpl(x, n < 0, n.absoluteValue.toUInt().toULong())
    fun setMod(x: BigIntBase, w: UInt): MutableBigInt =
        setModImpl(x, false, w.toULong())
    fun setMod(x: BigIntBase, l: Long): MutableBigInt =
        setModImpl(x, l < 0, l.absoluteValue.toULong())
    fun setMod(x: BigIntBase, dw: ULong): MutableBigInt =
        setModImpl(x, false, dw)
    fun setMod(x: BigIntBase, y: BigIntBase): MutableBigInt {
        if (y.meta.isNegative)
            throw ArithmeticException(ERR_MSG_MOD_NEG_DIVISOR)
        setRem(x, y)
        if (isNegative())
            setAdd(this, y)
        return this
    }

    private fun setModImpl(x: BigIntBase, ySign: Boolean, yDw: ULong): MutableBigInt {
        if (ySign)
            throw ArithmeticException(ERR_MSG_MOD_NEG_DIVISOR)
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
     * val mbi = MutableBigInt()
     * mbi += 42L
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
     * val mbi = MutableBigInt()
     * mbi -= 42L
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
     * - [MutableBigInt]
     *
     * Sign handling is automatically applied.
     *
     * When multiplying by another `MutableBigInt` that is the same instance
     * (`this === other`), a specialized squaring routine is used to prevent aliasing
     * issues and improve performance.
     *
     * Example usage:
     * ```
     * val mbi = MutableBigInt().setOne() // must start at 1 for multiplication
     * mbi *= 10
     * mbi *= anotherBigInt
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
     * val mbi = MutableBigInt().setOne() // must start at 1 for multiplication
     * mbi *= 10L
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
     * or [MutableBigInt] value.
     *
     * @param bi the value to multiply by.
     * @see timesAssign(Long)
     */
    operator fun timesAssign(bi: BigIntBase) { setMul(this, bi) }

    operator fun divAssign(n: Int) { setDiv(this, n) }

    operator fun divAssign(w: UInt) { setDiv(this, w) }

    operator fun divAssign(l: Long) { setDiv(this, l) }

    operator fun divAssign(dw: ULong) { setDiv(this, dw) }

    operator fun divAssign(bi: BigIntBase) { setDiv(this, bi) }

    operator fun remAssign(n: Int) { setRem(this, n) }

    operator fun remAssign(w: UInt) { setRem(this, w) }

    operator fun remAssign(l: Long) { setRem(this, l) }

    operator fun remAssign(dw: ULong) { setRem(this, dw) }

    operator fun remAssign(bi: BigIntBase) { setRem(this, bi) }

    /**
     * Adds the square of the given value to this accumulator in-place.
     *
     * The `addSquareOf` methods efficiently compute the square of the operand
     * and add it to this accumulator. Supported operand types include:
     * - [Int], [Long], [UInt], [ULong]
     * - [BigInt]
     * - [MutableBigInt]
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
     * val sumSqr = MutableBigInt()
     * for (v in data) {
     *     sumSqr.addSquareOf(v)
     * }
     * val totalSquares = sumSqr.toBigInt()
     * ```
     *
     * @param n the integer value to square and add.
     */
    fun addSquareOf(n: Int) = setAdd(this, n.toLong() * n.toLong())

    /**
     * Adds the square of the given UInt value to this accumulator.
     *
     * @param w the value to square and add.
     * @see addSquareOf(ULong)
     */
    fun addSquareOf(w: UInt) = setAdd(this, w.toULong() * w.toULong())

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
        setAddImpl(this, Meta(0, normLen), tmp1)
    }

    /**
     * Adds the square of the given BigInt value to this accumulator.
     *
     * @param bi the value to square and add.
     * @see addSquareOf(ULong)
     */
    fun addSquareOf(bi: BigIntBase) {
        ensureTmp1CapacityZeroed(bi.meta.normLen * 2)
        val normLenSqr = Mago.setSqr(tmp1, bi.magia, bi.meta.normLen)
        setAddImpl(this, Meta(0, normLenSqr), tmp1)
        validate()
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
     * arbitrary-precision values ([BigInt], [MutableBigInt]).
     *
     * This operation does not support unsigned types since they are always
     * non-negative ... use `+=`
     *
     * Example usage:
     * ```
     * val sumAbs = MutableBigInt()
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
     * [MutableBigInt] to this accumulator.
     *
     * @param hi the value to add.
     * @see addAbsValueOf(Long)
     */
    fun addAbsValueOf(bi: BigIntBase) =
        setAddImpl(this, bi.meta.abs(), bi.magia) // add if it is positive, subtract if it is negative

    /**
     * Mutates accumulator `this <<= bitCount`.
     * Sign remains the same.
     * Throws if [bitCount] is negative.
     */
    fun mutShl(bitCount: Int): MutableBigInt = setShl(this, bitCount)

    /**
     * Sets this accumulator to `x << bitCount`. Allocates space for the
     * resulting bit length. Throws if [bitCount] is negative.
     */
    fun setShl(x: MutableBigInt, bitCount: Int): MutableBigInt = when {
        bitCount < 0 -> throw IllegalArgumentException(ERR_MSG_NEG_BITCOUNT)
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
    fun mutUshr(bitCount: Int): MutableBigInt = setUshr(this, bitCount)

    /**
     * Sets this accumulator to `x >>> bitCount`.
     *
     * The sign of `x` is ignored and the resulting value is the
     * non-negative magnitude.
     *
     * Throws if [bitCount] is negative.
     */
    fun setUshr(x: BigIntBase, bitCount: Int): MutableBigInt {
        val zBitLen = x.magnitudeBitLen() - bitCount
        return when {
            bitCount < 0 -> throw IllegalArgumentException(ERR_MSG_NEG_BITCOUNT)
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
    fun mutShr(bitCount: Int): MutableBigInt = setShr(this, bitCount)

    /**
     * Sets this accumulator to `x >> bitCount`.
     *
     * Follows arithmetic shift right semantics if `x` is negative,
     * effectively treating the resulting value as if it were
     * stored in twos-complement.
     *
     * Throws if [bitCount] is negative.
     */
    fun setShr(x: BigIntBase, bitCount: Int): MutableBigInt {
        val zBitLen = x.magnitudeBitLen() - bitCount
        when {
            bitCount < 0 -> throw IllegalArgumentException(ERR_MSG_NEG_BITCOUNT)
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
    fun setBit(bitIndex: Int): MutableBigInt {
        if (bitIndex >= 0) {
            val wordIndex = bitIndex ushr 5
            val isolatedBit = (1 shl (bitIndex and 0x1F))
            if (wordIndex < meta.normLen) {
                magia[wordIndex] = magia[wordIndex] or isolatedBit
                return this
            }
            ensureCapacityCopyZeroExtend(wordIndex + 1)
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
    fun clearBit(bitIndex: Int): MutableBigInt {
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
    fun applyBitMask(bitWidth: Int, bitIndex: Int = 0): MutableBigInt {
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
        val dw = (magia[1].toULong() shl 32) or magia[0].toUInt().toULong()
        val nonZeroMask = ((-normLen).toLong() shr 63).toULong()
        val gt32Mask = ((1 - normLen) shr 31).toLong().toULong() or 0xFFFF_FFFFuL
        return dw and gt32Mask and nonZeroMask
    }

    /**
     * Value comparison for computational use.
     *
     * Equality is intentionally **asymmetric**:
     * - Compares by numeric value against [MutableBigInt], [BigInt], and
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
            is BigIntBase -> this EQ other
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
     * `MutableBigInt` is mutable and must never be used as a key in hash-based
     * collections (`HashMap`, `HashSet`, etc.). Calling `hashCode()` is therefore
     * unsupported and results in an exception.
     */
    override fun hashCode(): Int =
        throw UnsupportedOperationException(
            "mutable MutableBigInt is an invalid key in collections")

}
