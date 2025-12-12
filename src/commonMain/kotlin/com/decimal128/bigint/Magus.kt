// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.intrinsic.unsignedMulHi
import kotlin.math.min
import kotlin.math.max


// magia == MAGnitude IntArray ... it's magic!

typealias Magia = IntArray

private const val BARRETT_MU_1E9: ULong = 0x44B82FA09uL       // floor(2^64 / 1e9)
private const val ONE_E_9: ULong = 1_000_000_000uL

private const val M_U32_DIV_1E1 = 0xCCCCCCCDuL
private const val S_U32_DIV_1E1 = 35

private const val M_U32_DIV_1E2 = 0x51EB851FuL
private const val S_U32_DIV_1E2 = 37

private const val M_U64_DIV_1E4 = 0x346DC5D63886594BuL
private const val S_U64_DIV_1E4 = 11 // + 64 high

// these magic reciprocal constants only work for values up to
// 10**9 / 10**4
private const val M_1E9_DIV_1E4 = 879_609_303uL
private const val S_1E9_DIV_1E4 = 43

private const val LOG2_10_CEIL_32 = 14_267_572_565uL

private const val ERROR_ADD_OVERFLOW = "add overflow ... destination too small"
private const val ERROR_SUB_UNDERFLOW = "sub underflow ... minuend too small for subtrahend"
private const val ERROR_MUL_OVERFLOW = "mul overflow ... destination too small"
private const val ERROR_SHL_OVERFLOW = "shl overflow ... destination too small"


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
 *   exclusively by [BigIntAccumulator].
 *
 * ### Available Functionality
 * - Magia acts as a complete arbitrary-length integer **ALU** (Arithmetic Logic Unit).
 * - **Arithmetic:** `add`, `sub`, `mul`, `div`, `rem`, `sqr`, `gcd`
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
 * [BigInt] and [BigIntAccumulator].
 *
 * Magus ... ancient Latin word for a Persian magician who works with Magia
  */
object Magus {

