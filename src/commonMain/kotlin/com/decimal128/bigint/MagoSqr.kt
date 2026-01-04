// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.Mago.setMulSchoolbook
import com.decimal128.bigint.intrinsic.unsignedMulHi

/**
 * Squaring kernels for arbitrary-precision integers in
 * [Magia] MAGnitude IntArray little endian format with
 * accompanying `normLen` normalized length.
 *
 * Contains optimized implementations for multiple limb sizes,
 * from small unrolled cases to generic schoolbook and Karatsuba.
 *
 * Current transition points for different techniques were
 * determined on a circa-2019 8-Core Intel Core i9.
 * Comba-style mul/sqr with reduced memory read/write proved
 * to be slower than schoolbook, presumably due to larger L1
 * cache. Schoolbook squaring proved faster than karatsuba
 * up to approx 84 limbs == approx  2700 bits.
 */
internal object MagoSqr {

    const val KARATSUBA_SQR_THRESHOLD = 84

    const val SCHOOLBOOK_SQR_THRESHOLD = 19

    /**
     * Returns the 32-bit limb `n` zero-extended to a 64-bit `ULong`.
     */
    inline fun dw32(n: Int) = n.toUInt().toULong()


    /**
     * Squares `x[0 .. xNormLen)` and stores the result in `z`.
     *
     * Uses specialized unrolled implementations for small limb counts,
     * falling back to schoolbook squaring for larger inputs.
     *
     * Karatsuba has a separate [setSqrKaratsuba] entry point because
     * of its requirement for an additional intermediate `z` limb and
     * its use of temp storage.
     *
     * Requirements:
     * - `z.size ≥ 2 * xNormLen`
     *
     * @return normalized limb length of the result
     */
    fun setSqr(z: Magia, x: Magia, xNormLen: Int): Int {
        return when {
            xNormLen == 0 -> 0
            xNormLen == 1 -> setSqr1Limb(z, x)
            xNormLen == 2 -> setSqr2Limbs(z, x)
            xNormLen == 3 -> setSqr3Limbs(z, x)
            xNormLen == 4 -> setSqr4Limbs(z, x)
            xNormLen < SCHOOLBOOK_SQR_THRESHOLD ->
                // overhead cost for squaring, including doubling
                // of the cross terms, overwhelms simple
                // multiplication for quite a while
                setMulSchoolbook(z, x, xNormLen, x, xNormLen)
            else ->
                setSqrSchoolbook(z, 0, x, 0, xNormLen)
        }
    }

    /**
     * Squares `x[0 .. xNormLen)` using schoolbook multiplication and
     * writes the result into `z` starting at index 0.
     *
     * @return normalized limb length of the result
     */
    inline fun setSqrSchoolbook(z: Magia, x: Magia, xNormLen: Int): Int =
        setSqrSchoolbook(z, 0, x, 0, xNormLen)

    /**
     * Squares `x[xOff .. xOff+xNormLen)` using schoolbook multiplication and
     * writes the result into `z[zOff .. zOff+2*xNormLen)`.
     *
     * Cross terms `a[i]*a[j] (i<j)` are accumulated once, then doubled in a
     * linear pass. Diagonal terms `a[i]^2` are added afterward into columns
     * `2*i` with carry propagation.
     *
     * @return normalized limb length of the result
     */
    fun setSqrSchoolbook(z: Magia, zOff: Int, x: Magia, xOff: Int, xNormLen: Int): Int {
        if (xNormLen == 0)
            return 0
        val zLen = 2 * xNormLen
        check(zOff >= 0 && zOff + zLen <= z.size)
        z.fill(0, zOff, zOff + zLen)

        check(xOff >= 0 && xOff + xNormLen <= x.size)
        // 1) Cross terms: i < j
        // We compute the sum of all a[i]*a[j] where i < j
        for (i in 0 until xNormLen - 1) {
            val ai = dw32(x[xOff + i])
            var carry = 0uL
            for (j in i + 1 until xNormLen) {
                val k = i + j
                // Standard row-multiply accumulation
                val t = ai * dw32(x[xOff + j]) + dw32(z[zOff + k]) + carry
                z[zOff + k] = t.toInt()
                carry = t shr 32
            }
            z[zOff + i + xNormLen] = carry.toInt()
        }

        // 2) Double the cross terms: z = z * 2
        // This is much faster than doubling inside the loop because it's a linear scan

        var shiftCarry = 0uL
        for (i in 0 until zLen) {
            val zi = dw32(z[zOff + i])
            val t = (zi shl 1) or shiftCarry
            z[zOff + i] = t.toInt()
            shiftCarry = t shr 32
        }

        // 3) Diagonals: add a[i]^2 into column 2*i
        // We add these directly into the doubled cross-terms
        for (i in 0 until xNormLen) {
            var k = 2 * i
            val zk = dw32(z[zOff + k])
            val ai = dw32(x[xOff + i])
            val sqa = ai * ai + zk

            // Add low 32 bits
            z[zOff + k] = sqa.toInt()
            ++k
            // Add high 32 bits + carry
            var carry = dw32(z[zOff + k]) + (sqa shr 32)
            z[zOff + k] = carry.toInt()
            carry = carry shr 32
            ++k

            while (carry != 0uL && k < zLen) {
                carry = dw32(z[zOff + k]) + carry
                z[zOff + k] = carry.toInt()
                carry = carry shr 32
                ++k
            }
        }

        // Normalization
        val lastIndex = zLen - 1
        val lastLimb = z[zOff + lastIndex]
        val zNormLen = lastIndex + ((lastLimb or -lastLimb) ushr 31)
        return zNormLen
    }

