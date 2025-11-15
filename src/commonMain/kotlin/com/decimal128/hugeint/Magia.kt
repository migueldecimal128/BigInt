@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.hugeint

import kotlin.math.min
import kotlin.math.max


// magia == MAGnitude IntArray ... it's magic!

private const val BARRETT_MU_1E9: ULong = 0x44B82FA09uL       // floor(2^64 / 1e9)
private const val ONE_E_9: ULong = 1_000_000_000uL

private const val M_U32_DIV_1E1 = 0xCCCCCCCDuL
private const val S_U32_DIV_1E1 = 35

private const val M_U32_DIV_1E2 = 0x51EB851FuL
private const val S_U32_DIV_1E2 = 37

private const val M_U64_DIV_1E4 = 0x346DC5D63886594BuL
private const val S_U64_DIV_1E4 = 11 // + 64 high

/**
 * Provides low-level support for arbitrary-precision unsigned integer arithmetic.
 *
 * Unsigned magnitudes are represented in **little-endian** format, using 32-bit unsigned
 * limbs stored within an [IntArray].
 *
 * Multi-limb values are stored in [IntArray] even though the individual limbs are
 * unsigned. This stems from the lack of unsigned types in the JVM spec. Kotlin
 * [UIntArray] is actually a wrapper around [IntArray] and is inappropriate for this
 * application. Kotlin unsigned types [UInt] and [ULong] are used when primitive
 * integer types are passed around.
  *
 * ### Design Overview
 * - `new` functions return **immutable** [IntArray]s and are typically used by [HugeInt].
 * - `mutate` functions modify the supplied destination [IntArray] in place and are used
 *   by [HugeIntAccumulator].
 *
 * ### Available Functionality
 * - Magia is effectively a complete arbitrary-length integer ALU Arithmetic Logic Unit
 * - **Arithmetic operations:** `add`, `sub`, `mul`, `div`, `rem`, `sqr`
 * - **Standard bitwise operations:** `and`, `or`, `xor`, `shl`, `shr`
 * - **Advanced bit manipulation:** `bitLen`, `nlz`, `bitPopulation`, `testBit`, `setBit`,
 *   and bit-mask creation routines
 * - **Parsing:** Supports text input from `String`, `CharSequence`, `CharArray`, and
 *   UTF-8/ASCII encoded `ByteArray` representations
 * - **Conversion:** To `String` and UTF-8/ASCII encoded `ByteArray`
 * - **Serialization:** To and from little- or big-endian, two’s complement or unsigned
 *   formats
 *
 * Be advised that these functions are intended for a high-performance library. The caller
 * generally needs a deep understanding of low-level bit manipulation techniques. The goal
 * of high performance often leads to bit-twiddling in an effort to have blocks of branchless
 * code with a high level of instruction-level-parallelism. This adds significantly to the
 * complexity.
 *
 * This object forms the computational core for higher-level numeric abstractions such as [HugeInt]
 * and [HugeIntAccumulator].
 */
object Magia {

    /**
     * The one true zero-length array that is usually used to represent
     * the value ZERO.
     */
    val ZERO = IntArray(0)

    /**
     * We occasionally need the value ONE.
     *
     * **WARNING** do NOT mutate this.
     */
    val ONE = intArrayOf(1)

