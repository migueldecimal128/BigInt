package com.decimal128.bigint

import kotlin.math.min

class Barrett private constructor (val m: BigInt,
                                   val muBits: BigInt,
                                   val muLimbs: BigInt,
) {
    val mSquared = m.sqr().normalize()
    val mSquaredMagia = mSquared.magia
    val mMagia = m.magia
    val muLimbsMagia = muLimbs.magia
    val kBits = m.magnitudeBitLen()
    val kLimbs = mMagia.size
    val kLimbsMinus1 = kLimbs - 1
    val kLimbsPlus1 = kLimbs + 1
    val tmp1 = IntArray(kLimbsPlus1 + 1)
    val tmp2 = IntArray(tmp1.size * 2)

    companion object {
        operator fun invoke(m: BigInt): Barrett {
            if (m.isNegative() || m <= 1)
                throw ArithmeticException("Barrett divisor must be >1")
            val mNormalized = m.normalize()
            val muBits = calcMuBits(mNormalized).normalize()
            val muLimbs = calcMuLimbs(mNormalized).normalize()
            return Barrett(mNormalized, muBits, muLimbs)
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
            val mLimbLen = m.magia.size
            val x = BigInt.withSetBit(2 * mLimbLen * 32)
            val mu = x / m
            return mu
        }

    }

    fun remainder(x: BigInt): BigInt {
        val bitsAnswer = reduceBits(x)
        val limbsAnswer = reduceLimbs(x)
        check (bitsAnswer == limbsAnswer)
        val limbs2Answer = reduceLimbs2(x)
        check (limbsAnswer == limbs2Answer)
        val limbs3Answer = reduceLimbs3(x)
        check (limbsAnswer == limbs3Answer)
        //val limbs4Answer = reduceLimbs4(x)
        //check (limbsAnswer == limbs4Answer)
        return limbsAnswer
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
        val r1 = x and BigInt.withBitMask((kLimbs + 1) * 32)
        // r2 = (q3 * m) % b**(k + 1)
        val r2 = (q3 * m) and BigInt.withBitMask((kLimbs + 1) * 32)
        // r = r1 - r2
        var r = r1 - r2
        if (r.isNegative())
            r = r + BigInt.withSetBit((kLimbs + 1) * 32)
        while (r >= m)
            r = r - m
        return r
    }

    fun reduceLimbs2(x: BigInt): BigInt {
        require (x >= 0)
        require (x < mSquared)
        if (x < m) return x

        // q1 = floor(x / b**(k - 1))
        val q1 = x ushr (kLimbsMinus1 * 32)
        // q2 = q1 * mu
        val q2 = q1 * muLimbs
        // q3 = floor(q2 / b**(k + 1))
        val q3 = q2 ushr (kLimbsPlus1 * 32)
        // r1 = x % b**(k + 1)
        val r1 = x and BigInt.withBitMask(kLimbsPlus1 * 32)
        // r2 = (q3 * m) % b**(k + 1)
        val r2 = (q3 * m) and BigInt.withBitMask(kLimbsPlus1 * 32)
        // r = r1 - r2
        var r = r1 - r2
        if (r.isNegative())
            r = r + BigInt.withSetBit(kLimbsPlus1 * 32)
        while (r >= m)
            r = r - m
        return r
    }


    fun reduceLimbs3(x: BigInt): BigInt {
        require (x >= 0)
        require (x < mSquared)

        val xMagia = x.magia
        val xLen = Magia.normalizedLimbLen(xMagia)

        if (x < m) {
            check (Magia.compare(x.magia, xLen, mMagia, mMagia.size) < 0)
            return x
        }

        // q1 = floor(x / b**(k - 1))
        val q1 = x ushr (kLimbsMinus1 * 32)
        val mq1Len = xLen - kLimbsMinus1
        val mq1 = IntArray(mq1Len)
        x.magia.copyInto(mq1, 0, kLimbsMinus1, xLen)
        check (Magia.compare(q1.magia, Magia.normalizedLimbLen(q1.magia), mq1, mq1Len) == 0)

        // q2 = q1 * mu
        val q2 = q1 * muLimbs
        val mq2 = IntArray(muLimbs.magia.size * 2)
        val mq2Len = Magia.setMul(mq2, mq1, mq1Len, muLimbs.magia, muLimbs.magia.size)
        check (Magia.compare(q2.magia, Magia.normalizedLimbLen(q2.magia), mq2, mq2Len) == 0)

        // q3 = floor(q2 / b**(k + 1))
        val q3 = q2 ushr (kLimbsPlus1 * 32)
        val mq3 = IntArray(kLimbs * 2)
        val mq3Len = mq2Len - kLimbsPlus1
        mq2.copyInto(mq3, 0, kLimbsPlus1, mq2Len)
        check (Magia.compare(q3.magia, Magia.normalizedLimbLen(q3.magia), mq3, mq3Len) == 0)

        // r1 = x % b**(k + 1)
        val r1 = x and BigInt.withBitMask(kLimbsPlus1 * 32)
        val mr1 = IntArray(kLimbsPlus1)
        xMagia.copyInto(mr1, 0, 0, min(kLimbsPlus1, xLen))
        val mr1Len = Magia.normalizedLimbLen(mr1, kLimbsPlus1)
        check (Magia.compare(r1.magia, Magia.normalizedLimbLen(r1.magia), mr1, mr1Len) == 0)
        // r2 = (q3 * m) % b**(k + 1)
        val pq3m = q3 * m
        val mpq3m = IntArray(2 * kLimbs)
        val mpq3mLen = Magia.setMul(mpq3m, mq3, mq3Len, mMagia, mMagia.size)
        check (Magia.compare(pq3m.magia, Magia.normalizedLimbLen(pq3m.magia), mpq3m, mpq3mLen) == 0)

        val r2 = pq3m and BigInt.withBitMask(kLimbsPlus1 * 32)
        val mr2 = IntArray(kLimbsPlus1)
        mpq3m.copyInto(mr2, 0, 0, kLimbsPlus1)
        val mr2Len = Magia.normalizedLimbLen(mr2, kLimbsPlus1)
        check (Magia.compare(r2.magia, Magia.normalizedLimbLen(r2.magia), mr2, mr2Len) == 0)
        // r = r1 - r2
        //var r = r1 - r2
        var r = r1
        val mr = mr1
        var mrLen = mr1Len
        if (r < r2) {
            r += BigInt.withSetBit(kLimbsPlus1 * 32)
            check (mrLen == kLimbsPlus1)
            check (mr[kLimbsPlus1] == 0)
            mr[kLimbsPlus1] = 1
            mrLen = kLimbsPlus1 + 1
        }
        r -= r2
        mrLen = Magia.mutateSub(mr, mrLen, mr2, mr2Len)
        check (Magia.compare(r.magia, Magia.normalizedLimbLen(r.magia), mr, mrLen) == 0)

        while (r >= m) {
            check (Magia.compare(r.magia, Magia.normalizedLimbLen(r.magia), mMagia, mMagia.size) >= 0)
            r -= m
            mrLen = Magia.mutateSub(mr, mrLen, mMagia, mMagia.size)
            check (Magia.compare(r.magia, Magia.normalizedLimbLen(r.magia), mr, mrLen) == 0)
        }
        val magiaR = BigInt.fromLittleEndianIntArray(false, mr, mrLen)
        check (Magia.compare(r.magia, Magia.normalizedLimbLen(r.magia),
            magiaR.magia, magiaR.magia.size) == 0)

        return r
    }

    fun reduceLimbs4(x: BigInt): BigInt {
        require (x >= 0)
        require (x < mSquared)

        if (x < m)
            return x

        val xMagia = x.magia
        val xLen = Magia.normalizedLimbLen(xMagia)

        // q1 = floor(x / b**(k - 1))
        // val q1 = x ushr (kLimbsMinus1 * 32)
        val q1Len = xLen - kLimbsMinus1
        check (q1Len <= kLimbsPlus1)
        val q1Magia = IntArray(q1Len)
        x.magia.copyInto(q1Magia, 0, kLimbsMinus1, xLen)
        //check (Magia.compare(q1.magia, Magia.normalizedLimbLen(q1.magia), q1Magia, q1Len) == 0)

        // q2 = q1 * mu
        // val q2 = q1 * muLimbs
        println("mulimbs.magia.size:${muLimbs.magia.size}  kLimbs:$kLimbs")
        check (muLimbs.magia.size == kLimbs)
        val q2Magia = IntArray(muLimbs.magia.size * 2)
        val q2Len = Magia.setMul(q2Magia, q1Magia, q1Len, muLimbs.magia, muLimbs.magia.size)
        // check (Magia.compare(q2.magia, Magia.normalizedLimbLen(q2.magia), q2Magia, q2Len) == 0)

        // q3 = floor(q2 / b**(k + 1))
        // val q3 = q2 ushr (kLimbsPlus1 * 32)
        val q3Magia = IntArray(kLimbs * 2)
        val q3Len = q2Len - kLimbsPlus1
        q2Magia.copyInto(q3Magia, 0, kLimbsPlus1, q2Len)
        // check (Magia.compare(q3.magia, Magia.normalizedLimbLen(q3.magia), q3Magia, q3Len) == 0)

        // r1 = x % b**(k + 1)
        // val r1 = x and BigInt.withBitMask(kLimbsPlus1 * 32)
        val r1Magia = IntArray(kLimbsPlus1)
        xMagia.copyInto(r1Magia, 0, 0, min(kLimbsPlus1, xLen))
        val r1Len = Magia.normalizedLimbLen(r1Magia, kLimbsPlus1)
        // check (Magia.compare(r1.magia, Magia.normalizedLimbLen(r1.magia), r1Magia, r1Len) == 0)
        // r2 = (q3 * m) % b**(k + 1)
        // val pq3m = q3 * m
        val pMagia = IntArray(2 * kLimbs)
        val pLen = Magia.setMul(pMagia, q3Magia, q3Len, mMagia, mMagia.size)
        // check (Magia.compare(pq3m.magia, Magia.normalizedLimbLen(pq3m.magia), pMagia, pLen) == 0)

        // val r2 = pq3m and BigInt.withBitMask(kLimbsPlus1 * 32)
        val r2Magia = IntArray(kLimbsPlus1)
        pMagia.copyInto(r2Magia, 0, 0, kLimbsPlus1)
        val r2Len = Magia.normalizedLimbLen(r2Magia, kLimbsPlus1)
        // check (Magia.compare(r2.magia, Magia.normalizedLimbLen(r2.magia), r2Magia, r2Len) == 0)
        // r = r1 - r2
        //var r = r1 - r2
        // var r = r1
        val mr = r1Magia
        var mrLen = r1Len
        //if (r < r2) {
        if (Magia.compare(mr, mrLen, r2Magia, r2Len) < 0) {
            // r += BigInt.withSetBit(kLimbsPlus1 * 32)
            check (mrLen == kLimbsPlus1)
            check (mr[kLimbsPlus1] == 0)
            mr[kLimbsPlus1] = 1
            mrLen = kLimbsPlus1 + 1
        }
        // r -= r2
        mrLen = Magia.mutateSub(mr, mrLen, r2Magia, r2Len)
        // check (Magia.compare(r.magia, Magia.normalizedLimbLen(r.magia), mr, mrLen) == 0)

        while (Magia.compare(mr, mrLen, mMagia, mMagia.size) >= 0) {
            // check (r >= m)
            // r -= m
            mrLen = Magia.mutateSub(mr, mrLen, mMagia, mMagia.size)
            // check (Magia.compare(r.magia, Magia.normalizedLimbLen(r.magia), mr, mrLen) == 0)
        }
        val magiaR = BigInt.fromLittleEndianIntArray(false, mr, mrLen)
        //check (Magia.compare(r.magia, Magia.normalizedLimbLen(r.magia),
        //    magiaR.magia, magiaR.magia.size) == 0)

        return magiaR
    }

}