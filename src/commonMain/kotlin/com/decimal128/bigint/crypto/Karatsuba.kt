// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint.crypto

import com.decimal128.bigint.Mago
import com.decimal128.bigint.MagoSqr
import com.decimal128.bigint.MagoSqr.setSqrSchoolbook

object Karatsuba {

    private const val DEFAULT_KARATSUBA_SQR_THRESHOLD = 88
    val minLimbThreshold: Int = DEFAULT_KARATSUBA_SQR_THRESHOLD

    /**
     * Computes the square of a multi-limb magnitude using the Karatsuba algorithm.
     *
     * The input limbs `a[aOff ..< aOff + aLen]` are treated as an unsigned integer
     * in base 2³². The full result is written into
     * `z[zOff ..< zOff + 2·aLen]`.
     *
     * For `aLen < minLimbThreshold`, this function falls back to schoolbook
     * squaring. For larger inputs, the computation is recursive and reuses
     * the caller-supplied scratch buffer [t]; no allocation occurs.
     *
     * **Length requirements:**
     * - `z.size ≥ zOff + 2·aLen`
     * - `t.size ≥ 3·ceil(aLen / 2) + 3`
     *
     * @param z destination magnitude for the squared result
     * @param zOff starting index in [z]
     * @param a source magnitude to square
     * @param aOff starting index in [a]
     * @param aLen number of active limbs in [a]
     * @param t scratch buffer used for Karatsuba temporaries
     */

    fun setSqrKaratsuba(
        z: IntArray, zOff: Int,
        a: IntArray, aOff: Int, aLen: Int,
        t: IntArray
    ): Int {
        if (aLen < minLimbThreshold)
            return setSqrSchoolbook(z, zOff, a, aOff, aLen)

        val n = aLen
        val k0 = n / 2
        val k1 = n - k0
        require (zOff >= 0 && zOff + 2*n <= z.size)
        require (t.size >= (3*k1 + 3))

        setSqrKaratsuba(z, zOff,        a, aOff     , k0, t)
        setSqrKaratsuba(z, zOff + 2*k0, a, aOff + k0, k1, t)
        t.fill(0) // FIXME - not needed

        ksetAdd(t, a, aOff, k0, k1)

        t.fill(0, k1 + 1, 3*(k1 + 1))
        setSqrSchoolbook(t, k1 + 1, t, 0, k1 + 1)

        val z1Off = k1 + 1
        kmutSub(t, z1Off, z, zOff       , 2*k0)
        kmutSub(t, z1Off, z, zOff + 2*k0, 2*k1)

        val z1FullLen = 2 * (k1 + 1)
        kmutAddShifted(z, zOff, t, z1Off, z1FullLen, k0)

        val zLastIndex = 2*n - 1
        val zLastLimb = z[zOff + zLastIndex]
        val zNormLen = zLastIndex + (zLastLimb or -zLastLimb) ushr 31
        return zNormLen
    }

    // <<<<<<<<<< PRIMITIVES >>>>>>>>>

    /**
     * Returns the 32-bit limb `n` zero-extended to a 64-bit `ULong`.
     */
    private inline fun dw32(n: Int) = n.toUInt().toULong()

    /**
     * Adds two limb ranges from [a] into [t], with lengths [k0] and [k1], and
     * writes the final carry at index `k1`.
     *
     * Computes:
     * `t[0 ..< k1] = a[a0Off ..< a0Off + k0] + a[aOff + k0 ..< aOff + k0 + k1]`
     *
     * The invariant `k1 ≥ k0` must hold, and `k1 - k0 ≤ 1` (as guaranteed by
     * Karatsuba layout). The destination [t] does not need to be zero-initialized.
     * No allocation occurs; all carries are propagated explicitly.
     *
     * @param t destination array; must allow index `k1`
     * @param a source array containing both addends
     * @param aOff start of the first addend in [a]
     * @param k0 limb length of the first addend
     * @param k1 limb length of the second addend
     */
    fun ksetAdd(t: IntArray,
                a: IntArray, aOff: Int,
                k0: Int, k1: Int) {
        // 1. Setup absolute end boundaries
        val a1Off = aOff + k0
        val a1End = aOff + k0 + k1
        // The final carry is stored at t[k1], so we need t.size >= k1 + 1

        // 2. Dominating Check
        // This single block proves to the JIT that all subsequent accesses are safe.
        if (k0 < 0 || k1 < k0 ||
            aOff < 0 || a1Off > a.size ||
            a1End > a.size ||
            k1 >= t.size) {
            throw IllegalArgumentException()
        }

        var carry = 0uL

        // 3. Primary Summation Loop
        // By using '0 until k0', the JIT sees 'i' is bounded and can
        // eliminate range checks for t[i], a[a0Off + i], and a[a1Off + i].
        for (i in 0 until k0) {
            val tmp = dw32(a[aOff + i]) + dw32(a[a1Off + i]) + carry
            t[i] = tmp.toInt()
            carry = tmp shr 32
        }

        // 4. Handle the "extra" limb if k1 > k0
        // Because of Karatsuba constraints, this executes at most once.
        if (k0 < k1) {
            val tmp = dw32(a[a1Off + k0]) + carry
            t[k0] = tmp.toInt()
            carry = tmp shr 32
        }

        // 5. Store final carry
        // tEnd >= t.size check above ensures t[i] is safe here.
        t[k1] = carry.toInt()
    }