    /**
     * Squares a single 32-bit unsigned limb `a[0]` into `z`.
     *
     * @return normalized limb length (1 or 2)
     */
    private inline fun setSqr1Limb(z: Magia, a: Magia): Int {
        val dw = a[0].toUInt().toULong()
        val sq = dw * dw
        val hi = sq shr 32
        z[1] = hi.toInt()
        z[0] = sq.toInt()
        return (-hi.toLong() ushr 63).toInt() + 1
    }

    /**
     * Squares a 2-limb unsigned magnitude `a` into `z` using 128-bit multiply.
     *
     * @return normalized limb length (3 or 4)
     */
    private inline fun setSqr2Limbs(z: Magia, a: Magia): Int {
        val dw = (a[1].toULong() shl 32) or a[0].toUInt().toULong()
        val sqLo = dw * dw
        val sqHi = unsignedMulHi(dw, dw)
        val hiLimb = (sqHi shr 32).toInt()
        z[3] = hiLimb
        z[2] = sqHi.toInt()
        z[1] = (sqLo shr 32).toInt()
        z[0] = sqLo.toInt()
        return if (hiLimb == 0) 3 else 4
    }

    /**
     * Squares a 3-limb unsigned magnitude `a` into `z` using a fully unrolled,
     * carry-safe schoolbook implementation.
     *
     * @return normalized limb length (5 or 6)
     */
    private inline fun setSqr3Limbs(z: Magia, a: Magia): Int {
        val a0 = dw32(a[0])   // ULong, 0..2^32-1
        val a1 = dw32(a[1])
        val a2 = dw32(a[2])

        val s0 = a0 * a0
        val s1 = a1 * a1
        val s2 = a2 * a2
        val c01 = a0 * a1
        val c02 = a0 * a2
        val c12 = a1 * a2

        // carry is in "32-bit limbs" units: carry == next inbound value to add into column
        var carry = s0 shr 32

        // z[0]
        z[0] = s0.toInt()

        // ---- column 1: 2*c01 + carry ----
        run {
            var lo = carry                      // fits in ULong
            var hi = 0uL                        // counts 2^64 units

            // add (c01 << 1) with overflow bit (c01 >>> 63)
            val dLo = c01 shl 1
            val dHi = c01 shr 63               // 0 or 1

            val old = lo
            lo += dLo
            if (lo < old) hi++                  // 64-bit add overflow
            hi += dHi

            z[1] = lo.toInt()
            carry = (lo shr 32) + (hi shl 32)
        }

        // ---- column 2: s1 + 2*c02 + carry ----
        run {
            var lo = carry
            var hi = 0uL

            // + s1
            var old = lo
            lo += s1
            if (lo < old) hi++

            // + (c02 << 1) with overflow bit
            val dLo = c02 shl 1
            val dHi = c02 shr 63

            old = lo
            lo += dLo
            if (lo < old) hi++
            hi += dHi

            z[2] = lo.toInt()
            carry = (lo shr 32) + (hi shl 32)
        }

        // ---- column 3: 2*c12 + carry ----
        run {
            var lo = carry
            var hi = 0uL

            val dLo = c12 shl 1
            val dHi = c12 shr 63

            val old = lo
            lo += dLo
            if (lo < old) hi++
            hi += dHi

            z[3] = lo.toInt()
            carry = (lo shr 32) + (hi shl 32)
        }

        // ---- column 4: s2 + carry ----
        run {
            val t = carry + s2                  // this sum fits in <= 66 bits; still safe in ULong
            z[4] = t.toInt()
            z[5] = (t shr 32).toInt()
        }

        return if (z[5] == 0) 5 else 6
    }

