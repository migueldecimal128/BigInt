package com.decimal128.bigint.crypto

import com.decimal128.bigint.crypto.BigIntPrime.jacobi
import com.decimal128.bigint.toBigInt
import kotlin.test.Test
import kotlin.test.assertEquals

class TestJacobi {

    @Test
    fun jacobi_basic() {
        assertEquals(1, jacobi(1.toBigInt(), 7.toBigInt()))
        assertEquals(0, jacobi(0.toBigInt(), 7.toBigInt()))
    }

    @Test
    fun jacobi_quadraticResidues() {
        val p = 7.toBigInt()
        assertEquals(1,  jacobi(1.toBigInt(), p))
        assertEquals(1,  jacobi(2.toBigInt(), p)) // 2 is a residue mod 7
        assertEquals(-1, jacobi(3.toBigInt(), p))
    }

    @Test
    fun jacobi_negativeA() {
        val n = 11.toBigInt()
        assertEquals(jacobi(3.toBigInt(), n), jacobi((-8).toBigInt(), n))
    }

    @Test
    fun jacobi_knownValues() {
        assertEquals( 1, jacobi(5.toBigInt(), 11.toBigInt()))
        assertEquals(-1, jacobi(5.toBigInt(), 13.toBigInt()))
        assertEquals( 0, jacobi(9.toBigInt(), 15.toBigInt()))
        assertEquals( 1, jacobi(2.toBigInt(), 7.toBigInt()))
        assertEquals(-1, jacobi(3.toBigInt(), 7.toBigInt()))
    }
}