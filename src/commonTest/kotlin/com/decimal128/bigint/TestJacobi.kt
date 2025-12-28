package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigIntPrime.jacobi
import com.decimal128.bigint.toBigInt
import kotlin.test.Test
import kotlin.test.assertEquals

class TestJacobi {

    @Test
    fun jacobi_basic() {
        assertEquals(1, jacobi(1, 7.toBigInt()))
        assertEquals(0, jacobi(0, 7.toBigInt()))
    }

    @Test
    fun jacobi_quadraticResidues() {
        assertEquals(1,  jacobi(1, 7))
        assertEquals(1,  jacobi(2, 7)) // 2 is a residue mod 7
        assertEquals(-1, jacobi(3, 7))
    }

    @Test
    fun jacobi_negativeA() {
        val n = 11.toBigInt()
        assertEquals(jacobi(3, n), jacobi(-8, n))
    }

    @Test
    fun jacobi_knownValues() {
        assertEquals( 1, jacobi(5, 11))
        assertEquals(-1, jacobi(5, 13))
        assertEquals( 0, jacobi(9, 15))
        assertEquals( 1, jacobi(2, 7))
        assertEquals(-1, jacobi(3, 7))
    }
}