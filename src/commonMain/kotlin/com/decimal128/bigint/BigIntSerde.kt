// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import kotlin.math.max

object BigIntSerde {

    /**
     * Converts the value described by [meta] and [magia] to a big-endian two’s-complement
     * byte array.
     *
     * The result uses the minimal number of bytes required to represent the value,
     * but is always at least one byte long. Negative values are encoded using standard
     * two’s-complement form.
     *
     * @param meta metadata providing the sign and normalized limb length.
     * @param magia magnitude limb array in little-endian order.
     * @return a big-endian two’s-complement byte array.
     */
    fun toTwosComplementBigEndianByteArray(bi: BigIntNumber): ByteArray =
        toBinaryByteArray(bi, isTwosComplement = true, isBigEndian = true)

    /**
     * Converts the value described by [meta] and [magia] to a binary [ByteArray].
     *
     * The output format is controlled by [isTwosComplement] and [isBigEndian]. The returned
     * array uses the minimal number of bytes required to represent the value, but is always
     * at least one byte long.
     *
     * @param meta metadata providing the sign and normalized limb length.
     * @param magia magnitude limb array in little-endian order.
     * @param isTwosComplement whether to encode negative values using two’s-complement form.
     * @param isBigEndian whether the output byte order is big-endian (`true`) or little-endian (`false`).
     * @return a [ByteArray] containing the binary representation of the value.
     *
     * @throws IllegalArgumentException if the magnitude is not normalized.
     */
    fun toBinaryByteArray(bi: BigIntNumber, isTwosComplement: Boolean, isBigEndian: Boolean): ByteArray {
        check (bi.isNormalized())
        if (bi.meta.normLen >= 0 && bi.meta.normLen <= bi.magia.size) {
            val bitLen =
                if (isTwosComplement)
                    bi.bitLengthBigIntegerStyle() + 1
                else max(bi.magnitudeBitLen(), 1)
            val byteLen = (bitLen + 7) ushr 3
            val bytes = ByteArray(byteLen)
            toBinaryBytes(bi, isTwosComplement, isBigEndian, bytes, 0, byteLen)
            return bytes
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Writes the value described by [meta] and [magia] into [bytes] using the requested
     * binary format, without allocating.
     *
     * The encoding is controlled by [isTwosComplement] and [isBigEndian]. If
     * [requestedLen] ≤ 0, the minimal number of bytes required is written (always at
     * least one). If [requestedLen] > 0, exactly that many bytes are written, with
     * sign-extension applied if needed.
     *
     * @param meta metadata providing the sign and normalized limb length.
     * @param magia magnitude limb array in little-endian order.
     * @param isTwosComplement whether to encode negative values using two’s-complement form.
     * @param isBigEndian whether bytes are written in big-endian (`true`) or little-endian (`false`) order.
     * @param bytes destination array to write into.
     * @param offset start index in [bytes].
     * @param requestedLen number of bytes to write, or ≤ 0 to write the minimal length.
     * @return the number of bytes written.
     *
     * @throws IndexOutOfBoundsException if [bytes] is too small.
     * @throws IllegalStateException if [meta] normLen does not fit size of [magia]
     */
    fun toBinaryBytes(bi: BigIntNumber,
                      isTwosComplement: Boolean, isBigEndian: Boolean,
                      bytes: ByteArray, offset: Int = 0, requestedLen: Int = -1
    ): Int {
        check (bi.isNormalized())
        if (bi.meta.normLen >= 0 && bi.meta.normLen <= bi.magia.size &&
            offset >= 0 && (requestedLen <= 0 || requestedLen <= bytes.size - offset)) {

            val actualLen = if (requestedLen > 0) requestedLen else {
                val bitLen = if (isTwosComplement)
                    bi.bitLengthBigIntegerStyle() + 1
                else
                    max(bi.magnitudeBitLen(), 1)
                (bitLen + 7) ushr 3
            }

            // calculate offsets and stepping direction for BE BigEndian vs LE LittleEndian
            val offB1 = if (isBigEndian) -1 else 1 // BE == -1, LE ==  1
            val offB2 = offB1 shl 1                // BE == -2, LE ==  2
            val offB3 = offB1 + offB2              // BE == -3, LE ==  3
            val step1LoToHi = offB1                // BE == -1, LE ==  1
            val step4LoToHi = offB1 shl 2          // BE == -4, LE ==  4

            val ibLast = offset + actualLen - 1
            val ibLsb = if (isBigEndian) ibLast else offset // index Least significant byte
            val ibMsb = if (isBigEndian) offset else ibLast // index Most significant byte

            val negativeMask = if (bi.meta.isNegative) -1 else 0

            var remaining = actualLen

            var ib = ibLsb
            var iw = 0

            var carry = -negativeMask.toLong() // if (isNegative) then carry = 1 else 0

            while (remaining >= 4 && iw < bi.meta.normLen) {
                val v = bi.magia[iw++]
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
                val v = if (iw < bi.meta.normLen) bi.magia[iw++] else 0
                var w = (v xor negativeMask).toLong() + carry.toInt()
                do {
                    bytes[ib] = w.toByte()
                    ib += step1LoToHi
                    w = w shr 8
                } while (--remaining > 0)
            }
            check (iw == bi.meta.normLen)
            check(ib - step1LoToHi == ibMsb)
            return actualLen
        }
        throw IllegalStateException()
    }

    /**
     * Constructs a [Mago] magnitude from a sequence of raw binary bytes.
     *
     * The input bytes represent a non-negative magnitude if [isNegative] is `false`,
     * or a two’s-complement negative number if [isNegative] is `true`. In the latter case,
     * the bytes are complemented and incremented during decoding to produce the corresponding
     * positive magnitude. The sign itself is handled by the caller.
     *
     * The bytes may be in either big-endian or little-endian order, as indicated by [isBigEndian].
     *
     * The return value will be canonical ZERO or a normalized Magia
     *
     * @param isNegative  `true` if bytes encode a negative value in two’s-complement form.
     * @param isBigEndian `true` if bytes are in big-endian order, `false` for little-endian.
     * @param bytes       Source byte array.
     * @param off         Starting offset in [bytes].
     * @param len         Number of bytes to read.
     * @return a normalized [Magia] or ZERO
     * @throws IllegalArgumentException if the range `[off, off + len)` is invalid.
     */
    internal fun fromBinaryBytes(isNegative: Boolean, isBigEndian: Boolean,
                                 bytes: ByteArray, off: Int, len: Int): Magia {
        if (off < 0 || len < 0 || len > bytes.size - off)
            throw IllegalArgumentException()
        if (len == 0)
            return Mago.ZERO

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
                return if (isNegative) Mago.ONE else Mago.ZERO
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
     * Returns a copy of the magnitude as a little-endian IntArray.
     *
     * - Least significant limb is at index 0.
     * - Sign is ignored; only the magnitude is returned.
     *
     * @return a new IntArray containing the magnitude in little-endian order
     */
    fun magnitudeToLittleEndianIntArray(bi: BigIntNumber): IntArray =
        bi.magia.copyOf(bi.meta.normLen)

    /**
     * Returns a copy of the magnitude as a little-endian LongArray.
     *
     * - Combines every two 32-bit limbs into a 64-bit long.
     * - Least significant bits are in index 0.
     * - Sign is ignored; only the magnitude is returned.
     *
     * @return a new LongArray containing the magnitude in little-endian order
     */
    fun magnitudeToLittleEndianLongArray(bi: BigIntNumber): LongArray {
        val intLen = bi.meta.normLen
        val longLen = (intLen + 1) ushr 1
        val z = LongArray(longLen)
        var iw = 0
        var il = 0
        while (intLen - iw >= 2) {
            val lo = bi.magia[iw].toUInt().toLong()
            val hi = bi.magia[iw + 1].toLong() shl 32
            z[il] = hi or lo
            ++il
            iw += 2
        }
        if (iw < intLen)
            z[il] = bi.magia[iw].toUInt().toLong()
        return z
    }

}