    /**
     * Squares a 4-limb unsigned magnitude `a` into `z` using a fully unrolled,
     * carry-safe schoolbook implementation.
     *
     * @return normalized limb length (7 or 8)
     */
    private inline fun setSqr4Limbs(z: Magia, a: Magia): Int {
        val a0 = dw32(a[0]);
        val a1 = dw32(a[1])
        val a2 = dw32(a[2]);
        val a3 = dw32(a[3])

        val s0 = a0 * a0;
        val s1 = a1 * a1;
        val s2 = a2 * a2;
        val s3 = a3 * a3
        val c01 = a0 * a1;
        val c02 = a0 * a2;
        val c03 = a0 * a3
        val c12 = a1 * a2;
        val c13 = a1 * a3;
        val c23 = a2 * a3

        z[0] = s0.toInt()
        var carry = s0 shr 32

        // Column 1: 2*c01 + carry
        run {
            var lo = carry;
            var hi = 0uL
            val dLo = c01 shl 1;
            val dHi = c01 shr 63
            val old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            z[1] = lo.toInt(); carry = (lo shr 32) + (hi shl 32)
        }

        // Column 2: s1 + 2*c02 + carry
        run {
            var lo = carry;
            var hi = 0uL
            var old = lo; lo += s1
            if (lo < old) hi++
            val dLo = c02 shl 1;
            val dHi = c02 shr 63
            old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            z[2] = lo.toInt(); carry = (lo shr 32) + (hi shl 32)
        }

        // Column 3: 2*c03 + 2*c12 + carry (The n=4 Peak)
        run {
            var lo = carry;
            var hi = 0uL
            // + 2*c03
            var dLo = c03 shl 1;
            var dHi = c03 shr 63
            var old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            // + 2*c12
            dLo = c12 shl 1; dHi = c12 shr 63
            old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            z[3] = lo.toInt(); carry = (lo shr 32) + (hi shl 32)
        }

        // Column 4: s2 + 2*c13 + carry
        run {
            var lo = carry;
            var hi = 0uL
            var old = lo; lo += s2
            if (lo < old) hi++
            val dLo = c13 shl 1;
            val dHi = c13 shr 63
            old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            z[4] = lo.toInt(); carry = (lo shr 32) + (hi shl 32)
        }

        // Column 5: 2*c23 + carry
        run {
            var lo = carry;
            var hi = 0uL
            val dLo = c23 shl 1;
            val dHi = c23 shr 63
            val old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            z[5] = lo.toInt(); carry = (lo shr 32) + (hi shl 32)
        }

        // Column 6 & 7: s3 + carry
        run {
            val t = carry + s3
            z[6] = t.toInt()
            val z7 = (t shr 32).toInt()
            z[7] = z7
            return if (z7 == 0) 7 else 8
        }
    }

    /**
     * Squares `x[0..xNormLen)` using Karatsuba and writes the result into `z`.
     *
     * Requirements:
     * - `z.size ≥ 2*xNormLen + 1` (extra headroom for intermediate carry/borrow propagation)
     * - `tmp`, if provided, has size ≥ `3*((xNormLen+1)/2) + 3`
     *
     * @return normalized limb length of the result
     */
    fun setSqrKaratsuba(z: Magia, x: Magia, xNormLen: Int, tmp: IntArray? = null): Int {
        val k1 = (xNormLen + 1) / 2
        val tmpSize = 3 * k1 + 3
        verify { z.size >= 2 * xNormLen + 1 }
        verify { tmp == null || tmp.size >= tmpSize }
        val t = tmp ?: IntArray(tmpSize)
        karatsubaSqr(z, 0, x, 0, xNormLen, t)
        val zLastIndex = 2 * xNormLen - 1
        val zLastLimb = z[zLastIndex]
        val zNormLen = zLastIndex + ((zLastLimb or -zLastLimb) ushr 31)
        return zNormLen
    }

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
    fun karatsubaSqr(
        z: IntArray, zOff: Int,
        a: IntArray, aOff: Int, aLen: Int,
        t: IntArray
    ) {
        if (aLen < KARATSUBA_SQR_THRESHOLD) {
            setSqrSchoolbook(z, zOff, a, aOff, aLen)
            return
        }

        val n = aLen
        val k0 = n / 2
        val k1 = n - k0
        require (zOff >= 0 && zOff + 2*n <= z.size)
        require (t.size >= (3*k1 + 3))

        // square lo half of a into z as z0
        karatsubaSqr(z, zOff,        a, aOff     , k0, t)
        // square hi half of a into z as z1
        karatsubaSqr(z, zOff + 2*k0, a, aOff + k0, k1, t)
        // add a0 and a1 as s into t
        ksetAdd(t, a, aOff, k0, k1)
        // square s to s2 higher in t
        setSqrSchoolbook(t, k1 + 1, t, 0, k1 + 1)
        // subtract z0 from s2
        val z1Off = k1 + 1
        kmutSub(t, z1Off, z, zOff, 2 * k0)
        // subtract z2 from s2
        kmutSub(t, z1Off, z, zOff + 2 * k0, 2 * k1)
        // add shifted s2 as z1 into z
        val z1FullLen = 2 * (k1 + 1)
        kmutAddShifted(z, zOff, t, z1Off, z1FullLen, k0)
        // and we're done
    }

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

    /**
     * In-place subtraction of `z[xOff .. xOff+xLen)` from `t[s2Off .. s2Off+xLen)`,
     * with full borrow propagation into higher limbs of `t`.
     *
     * Throws if the borrow escapes `t`, indicating a violated Karatsuba invariant.
     */
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
