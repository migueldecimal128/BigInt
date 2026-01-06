// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import kotlin.math.min
import kotlin.math.max


// magia == MAGnitude IntArray ... it's magic!
// Mago = MAGnitude Operations

typealias Magia = IntArray

private const val ERR_MSG_ADD_OVERFLOW = "add overflow ... destination too small"
private const val ERR_MSG_SUB_UNDERFLOW = "sub underflow ... minuend too small for subtrahend"
private const val ERR_MSG_MUL_OVERFLOW = "mul overflow ... destination too small"
private const val ERR_MSG_SHL_OVERFLOW = "shl overflow ... destination too small"
private const val ERR_MSG_DIV_BY_ZERO = "div by zero"
private const val ERR_MSG_INVALID_ALLOCATION_LENGTH = "invalid allocation length"
private const val ERR_MSG_NEGATIVE_INDEX = "negative index"

/**
 * Provides low-level support for arbitrary-precision **unsigned** integer arithmetic.
 *
 * Unsigned magnitudes are represented in **little-endian** form, using 32-bit limbs
 * stored in a raw [IntArray]. Although the limbs represent unsigned values, an
 * [IntArray] is used instead of Kotlin [UIntArray] because the latter is merely a wrapper
 * around [IntArray] and is inappropriate for high-performance internal arithmetic.
 * Kotlin unsigned primitives ([UInt], [ULong]) are used for temporary scalar values.
 *
 * `Magia` stands for Magnitude IntArray. [Magia] is a kotlin typealias for [IntArray].
 * `Mago` stands for Magnitude Operations ... el mago hace magia ...
 * the magician does magic.
 *
 * ### Design Overview
 * - The bit-length (`bitLen`) is restricted to the non-negative range of an `Int`
 *   (i.e., **< 2³¹**). Consequently, an [Magia] may contain up to `2^(31–5)` limbs
 *   (exclusive). For example, an [Magia] of size `2²⁶–1` would consume ~256 MiB and
 *   represent an integer with approximately **6.46×10⁸ decimal digits**. In practice,
 *   performance and memory constraints will be reached long before this theoretical
 *   upper bound.
 * - `new*` functions construct generally construct values to be used by [BigInt]
 *   and are generally **immutable** limb arrays.
 * - `set*` functions store the result in the user-supplied first parameter and
 *   return the normalized length. The caller must ensure that there is sufficient
 *   space in the destination for operator and operands. Operators are implemented
 *   to allow aliased operands and result ... `x *= x`. The `set*` functions are used
 *   exclusively by [MutableBigInt].
 *
 * ### Available Functionality
 * - Magia acts as a complete arbitrary-length integer **ALU** (Arithmetic Logic Unit).
 * - **Arithmetic:** `add`, `sub`, `mul`, `div`, `rem`, `sqr`
 * - **Bitwise:** `and`, `or`, `xor`, `shl`, `shr`
 * - **Bit-operations:** `bitLen`, `nlz`, `bitPopulation`, `testBit`, `setBit`,
 *   and utility bit-mask construction routines.
 * - **Parsing:** From `String`, `CharSequence`, `CharArray`, and ASCII/UTF-8 `ByteArray`.
 * - **Conversion:** To decimal `String` or ASCII/UTF-8 `ByteArray`.
 * - **Serialization:** To and from little- or big-endian unsigned / two’s-complement
 *   formats.
 *
 * ### Notes on Intended Use
 * These routines are intentionally **low-level** and assume familiarity with
 * bit-manipulation techniques. High performance is achieved through
 * branch-elimination and instruction-level parallelism, which can make certain
 * routines appear intricate or non-obvious to readers unfamiliar with this style
 * of implementation.
 *
 * Magia forms the computational core used by higher-level abstractions such as
 * [BigInt] and [MutableBigInt].
 *
 * Magus ... ancient Latin word for a Persian magician who works with Magia
  */
internal object Mago {

    private inline fun bitLen(n: Int) = 32 - n.countLeadingZeroBits()

    private const val MASK32 = 0xFFFF_FFFFuL
    private const val MASK32L = 0xFFFF_FFFFL

    /**
     * The one true zero-length array that is usually used to represent
     * the value ZERO.
     */
    val ZERO = Magia(0)

    /**
     * We occasionally need the value ONE.
     *
     * **WARNING** do NOT mutate this.
     */
    internal val ONE = intArrayOf(1)

    /**
     * Returns the 32-bit limb `n` zero-extended to a 64-bit `ULong`.
     */
    private inline fun dw32(n: Int) = n.toUInt().toULong()

    /**
     * Largest allowed limb-array length.  (2²⁶−1 elements)
     * Chosen so that bitLength = limbLen * 32 always remains < Int.MAX_VALUE.
     */
    private const val MAX_ALLOC_SIZE = (1 shl 26) - 1

    /**
     * Converts the first limb of [x] into a single [UInt] value.
     *
     * - If [x] is empty, returns 0.
     * - If [x] has one or more limbs, returns x[0] as a UInt.
     *
     * Any limbs beyond the first are ignored.
     *
     * @param x the array of 32-bit limbs representing the magnitude (not necessarily normalized).
     * @return the unsigned 32-bit value represented by the first one or two limbs of [x].
     */
    fun toRawUInt(x: Magia): UInt = if (x.size == 0) 0u else x[0].toUInt()

    /**
     * Returns the low 64 bits of a limb array as an unsigned value.
     *
     * Interprets up to the first two 32-bit limbs of [x], using [xNormLen] to
     * determine how many limbs are significant:
     *
     * - `xNormLen == 0` → returns `0uL`
     * - `xNormLen == 1` → returns `x[0]` as an unsigned 64-bit value
     * - `xNormLen >= 2` → returns `(x[1] << 32) | x[0]`
     *
     * Limbs beyond the first two are ignored. The result is **not** a full numeric
     * conversion if the magnitude exceeds 64 bits; it simply exposes the low
     * 64 bits of the magnitude.
     *
     * @param x the limb array (least-significant limb first)
     * @param xNormLen normalized number of significant limbs in [x]
     * @return the low 64 bits of the magnitude as a [ULong]
     */
    fun toRawULong(x: Magia, xNormLen: Int): ULong {
        return when (xNormLen) {
            0 -> 0uL
            1 -> dw32(x[0])
            else -> (dw32(x[1]) shl 32) or dw32(x[0])
        }
    }

    /**
     * Returns a new limb array representing the given [ULong] value.
     *
     * Zero returns [ZERO].
     */
    fun newFromULong(dw: ULong): Magia = when {
        (dw shr 32) != 0uL -> intArrayOf(dw.toInt(), (dw shr 32).toInt())
        dw != 0uL -> intArrayOf(dw.toInt())
        else -> ZERO
    }

    fun newFromUInt(w: UInt): Magia =
        if (w != 0u) intArrayOf(w.toInt()) else ZERO

    /**
     * Stores the 64-bit unsigned value `dw` into `z` as 32-bit limbs (little-endian)
     * and returns the resulting limb length (0–2).
     */
    fun setULong(z: Magia, dw: ULong): Int {
        val lo = dw.toInt()
        val hi = (dw shr 32).toInt()
        if (dw == 0uL)
            return 0
        z[0] = lo
        if (hi == 0)
            return 1
        z[1] = hi
        return 2
    }

    fun setUInt(z: Magia, w: UInt): Int {
        val n = w.toInt()
        z[0] = n
        return (n or -n) ushr 31
    }

    /**
     * Returns the normalized limb length of **x**—the index of the highest
     * non-zero limb plus one. If all limbs are zero, returns `0`.
     *
     * **WARNING** This call can be dangerous. It should *only* be used
     * on magia returned by `new` functions because it will pick up
     * garbage upper limbs in values and temps allocated by [MutableBigInt]
     */
    inline fun normLen(x: Magia): Int {
        for (i in x.size - 1 downTo 0)
            if (x[i] != 0)
                return i + 1
        return 0
    }

    /**
     * Returns the number of nonzero limbs in the first [xLen] elements of [x],
     * excluding any leading zeros.
     *
     * @throws IllegalArgumentException if [xLen] is out of range for [x].
     */
    fun normLen(x: Magia, xLen: Int): Int {
        if (xLen >= 0 && xLen <= x.size) {
            for (i in xLen - 1 downTo 0)
                if (x[i] != 0)
                    return i + 1
            return 0
        }
        throw IllegalArgumentException()
    }

