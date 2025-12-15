package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigIntAccumulator

class Barrett private constructor (val m: BigInt,
                                   val muBits: BigInt,
                                   val muLimbs: BigInt
) {
    val mSquared = m.sqr()
    val kBits = m.magnitudeBitLen()
    val kLimbs = (kBits + 0x1F) ushr 5
    val shiftKMinus1Bits = (kLimbs - 1) * 32
    val shiftKPlus1Bits  = (kLimbs + 1) * 32
    val bPowKPlus1 = BigInt.withSetBit(shiftKPlus1Bits)

    // Initial capacities are sized by bitLen to avoid resizing in modPow hot paths
    val q = BigIntAccumulator.Companion.withInitialBitCapacity(2*kBits + 32)
    val r = BigIntAccumulator.Companion.withInitialBitCapacity(kBits + 1)
    val r1 = BigIntAccumulator.Companion.withInitialBitCapacity(kBits + 32)
    val r2 = BigIntAccumulator.Companion.withInitialBitCapacity(2*kBits + 32)

    companion object {
        operator fun invoke(m: BigInt): Barrett {
            if (m.isNegative() || m <= 1)
                throw ArithmeticException("Barrett divisor must be >1")
            val muBits = calcMuBits(m)
            val muLimbs = calcMuLimbs(m)
            return Barrett(m, muBits, muLimbs)
        }

        private fun calcMuBits(m: BigInt): BigInt {
            check (m.isNormalized())
            val mBitLen = m.magnitudeBitLen()
            val x = BigInt.withSetBit(2 * mBitLen)
            val mu = x / m
            return mu
        }

        private fun calcMuLimbs(m: BigInt): BigInt {
            check (m.isNormalized())
            val mLimbLen = m.meta.normLen
            val x = BigInt.withSetBit(2 * mLimbLen * 32)
            val mu = x / m
            return mu
        }

    }

    fun reduce(x: BigInt): BigInt {
        val bitsAnswer = reduceBits(x)
        val limbsAnswer = reduceLimbs(x)
        check (bitsAnswer == limbsAnswer)
        val limbs2Answer = reduceLimbs2(x)
        check (bitsAnswer == limbs2Answer)
        val limbs3Answer = reduceLimbs3(x)
        check (bitsAnswer == limbs3Answer)
        //val limbs4Answer = reduceLimbs4(x)
        //check (limbsAnswer == limbs4Answer)
        return bitsAnswer
    }

    fun reduceBits(x: BigInt): BigInt {
        require(x >= 0)
        require(x < mSquared)

        if (x < m) return x

        // q = floor(x * μ / 2^(2k))
        val q = (x * muBits) shr (2*kBits)

        // r = x - q*m
        var r = x - q*m

        if (r >= m) r -= m
        return r
    }

    fun reduceLimbs(x: BigInt): BigInt {
        require (x >= 0)
        require (x < mSquared)
        if (x < m) return x

        // q1 = floor(x / b**(k - 1))
        val q1 = x ushr ((kLimbs - 1) * 32)
        // q2 = q1 * mu
        val q2 = q1 * muLimbs
        // q3 = floor(q2 / b**(k + 1))
        val q3 = q2 ushr ((kLimbs + 1) * 32)
        // r1 = x % b**(k + 1)
        val r1 = x and BigInt.Companion.withBitMask((kLimbs + 1) * 32)
        // r2 = (q3 * m) % b**(k + 1)
        val r2 = (q3 * m) and BigInt.Companion.withBitMask((kLimbs + 1) * 32)
        // r = r1 - r2
        var r = r1 - r2
        if (r.isNegative())
            r += bPowKPlus1
        while (r >= m)
            r = r - m
        return r
    }

    fun reduceLimbs2(x: BigInt): BigInt {
        require (x >= 0)
        require (x < mSquared)
        if (x < m) return x

        // q1 = floor(x / b**(k - 1))
        val q1 = x ushr shiftKMinus1Bits
        // q2 = q1 * mu
        val q2 = q1 * muLimbs
        // q3 = floor(q2 / b**(k + 1))
        val q3 = q2 ushr shiftKPlus1Bits
        // r1 = x % b**(k + 1)
        val r1 = x and BigInt.Companion.withBitMask(shiftKPlus1Bits)
        // r2 = (q3 * m) % b**(k + 1)
        val r2 = (q3 * m) and BigInt.Companion.withBitMask(shiftKPlus1Bits)
        // r = r1 - r2
        var r = r1 - r2
        if (r.isNegative())
            r += r + bPowKPlus1
        while (r >= m)
            r = r - m
        return r
    }

    fun reduceLimbs3(x: BigInt): BigInt {
        require (x >= 0)
        require (x < mSquared)
        if (x < m) return x

        // q1 = floor(x / b**(k - 1))
        //val q1 = x ushr ((kLimbs - 1) * 32)
        q.set(x)
        q.mutShr(shiftKMinus1Bits)
        // q2 = q1 * mu
        //val q2 = q1 * muLimbs
        q *= muLimbs
        // q3 = floor(q2 / b**(k + 1))
        //val q3 = q2 ushr ((kLimbs + 1) * 32)
        q.mutShr(shiftKPlus1Bits)

        // r1 = x % b**(k + 1)
        //val r1 = x and BigInt.withBitMask((kLimbs + 1) * 32)
        r1.set(x)
        r1.applyBitMask(shiftKPlus1Bits)
        // r2 = (q3 * m) % b**(k + 1)
        //val r2 = (q3 * m) and BigInt.withBitMask((kLimbs + 1) * 32)
        r2.setMul(q, m)
        r2.applyBitMask(shiftKPlus1Bits)
        //var r = r1 - r2
        r.setSub(r1, r2)
        //if (r.isNegative())
        //    r = r + BigInt.withSetBit((kLimbs + 1) * 32)
        if (r.isNegative())
            r += bPowKPlus1

        if (r >= m) r -= m
        if (r >= m) r -= m

        return r.toBigInt()
    }

    fun reduceInto(x: BigIntAccumulator, out: BigIntAccumulator) {
        require (x >= 0)
        require (x < mSquared)
        if (x < m) {
            out.set(x)
            return
        }

        // q1 = floor(x / b**(k - 1))
        //val q1 = x ushr ((kLimbs - 1) * 32)
        q.set(x)
        q.mutShr(shiftKMinus1Bits)
        // q2 = q1 * mu
        //val q2 = q1 * muLimbs
        q *= muLimbs
        // q3 = floor(q2 / b**(k + 1))
        //val q3 = q2 ushr ((kLimbs + 1) * 32)
        q.mutShr(shiftKPlus1Bits)

        // r1 = x % b**(k + 1)
        //val r1 = x and BigInt.withBitMask((kLimbs + 1) * 32)
        r1.set(x)
        r1.applyBitMask(shiftKPlus1Bits)
        // r2 = (q3 * m) % b**(k + 1)
        //val r2 = (q3 * m) and BigInt.withBitMask((kLimbs + 1) * 32)
        r2.setMul(q, m)
        r2.applyBitMask(shiftKPlus1Bits)
        //var r = r1 - r2
        r.setSub(r1, r2)
        //if (r.isNegative())
        //    r = r + BigInt.withSetBit((kLimbs + 1) * 32)
        if (r.isNegative())
            r += bPowKPlus1

        if (r >= m) r -= m
        if (r >= m) r -= m

        out.set(r)
    }

    fun modMul(a: BigInt, b: BigInt, out: BigIntAccumulator) {
        val tmp: BigIntAccumulator = this.q
        tmp.setMul(a, b)
        reduceInto(tmp, out)
    }

    fun modSqr(a: BigInt, out: BigIntAccumulator) {
        val tmp: BigIntAccumulator = this.q
        tmp.setSqr(a)
        reduceInto(tmp, out)
    }

}