    fun kmutSub(t: IntArray, s2Off: Int,
                z: IntArray, xOff: Int, xLen: Int) {
        val start = s2Off
        val end = start + xLen

        // 1. Enhanced Dominating Check with Debug Info
        if (start < 0 || end > t.size || xOff < 0 || xOff + xLen > z.size) {
            throw IndexOutOfBoundsException(
                "kmutSub OOB: t.size=${t.size}, s2Off=$s2Off, xLen=$xLen, " +
                        "z.size=${z.size}, xOff=$xOff. End calculated as $end"
            )
        }

        var borrow = 0uL

        // 2. Primary Subtraction Loop
        for (i in 0 until xLen) {
            val tIdx = start + i
            val zIdx = xOff + i
            val tmp = dw32(t[tIdx]) - dw32(z[zIdx]) - borrow
            t[tIdx] = tmp.toInt()
            borrow = tmp shr 63
        }

        // 3. Instrumented Ripple Borrow Loop
        var k = end
        while (borrow != 0uL) {
            if (k >= t.size) {
                // This is a critical diagnostic:
                // It means the subtraction underflowed the entire workspace.
                throw IllegalStateException(
                    "Borrow escaped t.size! This implies (a0+a1)^2 < (a0^2 + a1^2), " +
                            "which is mathematically impossible for Karatsuba squaring."
                )
            }
            val tmp = dw32(t[k]) - borrow
            t[k] = tmp.toInt()
            borrow = tmp shr 63
            k++
        }
    }

    /**
     * Adds a limb range from [t] into [z] starting at an offset of [k0Shift] limbs.
     *
     * Computes:
     * `z[zOff + k0 ..< zOff + k0 + z1Len] += t[z1Off ..< z1Off + z1Len]`
     *
     * Carry is propagated forward in [z] until it clears or the end of the array
     * is reached. No allocation occurs.
     *
     * @param z destination array, mutated in place
     * @param zOff base offset in [z]
     * @param t source array containing the addend
     * @param z1Off starting index in [t]
     * @param z1Len number of limbs to add
     * @param k0Shift limb offset applied to the destination index
     */
    fun kmutAddShifted(z: IntArray, zOff: Int,
                       t: IntArray, z1Off: Int, z1Len: Int, k0Shift: Int) {
        val start = zOff + k0Shift
        val end = start + z1Len

        // 1. Corrected Dominating Check
        // If a carry can ripple, it could touch z[end].
        // We must prove that 'end' is a valid index if we want to ripple into it.
        if (start < 0 || end > z.size || z1Off < 0 || z1Off + z1Len > t.size) {
            throw IndexOutOfBoundsException()
        }

        var carry = 0uL

        // 2. Primary Addition Loop
        for (i in 0 until z1Len) {
            val tmp = dw32(z[start + i]) + dw32(t[z1Off + i]) + carry
            z[start + i] = tmp.toInt()
            carry = tmp shr 32
        }

        // 3. Ripple Carry
        var k = end
        // Use the actual size to ensure BCE, but the math
        // ensures carry becomes 0 before we run out of array.
        while (carry != 0uL && k < z.size) {
            val tmp = dw32(z[k]) + carry
            z[k] = tmp.toInt()
            carry = tmp shr 32
            k++
        }
    }

}