    private inline fun U32(n: Int) = n.toLong() and 0xFFFF_FFFFL
    private inline fun dw32(n: Int) = n.toUInt().toULong()

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
    fun toRawUInt(x: IntArray): UInt = if (x.size == 0) 0u else x[0].toUInt()

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
    fun toRawULong(x: IntArray): ULong {
        return when (x.size) {
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
    fun newFromULong(dw: ULong): IntArray {
        return when {
            (dw shr 32) != 0uL -> intArrayOf(dw.toInt(), (dw shr 32).toInt())
            dw != 0uL -> intArrayOf(dw.toInt())
            else -> ZERO
        }
    }

    /**
     * Returns the number of nonzero limbs in [x], excluding any leading zeros.
     */
    inline fun nonZeroLimbLen(x: IntArray): Int {
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
    fun nonZeroLimbLen(x: IntArray, xLen: Int): Int {
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
     * Creates a new limb array large enough to hold a value of [bitLen] bits.
     *
     * Returns [ZERO] if [bitLen] ≤ 0.
     */
    inline fun newWithBitLen(bitLen: Int) =
        if (bitLen > 0) IntArray(limbLenFromBitLen(bitLen)) else ZERO

    /**
     * Creates a new limb array with at least [floorLen] elements.
     *
     * This will never allocate less than 4 elements
     * Mutable routines use this to utilize all allocated storage,
     * rounding up to a 4 Int 16 byte boundary.
     */
    inline fun newWithFloorLen(floorLen: Int) : IntArray {
        val t = if (floorLen <= 0) 1 else floorLen
        val allocSize = (t + 3) and 3.inv()
        return IntArray(allocSize)
    }

    /**
     * Returns a normalized copy of of [x] with any leading
     * zero limbs removed.
     *
     * If all limbs are zero, returns [ZERO].
     */
    inline fun newCopyTrimmed(x: IntArray): IntArray = newCopyTrimmed(x, x.size)

    /**
     * Returns a normalized copy of the first [xLen] limbs of [src],
     * with any leading zero limbs removed.
     *
     * If all limbs are zero, returns [ZERO].
     *
     * @throws IllegalArgumentException if [xLen] is out of range for [src].
     */
    fun newCopyTrimmed(src: IntArray, xLen: Int): IntArray {
        if (xLen >= 0 && xLen <= src.size) {
            var lastIndex = xLen - 1
            while (lastIndex >= 0 && src[lastIndex] == 0)
                --lastIndex
            if (lastIndex < 0)
                return ZERO
            val limbLen = lastIndex + 1
            val z = IntArray(limbLen)
            //System.arraycopy(src, 0, z, 0, limbLen)
            src.copyInto(z, 0, 0, limbLen)
            return z
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a copy of [src] extended to at least [floorLen] elements.
     *
     * The new array preserves the contents of [src] and zero-fills the remainder.
     *
     * @throws IllegalArgumentException if [floorLen] is not greater than [src.size].
     */
    fun newCopyWithFloorLen(src: IntArray, floorLen: Int) : IntArray {
        if (floorLen > src.size) {
            val z = newWithFloorLen(floorLen)
            //System.arraycopy(src, 0, z, 0, min(src.size, z.size))
            src.copyInto(z, 0, 0, min(src.size, z.size))
            return z
        } else {
            throw IllegalArgumentException()
        }
    }

    fun newCopyWithExactLen(src: IntArray, exactLimbLen: Int): IntArray {
        if (exactLimbLen > 0) {
            val dst = IntArray(exactLimbLen)
            //System.arraycopy(src, 0, dst, 0, min(src.size, dst.size))
            src.copyInto(dst, 0, 0, min(src.size, dst.size))
            return dst
        }
        return ZERO
    }

    private fun newCopyWithBitLen(src: IntArray, newBitLen: Int): IntArray {
        val dst = newWithBitLen(newBitLen)
        //copy(dst, src)
        src.copyInto(dst, 0, 0, min(src.size, dst.size))
        return dst
    }

    /**
     * Returns a new limb array representing [x] plus the unsigned 32-bit value [w].
     *
     * The result is sized to accommodate any carry from the addition.
     */
    fun newAdd(x: IntArray, w: UInt): IntArray {
        val newBitLen = max(bitLen(x), (32 - w.countLeadingZeroBits())) + 1
        val z = newWithBitLen(newBitLen)
        var carry = w.toULong()
        val indexLimit = min(x.size, z.size)
        for (i in 0..<indexLimit) {
            val t = dw32(x[i]) + carry
            z[i] = t.toInt()
            carry = t shr 32
        }
        if (carry != 0uL)
            z[z.lastIndex] = 1
        return z
    }

    /**
     * Returns a new limb array representing [x] plus the signed 64-bit value [dw].
     *
     * The result is extended as needed to hold any carry or sign extension from the addition.
     */
    fun newAdd(x: IntArray, dw: Long): IntArray {
        val newBitLen = max(bitLen(x), (64 - dw.countLeadingZeroBits())) + 1
        val z = newWithBitLen(newBitLen)
        var carry = dw
        val indexLimit = min(x.size, z.size)
        for (i in 0..<indexLimit) {
            val t = U32(x[i]) + (carry and 0xFFFF_FFFFL)
            z[i] = t.toInt()
            carry = (t ushr 32) + (carry ushr 32)
        }
        if (carry != 0L)
            z[indexLimit] = carry.toInt()
        if (carry ushr 32 != 0L)
            z[indexLimit + 1] = (carry ushr 32).toInt()
        return z
    }

    /**
     * Returns a new limb array representing the sum of [x] and [y].
     *
     * The result is sized to accommodate any carry produced by the addition.
     */
    fun newAdd(x: IntArray, y: IntArray): IntArray {
        val newBitLen = max(bitLen(x), bitLen(y)) + 1
        val z = newWithBitLen(newBitLen)

        val min = min(z.size, min(x.size, y.size))
        var carry = 0L
        var i = 0
        while (i < min) {
            val t = U32(x[i]) + U32(y[i]) + carry
            z[i] = t.toInt()
            carry = t ushr 32
            check(carry in 0L..1L)
            ++i
        }
        val longer = if (x.size > y.size) x else y
        while (i < longer.size && i < z.size) {
            val t = U32(longer[i]) + carry
            z[i] = t.toInt()
            carry = t ushr 32
            ++i
        }
        if (carry != 0L && i < z.size)
            z[i] = 1
        return z
    }

    /**
     * Adds the unsigned 32-bit value [w] to [x], mutating it in place when possible.
     *
     * If the addition produces a carry beyond the current length of [x],
     * a new extended array is returned.
     */
    fun newOrMutateAdd(x: IntArray, w: UInt): IntArray {
        val carry = mutateAdd(x, x.size, w)
        if (carry == 0u)
            return x
        val z = newCopyWithExactLen(x, x.size + 1)
        z[x.size] = carry.toInt()
        return z
    }

    /**
     * Adds the unsigned 32-bit value [w] to the first [xLen] limbs of [x], modifying [x] in place.
     *
     * A non-zero return indicates a single limb that must be
     * handled by the caller.
     *
     * @return the final carry as an unsigned 32-bit value.
     * @throws IllegalArgumentException if [xLen] is out of range for [x].
     */
    fun mutateAdd(x: IntArray, xLen: Int, w: UInt): UInt {
        if (xLen >= 0 && xLen <= x.size) {
            var carry = w.toULong()
            var i = 0
            while (carry != 0uL && i < xLen) {
                val t = dw32(x[i]) + carry
                x[i] = t.toInt()
                carry = t shr 32
                ++i
            }
            return carry.toUInt()
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Adds the unsigned 64-bit value [dw] to the first [xLen] limbs of [x], modifying [x] in place.
     *
     * It is the caller's responsibility to properly handle the returned final carry,
     * which may occupy one or two additional limbs.
     *
     * @return the resulting carry as an unsigned 64-bit value.
     * @throws IllegalArgumentException if [xLen] is out of range for [x].
     */    fun mutateAdd(x: IntArray, xLen: Int, dw: ULong): ULong {
        if (xLen >= 0 && xLen <= x.size) {
            var carry = dw
            var i = 0
            while (carry != 0uL && i < xLen) {
                val s = dw32(x[i]) + (carry and 0xFFFF_FFFFuL)
                x[i] = s.toInt()
                carry = (carry shr 32) + (s shr 32)
                ++i
            }
            return carry
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Adds the first [yLen] limbs of [y] to the first [xLen] limbs of [x], mutating [x] in place.
     *
     * Handles carry propagation across the limbs of [x] and [y].
     * It is the caller's responsibility to check the returned carry and store it in [x],
     * extending the length of [x] if needed.
     *
     * @return the final carry as an unsigned 32-bit value.
     * @throws IllegalArgumentException if [xLen] or [yLen] are out of range for [x] or [y].
     */
    fun mutateAdd(x: IntArray, xLen: Int, y: IntArray, yLen: Int): UInt {
        if (xLen >= 0 && xLen <= x.size && yLen >= 0 && yLen <= y.size) {
            check(xLen == 0 || x[xLen - 1] != 0)
            val minLen = min(xLen, yLen)
            var carry = 0uL
            for (i in 0..<minLen) {
                carry = dw32(x[i]) + dw32(y[i]) + carry
                x[i] = carry.toInt()
                carry = carry shr 32
            }
            if (xLen >= yLen) {
                var i = yLen
                while (carry != 0uL && i < xLen) {
                    carry = dw32(x[i]) + carry
                    x[i] = carry.toInt()
                    carry = carry shr 32
                    ++i
                }
            } else {
                for (i in minLen..<yLen) {
                    carry = dw32(y[i]) + carry
                    x[i] = carry.toInt()
                    carry = carry shr 32
                }
            }
            return carry.toUInt()
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Returns a new limb array representing [x] minus the unsigned integer [w].
     *
     * If the result is zero or the subtraction underflows, returns [ZERO].
     */
    fun newSub(x: IntArray, w: Int): IntArray {
        val z = IntArray(nonZeroLimbLen(x))
        var orAccumulator = 0
        var borrow = U32(w)
        for (i in z.indices) {
            val t = U32(x[i]) - borrow
            val zi = t.toInt()
            z[i] = zi
            orAccumulator = orAccumulator or zi
            borrow = t ushr 63
        }
        return if (orAccumulator == 0 || borrow != 0L) ZERO else z
    }

    /**
     * Returns a new limb array representing [x] minus the signed 64-bit value [dw].
     * The caller must ensure that x >= y.
     *
     * If the result is zero or the subtraction underflows, returns [ZERO].
     */
    fun newSub(x: IntArray, dw: Long): IntArray {
        val z = IntArray(nonZeroLimbLen(x))
        var orAccumulator = 0
        var borrow = 0L
        if (z.isNotEmpty()) {
            val t0 = U32(x[0]) - (dw and 0xFFFF_FFFFL)
            val z0 = t0.toInt()
            z[0] = z0
            orAccumulator = z0
            if (z.size > 1) {
                borrow = t0 ushr 63
                val t1 = U32(x[1]) - (dw ushr 32) - borrow
                val z1 = t1.toInt()
                z[1] = z1
                orAccumulator = orAccumulator or z1
                borrow = t1 ushr 63
                var i = 2
                while (i < z.size) {
                    val t = U32(x[i]) - borrow
                    val zi = t.toInt()
                    z[i] = zi
                    orAccumulator = orAccumulator or zi
                    borrow = t ushr 63
                    ++i
                }
            }
        }
        return if (orAccumulator == 0 || borrow != 0L) ZERO else z
    }

    /**
     * Returns a new limb array representing [x] minus [y].
     *
     * Requires that [x] is greater than or equal to [y].
     * If the result is zero, returns [ZERO].
     */
    fun newSub(x: IntArray, y: IntArray): IntArray {
        check (compare(x, y) >= 0)
        val z = IntArray(nonZeroLimbLen(x))
        var orAccumulator = 0
        var borrow = 0L
        val min = min(z.size, min(x.size, y.size))
        var i = 0
        while (i < min) {
            val t = U32(x[i]) - U32(y[i]) - borrow
            val zi = t.toInt()
            z[i] = zi
            orAccumulator = orAccumulator or zi
            borrow = t ushr 63
            check(borrow in 0L..1L)
            ++i
        }
        while (i < x.size && i < z.size) {
            val t = U32(x[i]) - borrow
            val zi = t.toInt()
            z[i] = zi
            orAccumulator = orAccumulator or zi
            borrow = t ushr 63
            ++i
        }
        check (borrow == 0L)
        return if (orAccumulator != 0)
            z
        else
            ZERO
    }

    /**
     * Subtracts the unsigned 64-bit value [dw] from the first [xLen] limbs of [x], mutating [x] in place.
     *
     * @throws IllegalArgumentException if [xLen] is out of range for [x].
     */
    fun mutateSub(x: IntArray, xLen: Int, dw: ULong) {
        if (xLen >= 0 && xLen <= x.size) {

            val lo = dw and 0xFFFF_FFFFuL
            val hi = dw shr 32

            var borrow = 0uL

            val t0 = dw32(x[0]) - lo
            x[0] = t0.toInt()
            borrow = t0 shr 63

            val t1 = dw32(x[1]) - hi - borrow
            x[1] = t1.toInt()
            borrow = t1 shr 63

            var i = 2
            while (borrow != 0uL && i < xLen) {
                borrow = dw32(x[i]) - borrow
                x[i] = borrow.toInt()
                borrow = borrow shr 63
                ++i
            }
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Subtracts the first [yLen] limbs of [y] from the first [xLen] limbs of [x], mutating [x] in place.
     *
     * Requires that [x] is greater than or equal to [y].
     *
     * @throws IllegalArgumentException if [xLen] or [yLen] are out of range for [x] or [y].
     */
    fun mutateSub(x: IntArray, xLen: Int, y: IntArray, yLen: Int) {
        if (xLen >= 0 && xLen <= x.size && yLen >= 0 && yLen <= y.size) {
            check(xLen == 0 || x[xLen - 1] != 0)
            check(yLen == 0 || y[yLen - 1] != 0)
            check(xLen >= yLen)
            var borrow = 0uL // 0 or 1
            var i = 0
            while (i < yLen) {
                borrow = dw32(x[i]) - dw32(y[i]) - borrow
                x[i] = borrow.toInt()
                borrow = borrow shr 63
                ++i
            }
            while (borrow != 0uL && i < xLen) {
                borrow = dw32(x[i]) - borrow
                x[i] = borrow.toInt()
                borrow = borrow shr 63
                ++i
            }
            check(borrow == 0uL)
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Subtracts the first [xLen] limbs of [x] from the first [yLen] limbs of [y],
     * storing the result back into [x] and mutating it in place.
     *
     * This is effectively a "reverse" subtraction: result = y - x.
     * Requires that [xLen] equals [yLen] and that [y] is normalized.
     * The actual value of [x] must be less than [y], so the caller
     * should zero-pad [x] up to [xLen].
     *
     * @throws IllegalArgumentException if [xLen] or [yLen] are out of range for [x] or [y].
     */
    fun mutateReverseSub(x: IntArray, xLen: Int, y: IntArray, yLen: Int) {
        if (xLen >= 0 && xLen <= x.size && yLen >= 0 && yLen <= y.size) {
            // x is usually not normalized .. so don't check for it
            check(yLen == 0 || y[yLen - 1] != 0)
            if (xLen != yLen)
                print("foo!")
            check(xLen == yLen)
            var borrow = 0uL // 0 or 1
            var i = 0
            while (i < yLen) {
                borrow = dw32(y[i]) - dw32(x[i]) - borrow
                x[i] = borrow.toInt()
                borrow = borrow shr 63
                ++i
            }
            check(borrow == 0uL)
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Returns a new limb array representing [x] multiplied by the unsigned 32-bit value [w].
     *
     * Returns [ZERO] if [x] or [w] is zero.
     */
    fun newMul(x: IntArray, w: UInt): IntArray {
        val xLen = nonZeroLimbLen(x)
        if (xLen == 0 || w == 0u)
            return ZERO
        val xBitLen = bitLengthFromNormalized(x, xLen)
        val pBitLen = xBitLen + 32 - w.countLeadingZeroBits()
        val p = newWithBitLen(pBitLen)
        mul(p, x, xLen, w)
        return p
    }

    /**
     * Multiplies the first [xLen] limbs of [x] by the unsigned 32-bit value [w], storing the result in [p].
     *
     * Requirements:
     * - [w] must not be zero.
     * - [p.size] must be at least [xLen] + 1.
     * - [p] does not need to be zero-initialized.
     *
     * The final carry will always be written to [p][xLen] if present, even if zero.
     * The result is non-normalized; any additional limbs in [p] beyond [xLen] + 1 are not zeroed.
     *
     * This function can mutate [p] in-place.
     *
     * @throws IllegalArgumentException if [p.size] is too small to hold the result and carry.
     */
    fun mul(p: IntArray, x: IntArray, xLen: Int, w: UInt) {
        val w64 = w.toULong()
        var carry = 0uL
        for (i in 0..<xLen) {
            val t = dw32(x[i]) * w64 + carry
            p[i] = t.toInt()
            carry = t shr 32
        }
        if (p.size > xLen)
            p[xLen] = carry.toInt()
        else if (carry != 0uL)
            throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing [x] multiplied by the unsigned 64-bit value [dw].
     *
     * Optimized to delegate to [newMul] with a 32-bit value if the upper 32 bits of [dw] are zero.
     * Returns [ZERO] if [x] or [dw] is zero.
     */
    fun newMul(x: IntArray, dw: ULong): IntArray {
        if ((dw shr 32) == 0uL)
            return newMul(x, dw.toUInt())
        val xLen = nonZeroLimbLen(x)
        val xBitLen = bitLengthFromNormalized(x, xLen)
        if (xBitLen == 0)
            return ZERO
        val newBitLen = bitLen(x) + (64 - dw.countLeadingZeroBits())
        val z = newWithBitLen(newBitLen)
        mul(z, z.size, x, xLen, dw)
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
     * (typically requiring +2 limbs) or by checking bit lengths (1 or 2 extra limbs).
     *
     * @throws IllegalArgumentException if [xLen], [zLen], or array sizes are invalid.
     */
    fun mul(z: IntArray, zLen: Int, x: IntArray, xLen: Int, dw: ULong) {
        if (zLen >= 0 && zLen <= z.size && xLen >= 0 && xLen <= x.size && zLen > xLen) {
            val lo = dw and 0xFFFF_FFFFuL
            val hi = dw shr 32

            var ppPrevHi = 0uL


            // i = 1…n-1: do both halves in one pass
            for (i in 0..<xLen) {
                val xi = dw32(x[i])

                val pp = xi * lo + (ppPrevHi and 0xFFFF_FFFFuL)
                z[i] = pp.toInt()

                ppPrevHi = xi * hi + (ppPrevHi shr 32) + (pp shr 32)
            }
            for (i in xLen..<zLen) {
                z[i] = ppPrevHi.toInt()
                ppPrevHi = ppPrevHi shr 32
            }
            if (ppPrevHi == 0uL)
                return
        }
        throw IllegalArgumentException()
    }

    /**
     * Returns a new limb array representing the product of [x] and [y].
     *
     * Returns [ZERO] if either [x] or [y] is zero.
     */
    fun newMul(x: IntArray, y: IntArray): IntArray {
        val xBitLen = bitLen(x)
        val yBitLen = bitLen(y)
        if (xBitLen == 0 || yBitLen == 0)
            return ZERO
        val p = newWithBitLen(xBitLen + yBitLen)
        mul(p, x, limbLenFromBitLen(xBitLen), y, limbLenFromBitLen(yBitLen))
        return p
    }

    /**
     * Multiplies the first [xLen] limbs of [x] by the first [yLen] limbs of [y],
     * accumulating the result into [p].
     *
     * Requirements:
     * - [p] must be of size [xLen] + [yLen] or [xLen] + [yLen] - 1.
     * - The first [yLen] entries of [p] must be zeroed by the caller.
     * - [xLen] and [yLen] must be greater than zero and within the array bounds.
     * - The most significant limbs of [x] and [y] must be nonzero.
     * - For efficiency, if one array is longer, it is preferable to use it as [y].
     *
     * @return the number of limbs actually used in [p].
     * @throws IllegalArgumentException if preconditions on array sizes or lengths are violated.
     */
    fun mul(p: IntArray, x: IntArray, xLen: Int, y: IntArray, yLen: Int): Int {
        if (xLen > 0 && yLen > 0 && xLen <= x.size && yLen <= y.size && (xLen + yLen) <= p.size + 1) {
            check (x[xLen - 1] != 0)
            check (y[yLen - 1] != 0)
            for (i in 0..<xLen) {
                val xLimb = U32(x[i])
                var carry = 0L
                for (j in 0..<yLen) {
                    val yLimb = U32(y[j])
                    val t = xLimb * yLimb + U32(p[i + j]) + carry
                    p[i + j] = t.toInt()
                    carry = t ushr 32
                }
                if (i + yLen < p.size)
                    p[i + yLen] = carry.toInt()
                else if (carry != 0L)
                    throw IllegalArgumentException()
            }
            val lastIndex = min(xLen + yLen, p.size) - 1
            check (p[lastIndex] != 0 || p[lastIndex - 1] != 0)
            return lastIndex + (if (p[lastIndex] == 0) 0 else 1)
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Performs an in-place fused multiply-add on [x]: x[i] = x[i] * [m] + [a], propagating carry.
     *
     * Used internally when parsing base-10 text strings.
     *
     * @param x the limb array to be mutated.
     * @param m the multiplier.
     * @param a the addend.
     */
    private fun mutateFma(x: IntArray, m: Int, a: Int) {
        val m64 = U32(m)
        var carry = U32(a)
        for (i in x.indices) {
            val t = U32(x[i]) * m64 + carry
            x[i] = t.toInt()
            carry = t ushr 32
        }
    }

    /**
     * Returns a new limb array representing the square of [x].
     *
     * Returns [ZERO] if [x] is zero.
     */
    fun newSqr(x: IntArray): IntArray {
        val xLen = nonZeroLimbLen(x)
        if (xLen == 0)
            return ZERO
        val bitLen = bitLengthFromNormalized(x, xLen)
        val sqrLimbLen = (2 * bitLen + 0x1F) shr 5
        val p = IntArray(sqrLimbLen)
        sqr(p, x, xLen)
        return p
    }

    /**
     * Squares the first [xLen] limbs of [x], storing the result in [p].
     *
     * Requirements:
     * - [p] must be completely zero-initialized by the caller.
     * - [p.size] must be sufficient to hold the squared result (2 * [xLen] or 2 * [xLen] - 1 limbs).
     *
     * Only the minimum required limbs are written to [p]; if only 2 * [xLen] - 1 limbs are non-zero,
     * that is how many will be written. The caller must ensure zero-initialization of all 2 * [xLen] limbs.
     *
     * @return the normalized limb length of the result.
     */
    fun sqr(p: IntArray, x: IntArray, xLen: Int) : Int {
        // test to encourage bounds check elimination
        if (xLen > 0 && xLen <= x.size && xLen * 2 <= p.size + 1) {
            // 1) Cross terms: for i<j, add (x[i]*x[j]) twice into p[i+j]
            // these terms are doubled
            for (i in 0..<xLen) {
                val xi = dw32(x[i])
                var carry = 0uL
                for (j in (i + 1)..<xLen) {
                    val prod = xi * dw32(x[j])        // 32x32 -> 64
                    // add once
                    val t1 = prod + dw32(p[i + j]) + carry
                    val p1 = t1 and 0xFFFF_FFFFuL
                    carry = t1 shr 32
                    // add second time (doubling) — avoids (prod << 1) overflow
                    val t2 = prod + p1
                    p[i + j] = t2.toInt()
                    carry += t2 shr 32
                }
                // flush carry to the next limb(s)
                val k = i + xLen
                if (carry != 0uL) {
                    val t = dw32(p[k]) + carry
                    p[k] = t.toInt()
                    carry = t shr 32
                    if (carry != 0uL)
                        ++p[k + 1]
                }
            }

            // 2) Diagonals: add x[i]**2 into columns 2*i and 2*i+1
            // terms on the diagonal are not doubled
            for (i in 0..<xLen) {
                val sq = dw32(x[i]) * dw32(x[i])      // 64-bit
                // add low 32 to p[2*i]
                var t = dw32(p[2 * i]) + (sq and 0xFFFF_FFFFuL)
                p[2 * i] = t.toInt()
                var carry = t shr 32
                // add high 32 (and carry) to p[2*i+1]
                val s = (sq shr 32) + carry
                if (s != 0uL) {
                    t = dw32(p[2 * i + 1]) + s
                    p[2 * i + 1] = t.toInt()
                    carry = t shr 32
                    // propagate any remaining carry
                    var k = 2 * i + 2
                    while (carry != 0uL) {
                        t = dw32(p[k]) + carry
                        p[k] = t.toInt()
                        carry = t shr 32
                        k++
                    }
                }
            }
            var lastIndex = min(2 * xLen, p.size) - 1
            while (p[lastIndex] == 0)
                --lastIndex
            return lastIndex + 1
        } else {
            throw IllegalArgumentException()
        }
    }

    private fun copy(dst: IntArray, src: IntArray) {
        var i = 0
        val m = min(dst.size, src.size)
        while (i < m) {
            dst[i] = src[i]
            ++i
        }
        while (i < dst.size)
            dst[i++] = 0
    }

    /**
     * Returns a new [IntArray] representing [x] logically shifted right by [bitCount] bits.
     *
     * Bits shifted out of the least significant end are discarded, and new high bits are filled with zeros.
     * The original [x] is not modified.
     *
     * @param x the source magnitude in little-endian limb order.
     * @param bitCount the number of bits to shift right; must be non-negative.
     * @return a new [IntArray] containing the shifted result, normalized if all bits are shifted out.
     * @throws IllegalArgumentException if [bitCount] is negative.
     */
    fun newShiftRight(x: IntArray, bitCount: Int): IntArray {
        require(bitCount >= 0)
        val newBitLen = bitLen(x) - bitCount
        if (newBitLen <= 0)
            return ZERO
        val newWordLen = (newBitLen + 0x1F) ushr 5
        val wordShift = bitCount ushr 5
        val innerShift = bitCount and ((1 shl 5) - 1)
        val z = IntArray(max(newWordLen, 0))
        if (innerShift != 0) {
            val iLast = z.size - 1
            for (i in 0..<iLast)
                z[i] = (x[i + wordShift + 1] shl -innerShift) or (x[i + wordShift] ushr innerShift)
            val srcIndex = iLast + wordShift + 1
            z[iLast] = (
                    if (srcIndex < x.size)
                        (x[iLast + wordShift + 1] shl -innerShift)
                    else
                        0) or (x[iLast + wordShift] ushr innerShift)
        } else {
            for (i in z.indices)
                z[i] = x[i + wordShift]
        }
        return z
    }

    /**
     * Shifts the first [xLen] limbs of [x] right by [bitCount] bits, in place.
     *
     * Bits shifted out of the low end are discarded, and high bits are filled with zeros.
     * Returns [x] for convenience.
     *
     * @throws IllegalArgumentException if [xLen] or [bitCount] is out of range.
     */
    fun mutateShiftRight(x: IntArray, xLen: Int, bitCount: Int): IntArray {
        require (bitCount >= 0 && xLen >= 0 && xLen <= x.size)
        val wordShift = bitCount ushr 5
        val innerShift = bitCount and ((1 shl 5) - 1)
        if (wordShift >= xLen) {
            x.fill(0, 0, xLen)
            return x
        }
        val newLen = xLen - wordShift
        if (wordShift > 0) {
            //System.arraycopy(x, wordShift, x, 0, newLen)
            //shiftDown(x, wordShift, 0, newLen)
            for (i in 0..<newLen)
                x[i] = x[i + wordShift]
            for (i in newLen..<xLen)
                x[i] = 0
        }
        if (innerShift > 0) {
            val last = newLen - 1
            for (i in 0..<last)
                x[i] = (x[i + 1] shl (-innerShift)) or (x[i] ushr innerShift)
            x[last] = x[last] ushr innerShift
        }
        return x
    }

    /**
     * Returns a new [IntArray] representing [x] shifted left by [bitCount] bits.
     *
     * Bits shifted out of the low end propagate into higher limbs. The returned
     * array will be longer than [x] to accommodate the resulting value.
     *
     * @param x the source magnitude in little-endian limb order.
     * @param bitCount the number of bits to shift left; must be non-negative.
     * @return a new [IntArray] containing the shifted value.
     * @throws IllegalArgumentException if [bitCount] is negative.
     */
    fun newShiftLeft(x: IntArray, bitCount: Int): IntArray {
        val newBitLen = bitLen(x) + bitCount
        val z = newCopyWithBitLen(x, newBitLen)
        mutateShiftLeft(z, bitCount)
        return z
    }

    /**
     * Shifts [x] left by [bitCount] bits in place.
     *
     * Bits shifted out of the low end propagate into higher limbs.
     * The array [x] is mutated to contain the shifted result; its length
     * must be sufficient to hold any carry propagated into the top limb.
     * Otherwise, bits will be lost out the top.
     *
     * @param x the magnitude to shift, in little-endian limb order.
     * @param bitCount the number of bits to shift left; must be non-negative.
     * @throws IllegalArgumentException if [bitCount] is negative.
     */
    fun mutateShiftLeft(x: IntArray, bitCount: Int) =
        mutateShiftLeft(x, x.size, bitCount)

    /**
     * Shifts the lower [xLen] limbs of [x] left by [bitCount] bits, in place.
     *
     * Bits shifted out of the top (xLen-1) limb are discarded. Limbs at indices
     * >= [xLen] are not modified. This operation mutates [x] directly and does
     * not extend its length or preserve overflow bits.
     *
     * @param x the array of limbs, in little-endian order.
     * @param xLen the number of valid limbs to process from [x].
     * @param bitCount the number of bits to shift left; must be non-negative.
     * @throws IllegalArgumentException if [bitCount] is negative.
     */
    fun mutateShiftLeft(x: IntArray, xLen: Int, bitCount: Int) {
        require (bitCount >= 0 && xLen >= 0 && xLen <= x.size)
        val wordShift = bitCount ushr 5
        val innerShift = bitCount and ((1 shl 5) - 1)
        if (wordShift >= xLen) {
            x.fill(0, 0, xLen)
            return
        }
        if (wordShift > 0) {
            //val newLen = xLen - wordShift
            //System.arraycopy(x, 0, x, wordShift, newLen)
            for (i in xLen - 1 downTo wordShift)
                x[i] = x[i - wordShift]
            for (i in wordShift - 1 downTo 0)
                x[i] = 0
        }
        if (innerShift > 0) {
            for (i in xLen - 1 downTo 1)
                x[i] = (x[i] shl innerShift) or (x[i - 1] ushr -innerShift)
            x[0] = x[0] shl innerShift
        }
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
    fun isPowerOfTwo(x: IntArray, xLen: Int): Boolean {
        if (xLen >= 0 && xLen <= x.size) {
            var bitSeen = false
            for (i in 0..<xLen) {
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
    fun bitLen(x: IntArray): Int = bitLen(x, x.size)

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
    fun bitLen(x: IntArray, xLen: Int): Int {
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
    inline fun limbLenFromBitLen(bitLen: Int): Int = (bitLen + 0x1F) ushr 5

    /**
     * Fast path to return the bit length of [x] with a normalized [xLen].
     *
     * The parameter [xLen] _must_ represent a normalized length — that is,
     * if [xLen] > 0, then the most significant limb at index [xLen - 1]
     * must be non-zero. The result is the number of bits required to
     * represent the value contained in the lower [xLen] limbs of [x].
     *
     * @param x the array of 32-bit limbs.
     * @param xLen the number of significant limbs; must be normalized.
     * @return the bit length of the value represented by [x].
     */
    inline fun bitLengthFromNormalized(x: IntArray, xLen: Int): Int =
        if (xLen != 0) {
            check(xLen >= 0 && xLen <= x.size && x[xLen - 1] != 0)
            32 - x[xLen - 1].countLeadingZeroBits() + ((xLen - 1) shl 5)
        } else {
            0
        }

    /**
     * Overload of [bitLengthBigIntegerStyle] that considers all limbs in [x].
     *
     * Equivalent to calling [bitLengthBigIntegerStyle] with `xLen = x.size`.
     *
     * @param sign `true` if the value is negative, `false` otherwise.
     * @param x the array of 32-bit limbs representing the magnitude.
     * @return the bit length following BigInteger’s definition.
     */
    fun bitLengthBigIntegerStyle(sign: Boolean, x: IntArray): Int = bitLengthBigIntegerStyle(sign, x, x.size)

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
    fun bitLengthBigIntegerStyle(sign: Boolean, x: IntArray, xLen: Int): Int {
        if (xLen >= 0 && xLen <= x.size) {
            val bitLen = bitLen(x, xLen)
            val isNegPowerOfTwo = sign && isPowerOfTwo(x, xLen)
            val bitLengthBigIntegerStyle = bitLen - if (isNegPowerOfTwo) 1 else 0
            return bitLengthBigIntegerStyle
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Returns a new normalized array representing the bitwise AND of [x] and [y].
     *
     * The result contains only the significant limbs (no trailing zeros). The last limb
     * is determined by the highest index where `x[i] and y[i]` is non-zero. If the
     * result is entirely zero, [ZERO] is returned.
     *
     * @param x the first operand, represented as an array of 32-bit limbs.
     * @param y the second operand, represented as an array of 32-bit limbs.
     * @return a new [IntArray] containing the normalized bitwise AND result,
     *         or [ZERO] if the result is zero.
     */
    fun newAnd(x: IntArray, y: IntArray): IntArray {
        var iLast = min(x.size, y.size)
        do {
            --iLast
            if (iLast < 0)
                return ZERO
        } while ((x[iLast] and y[iLast]) == 0)
        val z = IntArray(iLast + 1)
        while (iLast >= 0) {
            z[iLast] = x[iLast] and y[iLast]
            --iLast
        }
        return z
    }

    /**
     * Returns a new normalized array representing the bitwise OR of [x] and [y].
     *
     * The resulting array contains only the significant limbs (no trailing zeros).
     * If the result is entirely zero, [ZERO] is returned.
     *
     * The operation first computes the OR for the overlapping portion of [x] and [y],
     * then copies any remaining limbs from the longer array.
     *
     * @param x the first operand, represented as an array of 32-bit limbs.
     * @param y the second operand, represented as an array of 32-bit limbs.
     * @return a new [IntArray] containing the normalized bitwise OR result,
     *         or [ZERO] if the result is zero.
     */
    fun newOr(x: IntArray, y: IntArray): IntArray {
        val xLen = nonZeroLimbLen(x)
        val yLen = nonZeroLimbLen(y)
        val maxLen = max(xLen, yLen)
        val minLen = min(xLen, yLen)
        if (maxLen == 0)
            return ZERO
        val z = IntArray(maxLen)
        var i = 0
        while (i < minLen) {
            z[i] = x[i] or y[i]
            ++i
        }
        when {
            i < xLen ->
                x.copyInto(z, i, i, xLen) // System.arraycopy(x, i, z, i, xLen - i)
            i < yLen ->
                y.copyInto(z, i, i, yLen) //System.arraycopy(y, i, z, i, yLen - i)
        }
        return z
    }

    /**
     * Returns a new IntArray representing the bitwise XOR of [x] and [y].
     *
     * The operation is performed limb-wise up to the lengths of the inputs' non-zero limbs.
     * Any extra limbs from the longer array are copied as-is.
     * The resulting array may contain trailing zero limbs and is **not guaranteed to be normalized**.
     *
     * @param x the first operand array of 32-bit limbs.
     * @param y the second operand array of 32-bit limbs.
     * @return an IntArray containing the XOR of [x] and [y], possibly with trailing zero limbs.
     */
    fun newXor(x: IntArray, y: IntArray): IntArray {
        val xLen = nonZeroLimbLen(x)
        val yLen = nonZeroLimbLen(y)
        val maxLen = max(xLen, yLen)
        val minLen = min(xLen, yLen)
        if (maxLen == 0)
            return ZERO
        val z = IntArray(maxLen)
        var nonZeroAccumulator = 0
        var i = 0
        while (i < minLen) {
            val t = x[i] xor y[i]
            z[i] = t
            nonZeroAccumulator = nonZeroAccumulator or t
            ++i
        }
        when {
            i < xLen ->
                x.copyInto(z, i, i, xLen) // System.arraycopy(x, i, z, i, xLen - i)
            i < yLen ->
                y.copyInto(z, i, i, yLen) // System.arraycopy(y, i, z, i, yLen - i)
            else -> if (nonZeroAccumulator == 0) return ZERO
        }
        return z
    }

    /**
     * Creates a new limb array with a single bit set at [bitIndex].
     *
     * The resulting array has the minimum length needed to contain that bit.
     *
     * @throws IllegalArgumentException if [bitIndex] is negative.
     */
    inline fun newWithSetBit(bitIndex: Int): IntArray {
        if (bitIndex >= 0) {
            val magia = Magia.newWithBitLen(bitIndex + 1)
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
    fun testBit(x: IntArray, bitIndex: Int): Boolean = testBit(x, x.size, bitIndex)

    /**
     * Tests whether the bit at the specified [bitIndex] is set in the given unsigned integer.
     *
     * @param x the array of 32-bit limbs representing the integer.
     * @param xLen the number of significant limbs to consider; must be normalized.
     * @param bitIndex the zero-based index of the bit to test (0 is least significant bit).
     * @return `true` if the specified bit is set, `false` otherwise.
     * @throws IllegalArgumentException if [xLen] is out of range for [x].
     */
    fun testBit(x: IntArray, xLen: Int, bitIndex: Int): Boolean {
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
    fun testAnyBitInLowerN(x: IntArray, bitCount: Int): Boolean =
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
    fun testAnyBitInLowerN(x: IntArray, xLen: Int, bitCount: Int): Boolean {
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
     * Checks whether the unsigned integer represented by [x] is equal to the single 32-bit value [y].
     *
     * Only the significant limbs of [x] are considered. Trailing zero limbs in [x] are ignored.
     *
     * @param x the array of 32-bit limbs representing the integer.
     * @param y the 32-bit integer to compare against.
     * @return `true` if [x] equals [y], `false` otherwise.
     */
    fun EQ(x: IntArray, y: Int) = nonZeroLimbLen(x) == 1 && x[0] == y

    /**
     * Checks whether the unsigned integers represented by [x] and [y] are equal.
     *
     * Comparison is based on the significant limbs of each array, ignoring any trailing zero limbs.
     *
     * @param x the first array of 32-bit limbs representing an unsigned integer.
     * @param y the second array of 32-bit limbs representing an unsigned integer.
     * @return `true` if [x] and [y] represent the same value, `false` otherwise.
     */
    fun EQ(x: IntArray, y: IntArray): Boolean = compare(x, y) == 0

    /**
     * Compares two arbitrary-precision integers represented as arrays of 32-bit limbs.
     *
     * Comparison is performed over the full lengths of both arrays.
     *
     * @param x the first integer array (least significant limb first).
     * @param y the second integer array (least significant limb first).
     * @return -1 if x < y, 0 if x == y, 1 if x > y.
     */
    fun compare(x: IntArray, y: IntArray): Int {
        val minLen = min(x.size, y.size)
        for (i in x.size - 1 downTo minLen)
            if (x[i] != 0)
                return 1
        for (i in y.size - 1 downTo minLen)
            if (y[i] != 0)
                return -1
        for (i in minLen - 1 downTo 0) {
            if (x[i] != y[i])
                return ((((U32(x[i]) - U32(y[i])) shr 63) shl 1) + 1).toInt()
        }
        return 0
    }

    /**
     * Compares two arbitrary-precision integers represented as arrays of 32-bit limbs,
     * considering only the first [xLen] limbs of [x] and [yLen] limbs of [y].
     *
     * Both input ranges must be normalized: the last limb of each range (if non-zero length)
     * must be non-zero.
     *
     * Comparison is unsigned per-limb (32-bit) from most significant to least significant limb.
     *
     * @param x the first integer array (least significant limb first).
     * @param xLen the number of significant limbs in [x] to consider; must be normalized.
     * @param y the second integer array (least significant limb first).
     * @param yLen the number of significant limbs in [y] to consider; must be normalized.
     * @return -1 if x < y, 0 if x == y, 1 if x > y.
     * @throws IllegalArgumentException if [xLen] or [yLen] are out of bounds for the respective arrays.
     */
    fun compare(x: IntArray, xLen: Int, y: IntArray, yLen: Int): Int {
        if (xLen >= 0 && xLen <= x.size && yLen >= 0 && yLen <= y.size) {
            check(xLen == 0 || x[xLen - 1] != 0)
            check(yLen == 0 || y[yLen - 1] != 0)
            if (xLen != yLen)
                return if (xLen > yLen) 1 else -1
            for (i in xLen - 1 downTo 0) {
                if (x[i] != y[i])
                    return ((((U32(x[i]) - U32(y[i])) shr 63) shl 1) + 1).toInt()
            }
            return 0
        } else {
            throw IllegalArgumentException()
        }
    }


    /**
     * Compares a multi-limb unsigned integer [x] with a single-limb unsigned value [w].
     *
     * - Returns `-1` if `x < w`
     * - Returns `0`  if `x == w`
     * - Returns `1`  if `x > w`
     *
     * The comparison treats [x] as an unsigned integer represented by its
     * 32-bit limbs, with the least significant limb at index 0.
     *
     * @param x the multi-limb unsigned integer to compare.
     * @param w the single-limb unsigned value to compare against.
     * @return `-1`, `0`, or `1` following the standard comparison semantics.
     */
    fun compare(x: IntArray, w: UInt): Int {
        val limbLen = nonZeroLimbLen(x)
        return when {
            (limbLen > 1) -> 1
            (limbLen == 0) -> if (w == 0u) 0 else -1
            else -> x[0].toUInt().compareTo(w)
        }
    }

    fun compare(x: IntArray, dw: ULong): Int = compare(x, nonZeroLimbLen(x), dw)

    fun compare(x: IntArray, xLen: Int, dw: ULong): Int {
        if (xLen >= 0 && xLen <= x.size) {
            check (xLen == 0 || x[xLen - 1] != 0)
            return if (xLen > 2) 1 else toRawULong(x).compareTo(dw)
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Divides the arbitrary-precision integer [x] by the 32-bit unsigned integer [w] in-place.
     *
     * Updates [x] with the quotient and returns the remainder.
     *
     * @param x the integer array (least significant limb first) to be divided; mutated with the quotient.
     * @param w the 32-bit unsigned divisor.
     * @return the remainder after division.
     * @throws ArithmeticException if [w] is zero.
     */
    fun mutateDivMod(x: IntArray, w: UInt): UInt =
        mutateDivMod(x, x.size, w)

    /**
     * Divides the first [xLen] limbs of [x] by the 32-bit unsigned integer [w] in-place.
     *
     * Updates [x] with the quotient and returns the remainder.
     * Only the lower [xLen] limbs are considered; higher limbs, if any, are ignored.
     *
     * @param x the integer array (least significant limb first) to be divided; mutated with the quotient.
     * @param xLen the number of significant limbs in [x] to include in the division; must be within `0..x.size`.
     * @param w the 32-bit unsigned divisor.
     * @return the remainder after division.
     * @throws ArithmeticException if [w] is zero.
     * @throws IllegalArgumentException if [xLen] is out of range.
     */
    fun mutateDivMod(x: IntArray, xLen: Int, w: UInt): UInt {
        if (xLen >= 0 && xLen <= x.size) {
            if (w == 0u)
                throw ArithmeticException("div by zero")
            val dw = w.toULong()
            var carry = 0uL
            for (i in xLen - 1 downTo 0) {
                val t = (carry shl 32) + dw32(x[i])
                val q = t / dw
                val r = t % dw
                x[i] = q.toInt()
                carry = r
            }
            return carry.toUInt()
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Returns a new integer array representing [x] divided by the 32-bit unsigned integer [w].
     *
     * This operation does not mutate the input [x]. The quotient is computed and returned as a
     * new array. If the quotient is zero, a shared [ZERO] array is returned.
     *
     * @param x the integer array (least significant limb first) to be divided.
     * @param w the 32-bit unsigned divisor.
     * @return a new [IntArray] containing the quotient of the division.
     * @throws ArithmeticException if [w] is zero.
     */
    fun newDiv(x: IntArray, w: UInt): IntArray {
        val q = newCopyTrimmed(x)
        mutateDivMod(q, w)
        return if (nonZeroLimbLen(q) > 0) q else ZERO
    }

    fun newDiv(x: IntArray, dw: ULong): IntArray {
        if ((dw shr 32) == 0uL)
            return newDiv(x, dw.toUInt())
        val xLen = nonZeroLimbLen(x)
        val cmp = compare(x, xLen, dw)
        when {
            cmp < 0 -> return ZERO
            cmp == 0 -> return ONE
        }
        val u = x
        val m = xLen
        val vnDw = dw
        val q = IntArray(m - 2 + 1)
        val r = null
        knuthDivide64(q, r, u, vnDw, m)
        return if (nonZeroLimbLen(q) > 0) q else ZERO
    }

    fun newDiv(x: IntArray, y: IntArray): IntArray {
        val n = nonZeroLimbLen(y)
        if (n < 2)
            return newDiv(x, y[0].toUInt())
        val m = nonZeroLimbLen(x)
        if (m < n)
            return ZERO
        val u = x
        val v = y
        val q = IntArray(m - n + 1)
        val r = null
        knuthDivide(q, r, u, v, m, n)
        return if (nonZeroLimbLen(q) > 0) q else ZERO
    }

    fun newMod(x: IntArray, w: UInt): IntArray {
        val q = newCopyTrimmed(x)
        val rem = mutateDivMod(q, w)
        return if (rem == 0u) ZERO else intArrayOf(rem.toInt())
    }

    fun newMod(x: IntArray, dw: ULong): IntArray {
        val lo = dw.toUInt()
        val hi = (dw shr 32).toUInt()
        return if (hi == 0u) newMod(x, lo) else newMod(x, intArrayOf(lo.toInt(), hi.toInt()))
    }

    fun newMod(x: IntArray, y: IntArray): IntArray {
        val n = nonZeroLimbLen(y)
        if (n <= 1) {
            if (n == 0)
                throw ArithmeticException("div by zero")
            return newMod(x, y[0].toUInt())
        }
        val m = nonZeroLimbLen(x)
        if (m == 0)
            return ZERO
        if (m < n)
            return newCopyTrimmed(x)
        val u = x
        val v = y
        val q = null
        val r = IntArray(y.size)
        knuthDivide(q, r, u, v, m, n)
        check (nonZeroLimbLen(r) <= n)
        return if (nonZeroLimbLen(r) > 0) r else ZERO
    }

    fun newDivMod(x: IntArray, y: IntArray): Array<IntArray> {
        val n = nonZeroLimbLen(y)
        if (n <= 1) {
            if (n == 0)
                throw ArithmeticException("div by zero")
            var div = newCopyTrimmed(x)
            val rem = mutateDivMod(div, y[0].toUInt())
            if (nonZeroLimbLen(div) == 0)
                div = ZERO
            return arrayOf(div, if (rem != 0u) intArrayOf(rem.toInt()) else ZERO)
        }
        val m = nonZeroLimbLen(x)
        if (m < n)
            return arrayOf(ZERO, newCopyTrimmed(x))
        val u = x
        val v = y
        val q = IntArray(m - n + 1)
        val r = IntArray(n)
        knuthDivide(q, r, u, v, m, n)
        check (nonZeroLimbLen(r) <= n)
        return arrayOf(
            if (nonZeroLimbLen(q) > 0) q else ZERO, if (nonZeroLimbLen(r) > 0) r else ZERO
        )
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
     * Returns 0 on success, 1 if (m < n || n < 2 || v[n − 1] == 0).
     */
    fun knuthDivide(
        q: IntArray?,
        r: IntArray?,
        u: IntArray,
        v: IntArray,
        m: Int,
        n: Int
    ) {
        if (m < n || n < 2 || v[n - 1] == 0)
            throw IllegalArgumentException()

        // Step D1: Normalize
        val un = newCopyWithExactLen(u, m + 1)
        val vn = newCopyWithExactLen(v, n)
        val shift = vn[n - 1].countLeadingZeroBits()
        if (shift > 0) {
            mutateShiftLeft(vn, shift)
            mutateShiftLeft(un, shift)
        }

        knuthDivideNormalizedCore(q, un, vn, m, n)

        if (r != null) {
            mutateShiftRight(un, un.size, shift)
            copy(r, un)
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
     *  - Converts each chunk into ASCII digits using [renderChunk9] and [renderChunkTail].
     *  - Prepends a leading ‘-’ if [isNegative] is true.
     *
     * @param isNegative whether to prefix the result with a minus sign.
     * @param magia the magnitude, least-significant word first.
     * @return the decimal string representation of the signed value.
     */
    fun toString(isNegative: Boolean, magia: IntArray): String =
        toString(isNegative, magia, nonZeroLimbLen(magia))

    /**
     * Converts a multi-limb integer to its decimal string representation.
     *
     * @param isNegative whether the resulting string should include a leading minus sign.
     * @param x the array of 32-bit limbs representing the integer (least-significant limb first).
     * @param xLen the number of significant limbs to consider from `x`.
     * @return the decimal string representation of the integer value.
     */
    fun toString(isNegative: Boolean, x: IntArray, xLen: Int): String {
        if (xLen >= 0 && xLen <= x.size) {
            val bitLen = bitLen(x, xLen)
            if (bitLen < 2) {
                if (bitLen == 0)
                    return "0"
                return if (isNegative) "-1" else "1"
            }
            val maxDigitLen = ((bitLen * 1234) shr 12) + 1
            val maxSignedLen = maxDigitLen + if (isNegative) 1 else 0
            var wordLen = nonZeroLimbLen(x, xLen)
            var t = newCopyWithExactLen(x, wordLen)
            val utf8 = ByteArray(maxSignedLen)
            var ib = utf8.size
            while (wordLen > 1) {
                val newLenAndRemainder = mutateBarrettDivBy1e9(t, wordLen)
                val chunk = newLenAndRemainder and 0xFFFF_FFFFuL
                renderChunk9(chunk, utf8, ib)
                wordLen = (newLenAndRemainder shr 32).toInt()
                t = newCopyWithExactLen(t, wordLen)
                ib -= 9
            }
            ib -= renderChunkTail(t[0].toUInt(), utf8, ib)
            if (isNegative)
                utf8[--ib] = '-'.code.toByte()
            val len = utf8.size - ib
            return utf8.decodeToString(ib, ib + len)
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
     * @param n the integer to render (interpreted as unsigned 32-bit).
     * @param utf8 the UTF-8 byte buffer to write digits into.
     * @param offMaxx the maximum exclusive offset within [utf8];
     *                digits are written backward from `offMaxx - 1`.
     * @return the number of bytes written.
     */
    private fun renderChunkTail(w: UInt, utf8: ByteArray, offMaxx: Int): Int {
        var t = w.toULong()
        var ib = offMaxx
        do {
            val divTen = (t * 0xCCCCCCCDuL) shr 35
            val digit = (t - (divTen * 10uL)).toInt()
            utf8[--ib] = ('0'.code + digit).toByte()
            t = divTen
        } while (t != 0uL)

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
     * @param dw the 9-digit unsigned long value to render.
     * @param utf8 the output byte buffer for ASCII digits.
     * @param offMaxx the maximum exclusive offset within [utf8];
     * digits occupy the range `offMaxx - 9 .. offMaxx - 1`.
     */
    private fun renderChunk9(dw: ULong, utf8: ByteArray, offMaxx: Int) {
        val abcde = unsignedMulHi(dw, M_U64_DIV_1E4) shr S_U64_DIV_1E4
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
    internal fun mutateBarrettDivBy1e9(magia: IntArray, len: Int): ULong {
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
     * @param magia the magnitude of the number, stored as an `IntArray` of 32-bit limbs.
     * @return the hexadecimal string representation of the magnitude.
     */
    fun toHexString(magia: IntArray) = toHexString(false, magia)

    /**
     * Converts the given magnitude array to a hexadecimal string representation.
     *
     * The resulting string is prefixed with `"0x"`, and a leading `'-'` sign is
     * included if [isNegative] is `true`. For example, a positive value might render
     * as `"0x1AF3"`, while a negative value would render as `"-0x1AF3"`.
     *
     * Each element of [magia] represents a 32-bit limb of the unsigned magnitude,
     * with the least significant limb first (little-endian order).
     *
     * @param isNegative whether the number is negative.
     * @param magia the magnitude of the number, stored as an `IntArray` of 32-bit limbs.
     * @return the hexadecimal string representation, prefixed with `"0x"`.
     *
     * Example:
     * ```
     * toHexString(false, intArrayOf(0x89ABCDEFu.toInt(), 0x01234567)) == "0x123456789ABCDEF"
     * toHexString(true,  intArrayOf(0x00000001)) == "-0x1"
     * ```
     */
    fun toHexString(isNegative: Boolean, magia: IntArray): String {
        val bitLen = bitLen(magia)
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
            var w = magia[i++]
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


    /**
     * Factory methods for constructing a numeric value from Latin-1 (ASCII) encoded input.
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
     * Constructs a [Magia] magnitude from a sequence of raw binary bytes.
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
     * @return A [Magia] magnitude as an [IntArray].
     * @throws IllegalArgumentException if the range `[off, off + len)` is invalid.
     */
    internal fun fromBinaryBytes(isNegative: Boolean, isBigEndian: Boolean,
                                 bytes: ByteArray, off: Int, len: Int): IntArray {
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

        val magia = IntArray((remaining + 3) shr 2)

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
     * @param xLen the number of significant limbs to consider; must be normalized (trailing zeros ignored).
     * @param isTwosComplement if `true`, the number is converted to two's-complement form.
     *                        Otherwise, magnitude-only representation is used.
     * @param isBigEndian if `true`, the most significant byte is first in the output array;
     *                    if `false`, least significant byte is first.
     * @return a [ByteArray] containing the binary representation of the integer.
     * @throws IllegalArgumentException if [xLen] is out of bounds (negative or greater than x.size).
     */
    fun toBinaryByteArray(sign: Boolean, x: IntArray, xLen: Int, isTwosComplement: Boolean, isBigEndian: Boolean): ByteArray {
        if (xLen >= 0 && xLen <= x.size) {
            val bitLen =
                if (isTwosComplement) bitLengthBigIntegerStyle(sign, x, xLen) + 1 else max(bitLen(x, xLen), 1)
            val byteLen = (bitLen + 7) ushr 3
            val bytes = ByteArray(byteLen)
            toBinaryBytes(x, xLen, sign and isTwosComplement, isBigEndian, bytes, 0, byteLen)
            return bytes
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Converts an arbitrary-precision integer into a binary representation within a [ByteArray],
     * automatically considering only the significant limbs (ignoring trailing zero limbs).
     *
     * This is a convenience wrapper around [toBinaryBytes] that computes [xLen] via [nonZeroLimbLen].
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
    internal fun toBinaryBytes(x: IntArray, isNegative: Boolean, isBigEndian: Boolean,
                               bytes: ByteArray, off: Int, requestedLen: Int): Int =
        toBinaryBytes(x, nonZeroLimbLen(x), isNegative, isBigEndian, bytes, off, requestedLen)

    /**
     * Converts an arbitrary-precision integer into a binary representation within a given [ByteArray].
     *
     * The integer is represented by an array of 32-bit limbs. This function writes the
     * binary bytes into the provided [bytes] array starting at offset [off], up to [requestedLen] bytes.
     * It supports both big-endian and little-endian byte ordering, as well as two's-complement
     * representation for negative numbers.
     *
     * @param x the array of 32-bit limbs representing the integer, least-significant limb first.
     * @param xLen the number of significant limbs in [x] to process.
     * @param isNegative whether the integer should be treated as negative (for two's-complement output).
     * @param isBigEndian if `true`, the most significant byte is stored first; if `false`, the least significant byte is first.
     * @param bytes the destination byte array.
     * @param off the starting offset in [bytes] where output begins.
     * @param requestedLen the number of bytes to write; if non-positive, the minimal number of bytes
     *                     required to represent the value is used.
     * @return the actual number of bytes written.
     * @throws IllegalArgumentException if [xLen] or [off] is out of bounds, or if [requestedLen] exceeds the available space.
     *
     * @implNote This function manually handles byte ordering and sign extension to allow
     * efficient serialization of large integers without additional temporary arrays.
     */
    internal fun toBinaryBytes(x: IntArray, xLen: Int, isNegative: Boolean, isBigEndian: Boolean,
                               bytes: ByteArray, off: Int, requestedLen: Int): Int {
        if (xLen >= 0 && xLen <= x.size &&
            off >= 0 && (requestedLen <= 0 || requestedLen <= bytes.size - off)) {

            val actualLen = if (requestedLen > 0) requestedLen else {
                val bitLen = if (isNegative)
                    bitLengthBigIntegerStyle(isNegative, x, xLen) + 1
                else
                    max(bitLen(x, xLen), 1)
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

            while (remaining >= 4 && iw < xLen) {
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
                val v = if (iw < xLen) x[iw++] else 0
                var w = (v xor negativeMask).toLong() + carry.toInt()
                do {
                    bytes[ib] = w.toByte()
                    ib += step1LoToHi
                    w = w shr 8
                } while (--remaining > 0)
            }
            check(iw == xLen || x[iw] == 0)
            check(ib - step1LoToHi == ibMsb)
            return actualLen
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Parses an unsigned decimal integer from a [Latin1Iterator] into a new [IntArray] representing its magnitude.
     *
     * This layer ignores any optional leading sign characters ('+' or '-') and processes only the magnitude.
     * Leading zeros and underscores ('_') are handled according to numeric literal conventions:
     * - Leading zeros are skipped.
     * - Underscores are ignored between digits.
     * - Hexadecimal input prefixed with "0x" or "0X" is delegated to [fromHex].
     *
     * The function accumulates decimal digits in blocks of 9 for efficiency, using
     * [mutateFma] to multiply and add into the resulting array.
     *
     * @param src the input iterator providing characters in Latin-1 encoding.
     * @return a new [IntArray] representing the magnitude of the parsed integer.
     * @throws IllegalArgumentException if the input does not contain a valid decimal integer.
     */
    fun from(src: Latin1Iterator): IntArray {
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
            var accumulator = 0
            var accumulatorDigitCount = 0
            val remainingLen = src.remainingLen() + if (ch == '\u0000') 0 else 1
            val bitLen = (remainingLen * 13607 + 4095) ushr 12
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
                accumulator = accumulator * 10 + n
                ++accumulatorDigitCount
                if (accumulatorDigitCount < 9)
                    continue
                mutateFma(z, 1000000000, accumulator)
                accumulator = 0
                accumulatorDigitCount = 0
            }
            if (ch == '\u0000' && chLast != '_') {
                if (accumulatorDigitCount > 0) {
                    var pow10 = 1
                    for (j in 0..<accumulatorDigitCount)
                        pow10 *= 10
                    mutateFma(z, pow10, accumulator)
                }
                return z
            }
        } while (false)
        throw IllegalArgumentException("integer parse error:$src")
    }

    internal fun fromHex(src: Latin1Iterator): IntArray {
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
     * number of trailing zeros ... or -1
     */
    internal fun ntz(magia: IntArray): Int {
        for (i in magia.indices) {
            if (magia[i] != 0)
                return (i shl 6) + magia[i].countTrailingZeroBits()
        }
        return -1
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
    fun bitPopulationCount(magia: IntArray): Int {
        var popCount = 0
        for (limb in magia)
            popCount += limb.countOneBits()
        return popCount
    }

    fun normalizedHashCode(x: IntArray): Int {
        val xLen = nonZeroLimbLen(x)
        var h = 0
        var i = 0
        while (i + 3 < xLen) {
            h = 31 * 31 * 31 * 31 * h +
                    31 * 31 * 31 * x[i] +
                    31 * 31 * x[i + 1] +
                    31 * x[i + 2] +
                    x[i + 3]
            i += 4
        }
        while (i < xLen) {
            h = 31 * h + x[i]
            ++i
        }
        return h
    }

}