    private inline fun bitLen(n: Int) = 32 - n.countLeadingZeroBits()

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
     * Converts the first one or two limbs of [x] into a single [ULong] value.
     *
     * - If [x] is empty, returns 0.
     * - If [x] has one limb, returns its value as an unsigned 64-bit integer.
     * - If [x] has two or more limbs, returns the combined value of the first two limbs
     *   with [x[0]] as the low 32 bits and [x[1]] as the high 32 bits.
     *
     * Any limbs beyond the first two are ignored.
     *
     * @param x the array of 32-bit limbs representing the magnitude (not necessarily normalized).
     * @return the unsigned 64-bit value represented by the first one or two limbs of [x].
     */
    fun toRawULong(x: Magia, xLen: Int = x.size): ULong {
        return when (xLen) {
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
    fun newFromULong(dw: ULong): Magia {
        return when {
            (dw shr 32) != 0uL -> intArrayOf(dw.toInt(), (dw shr 32).toInt())
            dw != 0uL -> intArrayOf(dw.toInt())
            else -> ZERO
        }
    }

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

    /**
     * Returns the normalized limb length of **x**—the index of the highest
     * non-zero limb plus one. If all limbs are zero, returns `0`.
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
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Returns `true` if this limb array is in normalized form.
     *
     * A limb array is considered *normalized* when:
     *
     *  - it is empty (representing the canonical zero), or
     *  - its most significant limb (the last element) is non-zero.
     *
     * In other words, a normalized array contains no unused leading zero limbs.
     * This check does not modify the array.
     *
     * @param x the limb array to test
     * @return `true` if `x` has no trailing zero limbs; `false` otherwise
     */
    fun isNormalized(x: Magia) = x.isEmpty() || x[x.lastIndex] != 0

    /**
     * Same as above, but checks only the first `xLen` limbs.
     */
    fun isNormalized(x: Magia, xLen: Int): Boolean {
        check (xLen >= 0 && xLen <= x.size)
        return xLen == 0 || x[xLen - 1] != 0
    }

    /**
     * Returns `x` if it is already in normalized limb form; otherwise returns
     * a new `Magia` containing the normalized representation.
     *
     * Normalization removes any unused most-significant zero limbs so that:
     *
     *  - either the array is a single zero limb, or
     *  - the most significant limb is non-zero.
     *
     * This function never modifies the input array. If normalization is required,
     * a trimmed copy is produced; otherwise the original array is returned
     * unchanged.
     *
     * @param x the limb array to check
     * @return `x` if it is already normalized, or a newly allocated normalized copy
     */
    fun normalizedCopyIfNeeded(x: Magia) = if (isNormalized(x)) x else newNormalizedCopy(x)

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
                Magia(normLenFromBitLen(bitLen))
            bitLen == 0 -> ZERO
            else ->
                throw IllegalArgumentException("invalid allocation bitLen:$bitLen")
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
    fun newWithFloorLen(floorLen: Int) : Magia {
        if (floorLen in 0..MAX_ALLOC_SIZE) {
            // if floorLen == 0 then add 1
            val t = floorLen + 1 - (-floorLen ushr 31)
            val allocSize = (t + 3) and 3.inv()
            if (allocSize <= MAX_ALLOC_SIZE)
                return Magia(allocSize)
        }
        throw IllegalArgumentException("invalid allocation length:$floorLen")
    }

    /**
     * Returns a normalized copy of of [x] with any leading
     * zero limbs removed.
     *
     * If all limbs are zero, returns [ZERO].
     */
    inline fun newNormalizedCopy(x: Magia): Magia = newNormalizedCopy(x, x.size)

    /**
     * Returns a normalized copy of the first [len] limbs of [x],
     * with any leading zero limbs removed.
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
     * Returns a copy of [x] extended to at least [floorLen] elements.
     *
     * The new array preserves the contents of [x] and zero-fills the remainder.
     *
     * @throws IllegalArgumentException if [floorLen] is not greater than [x.size].
     */
    fun newCopyWithFloorLen(x: Magia, floorLen: Int) : Magia {
        if (floorLen > x.size) {
            val z = newWithFloorLen(floorLen)
            x.copyInto(z, 0, 0, x.size)
            return z
        }
        throw IllegalArgumentException()
    }

    fun newCopyWithFloorLen(x: Magia, xLen: Int, floorLen: Int): Magia {
        if (xLen <= x.size && xLen < floorLen) {
            val z = newWithFloorLen(floorLen)
            x.copyInto(z, 0, 0, xLen)
            return z
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a copy of [x] whose length is the minimum number of limbs required
     * to hold [exactBitLen] bits. Leading limbs are preserved or truncated as needed.
     */
    fun newCopyWithExactBitLen(x: Magia, exactBitLen: Int): Magia =
        newCopyWithExactLimbLen(x, x.size, (exactBitLen + 0x1F) ushr 5)

    /**
     * Returns a copy of [x] whose length is the minimum number of limbs required
     * to hold [exactBitLen] bits. Leading limbs are preserved or truncated as needed.
     */
    fun newCopyWithExactBitLen(x: Magia, xLen: Int, exactBitLen: Int): Magia =
        newCopyWithExactLimbLen(x, xLen, (exactBitLen + 0x1F) ushr 5)

    /**
     * Returns a copy of [x] resized to exactly `exactLimbLen` limbs.
     * If the new length is larger, high-order limbs are zero-filled; if smaller,
     * high-order limbs are truncated.
     */
    fun newCopyWithExactLimbLen(x: Magia, exactLimbLen: Int): Magia =
        newCopyWithExactLimbLen(x, x.size, exactLimbLen)

    fun newCopyWithExactLimbLen(x: Magia, xLen: Int, exactLimbLen: Int): Magia {
        if (exactLimbLen in 1..MAX_ALLOC_SIZE) {
            val dst = Magia(exactLimbLen)
            //System.arraycopy(src, 0, dst, 0, min(src.size, dst.size))
            x.copyInto(dst, 0, 0, min(xLen, dst.size))
            return dst
        }
        if (exactLimbLen == 0)
            return ZERO
        throw IllegalArgumentException("invalid allocation length:$exactLimbLen")
    }

    /**
     * Returns a new limb array representing [x] plus the unsigned 64-bit value [dw].
     *
     * The result is extended as needed to hold any carry or sign extension from the addition.
     */
    fun newAdd(x: Magia, xNormLen: Int, dw: ULong): Magia {
        check (isNormalized(x, xNormLen))
        val newBitLen = max(bitLen(x, xNormLen), (64 - dw.countLeadingZeroBits())) + 1
        val z = newWithBitLen(newBitLen)
        var carry = dw
        for (i in 0..<xNormLen) {
            val t = dw32(x[i]) + (carry and 0xFFFF_FFFFuL)
            z[i] = t.toInt()
            carry = (t shr 32) + (carry shr 32)
        }
        if (carry != 0uL)
            z[xNormLen] = carry.toInt()
        if (carry shr 32 != 0uL)
            z[xNormLen + 1] = (carry shr 32).toInt()
        return z
    }

    /**
     * Returns a new limb array representing the sum of [x] and [y].
     *
     * The result will sometimes be not normalized.
     */
    fun newAdd(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
        check (isNormalized(x, xNormLen))
        check (isNormalized(y, yNormLen))
        val newBitLen = max(bitLen(x, xNormLen), bitLen(y, yNormLen)) + 1
        val z = newWithBitLen(newBitLen)
        setAdd(z, x, xNormLen, y, yNormLen)
        return z
    }

    /**
     * Adds the 32-bit unsigned value `w` to the limb array `x` in place and returns
     * the resulting array.
     *
     * If the addition overflows all limbs a new array with one
     * extra limb is returned.
     */
    fun newOrMutateAdd(x: Magia, w: UInt): Magia {
        var carry = w.toULong()
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
        check (normLen(x) == 0)
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
    fun setAdd(z: Magia, x: Magia, xNormLen: Int, dw: ULong): Int {
        if (xNormLen >= 0 && xNormLen <= x.size && isNormalized(x, xNormLen)) {
            var carry = dw
            var i = 0
            while (carry != 0uL && i < xNormLen) {
                val s = dw32(x[i]) + (carry and 0xFFFF_FFFFuL)
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
            throw ArithmeticException(ERROR_ADD_OVERFLOW)
        }
        throw IllegalArgumentException()
    }

    /**
     * Computes `z = x + y` using the low-order [xLen] and [yLen] limbs of the
     * inputs and returns the normalized limb length of the sum. The caller must
     * ensure that [z] is large enough to hold the result; the minimum size is
     * `max(bitLen(x), bitLen(y)) + 1` bits.
     *
     * @return the normalized limb count of the sum
     * @throws ArithmeticException if the result does not fit in [z]
     */
    fun setAdd(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        check (isNormalized(x, xNormLen))
        check (isNormalized(y, yNormLen))
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
                    check((carry shr 1) == 0uL)
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
                        throw ArithmeticException(ERROR_ADD_OVERFLOW)
                    z[i] = 1
                    ++i
                }
                check (isNormalized(z, i))
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
    fun newSub(x: Magia, xNormLen: Int, dw: ULong): Magia {
        check (isNormalized(x, xNormLen))
        if (xNormLen >= 0 && xNormLen <= x.size) {
            val z = Magia(xNormLen)
            var orAccumulator = 0
            var borrow = 0uL
            if (z.isNotEmpty()) {
                val t0 = dw32(x[0]) - (dw and 0xFFFF_FFFFuL)
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
    fun setSub(z: Magia, x: Magia, xLen: Int, dw: ULong): Int {
        if (xLen >= 0 && xLen <= x.size) {
            val xNormLen = normLen(x, xLen)
            if (z.size >= xNormLen) {
                if (xNormLen <= 2 && toRawULong(x, xNormLen) < dw)
                    throw ArithmeticException(ERROR_SUB_UNDERFLOW)
                var lastNonZeroIndex = -1
                var borrow = dw
                var i = 0
                while (i < xNormLen) {
                    val t = dw32(x[i]) - (borrow and 0xFFFF_FFFFuL)
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
                check (borrow == 0uL)
                val zNormLen = lastNonZeroIndex + 1
                check (isNormalized(z, zNormLen))
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
        check (isNormalized(x, xNormLen))
        check (isNormalized(y, yNormLen))
        val z = Magia(xNormLen)
        val zNormLen = setSub(z, x, xNormLen, y, yNormLen)
        check (isNormalized(z, zNormLen))
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
    fun setSub(z: Magia, x: Magia, xLen: Int, y: Magia, yLen: Int): Int {
        if (xLen >= 0 && xLen <= x.size && yLen >= 0 && yLen <= y.size) {
            val xNormLen = normLen(x, xLen)
            val yNormLen = normLen(y, yLen)
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
                        check(isNormalized(z, zNormLen))
                        return zNormLen
                    }
                }
                throw ArithmeticException(ERROR_SUB_UNDERFLOW)
            }
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing [x] multiplied by the unsigned 32-bit value [w].
     *
     * Returns [ZERO] if [x] or [w] is zero.
     */
    fun newMul(x: Magia, w: UInt): Magia {
        val xBitLen = bitLen(x)
        if (xBitLen == 0 || w == 0u)
            return ZERO
        val xNormLen = normLenFromBitLen(xBitLen)
        val zBitLen = xBitLen + 32 - w.countLeadingZeroBits()
        val z = newWithBitLen(zBitLen)
        val zNormLen = setMul(z, x, xNormLen, w)
        check (isNormalized(z, zNormLen))
        return z
    }

    /**
     * Multiplies a normalized multi-limb integer [x] (first [xLen] limbs) by a single 32-bit word [w],
     * storing the result in [z]. The operation is safe in-place, so [z] may be the same array as [x].
     *
     * @return number of significant limbs written to [z]
     * @throws ArithmeticException if [z] is too small to hold the full product (including carry)
     * @throws IllegalArgumentException if [xLen] <= 0 or [xLen] >= x.size
     */
    fun setMul(z: Magia, x: Magia, xLen: Int, w: UInt): Int {
        if (xLen > 0 && xLen <= x.size) {
            val xNormLen = normLen(x, xLen)
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
                    throw ArithmeticException(ERROR_MUL_OVERFLOW)
                z[i] = carry.toInt()
                ++i
            }
            check (isNormalized(z, i))
            return i

        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing [x] multiplied by the unsigned 64-bit value [dw].
     *
     * Returns [ZERO] if [x] or [dw] is zero.
     */
    fun newMul(x: Magia, dw: ULong): Magia {
        val xBitLen = bitLen(x)
        if (xBitLen == 0 || dw == 0uL)
            return ZERO
        val xNormLen = normLenFromBitLen(xBitLen)
        val zBitLen = xBitLen + 64 - dw.countLeadingZeroBits()
        val z = newWithBitLen(zBitLen)
        val zNormLen = setMul(z, x, xNormLen, dw)
        check (isNormalized(z, zNormLen))
        return z
    }

    /**
     * Multiplies the first [xLen] limbs of [x] by the unsigned 64-bit value [dw], storing the result in [z].
     *
     * - Performs a single-pass multiplication.
     * - Does not overwrite [x], allowing in-place multiplication scenarios.
     * - [zLen] must be greater than [xLen]; caller must ensure it is large enough to hold the full product.
     *
     * The caller is responsible for ensuring that [zLen] is sufficient, either by checking limb lengths
     * (typically requiring +2 limbs) or by checking bit lengths (0 to 2 extra limbs).
     *
     * @throws IllegalArgumentException if [xLen], [zLen], or array sizes are invalid.
     */
    fun setMul(z: Magia, x: Magia, xLen: Int, dw: ULong): Int {
        if ((dw shr 32) == 0uL)
            return setMul(z, x, xLen, dw.toUInt())
        if (xLen >= 0 && xLen <= x.size) {
            val xNormLen = normLen(x, xLen)
            val lo = dw and 0xFFFF_FFFFuL
            val hi = dw shr 32

            var ppPrevHi = 0uL

            var i = 0
            while (i < xNormLen) {
                val xi = dw32(x[i])

                val pp = xi * lo + (ppPrevHi and 0xFFFF_FFFFuL)
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
                check (isNormalized(z, i))
                return i
            }
            throw ArithmeticException(ERROR_MUL_OVERFLOW)
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing the product of [x] and [y].
     *
     * Returns [ZERO] if either [x] or [y] is zero.
     */
    fun newMul(x: Magia, y: Magia): Magia {
        val xBitLen = bitLen(x)
        val yBitLen = bitLen(y)
        if (xBitLen == 0 || yBitLen == 0)
            return ZERO
        val z = newWithBitLen(xBitLen + yBitLen)
        val zNormLen = setMul(z, x, normLenFromBitLen(xBitLen), y, normLenFromBitLen(yBitLen))
        check (isNormalized(z, zNormLen))
        return z
    }

    /**
     * Multiplies the first [xLen] limbs of [x] by the first [yLen] limbs of [y],
     * accumulating the result into [z].
     *
     * Requirements:
     * - [z] must be of sufficient size to hold the product
     *   `bitLen(x) + bitLen(y)`, at least [xLen] + [yLen] - 1.
     * - [xLen] and [yLen] must be greater than zero and within the array bounds.
     * - For efficiency, if one array is longer, it is preferable to use it as [y].
     *
     * @return the number of limbs actually used in [z].
     * @throws IllegalArgumentException if preconditions on array sizes or lengths are violated.
     */
    fun setMul(z: Magia, x: Magia, xLen: Int, y: Magia, yLen: Int): Int {
        if (xLen > 0 && xLen <= x.size && yLen > 0 && yLen <= y.size && z.size >= xLen + yLen - 1) {
            val xNormLen = normLen(x, xLen)
            val yNormLen = normLen(y, yLen)
            val corto = if (xNormLen < yNormLen) x else y
            val largo = if (xNormLen < yNormLen) y else x
            val cortoLen = if (xNormLen < yNormLen) xNormLen else yNormLen
            val largoLen = if (xNormLen < yNormLen) yNormLen else xNormLen

            z.fill(0, 0, largoLen) // zero out the product
            for (i in 0..<cortoLen) {
                val cortoLimb = dw32(corto[i])
                var carry = 0uL
                for (j in 0..<largoLen) {
                    val largoLimb = dw32(largo[j])
                    val t = cortoLimb * largoLimb + dw32(z[i + j]) + carry
                    z[i + j] = t.toInt()
                    carry = t shr 32
                }
                if (i + largoLen < z.size)
                    z[i + largoLen] = carry.toInt()
                else if (carry != 0uL)
                    throw ArithmeticException(ERROR_MUL_OVERFLOW)
            }
            var lastIndex = cortoLen + largoLen - 1
            // >= instead of == to help bounds check elimination
            if (lastIndex >= z.size || z[lastIndex] == 0)
                --lastIndex
            val zNormLen = lastIndex + 1
            check (isNormalized(z, zNormLen))
            return zNormLen
        } else if (xLen == 0 || yLen == 0) {
            return 0
        } else {
            throw IllegalArgumentException()
        }
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
    fun mutateFmaPow10(x: Magia, pow10: Int, a: UInt) {
        if (pow10 in 0..9) {
            val m64 = POW10[pow10].toULong()
            var carry = a.toULong()
            for (i in x.indices) {
                val t = dw32(x[i]) * m64 + carry
                x[i] = t.toInt()
                carry = t shr 32
            }
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Returns a new limb array representing the square of [x].
     *
     * Returns [ZERO] if [x] is zero.
     */
    fun newSqr(x: Magia): Magia {
        val bitLen = bitLen(x)
        if (bitLen == 0)
            return ZERO
        val xNormLen = normLenFromBitLen(bitLen)
        val sqrBitLen = 2 * bitLen
        val z = newWithBitLen(sqrBitLen)
        val zNormLen = setSqr(z, x, xNormLen)
        check (isNormalized(z, zNormLen))
        return z
    }

    /**
     * Squares the first [xLen] limbs of [x], storing the result in [z].
     *
     * Requirements:
     * - [z.size] must be sufficient to hold the squared result (2 * [xLen] or 2 * [xLen] - 1 limbs).
     *
     * @return the normalized limb length of the result.
     */
    fun setSqr(z: Magia, x: Magia, xNormLen: Int) : Int {
        if (xNormLen > 0 && xNormLen <= x.size) {
            check (isNormalized(x, xNormLen))
            if (z.size >= 2 * xNormLen - 1) {
                z.fill(0, 0, min(z.size, 2 * xNormLen))

                // 1) Cross terms: for i<j, add (x[i]*x[j]) twice into p[i+j]
                // these terms are doubled
                for (i in 0..<xNormLen) {
                    val xi = dw32(x[i])
                    var carry = 0uL
                    for (j in (i + 1)..<xNormLen) {
                        val prod = xi * dw32(x[j])        // 32x32 -> 64
                        // add once
                        val t1 = prod + dw32(z[i + j]) + carry
                        val p1 = t1 and 0xFFFF_FFFFuL
                        carry = t1 shr 32
                        // add second time (doubling) — avoids (prod << 1) overflow
                        val t2 = prod + p1
                        z[i + j] = t2.toInt()
                        carry += t2 shr 32
                    }
                    // flush carry to the next limb(s)
                    val k = i + xNormLen
                    if (carry != 0uL) {
                        val t = dw32(z[k]) + carry
                        z[k] = t.toInt()
                        carry = t shr 32
                        if (carry != 0uL)
                            ++z[k + 1]
                    }
                }

                // 2) Diagonals: add x[i]**2 into columns 2*i and 2*i+1
                // terms on the diagonal are not doubled
                for (i in 0..<xNormLen) {
                    val sq = dw32(x[i]) * dw32(x[i])      // 64-bit
                    // add low 32 to p[2*i]
                    var t = dw32(z[2 * i]) + (sq and 0xFFFF_FFFFuL)
                    z[2 * i] = t.toInt()
                    var carry = t shr 32
                    // add high 32 (and carry) to p[2*i+1]
                    val s = (sq shr 32) + carry
                    if (s != 0uL) {
                        t = dw32(z[2 * i + 1]) + s
                        z[2 * i + 1] = t.toInt()
                        carry = t shr 32
                        // propagate any remaining carry
                        var k = 2 * i + 2
                        while (carry != 0uL) {
                            if (k >= z.size)
                                throw IllegalArgumentException(ERROR_MUL_OVERFLOW)
                            t = dw32(z[k]) + carry
                            z[k] = t.toInt()
                            carry = t shr 32
                            k++
                        }
                    }
                }
                var lastIndex = 2 * xNormLen - 1
                if (lastIndex >= z.size || z[lastIndex] == 0)
                    --lastIndex
                val zNormLen = lastIndex + 1
                check (isNormalized(z, zNormLen))
                return zNormLen
            }
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new [Magia] representing [x] logically shifted right by [bitCount] bits.
     *
     * Bits shifted out of the least significant end are discarded, and new high bits are filled with zeros.
     * The original [x] is not modified.
     *
     * @param x the source magnitude in little-endian limb order.
     * @param bitCount the number of bits to shift right; must be non-negative.
     * @return a new [Magia] containing the shifted result, normalized if all bits are shifted out.
     * @throws IllegalArgumentException if [bitCount] is negative.
     */
    fun newShiftRight(x: Magia, bitCount: Int): Magia {
        require(bitCount >= 0)
        val xLen = normLen(x)
        val newBitLen = bitLen(x, xLen) - bitCount
        if (newBitLen <= 0)
            return ZERO
        val z = newWithBitLen(newBitLen)
        val zLen = setShiftRight(z, x, xLen, bitCount)
        check (zLen == z.size)
        return z
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
            check (shiftedLen <= xLen)
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
    fun setShiftRight(z: Magia, x: Magia, xLen: Int, bitCount: Int): Int {
        require(bitCount >= 0 && xLen >= 0 && xLen <= x.size)
        val xNormLen = normLen(x, xLen)
        if (xNormLen == 0)
            return 0
        require(x[xNormLen - 1] != 0)
        val newBitLen = bitLen(x, xNormLen) - bitCount
        if (newBitLen <= 0)
            return 0
        val zNormLen = (newBitLen + 0x1F) ushr 5
        require (zNormLen <= z.size)
        val wordShift = bitCount ushr 5
        val innerShift = bitCount and ((1 shl 5) - 1)
        if (innerShift != 0) {
            val iLast = zNormLen - 1
            for (i in 0..<iLast)
                z[i] = (x[i + wordShift + 1] shl (32-innerShift)) or (x[i + wordShift] ushr innerShift)
            val srcIndex = iLast + wordShift + 1
            z[iLast] = (
                    if (srcIndex < xLen)
                        (x[iLast + wordShift + 1] shl (32 - innerShift))
                    else
                        0) or
                    (x[iLast + wordShift] ushr innerShift)
        } else {
            for (i in 0..<zNormLen)
                z[i] = x[i + wordShift]
        }
        check (isNormalized(z, zNormLen))
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
    fun newShiftLeft(x: Magia, bitCount: Int): Magia {
        val xBitLen = bitLen(x)
        if (xBitLen == 0)
            return ZERO
        if (bitCount == 0)
            return newNormalizedCopy(x)
        val zBitLen = bitLen(x) + bitCount
        val zNormLen = normLenFromBitLen(zBitLen)
        val z = Magia(zNormLen)
        val zNormLen2 = setShiftLeft(z, x, x.size, bitCount)
        check (zNormLen == zNormLen2)
        check (isNormalized(z, zNormLen))
        return z
    }

    /**
     * Shifts `x[0‥xLen)` left by `bitCount` bits and writes the result into `z`
     * (supports in-place use when `z === x`).
     *
     * Returns the resulting normalized limb length, or throws
     * `ArithmeticException` if the result does not fit in `z`.
     */
    fun setShiftLeft(z: Magia, x: Magia, xLen: Int, bitCount: Int): Int {
        require(xLen in 0..x.size && bitCount >= 0)

        val xNormLen = normLen(x, xLen)
        if (xNormLen == 0)
            return 0

        val xBitLen = bitLen(x, xNormLen)

        val wordShift = bitCount ushr 5
        val innerShift = bitCount and 31

        // ------------------------------------------------------------
        // 1. Compute required bit length and limb length from *math*
        // ------------------------------------------------------------
        val zBitLen = xBitLen + bitCount
        val zNormLen = normLenFromBitLen(zBitLen)

        if (zNormLen > z.size)
            throw ArithmeticException(ERROR_SHL_OVERFLOW)

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
        check (isNormalized(x, xNormLen))
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

    /**
     * Returns the number of 32-bit limbs required to represent a value with the given [bitLen].
     *
     * Equivalent to ceiling(bitLen / 32).
     *
     * @param bitLen the number of significant bits.
     * @return the minimum number of 32-bit limbs needed to hold that many bits.
     */
    inline fun normLenFromBitLen(bitLen: Int) = (bitLen + 0x1F) ushr 5

    /**
     * Overload of [bitLengthBigIntegerStyle] that considers all limbs in [x].
     *
     * Equivalent to calling [bitLengthBigIntegerStyle] with `xLen = x.size`.
     *
     * @param sign `true` if the value is negative, `false` otherwise.
     * @param x the array of 32-bit limbs representing the magnitude.
     * @return the bit length following BigInteger’s definition.
     */
    fun bitLengthBigIntegerStyle(sign: Boolean, x: Magia): Int =
        bitLengthBigIntegerStyle(sign, x, normLen(x))

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
        check (isNormalized(x, xNormLen))
        if (xNormLen >= 0 && xNormLen <= x.size) {
            val bitLen = bitLen(x, xNormLen)
            val isNegPowerOfTwo = sign && isPowerOfTwo(x, xNormLen)
            val bitLengthBigIntegerStyle = bitLen - if (isNegPowerOfTwo) 1 else 0
            return bitLengthBigIntegerStyle
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Returns a new normalized array holding `x AND y` over their normalized
     * ranges. The result is trimmed to its highest non-zero limb, or [ZERO] if the
     * AND is entirely zero.
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
            check (isNormalized(x, xNormLen))
            check (isNormalized(y, yNormLen))
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
     */
    fun newOr(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
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
            check (isNormalized(x, xNormLen))
            check (isNormalized(y, yNormLen))
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
     * Returns a new normalized array holding `x XOR y` over their normalized
     * ranges. Uses `setXor` to compute the result into a temporary array and
     * trims the array to the returned normalized length. Returns [ZERO] if the
     * XOR result is zero.
     */
    fun newXor(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
        val maxLen = max(xNormLen, yNormLen)
        val z = Magia(maxLen)
        val zNormLen = setXor(z, x, xNormLen, y, yNormLen)
        return if (zNormLen == 0) ZERO else z.copyOf(zNormLen)
    }

    /**
     * Computes `z = x XOR y` over the normalized ranges `x[0‥xNormLen)` and
     * `y[0‥yNormLen)`, returning the normalized limb length of the result.
     * Supports in-place mutation when `z` aliases either operand.
     */
    fun setXor(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        if (xNormLen >= 0 && xNormLen <= x.size && yNormLen >= 0 && yNormLen <= y.size) {
            check (isNormalized(x, xNormLen))
            check (isNormalized(y, yNormLen))
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
     * Creates a new limb array with a single bit set at [bitIndex].
     *
     * The resulting array has the minimum length needed to contain that bit.
     *
     * @throws IllegalArgumentException if [bitIndex] is negative.
     */
    inline fun newWithSetBit(bitIndex: Int): Magia {
        if (bitIndex >= 0) {
            val magia = Magus.newWithBitLen(bitIndex + 1)
            magia[magia.lastIndex] = 1 shl (bitIndex and 0x1F)
            return magia
        }
        throw IllegalArgumentException()
    }

    /**
     * Tests whether the bit at the specified [bitIndex] is set in the given unsigned integer.
     *
     * This is a convenience overload that considers all limbs in [x].
     *
     * @param x the array of 32-bit limbs representing the integer.
     * @param bitIndex the zero-based index of the bit to test (0 is least significant bit).
     * @return `true` if the specified bit is set, `false` otherwise.
     */
    fun testBit(x: Magia, bitIndex: Int): Boolean = testBit(x, x.size, bitIndex)

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
        } else {
            throw IllegalArgumentException()
        }
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
        val loLimb = bitIndex ushr 5
        val innerShift = bitIndex and 0x1F
        if (bitIndex == 0)
            return toRawULong(x)
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
            check (innerShift != 0)
            z[limbIndex + 1] = (w shr (32 - innerShift)).toInt()
        }
        check (extractULongAtBitIndex(z, z.size, bitIndex) == w.toULong())
        return z
    }

    /**
     * Checks whether the unsigned integer represented by [x] is equal to the single 32-bit value [y].
     *
     * Only the significant limbs of [x] are considered. Trailing zero limbs in [x] are ignored.
     *
     * @param x the array of 32-bit limbs representing the integer.
     * @param y the 32-bit integer to compare against.
     * @return `true` if [x] equals [y], `false` otherwise.
     */
    fun EQ(x: Magia, y: Int) = normLen(x) == 1 && x[0] == y

    /**
     * Checks whether the unsigned integers represented by [x] and [y] are equal.
     *
     * Comparison is based on the significant limbs of each array, ignoring any trailing zero limbs.
     *
     * @param x the first array of 32-bit limbs representing an unsigned integer.
     * @param y the second array of 32-bit limbs representing an unsigned integer.
     * @return `true` if [x] and [y] represent the same value, `false` otherwise.
     */
    fun EQ(x: Magia, y: Magia): Boolean = compare(x, normLen(x), y, normLen(y)) == 0

    /**
     * Compares two arbitrary-precision integers represented as arrays of 32-bit limbs.
     *
     * Comparison is performed over the full lengths of both arrays.
     *
     * @param x the first integer array (least significant limb first).
     * @param y the second integer array (least significant limb first).
     * @return -1 if x < y, 0 if x == y, 1 if x > y.
     */
    fun compare(x: Magia, y: Magia): Int = compare(x, normLen(x), y, normLen(y))

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
        check (isNormalized(x, xNormLen))
        check (isNormalized(y, yNormLen))
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

    fun compare(x: Magia, xNormLen: Int, dw: ULong): Int {
        check (isNormalized(x, xNormLen))
        if (xNormLen >= 0 && xNormLen <= x.size) {
            return if (xNormLen > 2) 1 else toRawULong(x, xNormLen).compareTo(dw)
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Divides the normalized limb array `x[0‥xNormLen)` by the 32-bit unsigned
     * value `w`, writing the quotient into `z` (supports in-place use).
     *
     * Returns the normalized limb length of the quotient, or throws
     * `ArithmeticException` if `w == 0u`.
     */
    fun setDiv(z: Magia, x: Magia, xNormLen: Int, w: UInt): Int {
        if (xNormLen >= 0 && xNormLen <= x.size && z.size >= xNormLen) {
            check (isNormalized(x, xNormLen))
            if (w == 0u)
                throw ArithmeticException("div by zero")
            if (xNormLen == 0)
                return 0
            val dw = w.toULong()
            var carry = 0uL
            for (i in xNormLen - 1 downTo 0) {
                val t = (carry shl 32) + dw32(x[i])
                val q = t / dw
                val r = t % dw
                z[i] = q.toInt()
                carry = r
            }
            val zNormLen = xNormLen - if (z[xNormLen-1] == 0) 1 else 0
            check (isNormalized(z, zNormLen))
            return zNormLen
        }
        throw IllegalArgumentException()
    }

    /**
     * Computes `x mod w` for the normalized limb array `x[0‥xNormLen)`, returning
     * the remainder as a `UInt`. Throws `ArithmeticException` if `w == 0u`.
     */
    fun calcRem(x: Magia, xNormLen: Int, w: UInt): UInt {
        if (xNormLen >= 0 && xNormLen <= x.size) {
            check (isNormalized(x, xNormLen))
            if (w == 0u)
                throw ArithmeticException("div by zero")
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

    /**
     * Returns a new integer array representing [x] divided by the 32-bit unsigned integer [w].
     *
     * This operation does not mutate the input [x]. The quotient is computed and returned as a
     * new array. If the quotient is zero, a shared [ZERO] array is returned.
     *
     * @param x the integer array (least significant limb first) to be divided.
     * @param w the 32-bit unsigned divisor.
     * @return a new [Magia] containing the quotient of the division.
     * @throws ArithmeticException if [w] is zero.
     */
    fun newDiv(x: Magia, w: UInt): Magia {
        val xNormLen = normLen(x)
        if (xNormLen > 0) {
            val z = Magia(xNormLen)
            val zNormLen = setDiv(z, x, xNormLen, w)
            if (zNormLen > 0)
                return z
        }
        return ZERO
    }

    /**
     * Divides the normalized limb array `x` by the 64-bit unsigned divisor `dw`
     * (which may exceed 32 bits) and returns a new normalized quotient array.
     *
     * Uses a specialized Knuth division routine for 64-bit divisors.
     * Returns:
     * - [ZERO] if `x < dw`
     * - [ONE]  if `x == dw`
     * - the normalized quotient otherwise.
     */
    fun newDiv(x: Magia, dw: ULong): Magia {
        if ((dw shr 32) == 0uL)
            return newDiv(x, dw.toUInt())
        val xLen = normLen(x)
        val cmp = compare(x, xLen, dw)
        when {
            cmp < 0 -> return ZERO
            cmp == 0 -> return ONE
        }
        val u = x
        val m = xLen
        val vnDw = dw
        val q = Magia(m - 2 + 1)
        val r = null
        knuthDivide64(q, r, u, vnDw, m)
        return if (normLen(q) > 0) q else ZERO
    }

    /**
     * Divides the normalized limb array `x` by the normalized limb array `y` and
     * returns a new normalized quotient array.
     *
     * Handles all size relations:
     * - If `y` fits in one limb, delegates to the 32-bit divisor path.
     * - If `x < y`, returns [ZERO].
     * - If `x == y`, returns `[1]`.
     * - Otherwise performs full Knuth division on `x` by `y`.
     *
     * @return the normalized quotient, or [ZERO] if the quotient is zero.
     */
    fun newDiv(x: Magia, y: Magia): Magia {
        val xNormLen = normLen(x)
        val yNormLen = normLen(y)
        when {
            yNormLen < 2 -> return newDiv(x, y[0].toUInt())
            xNormLen < yNormLen -> return ZERO
            xNormLen == yNormLen -> {
                val xMswBitLen = bitLen(x[xNormLen-1])
                val yMswBitLen = bitLen(y[yNormLen-1])
                if (xMswBitLen < yMswBitLen)
                    return ZERO
                if (xMswBitLen == yMswBitLen) {
                    if (compare(x, xNormLen, y, yNormLen) < 0) ZERO else intArrayOf(1)
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
     * Divides the normalized limb array `x[0‥xNormLen)` by `y[0‥yNormLen)` and
     * writes the quotient into `z` (supports in-place use when `z === x` or
     * `z === y`).
     *
     * Handles all structured cases:
     * - If `y` fits in one limb, dispatches to the single-limb division routine.
     * - If `x < y`, the quotient is zero.
     * - If `x == y`, the quotient is 1.
     * - Otherwise performs full Knuth division.
     *
     * @return the normalized limb length of the quotient.
     * @throws ArithmeticException if `yNormLen == 0` (division by zero)
     * @throws IllegalArgumentException if `z` is too small to hold the quotient
     */
    fun setDiv(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        check (isNormalized(x, xNormLen))
        check (isNormalized(y, yNormLen))
        when {
            yNormLen == 0 -> throw ArithmeticException("div by zero")
            yNormLen == 1 -> return setDiv(z, x, xNormLen, y[0].toUInt())
            xNormLen < yNormLen -> return 0
            xNormLen == yNormLen -> {
                val xMswBitLen = bitLen(x[xNormLen - 1])
                val yMswBitLen = bitLen(y[yNormLen - 1])
                if (xMswBitLen < yMswBitLen)
                    return 0
                if (xMswBitLen == yMswBitLen) {
                    if (compare(x, xNormLen, y, yNormLen) < 0)
                        return 0
                    z[0] = 1
                    return 1
                }
            }
        }
        val m = xNormLen
        val n = yNormLen
        val u = x
        val v = y
        if (z.size < m - n + 1)
            throw IllegalArgumentException()
        val q = z
        val r = null
        val qNormLen = knuthDivide(q, r, u, v, m, n)
        return qNormLen
    }

    /**
     * Computes `x mod w` for the normalized range `x[0‥xNormLen)` and returns the
     * remainder as a new normalized single-limb array. Uses a fast path for values
     * fitting in one or two limbs, otherwise delegates to `calcRem`. Returns [ZERO]
     * if the remainder is zero.
     */
    fun newRem(x: Magia, xNormLen: Int, w: UInt): Magia {
        check (isNormalized(x, xNormLen))
        if (xNormLen > 0) {
            val rem =
                if (xNormLen <= 2)
                    (toRawULong(x, xNormLen) % w.toULong()).toUInt()
                else
                    calcRem(x, xNormLen, w)
            if (rem > 0u)
                return intArrayOf(rem.toInt())
        }
        return ZERO
    }

    /**
     * Computes `x mod w` for the normalized range `x[0‥xNormLen)` and stores the
     * remainder into `z[0]` if it is non-zero.
     *
     * Returns `1` if a non-zero remainder was written, or `0` if the remainder is
     * zero. Uses a fast path for 1–2 limb values, otherwise delegates to `calcRem`.
     */
    fun setRem(z: Magia, x: Magia, xNormLen: Int, w: UInt): Int {
        check (isNormalized(x, xNormLen))
        if (xNormLen > 0) {
            val rem =
                if (xNormLen <= 2)
                    (toRawULong(x, xNormLen) % w.toULong()).toUInt()
                else
                    calcRem(x, xNormLen, w)
            if (rem > 0u) {
                z[0] = rem.toInt()
                return 1
            }
        }
        return 0
    }

    /**
     * Computes `x mod dw` for the normalized range `x[0‥xNormLen)` where `dw` is a
     * 64-bit unsigned divisor.
     *
     * Uses a fast path when `dw` fits in 32 bits, another fast path when `x`
     * fits in one or two limbs, and otherwise falls back to the general
     * multi-limb remainder routine using the 2-limb array representation of `dw`.
     *
     * @return a new normalized array holding the remainder, or [ZERO] if the
     *         remainder is zero.
     */
    fun newRem(x: Magia, xNormLen: Int, dw: ULong): Magia {
        val lo = dw.toUInt()
        val hi = (dw shr 32).toUInt()
        if (hi == 0u)
            return newRem(x, xNormLen, lo)
        if (xNormLen <= 2)
            return newFromULong(toRawULong(x, xNormLen) % dw)
        return newRem(x, xNormLen, intArrayOf(lo.toInt(), hi.toInt()), 2)
    }

    /**
     * Returns a new normalized array holding `x mod y`.
     * Handles 1-limb divisors directly, otherwise delegates to `setRem`
     * and trims the result. Returns [ZERO] if the remainder is zero.
     */
    fun newRem(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
        check(isNormalized(y, yNormLen))
        if (yNormLen <= 1) {
            if (yNormLen == 0)
                throw ArithmeticException("div by zero")
            return newRem(x, xNormLen, y[0].toUInt())
        }
        val z = Magia(yNormLen)
        val zNormLen = setRem(z, x, xNormLen, y, yNormLen)
        return if (zNormLen == 0) ZERO else z.copyOf(zNormLen)
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
     * @throws ArithmeticException if `yNormLen == 0` (division by zero)
     * @throws IllegalArgumentException if `z` is too small to hold the remainder
     */
    fun setRem(z: Magia, x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Int {
        check (isNormalized(x, xNormLen))
        check (isNormalized(y, yNormLen))
        when {
            yNormLen == 0 -> throw ArithmeticException("div by zero")
            yNormLen == 1 -> return setRem(z, x, xNormLen, y[0].toUInt())
            yNormLen == 2 && xNormLen <= 2 ->
                return setULong(z, toRawULong(x, xNormLen) % toRawULong(y, yNormLen))
            xNormLen < yNormLen -> {
                x.copyInto(z, 0, 0, xNormLen)
                return xNormLen
            }
        }
        val n = yNormLen
        val m = xNormLen
        val u = x
        val v = y
        val q = null
        if (z.size < yNormLen)
            throw IllegalArgumentException()
        val r = z
        val rNormLen = knuthDivide(q, r, u, v, m, n)
        check (rNormLen == normLen(r))
        check (rNormLen <= n)
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
        n: Int
    ): Int {
        if (m < n || n < 2 || v[n - 1] == 0)
            throw IllegalArgumentException()

        // Step D1: Normalize
        val un = newCopyWithExactLimbLen(u, m + 1)
        val vn = newCopyWithExactLimbLen(v, n)
        val shift = vn[n - 1].countLeadingZeroBits()
        if (shift > 0) {
            setShiftLeft(vn, vn, n, shift)
            setShiftLeft(un, un, m, shift)
        }

        knuthDivideNormalizedCore(q, un, vn, m, n)

        var rNormLen = 0
        if (r != null)
            rNormLen = setShiftRight(r, un, normLen(un), shift)

        return when {
            q != null -> normLen(q, m-n+1)
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

            // estimate q̂ = (un[j+n]*B + un[j+n-1]) / vn[n-1]
            val hi = dw32(un[j + n])
            val lo = dw32(un[j + n - 1])
            //if (hi == 0L && lo < vn_1) // this would short-circuit,
            //    continue               // but probability is astronomically small
            val num = (hi shl 32) or lo
            var qhat = num / vn_1
            var rhat = num % vn_1

            // correct estimate
            while ((qhat shr 32) != 0uL ||
                qhat * vn_2 > (rhat shl 32) + dw32(un[j + n - 2])) {
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
                val prodLo = prod and 0xFFFF_FFFFuL
                val unIJ = dw32(un[j + i])
                val t = unIJ - prodLo - carry
                un[j + i] = t.toInt()
                carry = prodHi - (t.toLong() shr 32).toULong() // yes, this is a signed shift right
            }
            val t = dw32(un[j + n]) - carry
            un[j + n] = t.toInt()
            if (q != null)
                q[j] = (qhat - (t shr 63)).toInt()
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
     * @param q optional quotient array (length ≥ m − 1)
     * @param r optional remainder array (length ≥ m + 1)
     * @param u dividend limbs (least-significant limb first)
     * @param vDw 64-bit unsigned divisor (high 32 bits must be non-zero)
     * @param m number of significant limbs in `u` (≥ 2)
     * @throws IllegalArgumentException if `m < 2` or the high 32 bits of `vDw` are zero
     * @see knuthDivide
     */
    fun knuthDivide64(
        q: IntArray?,
        r: IntArray?,
        u: IntArray,
        vDw: ULong,
        m: Int,
    ) {
        if (m < 2 || (vDw shr 32) == 0uL)
            throw IllegalArgumentException()

        val v = intArrayOf(vDw.toInt(), (vDw shr 32).toInt())
        knuthDivide(q, r, u, v, m, 2)
    }

    /**
     * Converts the given unsigned integer magnitude [magia] to its decimal string form.
     *
     * Equivalent to calling [toString] with `isNegative = false`.
     *
     * @param magia the unsigned integer magnitude, least-significant word first.
     * @return the decimal string representation of [magia].
     */
    fun toString(magia: IntArray) = toString(isNegative = false, magia)

    /**
     * Converts a signed magnitude [magia] value into its decimal string representation.
     *
     * Performs a full base-10 conversion. Division and remainder operations
     * are done in chunks of one billion (1 000 000 000) to minimize costly
     * multi-precision divisions. Temporary heap allocation is used for an intermediate
     * quotient array, a temporary UTF-8 buffer, and the final [String] result.
     *
     * The algorithm:
     *  - Estimates the required digit count from [bitLen].
     *  - Copies [magia] into a temporary mutable array.
     *  - Repeatedly divides the number by 1e9 to extract 9-digit chunks.
     *  - Converts each chunk into ASCII digits using [render9DigitsBeforeIndex] and [renderTailDigitsBeforeIndex].
     *  - Prepends a leading ‘-’ if [isNegative] is true.
     *
     * @param isNegative whether to prefix the result with a minus sign.
     * @param magia the magnitude, least-significant word first.
     * @return the decimal string representation of the signed value.
     */
    fun toString(isNegative: Boolean, magia: Magia): String =
        toString(isNegative, magia, normLen(magia))

    /**
     * Converts a multi-limb integer to its decimal string representation.
     *
     * @param isNegative whether the resulting string should include a leading minus sign.
     * @param x the array of 32-bit limbs representing the integer (least-significant limb first).
     * @param xNormLen the number of significant limbs to consider from `x`.
     * @return the decimal string representation of the integer value.
     */
    fun toString(isNegative: Boolean, x: Magia, xNormLen: Int): String {
        if (xNormLen >= 0 && xNormLen <= x.size) {
            check (isNormalized(x, xNormLen))
            val bitLen = bitLen(x, xNormLen)
            if (bitLen < 2) {
                if (bitLen == 0)
                    return "0"
                return if (isNegative) "-1" else "1"
            }
            val maxSignedLen = maxDigitLenFromBitLen(bitLen) + if (isNegative) 1 else 0
            val utf8 = ByteArray(maxSignedLen)
            val limbLen = normLen(x, xNormLen)
            val t = newCopyWithExactLimbLen(x, limbLen)
            val len = destructiveToUtf8BeforeIndex(utf8, utf8.size, isNegative, t, limbLen)
            val startingIndex = utf8.size - len
            check (startingIndex <= 1)
            return utf8.decodeToString(startingIndex, utf8.size)
        } else {
            throw IllegalArgumentException()
        }
    }

    fun toString(x: Magia, xNormLen: Int) = toString(false, x, xNormLen)
    /**
     * Returns an upper bound on the number of decimal digits required to
     * represent a non-negative integer with the given bit length.
     *
     * For any positive integer `x`, the exact digit count is:
     *
     *     digits = floor(bitLen * log10(2)) + 1
     *
     * This function computes a tight conservative approximation using a
     * fixed-point 2**32 scaled constant that slightly exceeds `log10(2)`.
     * This function always produces a close safe upper bound on the number
     * of base-10 digits, never overestimating by more than 1 for values
     * with tens of thousands of digits
     *
     * @param bitLen the bit length of the integer (must be ≥ 0)
     * @return an upper bound on the required decimal digit count
     */
    inline fun maxDigitLenFromBitLen(bitLen: Int): Int {
        // LOG10_2_CEIL_SCALE_2_32  = 1292913987uL
        return (bitLen.toULong() * 1292913987uL shr 32).toInt() + 1
    }

    /**
     * Converts the big-integer value in `t` (length `tLen`) into decimal UTF-8 digits.
     *
     * Converts the big-integer value in `t` (length `tLen`) to decimal digits and
     * writes them into `utf8` **right-to-left**. ibMaxx is Max eXclusive, so
     * writing begins at index `ibMaxx - 1` and proceeds to the left.
     *
     * The array `t` is treated as a temporary work area and is **mutated in-place**
     * by repeated Barrett divisions by 1e9. Full 9-digit chunks are written with
     * `renderChunk9`, and the final limb is written with `renderChunkTail`.
     *
     * If `isNegative` is true, a leading '-' is inserted.
     *
     * @param utf8 the destination byte buffer where UTF-8 digits are written.
     * @param ibMaxx the exclusive upper index in `utf8`; writing starts at
     *               `ibMaxx - 1` and proceeds leftward.
     * @param isNegative whether a leading '-' should be inserted.
     * @param tmp a temporary big-integer buffer holding the magnitude; it is
     *            mutated in-place by repeated Barrett reduction divisions.
     * @param tmpLen the number of active limbs in `tmp`; must be ≥ 1, within bounds
     *               and normalized.
     *
     * @return the number of bytes written into `utf8`.
     */
    fun destructiveToUtf8BeforeIndex(utf8: ByteArray, ibMaxx: Int, isNegative: Boolean, tmp: Magia, tmpLen: Int): Int {
        if (tmpLen > 0 && tmpLen <= tmp.size && tmp[tmpLen - 1] != 0 &&
            ibMaxx > 0 && ibMaxx <= utf8.size) {
            var ib = ibMaxx
            var limbsRemaining = tmpLen
            while (limbsRemaining > 1) {
                val newLenAndRemainder = mutateBarrettDivBy1e9(tmp, limbsRemaining)
                val chunk = newLenAndRemainder and 0xFFFF_FFFFuL
                render9DigitsBeforeIndex(chunk, utf8, ib)
                limbsRemaining = (newLenAndRemainder shr 32).toInt()
                ib -= 9
            }
            ib -= renderTailDigitsBeforeIndex(tmp[0].toUInt(), utf8, ib)
            if (isNegative)
                utf8[--ib] = '-'.code.toByte()
            val len = utf8.size - ib
            return len
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Renders a single 32-bit unsigned integer [n] into its decimal digits
     * at the end of [utf8], starting from [offMaxx] and moving backward.
     *
     * Digits are emitted least-significant first and written backward into [utf8].
     * Uses reciprocal multiplication by `0xCCCCCCCD` (fixed-point reciprocal of 10)
     * to perform fast division and extract digits.
     *
     * If the value of [w] is zero then a zero digit is written.
     *
     * @param w the integer to render (interpreted as unsigned 32-bit).
     * @param utf8 the UTF-8 byte buffer to write digits into.
     * @param offMaxx the maximum exclusive offset within [utf8];
     *                digits are written backward from `offMaxx - 1`.
     * @return the number of bytes/digits written.
     */
    fun renderTailDigitsBeforeIndex(w: UInt, utf8: ByteArray, offMaxx: Int): Int {
        var t = w.toULong()
        var ib = offMaxx
        while (t >= 1000uL) {
            val t0 = unsignedMulHi(t, M_U64_DIV_1E4) shr S_U64_DIV_1E4
            val abcd = t - (t0 * 10000uL)
            t = t0
            val ab = (abcd * M_U32_DIV_1E2) shr S_U32_DIV_1E2
            val cd = abcd - (ab * 100uL)
            val a = (ab * M_U32_DIV_1E1) shr S_U32_DIV_1E1
            val b = ab - (a * 10uL)
            val c = (cd * M_U32_DIV_1E1) shr S_U32_DIV_1E1
            val d = cd - (c * 10uL)
            if (ib - 4 >= 0 && ib <= utf8.size) {
                utf8[ib - 4] = (a.toInt() + '0'.code).toByte()
                utf8[ib - 3] = (b.toInt() + '0'.code).toByte()
                utf8[ib - 2] = (c.toInt() + '0'.code).toByte()
                utf8[ib - 1] = (d.toInt() + '0'.code).toByte()
                ib -= 4
            } else {
                IllegalArgumentException()
            }
        }
        if (t != 0uL || w == 0u) {
            do {
                val divTen = (t * 0xCCCCCCCDuL) shr 35
                val digit = (t - (divTen * 10uL)).toInt()
                utf8[--ib] = ('0'.code + digit).toByte()
                t = divTen
            } while (t != 0uL)
        }

        return offMaxx - ib
    }

    /**
     * Renders a 9-digit chunk [dw] (0 ≤ [dw] < 1e9) into ASCII digits in [utf8],
     * ending just before [offMaxx].
     *
     * Digits are extracted using reciprocal-multiply division by powers
     * of 10 to avoid slow hardware division instructions.
     *
     * The layout written is:
     * ```
     * utf8[offMaxx - 9] .. utf8[offMaxx - 1] = '0'..'9'
     * ```
     *
     * @param dw the 9-digit unsigned long value to render ... `0..999999999`
     * @param utf8 the output byte buffer for ASCII digits.
     * @param offMaxx the maximum exclusive offset within [utf8];
     * digits occupy the range `offMaxx - 9 .. offMaxx - 1`.
     */
    fun render9DigitsBeforeIndex(dw: ULong, utf8: ByteArray, offMaxx: Int) {
        check (dw < 1_000_000_000uL)
        //val abcde = unsignedMulHi(dw, M_U64_DIV_1E4) shr S_U64_DIV_1E4
        val abcde = (dw * M_1E9_DIV_1E4) shr S_1E9_DIV_1E4
        val fghi  = dw - (abcde * 10000uL)

        val abc = (abcde * M_U32_DIV_1E2) shr S_U32_DIV_1E2
        val de = abcde - (abc * 100uL)

        val fg = (fghi * M_U32_DIV_1E2) shr S_U32_DIV_1E2
        val hi = fghi - (fg * 100uL)

        val a = (abc * M_U32_DIV_1E2) shr S_U32_DIV_1E2
        val bc = abc - (a * 100uL)

        val b = (bc * M_U32_DIV_1E1) shr S_U32_DIV_1E1
        val c = bc - (b * 10uL)

        val d = (de * M_U32_DIV_1E1) shr S_U32_DIV_1E1
        val e = de - (d * 10uL)

        val f = (fg * M_U32_DIV_1E1) shr S_U32_DIV_1E1
        val g = fg - (f * 10uL)

        val h = (hi * M_U32_DIV_1E1) shr S_U32_DIV_1E1
        val i = hi - (h * 10uL)

        // Explicit bounds check to enable elimination of individual checks
        val offMin = offMaxx - 9
        if (offMin >= 0 && offMaxx <= utf8.size) {
            utf8[offMaxx - 9] = (a.toInt() + '0'.code).toByte()
            utf8[offMaxx - 8] = (b.toInt() + '0'.code).toByte()
            utf8[offMaxx - 7] = (c.toInt() + '0'.code).toByte()
            utf8[offMaxx - 6] = (d.toInt() + '0'.code).toByte()
            utf8[offMaxx - 5] = (e.toInt() + '0'.code).toByte()
            utf8[offMaxx - 4] = (f.toInt() + '0'.code).toByte()
            utf8[offMaxx - 3] = (g.toInt() + '0'.code).toByte()
            utf8[offMaxx - 2] = (h.toInt() + '0'.code).toByte()
            utf8[offMaxx - 1] = (i.toInt() + '0'.code).toByte()
        } else {
            throw IndexOutOfBoundsException()
        }
    }

    /**
     * Performs an in-place Barrett division of a multi-limb integer (`magia`) by 1e9.
     *
     * Each limb of [magia] is a 32-bit unsigned value (stored in [Int]),
     * with the most significant limb at index `len - 1`.
     * The function replaces each limb with its quotient and returns both
     * the new effective length and the remainder.
     *
     * This version uses the **qHat + rHat staged Barrett method**:
     * 1. Compute an approximate quotient `qHat` using the precomputed Barrett reciprocal [BARRETT_MU_1E9].
     * 2. Compute the remainder `rHat = combined − qHat × 1e9`.
     * 3. Conditionally increment `qHat` (and subtract 1e9 from `rHat`) if `rHat ≥ 1e9`.
     *    This is a 0-or-1 correction; `qHat` never decreases.
     *
     * The remainder from each limb is propagated to the next iteration.
     *
     * After all limbs are processed, the function computes the new effective length
     * of [magia] (trimming the most significant zero limb, if present) without looping.
     *
     * The new len will usually be one less, but sometimes will be the same. The most
     * significant limb is always written ... meaning that it will be zero-ed out.
     *
     * @param magia the multi-limb integer to divide. Must have `magia[len - 1] != 0`.
     *              Each element represents 32 bits of the number.
     * @param len the number of limbs in [magia] to process.
     * @return a packed [ULong]:
     *   - upper 32 bits: new effective limb count after trimming
     *   - lower 32 bits: remainder of the division by 1e9
     *
     * **Note:** The correction is a 0-or-1 adjustment; `qHat` never decreases.
     * **Correctness:** Guarantees that after each limb, `0 ≤ rHat < 1e9`.
     */
    fun mutateBarrettDivBy1e9(magia: Magia, len: Int): ULong {
        var rem = 0uL
        check(magia[len - 1] != 0)
        for (i in len - 1 downTo 0) {
            val limb = magia[i].toUInt().toULong()
            val combined = (rem shl 32) or limb

            // approximate quotient using Barrett reciprocal
            var qHat = unsignedMulHi(combined, BARRETT_MU_1E9)

            // compute remainder
            var rHat = combined - qHat * ONE_E_9

            // 0-or-1 adjustment: increment qHat if remainder >= 1e9
            // use signed shr to propagate the sign bit
            // adjustMask will have value 0 or -1 (aka 0xFF...FF)
            // if (rHat < ONE_E_9) 0uL else -1uL
            val adjustMask = ((rHat - ONE_E_9).toLong() shr 63).toULong().inv()
            qHat -= adjustMask
            rHat -= ONE_E_9 and adjustMask

            magia[i] = qHat.toInt()
            rem = rHat
        }

        val mostSignificantLimbNonZero = (-magia[len - 1]) ushr 31 // 0 or 1
        val newLen = len - 1 + mostSignificantLimbNonZero

        // pack new length and remainder into a single Long
        return (newLen.toULong() shl 32) or (rem and 0xFFFF_FFFFuL)
    }

    /**
     * Converts the given magnitude array to a positive hexadecimal string representation.
     *
     * This is equivalent to calling [toHexString] with `isNegative = false`.
     * The returned string is prefixed with `"0x"`.
     *
     * @param magia the magnitude of the number, stored as an `Magia` of 32-bit limbs.
     * @return the hexadecimal string representation of the magnitude.
     */
    fun toHexString(magia: Magia) = toHexString(false, magia)

    /**
     * Converts the given magnitude array to a hexadecimal string representation.
     *
     * The resulting string is prefixed with `"0x"`, and a leading `'-'` sign is
     * included if [isNegative] is `true`. For example, a positive value might render
     * as `"0x1AF3"`, while a negative value would render as `"-0x1AF3"`.
     *
     * Each element of [x] represents a 32-bit limb of the unsigned magnitude,
     * with the least significant limb first (little-endian order).
     *
     * @param isNegative whether the number is negative.
     * @param x the magnitude of the number, stored as an `Magia` of 32-bit limbs.
     * @return the hexadecimal string representation, prefixed with `"0x"`.
     *
     * Example:
     * ```
     * toHexString(false, intArrayOf(0x89ABCDEFu.toInt(), 0x01234567)) == "0x123456789ABCDEF"
     * toHexString(true,  intArrayOf(0x00000001)) == "-0x1"
     * ```
     */
    fun toHexString(isNegative: Boolean, x: Magia): String =
        toHexString(isNegative, x, x.size)

    /**
     * Converts the magnitude [x] to a hexadecimal string.
     *
     * The limbs in [x] are stored in little-endian order (least-significant limb at index 0).
     * Only the first [xLen] limbs are used.
     *
     * The result is formatted in big-endian hex, prefixed with `"0x"`, and with a leading
     * `'-'` if [isNegative] is `true`.
     *
     * Examples:
     * ```
     * toHexString(false, intArrayOf(0x89ABCDEFu.toInt(), 0x01234567), 2)
     *     == "0x123456789ABCDEF"
     *
     * toHexString(true, intArrayOf(0x1), 1)
     *     == "-0x1"
     * ```
     */
    fun toHexString(isNegative: Boolean, x: Magia, xLen: Int): String {
        val bitLen = bitLen(x, xLen)
        var nybbleCount = (bitLen + 3) ushr 2
        val strLen = (if (isNegative) 1 else 0) + 2 + max(nybbleCount, 1)
        val bytes = ByteArray(strLen)
        bytes[0] = '-'.code.toByte()
        val n = if (isNegative) 1 else 0
        bytes[n + 0] = '0'.code.toByte()
        bytes[n + 1] = 'x'.code.toByte()
        bytes[n + 2] = '0'.code.toByte()
        var i = 0
        var j = bytes.size
        while (nybbleCount > 0) {
            var w = x[i++]
            val stepCount = min(8, nybbleCount)
            repeat(stepCount) {
                val nybble = w and 0x0F
                val ch = nybble + if (nybble < 10) '0'.code else 'A'.code - 10
                bytes[--j] = ch.toByte()
                w = w ushr 4
            }
            nybbleCount -= stepCount
        }
        return bytes.decodeToString()
    }

    fun toHexUtf8(x: Magia, utf8: ByteArray, off: Int, digitCount: Int, useUpperCase: Boolean) =
        toHexUtf8(x, normLen(x), utf8, off, digitCount, useUpperCase)

    fun toHexUtf8(x: Magia, xNormLen: Int, utf8: ByteArray, off: Int, digitCount: Int, useUpperCase: Boolean) {
        check (isNormalized(x, xNormLen))
        val alfaBase = if (useUpperCase) 'A' else 'a'
        var ichMax = off + digitCount
        var limbIndex = 0
        var nybblesRemaining = 0
        var w = 0
        while (ichMax > off) {
            if (nybblesRemaining == 0) {
                if (limbIndex == xNormLen) {
                    // if there are no limbs left then take as
                    // many zero nybbles as you want
                    nybblesRemaining = Int.MAX_VALUE
                    check (w == 0)
                } else {
                    w = x[limbIndex]
                    ++limbIndex
                    nybblesRemaining = 8
                }
            }
            val nybble = w and 0x0F
            --nybblesRemaining
            w = w ushr 4
            val ch = if (nybble < 10) '0' + nybble else alfaBase + (nybble - 10)
            --ichMax
            utf8[ichMax] = ch.code.toByte()
        }
    }

    /**
     * Factory methods for constructing a numeric value from ASCII/Latin-1/UTF-8 encoded input.
     *
     * Each overload accepts a different input source — `String`, `CharSequence`, `CharArray`,
     * or `ByteArray` — and creates a new instance by parsing its contents as an unsigned
     * or signed decimal number (depending on the implementation of `from`).
     *
     * For efficiency, these overloads avoid intermediate string conversions by using
     * specialized iterator types that stream the input data directly.
     *
     * @receiver none
     * @param str the source string to parse.
     * @param csq the character sequence to parse.
     * @param chars the character array to parse.
     * @param bytes the ASCII byte array to parse.
     * @param off the starting offset of the input segment (inclusive).
     * @param len the number of characters or bytes to read from the input.
     * @return a parsed numeric value represented internally by this class.
     *
     * @see StringLatin1Iterator
     * @see CharSequenceLatin1Iterator
     * @see CharArrayLatin1Iterator
     * @see ByteArrayLatin1Iterator
     */
    fun from(str: String) = from(StringLatin1Iterator(str))
    fun from(str: String, off: Int, len: Int) = from(StringLatin1Iterator(str, off, len))
    fun from(csq: CharSequence) = from(CharSequenceLatin1Iterator(csq))
    fun from(csq: CharSequence, off: Int, len: Int) =
        from(CharSequenceLatin1Iterator(csq, off, len))

    fun from(chars: CharArray) = from(CharArrayLatin1Iterator(chars))
    fun from(chars: CharArray, off: Int, len: Int) =
        from(CharArrayLatin1Iterator(chars, off, len))

    fun fromAscii(bytes: ByteArray) = from(ByteArrayLatin1Iterator(bytes))
    fun fromAscii(bytes: ByteArray, off: Int, len: Int) =
        from(ByteArrayLatin1Iterator(bytes, off, len))


    /**
     * Factory methods for constructing a numeric value from a hexadecimal representation
     * in Latin-1 (ASCII) encoded input.
     *
     * Each overload accepts a different input source — `String`, `CharSequence`,
     * `CharArray`, or `ByteArray` — and parses its contents as a hexadecimal number.
     * The input may include digits '0'–'9' and letters 'A'–'F' or 'a'–'f'.
     *
     * These overloads use specialized iterator types to stream input efficiently,
     * avoiding intermediate string allocations.
     *
     * @receiver none
     * @param str the source string containing hexadecimal digits.
     * @param csq the character sequence containing hexadecimal digits.
     * @param chars the character array containing hexadecimal digits.
     * @param bytes the ASCII byte array containing hexadecimal digits.
     * @param off the starting offset of the input segment (inclusive).
     * @param len the number of characters or bytes to read from the input.
     * @return a numeric value parsed from the hexadecimal input.
     *
     * @see StringLatin1Iterator
     * @see CharSequenceLatin1Iterator
     * @see CharArrayLatin1Iterator
     * @see ByteArrayLatin1Iterator
     */
    fun fromHex(str: String) = fromHex(StringLatin1Iterator(str, 0, str.length))
    fun fromHex(str: String, off: Int, len: Int) = fromHex(StringLatin1Iterator(str, off, len))
    fun fromHex(csq: CharSequence) = fromHex(CharSequenceLatin1Iterator(csq, 0, csq.length))
    fun fromHex(csq: CharSequence, off: Int, len: Int) =
        fromHex(CharSequenceLatin1Iterator(csq, off, len))

    fun fromHex(chars: CharArray) = fromHex(CharArrayLatin1Iterator(chars, 0, chars.size))
    fun fromHex(chars: CharArray, off: Int, len: Int) =
        fromHex(CharArrayLatin1Iterator(chars, off, len))
    fun fromAsciiHex(bytes: ByteArray) =
        fromHex(ByteArrayLatin1Iterator(bytes, 0, bytes.size))
    fun fromAsciiHex(bytes: ByteArray, off: Int, len: Int) =
        fromHex(ByteArrayLatin1Iterator(bytes, off, len))

    /**
     * Determines whether a character is valid in a textual hexadecimal representation.
     *
     * Valid characters include:
     * - Digits '0'–'9'
     * - Letters 'A'–'F' and 'a'–'f'
     * - Underscore '_'
     *
     * Underscores are commonly allowed as digit separators in numeric literals.
     *
     * This function uses a bitmask to efficiently check if the character is one
     * of the allowed hexadecimal characters or an underscore.
     *
     * @param c the character to test
     * @return `true` if [c] is a valid hexadecimal digit or underscore, `false` otherwise
     */
    private inline fun isHexAsciiCharOrUnderscore(c: Char): Boolean {
        // if a bit is turned on, then it is a valid char in
        // hex representation.
        // this means [0-9A-Fa-f_]
        val hexDigitAndUnderscoreMask = 0x007E_8000_007E_03FFL
        val idx = c.code - '0'.code
        return (idx >= 0) and (idx <= 'f'.code - '0'.code) and
                (((hexDigitAndUnderscoreMask ushr idx) and 1L) != 0L)
    }

    /**
     * Constructs a [Magus] magnitude from a sequence of raw binary bytes.
     *
     * The input bytes represent a non-negative magnitude if [isNegative] is `false`,
     * or a two’s-complement negative number if [isNegative] is `true`. In the latter case,
     * the bytes are complemented and incremented during decoding to produce the corresponding
     * positive magnitude. The sign itself is handled by the caller.
     *
     * The bytes may be in either big-endian or little-endian order, as indicated by [isBigEndian].
     *
     * @param isNegative  `true` if bytes encode a negative value in two’s-complement form.
     * @param isBigEndian `true` if bytes are in big-endian order, `false` for little-endian.
     * @param bytes       Source byte array.
     * @param off         Starting offset in [bytes].
     * @param len         Number of bytes to read.
     * @return A [Magus] magnitude as an [Magia].
     * @throws IllegalArgumentException if the range `[off, off + len)` is invalid.
     */
    internal fun fromBinaryBytes(isNegative: Boolean, isBigEndian: Boolean,
                                 bytes: ByteArray, off: Int, len: Int): Magia {
        if (off < 0 || len < 0 || len > bytes.size - off)
            throw IllegalArgumentException()
        if (len == 0)
            return ZERO

        // calculate offsets and stepping direction for BE BigEndian vs LE LittleEndian
        val offB1 = if (isBigEndian) -1 else 1 // BE == -1, LE ==  1
        val offB2 = offB1 shl 1                // BE == -2, LE ==  2
        val offB3 = offB1 + offB2              // BE == -3, LE ==  3
        val step1HiToLo = - offB1              // BE ==  1, LE == -1
        val step4LoToHi = offB1 shl 2          // BE == -4, LE ==  4

        val ibLast = off + len - 1
        val ibLsb = if (isBigEndian) ibLast else off // index Least significant byte
        var ibMsb = if (isBigEndian) off else ibLast // index Most significant byte

        val negativeMask = if (isNegative) -1 else 0

        // Leading sign-extension bytes (0x00 for non-negative, 0xFF for negative) are flushed
        // If all bytes are flush bytes, the result is [ZERO] or [ONE], depending on [isNegative].
        val leadingFlushByte = negativeMask
        var remaining = len
        while (bytes[ibMsb].toInt() == leadingFlushByte) {
            ibMsb += step1HiToLo
            --remaining
            if (remaining == 0)
                return if (isNegative) ONE else ZERO
        }

        val magia = Magia((remaining + 3) ushr 2)

        var ib = ibLsb
        var iw = 0

        var carry = -negativeMask.toLong() // if (isNegative) then carry = 1 else 0

        while (remaining >= 4) {
            val b3 = bytes[ib + offB3].toInt() and 0xFF
            val b2 = bytes[ib + offB2].toInt() and 0xFF
            val b1 = bytes[ib + offB1].toInt() and 0xFF
            val b0 = bytes[ib        ].toInt() and 0xFF
            val w = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            carry += (w xor negativeMask).toLong() and 0xFFFF_FFFFL
            magia[iw++] = carry.toInt()
            carry = carry shr 32
            check ((carry shr 1) == 0L)
            ib += step4LoToHi
            remaining -= 4
        }
        if (remaining > 0) {
            val b3 = negativeMask and 0xFF
            val b2 = (if (remaining == 3) bytes[ib + offB2].toInt() else negativeMask) and 0xFF
            val b1 = (if (remaining >= 2) bytes[ib + offB1].toInt() else negativeMask) and 0xFF
            val b0 = bytes[ib].toInt() and 0xFF
            val w = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            magia[iw++] = (w xor negativeMask) + carry.toInt()
        }
        check(iw == magia.size)
        return magia
    }

    /**
     * Converts an arbitrary-precision integer into a binary representation as a [ByteArray].
     *
     * The integer is represented by an array of 32-bit limbs, optionally in two's-complement form.
     *
     * @param sign `true` if the number is negative, `false` otherwise.
     * @param x the array of 32-bit limbs representing the integer, least-significant limb first.
     * @param xNormLen the number of significant limbs to consider; must be normalized (trailing zeros ignored).
     * @param isTwosComplement if `true`, the number is converted to two's-complement form.
     *                        Otherwise, magnitude-only representation is used.
     * @param isBigEndian if `true`, the most significant byte is first in the output array;
     *                    if `false`, least significant byte is first.
     * @return a [ByteArray] containing the binary representation of the integer.
     * @throws IllegalArgumentException if [xNormLen] is out of bounds (negative or greater than x.size).
     */
    fun toBinaryByteArray(sign: Boolean, x: Magia, xNormLen: Int, isTwosComplement: Boolean, isBigEndian: Boolean): ByteArray {
        check (isNormalized(x, xNormLen))
        if (xNormLen >= 0 && xNormLen <= x.size) {
            val bitLen =
                if (isTwosComplement) bitLengthBigIntegerStyle(sign, x, xNormLen) + 1 else max(bitLen(x, xNormLen), 1)
            val byteLen = (bitLen + 7) ushr 3
            val bytes = ByteArray(byteLen)
            toBinaryBytes(x, xNormLen, sign and isTwosComplement, isBigEndian, bytes, 0, byteLen)
            return bytes
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Converts an arbitrary-precision integer into a binary representation within a [ByteArray],
     * automatically considering only the significant limbs (ignoring trailing zero limbs).
     *
     * This is a convenience wrapper around [toBinaryBytes] that computes [xLen] via [normLen].
     *
     * @param x the array of 32-bit limbs representing the integer, least-significant limb first.
     * @param isNegative whether the integer should be treated as negative (for two's-complement output).
     * @param isBigEndian if `true`, the most significant byte is stored first; if `false`, the least significant byte is first.
     * @param bytes the destination byte array.
     * @param off the starting offset in [bytes] where output begins.
     * @param requestedLen the number of bytes to write; if non-positive, the minimal number of bytes
     *                     required to represent the value is used.
     * @return the actual number of bytes written.
     * @throws IllegalArgumentException if [off] or [requestedLen] exceed array bounds.
     */
    internal fun toBinaryBytes(x: Magia, isNegative: Boolean, isBigEndian: Boolean,
                               bytes: ByteArray, off: Int, requestedLen: Int): Int =
        toBinaryBytes(x, normLen(x), isNegative, isBigEndian, bytes, off, requestedLen)

    /**
     * Converts an arbitrary-precision integer into a binary representation within a given [ByteArray].
     *
     * The integer is represented by an array of 32-bit limbs. This function writes the
     * binary bytes into the provided [bytes] array starting at offset [off], up to [requestedLen] bytes.
     * It supports both big-endian and little-endian byte ordering, as well as two's-complement
     * representation for negative numbers.
     *
     * @param x the array of 32-bit limbs representing the integer, least-significant limb first.
     * @param xNormLen the number of significant limbs in [x] to process.
     * @param isNegative whether the integer should be treated as negative (for two's-complement output).
     * @param isBigEndian if `true`, the most significant byte is stored first; if `false`, the least significant byte is first.
     * @param bytes the destination byte array.
     * @param off the starting offset in [bytes] where output begins.
     * @param requestedLen the number of bytes to write; if non-positive, the minimal number of bytes
     *                     required to represent the value is used.
     * @return the actual number of bytes written.
     * @throws IllegalArgumentException if [xNormLen] or [off] is out of bounds, or if [requestedLen] exceeds the available space.
     *
     * @implNote This function manually handles byte ordering and sign extension to allow
     * efficient serialization of large integers without additional temporary arrays.
     */
    internal fun toBinaryBytes(x: Magia, xNormLen: Int, isNegative: Boolean, isBigEndian: Boolean,
                               bytes: ByteArray, off: Int, requestedLen: Int): Int {
        check (isNormalized(x, xNormLen))
        if (xNormLen >= 0 && xNormLen <= x.size &&
            off >= 0 && (requestedLen <= 0 || requestedLen <= bytes.size - off)) {

            val actualLen = if (requestedLen > 0) requestedLen else {
                val bitLen = if (isNegative)
                    bitLengthBigIntegerStyle(isNegative, x, xNormLen) + 1
                else
                    max(bitLen(x, xNormLen), 1)
                (bitLen + 7) ushr 3
            }

            // calculate offsets and stepping direction for BE BigEndian vs LE LittleEndian
            val offB1 = if (isBigEndian) -1 else 1 // BE == -1, LE ==  1
            val offB2 = offB1 shl 1                // BE == -2, LE ==  2
            val offB3 = offB1 + offB2              // BE == -3, LE ==  3
            val step1LoToHi = offB1                // BE == -1, LE ==  1
            val step4LoToHi = offB1 shl 2          // BE == -4, LE ==  4

            val ibLast = off + actualLen - 1
            val ibLsb = if (isBigEndian) ibLast else off // index Least significant byte
            val ibMsb = if (isBigEndian) off else ibLast // index Most significant byte

            val negativeMask = if (isNegative) -1 else 0

            var remaining = actualLen

            var ib = ibLsb
            var iw = 0

            var carry = -negativeMask.toLong() // if (isNegative) then carry = 1 else 0

            while (remaining >= 4 && iw < xNormLen) {
                val v = x[iw++]
                carry += (v xor negativeMask).toLong() and 0xFFFF_FFFFL
                val w = carry.toInt()
                carry = carry shr 32

                val b3 = (w shr 24).toByte()
                val b2 = (w shr 16).toByte()
                val b1 = (w shr 8).toByte()
                val b0 = (w).toByte()

                bytes[ib + offB3] = b3
                bytes[ib + offB2] = b2
                bytes[ib + offB1] = b1
                bytes[ib] = b0

                ib += step4LoToHi
                remaining -= 4
            }
            if (remaining > 0) {
                val v = if (iw < xNormLen) x[iw++] else 0
                var w = (v xor negativeMask).toLong() + carry.toInt()
                do {
                    bytes[ib] = w.toByte()
                    ib += step1LoToHi
                    w = w shr 8
                } while (--remaining > 0)
            }
            check(iw == xNormLen || x[iw] == 0)
            check(ib - step1LoToHi == ibMsb)
            return actualLen
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Parses an unsigned decimal integer from a [Latin1Iterator] into a new [Magia] representing its magnitude.
     *
     * This layer ignores any optional leading sign characters ('+' or '-') and processes only the magnitude.
     * Leading zeros and underscores ('_') are handled according to numeric literal conventions:
     * - Leading zeros are skipped.
     * - Underscores are ignored between digits.
     * - Hexadecimal input prefixed with "0x" or "0X" is delegated to [fromHex].
     *
     * The function accumulates decimal digits in blocks of 9 for efficiency, using
     * [mutateFmaPow10] to multiply and add into the resulting array.
     *
     * @param src the input iterator providing characters in Latin-1 encoding.
     * @return a new [Magia] representing the magnitude of the parsed integer.
     * @throws IllegalArgumentException if the input does not contain a valid decimal integer.
     */
    fun from(src: Latin1Iterator): Magia {
        invalid_syntax@
        do {
            var leadingZeroSeen = false
            var ch = src.nextChar()
            if (ch == '-' || ch == '+') // discard leading sign
                ch = src.nextChar()
            if (ch == '0') { // discard leading zero
                ch = src.nextChar()
                if (ch == 'x' || ch == 'X')
                    return fromHex(src.reset())
                leadingZeroSeen = true
            }
            while (ch == '0' || ch == '_') {
                if (ch == '_' && !leadingZeroSeen)
                    break@invalid_syntax
                leadingZeroSeen = leadingZeroSeen or (ch == '0')
                ch = src.nextChar() // discard all leading zeros
            }
            var accumulator = 0u
            var accumulatorDigitCount = 0
            val remainingLen = src.remainingLen() + if (ch == '\u0000') 0 else 1
            // val bitLen = (remainingLen * 13607 + 4095) ushr 12
            val roundUp32 = (1uL shl 32) - 1uL
            val bitLen =
                ((remainingLen.toULong() * LOG2_10_CEIL_32 + roundUp32) shr 32).toInt()
            if (bitLen == 0) {
                if (leadingZeroSeen)
                    return ZERO
                break@invalid_syntax
            }
            val z = newWithBitLen(bitLen)

            src.prevChar() // back up one
            var chLast = '\u0000'
            while (true) {
                chLast = ch
                ch = src.nextChar()
                if (ch == '_')
                    continue
                if (ch !in '0'..'9')
                    break
                val n = ch - '0'
                accumulator = accumulator * 10u + n.toUInt()
                ++accumulatorDigitCount
                if (accumulatorDigitCount < 9)
                    continue
                mutateFmaPow10(z, 9, accumulator)
                accumulator = 0u
                accumulatorDigitCount = 0
            }
            if (ch == '\u0000' && chLast != '_') {
                if (accumulatorDigitCount > 0)
                    mutateFmaPow10(z, accumulatorDigitCount, accumulator)
                return z
            }
        } while (false)
        throw IllegalArgumentException("integer parse error:$src")
    }

    internal fun fromHex(src: Latin1Iterator): Magia {
        invalid_syntax@
        do {
            var leadingZeroSeen = false
            var ch = src.nextChar()
            if (ch == '+' || ch == '-')
                ch = src.nextChar()
            if (ch == '0') {
                ch = src.nextChar()
                if (ch == 'x' || ch == 'X')
                    ch = src.nextChar()
                else
                    leadingZeroSeen = true
            }
            while (ch == '0' || ch == '_') {
                if (ch == '_' && !leadingZeroSeen)
                    break@invalid_syntax
                leadingZeroSeen = leadingZeroSeen or (ch == '0')
                ch = src.nextChar()
            }
            if (ch != '\u0000')
                src.prevChar() // back up one
            var nybbleCount = 0
            while (src.hasNext()) {
                ch = src.nextChar()
                if (!isHexAsciiCharOrUnderscore(ch))
                    break@invalid_syntax
                nybbleCount += if (ch == '_') 0 else 1
            }
            if (ch == '_') // last char seen was '_'
                break@invalid_syntax
            if (nybbleCount == 0) {
                if (leadingZeroSeen)
                    return ZERO
                break@invalid_syntax
            }
            val z = newWithBitLen(nybbleCount shl 2)
            var nybblesLeft = nybbleCount
            for (k in 0..<z.size) {
                var w = 0
                val stepCount = min(nybblesLeft, 8)
                repeat(stepCount) { n ->
                    var ch: Char
                    do {
                        ch = src.prevChar()
                    } while (ch == '_')
                    val nybble = when (ch) {
                        in '0'..'9' -> ch - '0'
                        in 'A'..'F' -> ch - 'A' + 10
                        in 'a'..'f' -> ch - 'a' + 10
                        else -> throw IllegalStateException()
                    }
                    w = w or (nybble shl (n shl 2))
                }
                z[k] = w // compiler knows 0 <= k < zLen <= z.size, bounds check can be eliminated
                nybblesLeft -= stepCount
            }
            return z
        } while (false)
        throw IllegalArgumentException("integer parse error:$src")
    }

    /**
     * Returns the count of trailing zero *bits* in the lower [xLen] limbs
     * of [x], or `-1` if all those limbs are zero.
     *
     * Limbs are interpreted in little-endian order:
     * `x[0]` contains the least-significant 32 bits.
     *
     * The result is computed by scanning limbs `0 ..< xLen` until a non-zero
     * limb is encountered. If a non-zero limb `x[i]` is found, the return
     * value is:
     *
     *     (i * 32) + countTrailingZeroBits(x[i])
     *
     * If all examined limbs are zero, this method returns `-1`.
     *
     * @param x the magnitude array in little-endian limb order
     * @param xLen the number of low limbs to inspect; must satisfy
     *             `0 <= xLen <= x.size`
     * @return the count of trailing zero bits, or `-1` if the inspected
     *         region is entirely zero
     * @throws IllegalArgumentException if [xLen] is out of bounds
     */
    internal fun ctz(x: Magia, xLen: Int): Int {
        if (xLen >= 0 && xLen <= x.size) {
            for (i in 0..<xLen) {
                if (x[i] != 0)
                    return (i shl 5) + x[i].countTrailingZeroBits()
            }
            return -1
        }
        throw IllegalArgumentException()
    }


    /**
     * Returns the number of trailing zero bits in the given arbitrary-precision integer,
     * represented as an array of 32-bit limbs.
     *
     * A "trailing zero bit" is a zero bit in the least significant position of the number.
     *
     * The count is computed starting from the least significant limb (index 0).
     * If the entire number is zero, -1 is returned.
     *
     * @param magia the integer array (least significant limb first)
     * @return the number of trailing zero bits, or -1 if all limbs are zero
     */
    fun bitPopulationCount(x: Magia, xNormLen: Int): Int {
        if (xNormLen >= 0 && xNormLen <= x.size) {
            var popCount = 0
            for (i in 0..<xNormLen)
                popCount += x[i].countOneBits()
            return popCount
        }
        throw IllegalArgumentException()
    }

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
    fun normalizedHashCode(x: Magia): Int {
        val xNormLen = normLen(x)
        var h = 0
        var i = 0
        while (i + 3 < xNormLen) {
            h = 31 * 31 * 31 * 31 * h +
                    31 * 31 * 31 * x[i] +
                    31 * 31 * x[i + 1] +
                    31 * x[i + 2] +
                    x[i + 3]
            i += 4
        }
        while (i < xNormLen) {
            h = 31 * h + x[i]
            ++i
        }
        return h
    }

    fun gcd(x: Magia, xNormLen: Int, y: Magia, yNormLen: Int): Magia {
        var u = newCopyWithExactLimbLen(x, xNormLen)
        var v = newCopyWithExactLimbLen(y, yNormLen)

        var uNormLen = u.size
        var vNormLen = v.size
        if (uNormLen <= 0 || vNormLen <= 0)
            throw IllegalArgumentException()

        val ntzU = ctz(u, uNormLen)
        val ntzV = ctz(v, vNormLen)
        val initialShift = min(ntzU, ntzV)
        if (ntzU > 0)
            uNormLen = setShiftRight(u, u, uNormLen, ntzU)
        if (ntzV > 0)
            vNormLen = setShiftRight(v, v, vNormLen, ntzV)

        // Now both u and v are odd
        while (vNormLen != 0) {
            // Remove factors of 2 from v
            val tz = ctz(v, vNormLen)
            if (tz > 0)
                vNormLen = setShiftRight(v, v, vNormLen, tz)
            // Ensure u <= v
            val cmp = compare(u, uNormLen, v, vNormLen)
            if (cmp > 0) {
                // swap pointers and lengths
                val tmpA = u; u = v; v = tmpA
                val tmpL = uNormLen; uNormLen = vNormLen; vNormLen = tmpL
            }
            // v = v - u
            //mutateSub(v, vLen, u, uLen)
            //vLen = normLen(v, vLen)
            vNormLen = setSub(v, v, vNormLen, u, uNormLen)
        }
        // Final result = u * 2^shift
        if (initialShift > 0)
            uNormLen = setShiftLeft(u, u, uNormLen, initialShift)

        return newCopyWithExactLimbLen(u, uNormLen)
    }

}