    fun normLen(x: Magia, xOff: Int, xLen: Int): Int {
        if (xOff >= 0 && xLen >= 0 && xOff + xLen <= x.size) {
            for (i in xLen - 1 downTo 0)
                if (x[xOff + i] != 0)
                    return i + 1
            return 0
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns `true` if the prefix of the limb array is in normalized form.
     *
     * A limb sequence is considered *normalized* when:
     *
     *  - `xLen == 0`, representing the canonical zero, or
     *  - the most significant limb `x[xLen - 1]` is non-zero.
     *
     * In other words, the first [xLen] limbs contain no unused leading zero limbs
     * at the most significant end. Limbs at indices ≥ `xLen` are ignored.
     *
     * This check does not modify the array.
     *
     * @param x the limb array (least-significant limb first)
     * @param xLen number of significant limbs to test
     * @return `true` if the limb sequence is normalized; `false` otherwise
     */
    fun isNormalized(x: Magia, xLen: Int): Boolean {
        verify { xLen >= 0 && xLen <= x.size }
        return xLen == 0 || x[xLen - 1] != 0
    }

    fun isNormalized(x: Magia, xOff: Int, xLen: Int): Boolean {
        verify { xOff >= 0 && xLen >= 0 && xOff + xLen <= x.size }
        return xLen == 0 || x[xOff + xLen - 1] != 0
    }

    /**
     * Allocates a new limb array large enough to represent a value whose
     * magnitude requires **[bitLen]** bits.
     *
     * • If `bitLen <= 0`, returns the canonical zero-array **[ZERO]**.
     * • If `1 ≤ bitLen ≤ MAX_ALLOC_SIZE * 32`, allocates an array sized by
     *   `limbLenFromBitLen(bitLen)`.
     * • Otherwise throws `IllegalArgumentException`.
     *
     * @param bitLen required bit length of the value.
     * @return an `Magia` sized to hold a value of the given bit length,
     *         or **ZERO** when `bitLen <= 0`.
     */
    fun newWithBitLen(bitLen: Int): Magia {
        return when {
            bitLen in 1..(MAX_ALLOC_SIZE*32) ->
                Magia(limbLenFromBitLen(bitLen))
            bitLen == 0 -> ZERO
            else ->
                throw IllegalArgumentException(ERR_MSG_INVALID_ALLOCATION_LENGTH)
        }
    }

    /**
     * Creates a new limb array with at least **floorLen** elements.
     *
     * Used by mutating accumulators that expect values to grow. The
     * allocated size is rounded up to the next multiple of 4 to reduce
     * external fragmentation and ensure all allocated storage is usable.
     *
     * The maximum allowed size is **MAX_ALLOC_SIZE = 2²⁶ − 1**, chosen so
     * any array allocated here can represent a BigInt whose `bitLen`
     * will remain `< Int.MAX_VALUE`.
     *
     * @param floorLen minimum required limb count (0 ≤ floorLen ≤ MAX_ALLOC_SIZE)
     * @return an `Magia` of size ≥ floorLen, rounded up to a multiple of 4
     */
    fun newWithFloorLen(floorLen: Int): Magia =
        Magia(calcHeapLimbQuantum(floorLen))

    inline fun calcHeapLimbQuantum(floorLen: Int): Int {
        if (floorLen in 0..MAX_ALLOC_SIZE) {
            // if floorLen == 0 then add 1
            val t = floorLen + 1 - (-floorLen ushr 31)
            val allocSize = (t + 3) and 3.inv()
            if (allocSize <= MAX_ALLOC_SIZE)
                return allocSize
        }
        throw IllegalArgumentException(ERR_MSG_INVALID_ALLOCATION_LENGTH)
    }

     /**
      * Returns a normalized copy of the first [len] limbs of [x],
      * with any leading zero limbs removed.
      *
      * Note that len is not necessarily normalized.
      *
      * If all limbs are zero, returns [ZERO].
      *
      * @throws IllegalArgumentException if [len] is out of range for [x].
      */
    fun newNormalizedCopy(x: Magia, len: Int): Magia {
        val normLen = normLen(x, len)
        if (normLen > 0) {
            val z = Magia(normLen)
            x.copyInto(z, 0, 0, normLen)
            return z
        }
        return ZERO
    }

    /**
     * Returns a copy of [x] whose length is the minimum number of limbs required
     * to hold [exactBitLen] bits. Leading limbs are preserved or truncated as needed.
     */
    fun newCopyWithExactBitLen(x: Magia, xLen: Int, exactBitLen: Int): Magia =
        newCopyWithExactLimbLen(x, xLen, (exactBitLen + 0x1F) ushr 5)

    /**
     * Returns a copy of the first [xLen] limbs of [x] resized to exactly `exactLimbLen` limbs.
     * If the new length is larger, high-order limbs are zero-filled; if smaller,
     * high-order limbs are truncated.
     */
    fun newCopyWithExactLimbLen(x: Magia, xLen: Int, exactLimbLen: Int): Magia {
        if (exactLimbLen in 1..MAX_ALLOC_SIZE) {
            val dst = Magia(exactLimbLen)
            x.copyInto(dst, 0, 0, min(xLen, dst.size))
            return dst
        }
        if (exactLimbLen == 0)
            return ZERO
        throw IllegalArgumentException(ERR_MSG_INVALID_ALLOCATION_LENGTH)
    }

    /**
     * Returns a new limb array representing [x] plus the unsigned 32-bit value [w].
     */

    inline fun newAdd32(x: Magia, xNormLen: Int, w: UInt): Magia {
        if (xNormLen > 0 && xNormLen <= x.size) {
            verify { isNormalized(x, xNormLen) }
            val wBitLen = 32 - w.countLeadingZeroBits()
            val newBitLen = max(normBitLen(x, xNormLen), wBitLen) + 1
            val z = newWithBitLen(newBitLen)
            //val z = IntArray(xNormLen + 1)
            var carry = w.toULong()
            var i = 0
            while (i < xNormLen) {
                carry += dw32(x[i])
                z[i] = carry.toInt()
                carry = carry shr 32
                ++i
            }
            if (i < z.size)
                z[i] = carry.toInt()
            return z
        }
        if (xNormLen == 0) {
            if (w != 0u)
                return intArrayOf(w.toInt())
            return ZERO
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing [x] plus the unsigned 64-bit value [dw].
     */
    inline fun newAdd64(x: Magia, xNormLen: Int, dw: ULong): Magia {
        if (xNormLen <= x.size) {
            verify { isNormalized(x, xNormLen) }
            val dwBitLen = 64 - dw.countLeadingZeroBits()
            val newBitLen = max(normBitLen(x, xNormLen), dwBitLen) + 1
            val z = newWithBitLen(newBitLen)
            var carry = dw
            var i = 0
            while (i < xNormLen) {
                val t = dw32(x[i]) + (carry and MASK32)
                z[i] = t.toInt()
                carry = (t shr 32) + (carry shr 32)
                ++i
            }
            if (i < z.size) {
                z[i] = carry.toInt()
                ++i
                if (i < z.size)
                    z[i] = (carry shr 32).toInt()
            }
            return z
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing the sum of [x] and [y].
     *
     * The result will sometimes be not normalized.
     */
    fun newAdd(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
        verify { isNormalized(x, xNormLen) }
        verify { isNormalized(y, yNormLen) }
        val newLen = max(xNormLen, yNormLen) + 1
        val z = Magia(newLen)
        //val newBitLen = max(normBitLen(x, xNormLen), normBitLen(y, yNormLen)) + 1
        //val z = newWithBitLen(newBitLen)
        setAdd(z, x, xNormLen, y, yNormLen)
        return z
    }

    /**
     * Increments normalized limb array [x] in place and
     * returns the resulting normalized [Magia].
     *
     * If the addition overflows all limbs a new array with one
     * extra limb is returned.
     *
     * @return the normalized input [x] or a new normalized [Magia]
     */
    fun newOrMutateIncrement(x: Magia): Magia {
        verify { isNormalized(x, x.size) }
        var carry = 1uL
        var i = 0
        while (carry != 0uL && i < x.size) {
            val t = dw32(x[i]) + carry
            x[i] = t.toInt()
            carry = t shr 32
            ++i
        }
        if (carry == 0uL)
            return x
        // the only way we can have a carry is if all limbs
        // have mutated to zero
        verify { normLen(x) == 0 }
        val z = Magia(x.size + 1)
        z[x.size] = 1
        return z
    }

    /**
     * Adds the unsigned 64-bit value `dw` to `x[0‥xNormLen)` and writes the result
     * into `z` (which may be the same array for in-place mutation).
     *
     * Returns the resulting normalized limb length.
     * Throws `ArithmeticException` if the sum overflows `z`.
     */
    fun setAdd32(z: Magia, x: Magia, xNormLen: Int, w: UInt): Int {
        if (xNormLen >= 0 && xNormLen <= x.size && isNormalized(x, xNormLen)) {
            var carry = w.toULong()
            var i = 0
            while (carry != 0uL && i < xNormLen) {
                carry += dw32(x[i])
                z[i] = carry.toInt()
                carry = carry shr 32
                ++i
            }
            if (carry == 0uL) {
                if (i < xNormLen && z !== x)
                    x.copyInto(z, i, i, xNormLen)
                return xNormLen
            }
            if (i < z.size) {
                z[i] = carry.toInt()
                ++i
                return i
            }
            throw ArithmeticException(ERR_MSG_ADD_OVERFLOW)
        }
        throw IllegalArgumentException()
    }

    /**
     * Adds the unsigned 64-bit value `dw` to `x[0‥xNormLen)` and writes the result
     * into `z` (which may be the same array for in-place mutation).
     *
     * Returns the resulting normalized limb length.
     * Throws `ArithmeticException` if the sum overflows `z`.
     */
    fun setAdd64(z: Magia, x: Magia, xNormLen: Int, dw: ULong): Int {
        if (xNormLen >= 0 && xNormLen <= x.size && isNormalized(x, xNormLen)) {
            var carry = dw
            var i = 0
            while (carry != 0uL && i < xNormLen) {
                val s = dw32(x[i]) + (carry and MASK32)
                z[i] = s.toInt()
                carry = (carry shr 32) + (s shr 32)
                ++i
            }
            if (carry == 0uL) {
                if (i < xNormLen && z !== x)
                    x.copyInto(z, i, i, xNormLen)
                return xNormLen
            }
            while (i < z.size) {
                z[i] = carry.toInt()
                ++i
                carry = carry shr 32
                if (carry == 0uL)
                    return i
            }
            throw ArithmeticException(ERR_MSG_ADD_OVERFLOW)
        }
        throw IllegalArgumentException()
    }

    /**
     * Computes `z = x + y` using the low-order [xLen] and [yLen] limbs of the
     * inputs and returns the normalized limb length of the sum. The caller must
     * ensure that [z] is large enough to hold the result; the minimum size is
     * `max(normBitLen(x), normBitLen(y)) + 1` bits.
     *
     * @return the normalized limb count of the sum
     * @throws ArithmeticException if the result does not fit in [z]
     */
    fun setAdd(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        verify { isNormalized(x, xNormLen) }
        verify { isNormalized(y, yNormLen) }
        if (xNormLen >= 0 && xNormLen <= x.size && yNormLen >= 0 && yNormLen <= y.size) {
            val maxNormLen = max(xNormLen, yNormLen)
            val minNormLen = min(xNormLen, yNormLen)
            if (z.size >= maxNormLen) {
                var carry = 0uL
                var i = 0
                while (i < minNormLen) {
                    val t = dw32(x[i]) + dw32(y[i]) + carry
                    z[i] = t.toInt()
                    carry = t shr 32
                    verify { (carry shr 1) == 0uL }
                    ++i
                }
                val longer = if (xNormLen > yNormLen) x else y
                while (i < maxNormLen && i < z.size) {
                    val t = dw32(longer[i]) + carry
                    z[i] = t.toInt()
                    carry = t shr 32
                    ++i
                }
                if (carry != 0uL) {
                    if (i == z.size)
                        throw ArithmeticException(ERR_MSG_ADD_OVERFLOW)
                    z[i] = 1
                    ++i
                }
                verify { isNormalized(z, i) }
                return i
            }
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing [x] minus the unsigned 64-bit value [dw].
     * The caller must ensure that x >= y.
     *
     * If the result is zero or the subtraction underflows, returns [ZERO].
     */
    fun newSub64(x: Magia, xNormLen: Int, dw: ULong): Magia {
        verify { isNormalized(x, xNormLen) }
        if (xNormLen >= 0 && xNormLen <= x.size) {
            val z = Magia(xNormLen)
            var orAccumulator = 0
            var borrow = 0uL
            if (z.isNotEmpty()) {
                val t0 = dw32(x[0]) - (dw and MASK32)
                val z0 = t0.toInt()
                z[0] = z0
                orAccumulator = z0
                if (z.size > 1) {
                    borrow = t0 shr 63
                    val t1 = dw32(x[1]) - (dw shr 32) - borrow
                    val z1 = t1.toInt()
                    z[1] = z1
                    orAccumulator = orAccumulator or z1
                    borrow = t1 shr 63
                    var i = 2
                    while (i < z.size) {
                        val t = dw32(x[i]) - borrow
                        val zi = t.toInt()
                        z[i] = zi
                        orAccumulator = orAccumulator or zi
                        borrow = t shr 63
                        ++i
                    }
                }
            }
            return if (orAccumulator == 0 || borrow != 0uL) ZERO else z
        }
        throw IllegalArgumentException()
    }

    fun newSub32(x: Magia, xNormLen: Int, w: UInt): Magia = newSub64(x, xNormLen, w.toULong())

    /**
     * Subtracts a 64-bit unsigned integer [dw] from a multi-limb big integer [x] (first [xLen] limbs),
     * storing the result in [z].
     *
     * Returns the normalized length of [z] (excluding trailing zero limbs).
     *
     * @param z the destination array to receive the result; must have size >= normalized length of [x]
     * @param x the source big integer limbs array
     * @param xLen number of limbs from [x] to consider
     * @param dw the 64-bit unsigned integer to subtract from [x]
     * @return normalized length of the subtraction result in [z]
     * @throws IllegalArgumentException if input lengths are invalid or arrays too small
     * @throws ArithmeticException if the subtraction underflows (i.e., dw > x)
     */
    fun setSub64(z: Magia, x: Magia, xLen: Int, dw: ULong): Int {
        if (xLen >= 0 && xLen <= x.size) {
            val xNormLen = normLen(x, xLen)
            if (z.size >= xNormLen) {
                if (xNormLen <= 2 && toRawULong(x, xNormLen) < dw)
                    throw ArithmeticException(ERR_MSG_SUB_UNDERFLOW)
                var lastNonZeroIndex = -1
                var borrow = dw
                var i = 0
                while (i < xNormLen) {
                    val t = dw32(x[i]) - (borrow and MASK32)
                    val zi = t.toInt()
                    z[i] = zi
                    val carryOut = t shr 63         // 1 if borrow-in consumed more than limb
                    borrow = (borrow shr 32) + carryOut
                    // branchless update of last non-zero
                    val nonZeroMask = (zi or -zi) shr 31
                    lastNonZeroIndex =
                        (lastNonZeroIndex and nonZeroMask.inv()) or (i and nonZeroMask)
                    ++i
                }
                verify { borrow == 0uL }
                val zNormLen = lastNonZeroIndex + 1
                verify { isNormalized(z, zNormLen) }
                return zNormLen
            }
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing [x] minus [y].
     *
     * Requires that [x] is greater than or equal to [y].
     * If the result is zero, returns [ZERO].
     */
    fun newSub(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
        verify { isNormalized(x, xNormLen) }
        verify { isNormalized(y, yNormLen) }
        val z = Magia(xNormLen)
        val zNormLen = setSub(z, x, xNormLen, y, yNormLen)
        verify { isNormalized(z, zNormLen) }
        return if (zNormLen == 0) ZERO else z
    }

    /**
     * Computes `z = x - y` using the low-order [xLen] and [yLen] limbs and returns
     * the normalized limb length of the result.
     *
     * This operation:
     *  • reads each limb before writing it,
     *  • allows `z` to alias `x` or `y` safely,
     *  • requires `x ≥ y`,
     *  • and writes the result into [z] without allocation.
     *
     * [z] must have capacity for the normalized length of `x`.
     *
     * @return normalized limb count of the result
     * @throws ArithmeticException if `x < y`
     */
    fun setSub(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        if (xNormLen >= 0 && xNormLen <= x.size && yNormLen >= 0 && yNormLen <= y.size) {
            verify { isNormalized(x, xNormLen) }
            verify { isNormalized(y, yNormLen) }
            if (z.size >= xNormLen) {
                if (xNormLen >= yNormLen) {
                    var borrow = 0uL
                    var lastNonZeroIndex = -1
                    var i = 0
                    while (i < yNormLen) {
                        val t = dw32(x[i]) - dw32(y[i]) - borrow
                        val zi = t.toInt()
                        z[i] = zi
                        val nonZeroMask = (zi or -zi) shr 31
                        lastNonZeroIndex = (lastNonZeroIndex and nonZeroMask.inv()) or (i and nonZeroMask)
                        borrow = t shr 63
                        ++i
                    }
                    while (i < xNormLen) {
                        val t = dw32(x[i]) - borrow
                        val zi = t.toInt()
                        z[i] = zi
                        val nonZeroMask = (zi or -zi) shr 31
                        lastNonZeroIndex = (lastNonZeroIndex and nonZeroMask.inv()) or (i and nonZeroMask)
                        borrow = t shr 63
                        ++i
                    }
                    if (borrow == 0uL) {
                        val zNormLen = lastNonZeroIndex + 1
                        verify { isNormalized(z, zNormLen) }
                        return zNormLen
                    }
                }
                throw ArithmeticException(ERR_MSG_SUB_UNDERFLOW)
            }
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing [x] multiplied by the unsigned 32-bit value [w].
     *
     * Only the normalized limbs `[0, xNormLen)` of [x] are used. Returns [ZERO] if [x] is zero
     * or if [w] is zero.
     *
     * Limb storage is preallocated using a bit-length estimate based on [x] and [w]; the
     * returned array may therefore contain unused high limbs even though the computed
     * product itself is normalized.
     *
     * @param x magnitude limb array for the multiplicand.
     * @param xNormLen normalized limb length of [x].
     * @param w unsigned 32-bit multiplier.
     *
     * @return [ZERO] or a (possibly non-normalized) [Magia] containing the product.
     */
    fun newMul(x: Magia, xNormLen: Int, w: UInt): Magia {
        verify { isNormalized(x, xNormLen) }
        if (xNormLen == 0 || w == 0u)
            return ZERO
        val xBitLen = normBitLen(x, xNormLen)
        val zBitLen = xBitLen + 32 - w.countLeadingZeroBits()
        val z = newWithBitLen(zBitLen)
        val zNormLen = setMul32(z, x, xNormLen, w)
        verify { isNormalized(z, zNormLen) }
        return z
    }

    /**
     * Multiplies a normalized multi-limb integer [x] (first [xNormLen] limbs) by a single 32-bit word [w],
     * storing the result in [z]. The operation is safe in-place, so [z] may be the same array as [x].
     *
     * @return number of significant limbs written to [z]
     * @throws ArithmeticException if [z] is too small to hold the full product (including carry)
     * @throws IllegalArgumentException if [xNormLen] <= 0 or [xNormLen] >= x.size
     */
    fun setMul32(z: Magia, x: Magia, xNormLen: Int, w: UInt): Int {
        if (xNormLen >= 0 && xNormLen <= x.size) {
            verify { isNormalized(x, xNormLen) }
            return when {
                xNormLen > 0 && w.countOneBits() > 1 -> {
                    val w64 = w.toULong()
                    var carry = 0uL
                    var i = 0
                    while (i < xNormLen) {
                        val t = dw32(x[i]) * w64 + carry
                        z[i] = t.toInt()
                        carry = t shr 32
                        ++i
                    }
                    if (carry != 0uL) {
                        if (i == z.size)
                            throw ArithmeticException(ERR_MSG_MUL_OVERFLOW)
                        z[i] = carry.toInt()
                        ++i
                    }
                    verify { isNormalized(z, i) }
                    i
                }

                xNormLen == 0 || w == 0u -> 0
                else -> setShiftLeft(z, x, xNormLen, w.countTrailingZeroBits())
            }
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing [x] multiplied by the unsigned 64-bit value [dw].
     *
     * Only the normalized limbs `[0, xNormLen)` of [x] are used. Returns [ZERO] if [x] is zero
     * or if [dw] is zero.
     *
     * Limb storage is preallocated using a bit-length estimate based on [x] and [dw]; the
     * returned array may therefore contain unused high limbs even though the computed
     * product itself is normalized.
     *
     * @param x magnitude limb array for the multiplicand.
     * @param xNormLen normalized limb length of [x].
     * @param dw unsigned 64-bit multiplier.
     *
     * @return [ZERO] or a (possibly non-normalized) [Magia] containing the product.
     */
    fun newMul(x: Magia, xNormLen: Int, dw: ULong): Magia {
        if (xNormLen == 0 || dw == 0uL)
            return ZERO
        val xBitLen = normBitLen(x, xNormLen)
        val zBitLen = xBitLen + 64 - dw.countLeadingZeroBits()
        val z = newWithBitLen(zBitLen)
        val zNormLen = setMul64(z, x, xNormLen, dw)
        verify { isNormalized(z, zNormLen) }
        return z
    }

    /**
     * Multiplies the first [xNormLen] limbs of [x] by the unsigned 64-bit value [dw], storing the result in [z].
     *
     * - Performs a single-pass multiplication.
     * - Does not overwrite [x], allowing in-place multiplication scenarios.
     * - [zLen] must be greater than [xNormLen]; caller must ensure it is large enough to hold the full product.
     *
     * The caller is responsible for ensuring that [zLen] is sufficient, either by checking limb lengths
     * (typically requiring +2 limbs) or by checking bit lengths (0 to 2 extra limbs).
     *
     * @throws IllegalArgumentException if [xNormLen], [zLen], or array sizes are invalid.
     */
    fun setMul64(z: Magia, x: Magia, xNormLen: Int, dw: ULong): Int {
        if ((dw shr 32) == 0uL)
            return setMul32(z, x, xNormLen, dw.toUInt())
        if (xNormLen >= 0 && xNormLen <= x.size) {
            verify { isNormalized(x, xNormLen) }
            if (xNormLen > 0 && dw.countOneBits() > 1) {
                val lo = dw and MASK32
                val hi = dw shr 32

                var ppPrevHi = 0uL

                var i = 0
                while (i < xNormLen) {
                    val xi = dw32(x[i])

                    val pp = xi * lo + (ppPrevHi and MASK32)
                    z[i] = pp.toInt()

                    ppPrevHi = xi * hi + (ppPrevHi shr 32) + (pp shr 32)
                    ++i
                }
                if (ppPrevHi != 0uL && i < z.size) {
                    z[i] = ppPrevHi.toInt()
                    ppPrevHi = ppPrevHi shr 32
                    ++i
                }
                if (ppPrevHi != 0uL && i < z.size) {
                    z[i] = ppPrevHi.toInt()
                    ppPrevHi = ppPrevHi shr 32
                    ++i
                }
                if (ppPrevHi == 0uL) {
                    verify { isNormalized(z, i) }
                    return i
                }
                throw ArithmeticException(ERR_MSG_MUL_OVERFLOW)
            }
            if (xNormLen == 0)
                return 0
            verify { dw.countOneBits() == 1 }
            return setShiftLeft(z, x, xNormLen, dw.countTrailingZeroBits())
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing the product of [x] and [y].
     *
     * Only the normalized limbs `[0, xNormLen)` of [x] and `[0, yNormLen)` of [y] are used.
     * Returns [ZERO] if either operand is zero.
     *
     * Limb storage is preallocated using bit-length estimates; the returned array may
     * contain unused high limbs even though the product itself is normalized.
     *
     * @param x magnitude limb array for the first operand.
     * @param xNormLen normalized limb length of [x].
     * @param y magnitude limb array for the second operand.
     * @param yNormLen normalized limb length of [y].
     *
     * @return [ZERO] or a (possibly non-normalized) limb array containing the product.
     */
    fun newMul(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
        if (xNormLen == 0 || yNormLen == 0)
            return ZERO
        val xBitLen = normBitLen(x, xNormLen)
        val yBitLen = normBitLen(y, yNormLen)
        val z = newWithBitLen(xBitLen + yBitLen)
        val zNormLen = setMul(z, x, limbLenFromBitLen(xBitLen), y, limbLenFromBitLen(yBitLen))
        verify { isNormalized(z, zNormLen) }
        return z
    }

    /**
     * Multiplies the first [xNormLen] limbs of [x] by the first [yNormLen] limbs of [y],
     * accumulating the result into [z].
     *
     * Requirements:
     * - [z] must be of sufficient size to hold the product
     *   `normBitLen(x) + normBitLen(y)`, at least [xNormLen] + [yNormLen] - 1.
     * - [xNormLen] and [yNormLen] must be greater than zero and within the array bounds.
     * - For efficiency, if one array is longer, it is preferable to use it as [y].
     *
     * @return the normalized number of limbs actually used in [z].
     * @throws IllegalArgumentException if preconditions on array sizes or lengths are violated.
     */

    fun setMul(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        val zNormLen = setMulSchoolbook(z, x, xNormLen, y, yNormLen)
        return zNormLen
    }

    fun setMulSchoolbook(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        verify { z !== x && z !== y }
        if (xNormLen >= 0 && xNormLen <= x.size &&
            yNormLen >= 0 && yNormLen <= y.size &&
            z.size >= xNormLen + yNormLen - 1
        ) {

            if (xNormLen == 0 || yNormLen == 0)
                return 0

            val corto = if (xNormLen < yNormLen) x else y
            val largo = if (xNormLen < yNormLen) y else x
            val cortoLen = if (xNormLen < yNormLen) xNormLen else yNormLen
            val largoLen = if (xNormLen < yNormLen) yNormLen else xNormLen

            // zero out the minimum amount needed
            // higher limbs will be written before read
            z.fill(0, 0, largoLen)
            for (i in 0..<cortoLen) {
                val cortoLimb = dw32(corto[i])
                var carry = 0uL
                for (j in 0..<largoLen) {
                    val largoLimb = dw32(largo[j])
                    carry = cortoLimb * largoLimb + dw32(z[i + j]) + carry
                    z[i + j] = carry.toInt()
                    carry = carry shr 32
                }
                if (i + largoLen < z.size)
                    z[i + largoLen] = carry.toInt()
                else if (carry != 0uL)
                    throw ArithmeticException(ERR_MSG_MUL_OVERFLOW)
            }
            var lastIndex = cortoLen + largoLen - 1
            // >= instead of == to help bounds check elimination
            if (lastIndex >= z.size || z[lastIndex] == 0)
                --lastIndex
            val zNormLen = lastIndex + 1
            verify { isNormalized(z, zNormLen) }
            return zNormLen
        }
        throw IllegalArgumentException()
    }

    /**
     * Powers of 10 from 10⁰ through 10⁹.
     *
     * Used for fast small-power decimal scaling.
     */
    private val POW10 = IntArray(10)
    init {
        POW10[0] = 1
        for (i in 1 until POW10.size)
            POW10[i] = POW10[i - 1] * 10
    }


    /**
     * Performs an in-place fused multiply-add on the limb array [x]:
     *
     *     x = x * 10^pow10 + a
     *
     * where the multiplication is carried out using a precomputed 64-bit
     * multiplier for powers of ten in the fixed range **0‥9**.
     *
     * This is used internally during text parsing of decimal inputs to
     * accumulate digits efficiently in base-2³² limbs.
     *
     * Requirements:
     *  • `pow10` must be in **0..9**
     *  • `a` is an unsigned 32-bit addend (lower 32 bits of the next digit chunk)
     *
     * If `pow10` lies outside 0..9, an `IllegalArgumentException` is thrown.
     *
     * @param x the limb array to mutate (little-endian base-2³²).
     * @param pow10 the decimal power (0..9) selecting the precomputed multiplier.
     * @param a the unsigned 32-bit addend fused into the result.
     */
    fun mutateFmaPow10(x: Magia, xLen: Int, pow10: Int, a: UInt) {
        if (pow10 in 0..9) {
            val m64 = POW10[pow10].toULong()
            var carry = a.toULong()
            for (i in 0..<xLen) {
                val t = dw32(x[i]) * m64 + carry
                x[i] = t.toInt()
                carry = t shr 32
            }
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Returns a new limb array containing the square of a normalized magnitude.
     *
     * The result represents `x²`, computed from the first [xNormLen] limbs of [x].
     * If `xNormLen == 0` (canonical zero), this function returns [ZERO].
     *
     * The returned array is freshly allocated, large enough to hold the full
     * square estimated from bit length.
     *
     * Preconditions:
     * - [x] must be normalized for [xNormLen]
     *
     * @param x the source limb array (least-significant limb first)
     * @param xNormLen number of significant limbs in [x]
     * @return a non-normalized limb array representing `x²`
     */
    fun newSqr(x: Magia, xNormLen: Int): Magia {
        verify { isNormalized(x, xNormLen) }
        if (xNormLen < MagoSqr.KARATSUBA_SQR_THRESHOLD) {
            val z = IntArray(2 * xNormLen)
            val zNormLen = MagoSqr.setSqr(z, x, xNormLen)
            return z
        }
        val z = IntArray(2 * xNormLen + 1)
        val t = IntArray(3 * ((xNormLen + 1) / 2) + 3)
        val zNormLen = MagoSqr.karatsubaSqr(z, 0, x, 0, xNormLen, t)
        return z
    }

    /**
     * Returns a new [Magia] representing [x] logically shifted right by [bitCount] bits.
     *
     * Bits shifted out of the least significant end are discarded, and new high bits are filled with zeros.
     * The original [x] is not modified.
     *
     * @param x the source magnitude in little-endian limb order.
     * @param bitCount the number of bits to shift right; must be non-negative.
     * @return a new normalized [Magia] containing the shifted result or [ZERO]
     * @throws IllegalArgumentException if [bitCount] is negative.
     */
    fun newShiftRight(x: Magia, xNormLen: Int, bitCount: Int): Magia {
        if (bitCount >= 0) {
            verify { isNormalized(x, xNormLen) }
            require(bitCount >= 0)
            val newBitLen = normBitLen(x, xNormLen) - bitCount
            if (newBitLen <= 0)
                return ZERO
            val z = newWithBitLen(newBitLen)
            val zNormLen = setShiftRight(z, x, xNormLen, bitCount)
            check(zNormLen == z.size)
            verify { isNormalized(z, zNormLen) }
            return z
        }
        throw IllegalArgumentException()
    }

    /**
     * Shifts the first [xLen] limbs of [x] right by [bitCount] bits, in place.
     *
     * Bits shifted out of the low end are discarded, and high bits are filled with zeros.
     * Returns [x] for convenience.
     *
     * @throws IllegalArgumentException if [xLen] or [bitCount] is out of range.
     * @return normLen
     */
    fun mutateShiftRight(x: Magia, xLen: Int, bitCount: Int): Magia {
        require (bitCount >= 0 && xLen >= 0 && xLen <= x.size)
        if (xLen > 0 && bitCount > 0) {
            val shiftedLen = setShiftRight(x, x, xLen, bitCount)
            verify { shiftedLen <= xLen }
            for (i in shiftedLen..<xLen)
                x[i] = 0
        }
        return x
    }

    /**
     * Shifts the first [xLen] limbs of [x] right by [bitCount] bits, storing
     * the results in z.
     *
     * This will successfully operate mutate in-place, where z === x
     *
     * Bits shifted out of the low end are discarded.
     * returns the number of limbs used in z.
     *
     * @throws IllegalArgumentException if [xLen] or [bitCount] is out of range.
     */
    fun setShiftRight(z: Magia, x: Magia, xNormLen: Int, bitCount: Int): Int {
        require(bitCount >= 0 && xNormLen >= 0 && xNormLen <= x.size)
        verify { isNormalized(x, xNormLen) }
        if (xNormLen == 0)
            return 0
        require(x[xNormLen - 1] != 0)
        val newBitLen = normBitLen(x, xNormLen) - bitCount
        if (newBitLen <= 0)
            return 0
        val zNormLen = (newBitLen + 0x1F) ushr 5
        require(zNormLen <= z.size)
        val wordShift = bitCount ushr 5
        val innerShift = bitCount and ((1 shl 5) - 1)
        if (innerShift != 0) {
            val iLast = zNormLen - 1
            for (i in 0..<iLast)
                z[i] = (x[i + wordShift + 1] shl (32 - innerShift)) or (x[i + wordShift] ushr innerShift)
            val srcIndex = iLast + wordShift + 1
            z[iLast] = (
                    if (srcIndex < xNormLen)
                        (x[iLast + wordShift + 1] shl (32 - innerShift))
                    else
                        0) or
                    (x[iLast + wordShift] ushr innerShift)
        } else {
            for (i in 0..<zNormLen)
                z[i] = x[i + wordShift]
        }
        verify { isNormalized(z, zNormLen) }
        return zNormLen
    }

    /**
     * Returns a new [Magia] representing [x] shifted left by [bitCount] bits.
     *
     * Bits shifted out of the low end propagate into higher limbs. The returned
     * array will be longer than [x] to accommodate the resulting value.
     *
     * @param x the source magnitude in little-endian limb order.
     * @param bitCount the number of bits to shift left; must be non-negative.
     * @return a new [Magia] containing the shifted value.
     * @throws IllegalArgumentException if [bitCount] is negative.
     */
    fun newShiftLeft(x: Magia, xNormLen: Int, bitCount: Int): Magia {
        val xBitLen = normBitLen(x, xNormLen)
        if (xBitLen == 0)
            return ZERO
        if (bitCount == 0)
            return newNormalizedCopy(x, xNormLen)
        val zBitLen = xBitLen + bitCount
        val zNormLen = limbLenFromBitLen(zBitLen)
        val z = Magia(zNormLen)
        val zNormLen2 = setShiftLeft(z, x, xNormLen, bitCount)
        verify { zNormLen == zNormLen2 }
        verify { isNormalized(z, zNormLen) }
        return z
    }

    /**
     * Shifts `x[0‥xLen)` left by `bitCount` bits and writes the result into `z`
     * (supports in-place use when `z === x`).
     *
     * Returns the resulting normalized limb length, or throws
     * `ArithmeticException` if the result does not fit in `z`.
     */
    fun setShiftLeft(z: Magia, x: Magia, xNormLen: Int, bitCount: Int): Int {
        verify { isNormalized(x, xNormLen) }

        if (xNormLen == 0)
            return 0

        val xBitLen = normBitLen(x, xNormLen)

        val wordShift = bitCount ushr 5
        val innerShift = bitCount and 31

        // ------------------------------------------------------------
        // 1. Compute required bit length and limb length from *math*
        // ------------------------------------------------------------
        val zBitLen = xBitLen + bitCount
        val zNormLen = limbLenFromBitLen(zBitLen)

        if (zNormLen > z.size)
            throw ArithmeticException(ERR_MSG_SHL_OVERFLOW)

        // ------------------------------------------------------------
        // 2. Fast path: whole-limb shift only
        // ------------------------------------------------------------
        if (innerShift == 0) {
            // new length is xNormLen + wordShift, but we trust zNormLen from bitLen
            x.copyInto(z, wordShift, 0, xNormLen)
            z.fill(0, 0, wordShift)

            check(isNormalized(z, zNormLen))
            return zNormLen
        }

        // ------------------------------------------------------------
        // 3. Non-zero inner shift: detect spill from top limb
        // ------------------------------------------------------------
        val top = x[xNormLen - 1]
        val spill = top ushr (32 - innerShift)   // 0 if no spill

        // Sanity: math-based zNormLen must match spill-based expectation
        // (optional, but nice while debugging)
        // val expected = xNormLen + wordShift + if (spill != 0) 1 else 0
        // check(expected == zNormLen)

        // ------------------------------------------------------------
        // 4. Write spill limb FIRST (important if z === x)
        // ------------------------------------------------------------
        if (spill != 0) {
            z[zNormLen - 1] = spill
        }

        // ------------------------------------------------------------
        // 5. Main shift loop: high → low (in-place safe)
        // ------------------------------------------------------------
        for (src in xNormLen - 1 downTo 1) {
            val dst = src + wordShift
            z[dst] = (x[src] shl innerShift) or
                    (x[src - 1].ushr(32 - innerShift))
        }

        // lowest shifted limb
        z[wordShift] = x[0] shl innerShift

        // clear lower limbs
        z.fill(0, 0, wordShift)

        check(isNormalized(z, zNormLen))
        return zNormLen
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
        verify { isNormalized(x, xNormLen) }
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
     * Returns the bit length of the value represented by the entire [x] array.
     *
     * The bit length is defined as the index (1-based) of the most significant set bit
     * in the integer. If all limbs are zero, the result is 0.
     *
     * This is equivalent to calling [bitLen] with [xLen] equal to [x.size].
     *
     * @param x the array of limbs, in little-endian order.
     * @return the number of bits required to represent the value.
     */
    fun bitLen(x: Magia): Int = bitLen(x, x.size)

    /**
     * Returns the bit length of the value represented by the first [xLen] limbs of [x].
     *
     * Scans from the most significant limb to find the highest set bit.
     * Returns 0 if all limbs are zero.
     *
     * @param x the limb array representing the integer.
     * @param xLen the number of significant limbs to examine.
     * @throws IllegalArgumentException if [xLen] is out of range.
     */
    internal fun bitLen(x: Magia, xLen: Int): Int {
        if (xLen >= 0 && xLen <= x.size) {
            for (i in xLen - 1 downTo 0)
                if (x[i] != 0)
                    return 32 - x[i].countLeadingZeroBits() + (i * 32)
            return 0
        } else {
            throw IllegalArgumentException()
        }
    }

    internal fun normBitLen(x: Magia, xLen: Int): Int {
        if (xLen > 0) {
            val lastLimb = x[xLen - 1]
            verify { lastLimb != 0 }
            return (xLen shl 5) - lastLimb.countLeadingZeroBits()
        }
        return 0
    }

    /**
     * Returns the number of 32-bit limbs required to represent a value with the given [bitLen].
     *
     * Equivalent to ceiling(bitLen / 32).
     *
     * @param bitLen the number of significant bits.
     * @return the minimum number of 32-bit limbs needed to hold that many bits.
     */
    inline fun limbLenFromBitLen(bitLen: Int) = (bitLen + 0x1F) ushr 5

    /**
     * Returns the bit length using Java's BigInteger-style semantics.
     *
     * This represents the number of bits required to encode the value in
     * two's-complement form, excluding the sign bit.
     *
     * For positive values, this is identical to [bitLen(x, xLen)].
     * For negative values, the result is one less if the magnitude is an
     * exact power of two (for example, -128 has a bit length of 7 not 8,
     * and -1 has a bit length of 0).
     *
     * @param sign `true` if the value is negative, `false` otherwise.
     * @param x the array of 32-bit limbs representing the magnitude.
     * @param xLen the number of significant limbs to consider; must be normalized.
     * @return the bit length following BigInteger’s definition.
     */
    fun bitLengthBigIntegerStyle(sign: Boolean, x: Magia, xNormLen: Int): Int {
        verify { isNormalized(x, xNormLen) }
        if (xNormLen >= 0 && xNormLen <= x.size) {
            val bitLen = normBitLen(x, xNormLen)
            val isNegPowerOfTwo = sign && isPowerOfTwo(x, xNormLen)
            val bitLengthBigIntegerStyle = bitLen - if (isNegPowerOfTwo) 1 else 0
            return bitLengthBigIntegerStyle
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Returns the count of trailing zero bits in the unsigned magnitude.
     *
     * Scans limbs `0 ..< normLen` in little-endian order and returns the bit index
     * of the least-significant set bit. Returns `-1` if the magnitude is zero
     * (`normLen == 0` or all inspected limbs are zero).
     *
     * @param x the limb array in little-endian order.
     * @param normLen the count of significant limbs to inspect.
     * @return the count of trailing zero bits, or `-1` if the magnitude is zero.
     * @throws IllegalArgumentException if `normLen` is out of bounds.
     */
    fun ctz(x: Magia, normLen: Int): Int {
        if (normLen >= 0 && normLen <= x.size) { // BCE
            for (i in 0..<normLen) {
                if (x[i] != 0)
                    return (i shl 5) + x[i].countTrailingZeroBits()
            }
            return -1
        }
        throw IllegalArgumentException()
    }


    /**
     * Returns a new normalized array holding `x AND y` over their normalized
     * ranges. The result is trimmed to its highest non-zero limb, or [ZERO] if the
     * AND is entirely zero.
     *
     * @return normalized [Magia] value or [ZERO]
     */
    fun newAnd(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
        var iLast = min(xNormLen, yNormLen)
        do {
            --iLast
            if (iLast < 0)
                return ZERO
        } while ((x[iLast] and y[iLast]) == 0)
        val z = Magia((iLast + 1))
        while (iLast >= 0) {
            z[iLast] = x[iLast] and y[iLast]
            --iLast
        }
        return z
    }

    /**
     * Computes `z = x AND y` over the normalized ranges `x[0‥xNormLen)` and
     * `y[0‥yNormLen)`, returning the normalized limb length of the result.
     * Supports in-place mutation when `z` aliases `x` or `y`.
     */
    fun setAnd(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        if (xNormLen >= 0 && xNormLen <= x.size && yNormLen >= 0 && yNormLen <= y.size) {
            verify { isNormalized(x, xNormLen) }
            verify { isNormalized(y, yNormLen) }
            val minLen = min(xNormLen, yNormLen)
            if (minLen <= z.size) {
                var i = minLen
                do {
                    --i
                    if (i < 0)
                        return 0
                } while ((x[i] and y[i]) == 0)
                val zNormLen = i + 1
                while (i >= 0) {
                    z[i] = x[i] and y[i]
                    --i
                }
                return zNormLen
            }
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new normalized array holding `x OR y` over their normalized ranges.
     * If the OR is zero, returns [ZERO].
     *
     * @return the normalized [Magia] value or [ZERO]
     */
    fun newOr(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
        verify { isNormalized(x, xNormLen) }
        verify { isNormalized(y, yNormLen) }
        val maxLen = max(xNormLen, yNormLen)
        if (maxLen != 0) {
            val z = Magia(maxLen)
            setOr(z, x, xNormLen, y, yNormLen)
            return z
        }
        return ZERO
    }

    /**
     * Computes `z = x OR y` over the normalized ranges `x[0‥xNormLen)` and
     * `y[0‥yNormLen)`.
     * The result length is the larger of the two operand lengths, and the write
     * supports in-place mutation when `z` aliases either operand.
     *
     * @return the normalized limb length of the OR result.
     */
    fun setOr(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        if (xNormLen >= 0 && xNormLen <= x.size && yNormLen >= 0 && yNormLen <= y.size) {
            verify { isNormalized(x, xNormLen) }
            verify { isNormalized(y, yNormLen) }
            val maxLen = max(xNormLen, yNormLen)
            if (maxLen <= z.size) {
                val minLen = min(xNormLen, yNormLen)
                val zNormLen = maxLen
                for (i in 0..<minLen)
                    z[i] = x[i] or y[i]
                when {
                    minLen < xNormLen ->
                        x.copyInto(z, minLen, minLen, xNormLen)

                    minLen < yNormLen ->
                        y.copyInto(z, minLen, minLen, yNormLen)
                }
                return zNormLen
            }
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new non-normalized array holding `x XOR y` over their normalized
     * ranges. Uses `setXor` to compute the result. Returns [ZERO] if the
     * XOR result is zero.
     *
     * @return non-normalized [Magia]
     */
    fun newXor(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
        val maxLen = max(xNormLen, yNormLen)
        val z = Magia(maxLen)
        val zNormLen = setXor(z, x, xNormLen, y, yNormLen)
        return if (zNormLen == 0) ZERO else z
    }

    /**
     * Computes `z = x XOR y` over the normalized ranges `x[0‥xNormLen)` and
     * `y[0‥yNormLen)`, returning the normalized limb length of the result.
     * Supports in-place mutation when `z` aliases either operand.
     */
    fun setXor(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        if (xNormLen >= 0 && xNormLen <= x.size && yNormLen >= 0 && yNormLen <= y.size) {
            verify { isNormalized(x, xNormLen) }
            verify { isNormalized(y, yNormLen) }
            val maxLen = max(xNormLen, yNormLen)
            if (maxLen <= z.size) {
                val minLen = min(xNormLen, yNormLen)
                for (i in 0..<minLen)
                    z[i] = x[i] xor y[i]
                val zNormLen = when {
                    minLen < xNormLen -> {
                        x.copyInto(z, minLen, minLen, xNormLen)
                        xNormLen
                    }

                    minLen < yNormLen -> {
                        y.copyInto(z, minLen, minLen, yNormLen)
                        yNormLen
                    }

                    else -> normLen(z, minLen)
                }
                return zNormLen
            }
        }
        throw IllegalArgumentException()
    }

    /**
     * Tests whether the bit at the specified [bitIndex] is set in the given unsigned integer.
     *
     * @param x the array of 32-bit limbs representing the integer.
     * @param xLen the number of significant limbs to consider; must be normalized.
     * @param bitIndex the zero-based index of the bit to test (0 is least significant bit).
     * @return `true` if the specified bit is set, `false` otherwise.
     * @throws IllegalArgumentException if [xLen] is out of range for [x].
     */
    fun testBit(x: Magia, xLen: Int, bitIndex: Int): Boolean {
        if (xLen >= 0 && xLen <= x.size) {
            val wordIndex = bitIndex ushr 5
            if (wordIndex >= xLen)
                return false
            val word = x[wordIndex]
            val bitMask = 1 shl (bitIndex and 0x1F)
            return (word and bitMask) != 0
        }
        throw IllegalArgumentException()
    }

    /**
     * Checks if any of the lower [bitCount] bits in [x] are set.
     *
     * Considers all limbs of [x]. Efficiently stops scanning as soon as a set bit is found.
     *
     * @param x the array of 32-bit limbs representing the integer.
     * @param bitCount the number of least-significant bits to check.
     * @return `true` if at least one of the lower [bitCount] bits is set, `false` otherwise.
     */
    fun testAnyBitInLowerN(x: Magia, bitCount: Int): Boolean =
        testAnyBitInLowerN(x, x.size, bitCount)

    /**
     * Checks if any of the lower [bitCount] bits in [x] are set.
     *
     * Only the first [xLen] limbs of [x] are considered.
     * Scans efficiently, stopping as soon as a set bit is found.
     *
     * @param x the array of 32-bit limbs representing the integer.
     * @param xLen the number of significant limbs to consider; must be within `0..x.size`.
     * @param bitCount the number of lower bits to check (starting from the least significant bit).
     * @return `true` if at least one of the lower [bitCount] bits is set, `false` otherwise.
     * @throws IllegalArgumentException if [xLen] is out of range for [x].
     */
    fun testAnyBitInLowerN(x: Magia, xLen: Int, bitCount: Int): Boolean {
        if (xLen >= 0 && xLen <= x.size) {
            val lastBitIndex = bitCount - 1
            val lastWordIndex = lastBitIndex ushr 5
            for (i in 0..<lastWordIndex) {
                if (i == xLen)
                    return false
                if (x[i] != 0)
                    return true
            }
            if (lastWordIndex == xLen)
                return false
            val bitMask = -1 ushr (0x1F - (lastBitIndex and 0x1F))
            return (x[lastWordIndex] and bitMask) != 0
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Returns the 64-bit unsigned value formed by taking the magnitude,
     * shifting it right by [bitIndex] bits, and truncating to the low 64 bits.
     * In other words:
     *
     *     result = (x >> bitIndex) mod 2^64
     *
     * Bits above the available limbs are treated as zero, so the returned
     * 64-bit value is always well-defined.
     *
     * This reads up to three 32-bit little-endian limbs from [x] to assemble
     * the 64-bit result.
     *
     * @param x the little-endian 32-bit limb array.
     * @param bitIndex the starting bit position (0 = least-significant bit).
     * @return the low 64 bits of (magnitude >> bitIndex).
     */
    fun extractULongAtBitIndex(x: Magia, xNormLen: Int, bitIndex: Int): ULong {
        if (bitIndex >= 0) {
            val loLimb = bitIndex ushr 5
            val innerShift = bitIndex and 0x1F
            if (bitIndex == 0)
                return toRawULong(x, xNormLen)
            if (loLimb >= xNormLen)
                return 0uL
            val lo = x[loLimb].toUInt().toULong()
            if ((loLimb + 1) == xNormLen)
                return lo shr innerShift
            val mid = x[loLimb + 1].toUInt().toULong()
            if ((loLimb + 2) == xNormLen || innerShift == 0)
                return ((mid shl 32) or lo) shr innerShift
            val hi = x[loLimb + 2].toUInt().toULong()
            return (hi shl (64 - innerShift)) or (mid shl (32 - innerShift)) or (lo shr innerShift)
        }
        throw IllegalArgumentException(ERR_MSG_NEGATIVE_INDEX)

    }

    /**
     * Creates a new limb array containing the 32-bit unsigned value [w] placed at the
     * bit position [bitIndex], with all other bits zero. This is equivalent to
     *
     *     result = w << bitIndex
     *
     * represented as a little-endian array of 32-bit limbs.
     *
     * The array is sized to hold all non-zero bits of `(w << bitIndex)`. If [w] is
     * zero, the canonical zero array [ZERO] is returned.
     *
     * Shift operations rely on JVM semantics, where 32-bit shifts use the shift
     * count modulo 32.
     *
     * @param w the 32-bit unsigned value to place.
     * @param bitIndex the bit position (0 = least-significant bit).
     * @return a new limb array with `w` inserted beginning at [bitIndex].
     */
    fun newWithUIntAtBitIndex(w: UInt, bitIndex: Int): Magia {
        if (w == 0u)
            return ZERO
        val wBitLen = 32 - w.countLeadingZeroBits()
        val z = newWithBitLen(wBitLen + bitIndex)
        val limbIndex = bitIndex ushr 5
        val innerShift = bitIndex and 0x1F
        z[limbIndex] = (w shl innerShift).toInt()
        if (limbIndex + 1 < z.size) {
            verify { innerShift != 0 }
            z[limbIndex + 1] = (w shr (32 - innerShift)).toInt()
        }
        verify { extractULongAtBitIndex(z, z.size, bitIndex) == w.toULong() }
        return z
    }

    /**
     * Returns `true` if the normalized magnitude [x] equals the single 32-bit value [y].
     *
     * Only the first [xNormLen] limbs are considered.
     *
     * @param x the limb array (least-significant limb first)
     * @param xNormLen number of significant limbs in [x]
     * @param y the 32-bit value to compare against
     */
    fun EQ(x: Magia, xNormLen: Int, y: Int) = xNormLen == 1 && x[0] == y

    /**
     * Checks whether the unsigned integers represented by [x] and [y] are equal.
     *
     * Comparison is based on the significant limbs of each array, ignoring any trailing zero limbs.
     *
     * @param x the first array of 32-bit limbs representing an unsigned integer.
     * @param y the second array of 32-bit limbs representing an unsigned integer.
     * @return `true` if [x] and [y] represent the same value, `false` otherwise.
     */
    fun EQ(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Boolean =
        compare(x, xNormLen, y, yNormLen) == 0

    /**
     * Compares two arbitrary-precision integers represented as arrays of 32-bit limbs,
     * considering only the first [xNormLen] limbs of [x] and [yNormLen] limbs of [y].
     *
     * Both input ranges must be normalized: the last limb of each range (if non-zero length)
     * must be non-zero.
     *
     * Comparison is unsigned per-limb (32-bit) from most significant to least significant limb.
     *
     * @param x the first integer array (least significant limb first).
     * @param xNormLen the number of significant limbs in [x] to consider; must be normalized.
     * @param y the second integer array (least significant limb first).
     * @param yNormLen the number of significant limbs in [y] to consider; must be normalized.
     * @return -1 if x < y, 0 if x == y, 1 if x > y.
     * @throws IllegalArgumentException if [xNormLen] or [yNormLen] are out of bounds for the respective arrays.
     */
    fun compare(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        verify { isNormalized(x, xNormLen) }
        verify { isNormalized(y, yNormLen) }
        if (xNormLen >= 0 && xNormLen <= x.size && yNormLen >= 0 && yNormLen <= y.size) {
            if (xNormLen != yNormLen)
                return if (xNormLen > yNormLen) 1 else -1
            for (i in xNormLen - 1 downTo 0) {
                if (x[i] != y[i])
                    return ((dw32(x[i]) - dw32(y[i])).toLong() shr 63).toInt() or 1
            }
            return 0
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Compares the normalized magnitude [x] with the unsigned 64-bit value [dw].
     *
     * @param x the limb array (least-significant limb first)
     * @param xNormLen number of significant limbs in [x]
     * @param dw the unsigned value to compare against
     * @return a negative value if `x < dw`, zero if `x == dw`, or a positive value if `x > dw`
     */
    fun compare(x: Magia, xNormLen: Int, dw: ULong): Int {
        verify { isNormalized(x, xNormLen) }
        if (xNormLen >= 0 && xNormLen <= x.size) {
            return if (xNormLen > 2) 1 else toRawULong(x, xNormLen).compareTo(dw)
        }
        throw IllegalArgumentException()
    }

    /**
     * Divides the normalized limb array `x[0‥xNormLen)` by the 32-bit unsigned
     * value `w`, writing the quotient into `z` (supports in-place use).
     *
     * Returns the normalized limb length of the quotient, or throws
     * `ArithmeticException` if `w == 0u`.
     *
     * @return the normalized length
     */
    fun setDiv32(z: Magia, x: Magia, xNormLen: Int, w: UInt): Int {
        if (xNormLen >= 0 && xNormLen <= x.size && z.size >= xNormLen) {
            verify { isNormalized(x, xNormLen) }
            when {
                w == 0u -> throw ArithmeticException(ERR_MSG_DIV_BY_ZERO)
                xNormLen == 0 -> return 0
                w.countOneBits() == 1 ->
                    return setShiftRight(z, x, xNormLen, w.countTrailingZeroBits())
            }
            val dw = w.toULong()
            var carry = 0uL
            for (i in xNormLen - 1 downTo 0) {
                val t = (carry shl 32) + dw32(x[i])
                val q = t / dw
                val r = t % dw
                z[i] = q.toInt()
                carry = r
            }
            val zNormLen = xNormLen - if (z[xNormLen - 1] == 0) 1 else 0
            verify { isNormalized(z, zNormLen) }
            return zNormLen
        }
        throw IllegalArgumentException()
    }

    /**
     * Divides the unsigned integer represented by [x] by the 64-bit unsigned divisor [dw],
     * storing the quotient in [z].
     *
     * Only the normalized limbs `[0, xNormLen)` of [x] are used.
     *
     * @return the normalized limb length of the quotient stored in [z].
     */
    fun setDiv64(z: Magia, x: Magia, xNormLen: Int, unBuf: IntArray?, dw: ULong): Int {
        if (xNormLen >= 0 && xNormLen <= x.size && z.size >= xNormLen - 2 + 1) {
            verify { isNormalized(x, xNormLen) }

            val tryLen = trySetDivFastPath64(z, x, xNormLen, dw)
            if (tryLen >= 0)
                return tryLen

            val u = x
            val m = xNormLen
            val vnDw = dw
            val q = z
            val r = null
            val qNormLen = knuthDivide64(q, r, u, vnDw, m, unBuf).toInt()
            return qNormLen
        }
        throw IllegalArgumentException()
    }

    /**
     * Attempts to compute `x / y` using fast paths for small or simple cases.
     *
     * On success, writes the quotient to [zMagia] and returns its normalized limb length.
     * Returns `-1` if no fast path applies and a full division is required.
     *
     * @throws ArithmeticException if `y == 0`.
     */
    fun trySetDivFastPath(zMagia: Magia, xMagia: Magia, xNormLen: Int, yMagia: Magia, yNormLen: Int): Int {
        when {
            yNormLen == 0 -> throw ArithmeticException(ERR_MSG_DIV_BY_ZERO)
            xNormLen == 0 -> return 0
            xNormLen < yNormLen -> return 0
            xNormLen <= 2 ->
                return setULong(zMagia, toRawULong(xMagia, xNormLen) / toRawULong(yMagia, yNormLen))
            yNormLen == 1 -> return setDiv32(zMagia, xMagia, xNormLen, yMagia[0].toUInt())
            isPowerOfTwo(yMagia, yNormLen) ->
                return setShiftRight(zMagia, xMagia, xNormLen,
                    ctz(yMagia, yNormLen))
            // note that this will handle aliasing
            // when x === yMeta,yMagia
            xNormLen == yNormLen -> {
                val xBitLen = normBitLen(xMagia, xNormLen)
                val yBitLen = normBitLen(yMagia, yNormLen)
                if (xBitLen < yBitLen)
                    return 0
                if (xBitLen == yBitLen) {
                    val cmp = compare(xMagia, xNormLen, yMagia, yNormLen)
                    return if (cmp < 0) {
                        0
                    } else {
                        zMagia[0] = 1
                        1
                    }
                }
            }
        }
        return -1
    }

    fun trySetDivFastPath64(zMagia: Magia, xMagia: Magia, xNormLen: Int, yDw: ULong): Int {
        when {
            yDw == 0uL -> throw ArithmeticException(ERR_MSG_DIV_BY_ZERO)
            xNormLen == 0 -> return 0
            xNormLen == 1 -> return setUInt(zMagia, (xMagia[0].toUInt().toULong() / yDw).toUInt())
            xNormLen == 2 ->
                return setULong(zMagia,
                    ((xMagia[1].toULong() shl 32) or xMagia[0].toUInt().toULong()) / yDw)
            yDw.countOneBits() == 1 ->
                return setShiftRight(zMagia, xMagia, xNormLen, yDw.countTrailingZeroBits())
            (yDw shr 32) == 0uL -> return setDiv32(zMagia, xMagia, xNormLen, yDw.toUInt())
        }
        return -1
    }

    /**
     * Computes `x mod w` for the normalized limb array `x[0‥xNormLen)`, returning
     * the remainder as a `UInt`. Throws `ArithmeticException` if `w == 0u`.
     */
    fun calcRem32(x: Magia, xNormLen: Int, w: UInt): UInt {
        if (xNormLen >= 0 && xNormLen <= x.size) {
            verify { isNormalized(x, xNormLen) }
            when {
                w == 0u -> throw ArithmeticException(ERR_MSG_DIV_BY_ZERO)
                xNormLen <= 2 -> return when {
                    xNormLen == 2 -> {
                        val xDw = (x[1].toULong() shl 32) or x[0].toUInt().toULong()
                        (xDw % w.toULong()).toUInt()
                    }

                    xNormLen == 1 -> x[0].toUInt() % w
                    else -> 0u
                }

                w.countOneBits() == 1 ->
                    return x[0].toUInt() and ((1u shl w.countTrailingZeroBits()) - 1u)
            }

            val dw = w.toULong()
            var carry = 0uL
            for (i in xNormLen - 1 downTo 0) {
                val t = (carry shl 32) + dw32(x[i])
                val r = t % dw
                carry = r
            }
            return carry.toUInt()
        }
        throw IllegalArgumentException()
    }

    fun calcRem64(x: Magia, xNormLen: Int, unBuf: IntArray?, dw: ULong): ULong {
        if (xNormLen >= 0 && xNormLen <= x.size) {
            verify { isNormalized(x, xNormLen) }
            when {
                (dw shr 32) == 0uL -> return calcRem32(x, xNormLen, dw.toUInt()).toULong()
                xNormLen <= 2 -> return when {
                    xNormLen == 2 -> {
                        val xDw = (x[1].toULong() shl 32) or x[0].toUInt().toULong()
                        xDw % dw
                    }

                    xNormLen == 1 -> x[0].toUInt().toULong()
                    else -> 0uL
                }

                dw.countOneBits() == 1 ->
                    return ((x[1].toULong() shl 32) or x[0].toUInt().toULong()) and
                            ((1uL shl dw.countTrailingZeroBits()) - 1uL)
            }

            val rem = knuthDivide64(q = null, r = null, u = x, vDw = dw, m = xNormLen, unBuf = unBuf)
            return rem
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new integer array representing [x] divided by the 32-bit unsigned integer [w].
     *
     * This operation does not mutate the input [x]. The quotient is computed and returned as a
     * new array. If the quotient is zero, a shared [ZERO] array is returned.
     *
     * @param x the integer array (least significant limb first) to be divided.
     * @param w the 32-bit unsigned divisor.
     * @return a new non-normalized [Magia] containing the quotient of the division.
     * @throws ArithmeticException if [w] is zero.
     */
    fun newDiv(x: Magia, xNormLen: Int, w: UInt): Magia {
        verify { isNormalized(x, xNormLen) }
        if (xNormLen > 0) {
            val z = Magia(xNormLen)
            val zNormLen = setDiv32(z, x, xNormLen, w)
            if (zNormLen > 0)
                return z
        }
        return ZERO
    }

    /**
     * Divides the normalized limb array `x` by the 64-bit unsigned divisor `dw`
     * and returns a new non-normalized quotient array.
     *
     * Uses a specialized Knuth division routine for 64-bit divisors.
     * Returns:
     * - [ZERO] if `x < dw`
     * - [ONE]  if `x == dw`
     * - the non-normalized quotient otherwise.
     */
    fun newDiv(x: Magia, xNormLen: Int, dw: ULong): Magia {
        if ((dw shr 32) == 0uL)
            return newDiv(x, xNormLen, dw.toUInt())
        val cmp = compare(x, xNormLen, dw)
        when {
            cmp < 0 -> return ZERO
            cmp == 0 -> return ONE
        }
        val u = x
        val m = xNormLen
        val vnDw = dw
        val q = Magia(m - 2 + 1)
        val r = null
        val qNormLen = knuthDivide64(q, r, u, vnDw, m, unBuf=null).toInt()
        return if (qNormLen > 0) q else ZERO
    }

    /**
     * Divides the normalized limb array `x` by the normalized limb array `y` and
     * returns a new non-normalized quotient array.
     *
     * Handles all size relations:
     * - If `y` fits in one limb, delegates to the 32-bit divisor path.
     * - If `x < y`, returns [ZERO].
     * - If `x == y`, returns `[1]`.
     * - Otherwise performs full Knuth division on `x` by `y`.
     *
     * @return the non-normalized quotient, or [ZERO] if the quotient is zero.
     */
    fun newDiv(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
        verify { isNormalized(x, xNormLen) }
        verify { isNormalized(y, yNormLen) }
        when {
            yNormLen == 0 -> throw ArithmeticException(ERR_MSG_DIV_BY_ZERO)
            yNormLen == 1 -> return newDiv(x, xNormLen, y[0].toUInt())
            xNormLen <= yNormLen -> {
                val xBitLen = normBitLen(x, xNormLen)
                val yBitLen = normBitLen(y, yNormLen)
                if (xBitLen < yBitLen)
                    return ZERO
                if (xBitLen == yBitLen) {
                    val cmp = compare(x, xNormLen, y, yNormLen)
                    return if (cmp < 0) ZERO else ONE
                }
            }
        }
        val m = xNormLen
        val n = yNormLen
        val u = x
        val v = y
        val q = IntArray(m - n + 1)
        val r = null
        val qNormLen = knuthDivide(q, r, u, v, m, n)
        return if (qNormLen > 0) q else ZERO
    }

    /**
     * Divides the normalized integer [x] by [y] using Knuth division.
     *
     * @param z destination limb array for the quotient; must have size ≥ `xNormLen - yNormLen + 1`.
     * @param x source limb array representing the dividend.
     * @param xNormLen normalized limb length of [x].
     * @param xTmp optional temporary array of size ≥ `xNormLen + 1`, used for normalization.
     * @param y source limb array representing the divisor.
     * @param yNormLen normalized limb length of [y].
     * @param yTmp optional temporary array of size ≥ `yNormLen`, used for normalization.
     *
     * @return the normalized limb length of the quotient written to [z].
     *
     * @throws ArithmeticException if `yNormLen == 0`.
     * @throws IllegalArgumentException if [z], [xTmp], or [yTmp] are too small.
     */
    fun setDiv(z: Magia,
               x: Magia, xNormLen: Int, xTmp: Magia?,
               y: Magia, yNormLen: Int, yTmp: Magia?): Int {
        verify { isNormalized(x, xNormLen) }
        verify { isNormalized(y, yNormLen) }
        verify { xTmp == null || xTmp.size >= xNormLen + 1 }
        verify { yTmp == null || yTmp.size >= yNormLen }
        if (xNormLen < yNormLen || yNormLen < 2)
            throw IllegalArgumentException()
        val m = xNormLen
        val n = yNormLen
        val u = x
        val v = y
        if (z.size < m - n + 1)
            throw IllegalArgumentException()
        val q = z
        val r = null
        if (yNormLen > 2) {
            val qNormLen = knuthDivide(q, r, u, v, m, n, xTmp, yTmp)
            return qNormLen
        }
        val vDw: ULong = (v[1].toULong() shl 32) or (v[0].toUInt().toULong())
        val qNormLen = knuthDivide64(q, r, u, vDw, m, xTmp).toInt()
        return qNormLen
    }

    /**
     * Computes the remainder of dividing the magnitude slice `x[0‥xNormLen)` by the
     * 64-bit unsigned modulus `dw`, with optional modular-ring normalization.
     *
     * The computation is performed on unsigned magnitudes. An unsigned remainder is
     * obtained first. If `applyModNormalization` is `true` and that remainder is
     * non-zero, the result is computed as `dw - remainder`, producing the least
     * non-negative residue in `[0, dw)`. If the flag is `false`, the raw unsigned
     * remainder is returned without sign normalization.
     *
     * Usage convention:
     *  • `rem` calls pass `false` (raw unsigned remainder semantics).
     *  • `mod` calls pass `(dividend < 0)` so negative dividends normalize into the
     *    modular ring.
     *
     * Fast paths are used when `dw` fits in 32 bits or when `x` occupies one or
     * two limbs; otherwise a 2-limb representation of `dw` is used for general
     * multi-limb remainder.
     *
     * @param x   the limbs holding the magnitude of the dividend
     * @param xNormLen the number of significant limbs in `x`
     * @param applyModRingNormalization `true` requests modular-ring normalization;
     *        `false` returns the raw unsigned remainder
     * @param dw  the 64-bit unsigned modulus
     *
     * @return a normalized limb array holding the remainder (raw or normalized),
     *         or [ZERO] when the result is zero.
     */
    fun newRemOrMod64(x: Magia, xNormLen: Int,
                      applyModRingNormalization: Boolean, dw: ULong): Magia {
        val rem = calcRem64(x, xNormLen, null, dw)
        return newFromULong(
            if (!applyModRingNormalization || rem == 0uL) rem else dw - rem)
    }

    fun setRem64(z: Magia, x: Magia, xNormLen: Int, dw: ULong): Int {
        verify { isNormalized(x, xNormLen) }
        return when {
            (dw shr 32) == 0uL -> {
                val rem = calcRem32(x, xNormLen, dw.toUInt())
                val z0 = rem.toInt()
                z[0] = z0
                return (z0 or -z0) ushr 31
            }

            xNormLen == 0 -> 0
            xNormLen <= 2 -> setULong(z, toRawULong(x, xNormLen) % dw)
            else -> setRem(z, x, xNormLen, intArrayOf(dw.toInt(), (dw shr 32).toInt()), 2)
        }
    }

    /**
     * Returns a new non-normalized array holding `x mod y`.
     * Handles 1-limb divisors directly, otherwise delegates to `setRem`
     * and trims the result. Returns [ZERO] if the remainder is zero.
     *
     * @return the non-normalized [Magia] remainder or [ZERO]
     */
    fun newRemOrMod(x: Magia, xNormLen: Int,
                    applyModRingNormalization: Boolean, y: Magia, yNormLen: Int): Magia {
        verify { isNormalized(x, xNormLen) }
        verify { isNormalized(y, yNormLen) }
        when {
            yNormLen == 0 -> throw ArithmeticException(ERR_MSG_DIV_BY_ZERO)
            xNormLen == 0 -> return ZERO
            yNormLen <= 2 -> return newRemOrMod64(
                x, xNormLen,
                applyModRingNormalization,
                y[0].toUInt().toULong() or if (yNormLen == 2) (y[1].toULong() shl 32) else 0uL
            )

            xNormLen <= yNormLen -> {
                if (normBitLen(x, xNormLen) <= normBitLen(y, yNormLen)) {
                    val cmp = compare(x, xNormLen, y, yNormLen)
                    return when {
                        cmp < 0 && !applyModRingNormalization -> x.copyOf(xNormLen)
                        cmp < 0 -> newSub(y, yNormLen, x, xNormLen)
                        cmp == 0 -> ZERO
                        else -> {
                            val rem = newSub(x, xNormLen, y, yNormLen)
                            if (!applyModRingNormalization)
                                return rem
                            // setSub will mutate in-place
                            val mod = rem
                            val modNormLen = setSub(mod, y, yNormLen, rem, normLen(rem))
                            mod.fill(0, modNormLen)
                            mod
                        }
                    }
                }
            }
        }
        val z = Magia(yNormLen)
        val remNormLen = setRem(z, x, xNormLen, y, yNormLen)
        when {
            remNormLen == 0 -> return ZERO
            applyModRingNormalization -> {
                val modNormLen = setSub(z, y, yNormLen, z, remNormLen)
                z.fill(0, modNormLen)
            }
        }
        return z
    }

    /**
     * Computes `x mod y` over the normalized ranges `x[0‥xNormLen)` and
     * `y[0‥yNormLen)`, writing the remainder into `z`.
     *
     * Fast paths:
     * - If `y` fits in one limb, delegates to the single-limb remainder routine.
     * - If both operands fit in at most two limbs, performs a 64-bit remainder.
     * - If `x < y`, the remainder is just `x`.
     *
     * Otherwise performs full Knuth division with the quotient discarded and the
     * remainder written into `z`. The returned value is the normalized limb length
     * of the remainder.
     *
     * @return the normalized limb length of the remainder
     * @throws ArithmeticException if `yNormLen == 0` (division by zero)
     * @throws IllegalArgumentException if `z` is too small to hold the remainder
     */
    fun setRem(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        verify { isNormalized(x, xNormLen) }
        verify { isNormalized(y, yNormLen) }
        val rLen0 = trySetRemFastPath(z, x, xNormLen, y, yNormLen)
        if (rLen0 >= 0)
            return rLen0
        val n = yNormLen
        val m = xNormLen
        val u = x
        val v = y
        val q = null
        if (z.size < yNormLen)
            throw IllegalArgumentException()
        val r = z
        val rNormLen = knuthDivide(q, r, u, v, m, n)
        verify { rNormLen <= n }
        verify { isNormalized(r, rNormLen) }
        return rNormLen
    }

    fun trySetRemFastPath(zMagia: Magia, xMagia: Magia, xNormLen: Int, yMagia: Magia, yNormLen: Int): Int {
        when {
            yNormLen == 0 -> throw ArithmeticException(ERR_MSG_DIV_BY_ZERO)
            xNormLen < yNormLen -> {
                xMagia.copyInto(zMagia, 0, 0, xNormLen)
                return xNormLen
            }
            yNormLen == 2 && xNormLen <= 2 ->
                return setULong(zMagia, toRawULong(xMagia, xNormLen) % toRawULong(yMagia, yNormLen))
            yNormLen == 1 -> return setRem64(zMagia, xMagia, xNormLen, yMagia[0].toUInt().toULong())
            // note that this will handle aliasing
            // when x === yMeta,yMagia
            xNormLen == yNormLen -> {
                val xBitLen = normBitLen(xMagia, xNormLen)
                val yBitLen = normBitLen(yMagia, yNormLen)
                if (xBitLen < yBitLen) {
                    xMagia.copyInto(zMagia, 0, 0, xNormLen)
                    return xNormLen
                }
                if (xBitLen == yBitLen) {
                    val cmp = compare(xMagia, xNormLen, yMagia, yNormLen)
                    return when {
                        cmp < 0 -> {
                            xMagia.copyInto(zMagia, 0, 0, xNormLen)
                            xNormLen
                        }
                        cmp == 0 -> 0
                        else -> setSub(zMagia, xMagia, xNormLen, yMagia, yNormLen)
                    }
                }
            }
        }
        return -1
    }

    fun setRem(z: Magia,
               x: Magia, xNormLen: Int, xTmp: Magia?,
               y: Magia, yNormLen: Int, yTmp: Magia?): Int {
        verify { isNormalized(x, xNormLen) }
        verify { isNormalized(y, yNormLen) }
        verify { xTmp == null || xTmp.size >= xNormLen + 1 }
        verify { yTmp == null || yTmp.size >= yNormLen }
        if (xNormLen < yNormLen || yNormLen < 2)
            throw IllegalArgumentException()
        val m = xNormLen
        val n = yNormLen
        val u = x
        val v = y
        if (z.size < yNormLen)
            throw IllegalArgumentException()
        val r = z
        if (yNormLen > 2) {
            val rNormLen = knuthDivide(q = null, r, u, v, m, n, xTmp, yTmp)
            return rNormLen
        }
        val vDw: ULong = (v[1].toULong() shl 32) or (v[0].toUInt().toULong())
        val rNormLen = knuthDivide64(q = null, r, u, vDw, m, xTmp).toInt()
        return rNormLen
    }

    /**
     * Multi‐word division (Knuth’s Algorithm D) in base 2^32.
     *
     * q: quotient array (length ≥ m – n + 1), or null if not needed
     * r: remainder array (length ≥ n), or null if not needed
     * u: dividend array (length = m), little‐endian (u[0] = low word)
     * v: divisor array (length = n ≥ 2), little‐endian, v[n − 1] ≠ 0
     * m: number of words in u (≥ n)
     * n: number of words in v (≥ 1)
     *
     * @return qNormLen if q != null, rNormLen if r != null, else -1
     */
    fun knuthDivide(
        q: IntArray?,
        r: IntArray?,
        u: IntArray,
        v: IntArray,
        m: Int,
        n: Int,
        unBuf: IntArray? = null,
        vnBuf: IntArray? = null
    ): Int {
        if (m < n || n < 2 || v[n - 1] == 0)
            throw IllegalArgumentException()
        verify { r !== q }

        // Step D1: Normalize
        val un = when {
            unBuf != null && unBuf.size < m + 1 -> throw IllegalArgumentException()
            unBuf != null -> unBuf
            r != null && r.size >= m + 1 && r !== v -> r
            else -> IntArray(m + 1)
        }
        u.copyInto(un, 0, 0, m)
        un[m] = 0
        val vn = when {
            vnBuf != null && vnBuf.size < n -> throw IllegalArgumentException()
            vnBuf != null -> vnBuf
            else -> IntArray(n)
        }
        v.copyInto(vn, 0, 0, n)

        val shift = vn[n - 1].countLeadingZeroBits()
        if (shift > 0) {
            setShiftLeft(vn, vn, n, shift)
            setShiftLeft(un, un, m, shift)
        }

        knuthDivideNormalizedCore(q, un, vn, m, n)

        var rNormLen = 0
        if (r != null)
            rNormLen = setShiftRight(r, un, normLen(un, m + 1), shift)

        return when {
            q != null -> normLen(q, m - n + 1)
            r != null -> rNormLen
            else -> -1
        }
    }

    /**
     * Core of Knuth division in base 2^32 that takes un and vn,
     * the normalized copies of u an v.
     *
     * un is side-effected and contains the remainder.
     *
     * q: quotient array (length ≥ m – n + 1)
     * un: normalized dividend array (length = m + 1) with an extra zero limb
     * vn: normalized divisor array (length = n ≥ 2), little‐endian, hi bit of vn[n - 1] is set
     * m: the original m ... one less than the length of un
     * n: number of words in vn (≥ 1)
     *
     * throws IllegalArgumentException if (m < n || n < 2 || v[n − 1] == 0).
     */
    fun knuthDivideNormalizedCore(
        q: IntArray?,
        un: IntArray,
        vn: IntArray,
        m: Int,
        n: Int
    ) {
        if (m < n || n < 2 || vn[n - 1] >= 0)
            throw IllegalArgumentException()

        val vn_1 = dw32(vn[n - 1])
        val vn_2 = dw32(vn[n - 2])

        // -- main loop --
        for (j in m - n downTo 0) {
            val jn = j + n
            val jn1 = jn -1
            val jn2 = jn - 2
            // estimate q̂ = (un[j+n]*B + un[j+n-1]) / vn[n-1]
            val hi = dw32(un[jn])
            val lo = dw32(un[jn1])
            //if (hi == 0L && lo < vn_1) // this would short-circuit,
            //    continue               // but probability is astronomically small
            val num = (hi shl 32) or lo
            var qhat = num / vn_1
            var rhat = num % vn_1

            val un_j = dw32(un[jn2])
            // correct estimate
            while ((qhat shr 32) != 0uL ||
                qhat * vn_2 > (rhat shl 32) + un_j) {
                qhat--
                rhat += vn_1
                if ((rhat shr 32) != 0uL)
                    break
            }

            // multiply & subtract
            var carry = 0uL
            for (i in 0 until n) {
                val prod = qhat * dw32(vn[i])
                val prodHi = prod shr 32
                val prodLo = prod and MASK32
                val unIJ = dw32(un[j + i])
                val t = unIJ - prodLo - carry
                un[j + i] = t.toInt()
                carry = prodHi - (t.toLong() shr 32).toULong() // yes, this is a signed shift right
            }
            val t = dw32(un[jn]) - carry
            un[jn] = t.toInt()
            if (q != null) {
                val borrow = t shr 63
                q[j] = (qhat - borrow).toInt()
            }
            if (t.toLong() < 0L) {
                var c2 = 0uL
                for (i in 0 until n) {
                    val sum = dw32(un[j + i]) + dw32(vn[i]) + c2
                    un[j + i] = sum.toInt()
                    c2 = sum shr 32
                }
                un[j + n] += c2.toInt()
            }
        }
    }

    /**
     * Divides a multi-limb unsigned integer by a 64-bit divisor.
     *
     * This is a convenience wrapper around [knuthDivide], where the divisor
     * `vDw` is expanded into two 32-bit limbs. The quotient and remainder
     * are written to `q` and `r` if provided.
     *
     * Since this is specialized for 64-bit divisor it has some **special**
     * behavior. The returned value is a ULong that has different
     * interpretations depending upon whether we are trying to calculate
     * the quotient or the remainder.
     *
     * if q != null then qNormLen is returned ... as a ULong
     * if r != null then rNormLen is returned ... as a ULong
     * otherwise, the 64-bit remainder itself is returned
     *
     * @param q optional quotient array (length ≥ m − 1)
     * @param r optional remainder array (length ≥ m + 1)
     * @param u dividend limbs (least-significant limb first)
     * @param vDw 64-bit unsigned divisor (high 32 bits must be non-zero)
     * @param m number of significant limbs in `u` (≥ 2)
     * @return a ULong with qNormLen if q != null, rNormLen if r != null,
     *         the remainder itself.
     *
     * @throws IllegalArgumentException if `m < 2` or the high 32 bits of `vDw` are zero
     * @see knuthDivide
     */
    fun knuthDivide64(
        q: IntArray?,
        r: IntArray?,
        u: IntArray,
        vDw: ULong,
        m: Int,
        unBuf: IntArray?
    ): ULong {
        if (m < 2 || (vDw shr 32) == 0uL)
            throw IllegalArgumentException()

        // Step D1: Normalize
        val un = when {
            unBuf != null && unBuf.size < m + 1 -> throw IllegalArgumentException()
            unBuf != null -> unBuf
            r != null && r.size >= m + 1 -> r
            else -> IntArray(m + 1)
        }
        u.copyInto(un, 0, 0, m)
        un[m] = 0
        val shift = vDw.countLeadingZeroBits()
        val vnDw = vDw shl shift

        if (shift > 0)
            setShiftLeft(un, un, m, shift)

        knuthDivideNormalizedCore64(q, un, vnDw, m)

        var rNormLen = 0
        if (r != null)
            rNormLen = setShiftRight(r, un, normLen(un, m + 1), shift)

        if (q != null)
            return normLen(q, m-2+1).toULong()
        if (r != null)
            return rNormLen.toULong()
        // caller wants the remainder as a ULong
        val un0 = un[0].toUInt().toULong()
        val un1 = un[1].toUInt().toULong()
        val un2 = un[2].toUInt().toULong()
        val rDw: ULong =
            if (shift > 0) {
                (un2 shl (64 - shift)) or (un1 shl (32 - shift)) or (un0 shr (shift))
            } else {
                (un1 shl 32) or un0
            }
        return rDw
    }

    fun knuthDivideNormalizedCore64(
        q: IntArray?,
        un: IntArray,
        vnDw: ULong,
        m: Int
    ) {
        if (m < 2 || (vnDw shr 63) != 1uL)
            throw IllegalArgumentException()

        //val vn_1 = dw32(vn[n - 1])
        //val vn_2 = dw32(vn[n - 2])
        val vn1 = vnDw shr 32
        val vn0 = vnDw and MASK32

        // -- main loop --
        for (j in m - 2 downTo 0) {
            val j1 = j + 1
            val j2 = j + 2
            // estimate q̂ = (un[j+n]*B + un[j+n-1]) / vn[n-1]
            val hi = dw32(un[j2])
            val lo = dw32(un[j1])
            //if (hi == 0L && lo < vn_1) // this would short-circuit,
            //    continue               // but probability is astronomically small
            val num = (hi shl 32) or lo
            var qhat = num / vn1
            var rhat = num % vn1

            val un_j = dw32(un[j])
            // correct estimate
            while ((qhat shr 32) != 0uL ||
                qhat * vn0 > (rhat shl 32) + un_j) {
                qhat--
                rhat += vn1
                if ((rhat shr 32) != 0uL)
                    break
            }

            // multiply & subtract
            var carry = 0uL
            // i = 0
            run {
                val prod = qhat * vn0
                val prodHi = prod shr 32
                val prodLo = prod and MASK32
                val un0 = un_j
                val t0 = un0 - prodLo - carry
                un[j] = t0.toInt()
                carry = prodHi - (t0.toLong() shr 32).toULong()
            }

            // i = 1
            run {
                val prod = qhat * vn1
                val prodHi = prod shr 32
                val prodLo = prod and MASK32
                val un1 = dw32(un[j1])
                val t1 = un1 - prodLo - carry
                un[j1] = t1.toInt()
                carry = prodHi - (t1.toLong() shr 32).toULong()
            }

            val t = dw32(un[j2]) - carry
            un[j2] = t.toInt()
            if (q != null) {
                val borrow = t shr 63
                q[j] = (qhat - borrow).toInt()
            }
            if (t.toLong() < 0L) {
                var c2 = 0uL
                // i = 0
                run {
                    val sum0 = dw32(un[j]) + vn0 + c2
                    un[j] = sum0.toInt()
                    c2 = sum0 shr 32
                }
                // i = 1
                run {
                    val sum1 = dw32(un[j + 1]) + vn1 + c2
                    un[j + 1] = sum1.toInt()
                    c2 = sum1 shr 32
                }

                un[j2] += c2.toInt()
            }
        }
    }

}
