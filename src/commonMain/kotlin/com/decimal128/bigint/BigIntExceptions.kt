package com.decimal128.bigint

private const val ERR_MSG_ADD_OVERFLOW = "add overflow ... destination too small"
private const val ERR_MSG_SUB_UNDERFLOW = "sub underflow ... minuend too small for subtrahend"
private const val ERR_MSG_MUL_OVERFLOW = "mul overflow ... destination too small"
private const val ERR_MSG_SHL_OVERFLOW = "shl overflow ... destination too small"
private const val ERR_MSG_DIV_BY_ZERO = "div by zero"
private const val ERR_MSG_INVALID_ALLOCATION_LENGTH = "invalid allocation length"
private const val ERR_MSG_NEGATIVE_INDEX = "negative index"
private const val ERR_MSG_MOD_NEG_DIVISOR = "modulus with a negative divisor is undefined"
private const val ERR_MSG_NEG_BITCOUNT = "negative bitCount"
private const val ERR_MSG_BITLEN_LE_0 = "invalid bitLen <= 0"
private const val ERR_MSG_INVALID_BITLEN_RANGE = "invalid bitLen range: 0 <= bitLenMin <= bitLenMax"
private const val ERR_MSG_NEG_BITINDEX = "negative bitIndex"
private const val ERR_MSG_BOUNDS_CHECK_VIOLATION = "bounds check violation"
private const val ERR_MSG_BAD_KNUTH_ARGUMENT = "bad knuth argument"
private const val ERR_MSG_HASH_CODE_UNSUPPORTED =
    "mutable MutableBigInt is an invalid key in collections"

internal fun throwDivByZero() {
    throw ArithmeticException(ERR_MSG_DIV_BY_ZERO)
}

internal fun throwDivByZero_Int(): Int {
    throw ArithmeticException(ERR_MSG_DIV_BY_ZERO)
}

internal fun throwDivByZero_BigInt(): BigInt {
    throw ArithmeticException(ERR_MSG_DIV_BY_ZERO)
}

internal fun throwAddOverflow(): Nothing {
    throw IllegalStateException(ERR_MSG_ADD_OVERFLOW)
}

internal fun throwSubUnderflow(): Nothing {
    throw IllegalStateException(ERR_MSG_SUB_UNDERFLOW)
}

internal fun throwSubUnderflow_Int(): Int {
    throw IllegalStateException(ERR_MSG_SUB_UNDERFLOW)
}

internal fun throwMulOverflow(): Nothing {
    throw IllegalStateException(ERR_MSG_MUL_OVERFLOW)
}

internal fun throwShlOverflow(): Nothing {
    throw IllegalStateException(ERR_MSG_SHL_OVERFLOW)
}

internal fun throwInvalidAllocationLength(): Nothing {
    throw IllegalStateException(ERR_MSG_INVALID_ALLOCATION_LENGTH)
}

internal fun throwInvalidAllocationLength_Int(): Int {
    throw IllegalStateException(ERR_MSG_INVALID_ALLOCATION_LENGTH)
}

internal fun throwInvalidAllocationLength_Magia(): Magia {
    throw IllegalStateException(ERR_MSG_INVALID_ALLOCATION_LENGTH)
}

internal fun throwNegativeIndex(): Nothing {
    throw IllegalStateException(ERR_MSG_NEGATIVE_INDEX)
}

internal fun throwModNegDivisor(): Nothing {
    throw ArithmeticException(ERR_MSG_MOD_NEG_DIVISOR)
}

internal fun throwNegBitCount(): Nothing {
    throw IllegalArgumentException(ERR_MSG_NEG_BITCOUNT)
}

internal fun throwBitLenLE0(): Nothing {
    throw IllegalArgumentException(ERR_MSG_BITLEN_LE_0)
}

internal fun throwInvalidBitLenRange(): Nothing {
    throw IllegalArgumentException(ERR_MSG_INVALID_BITLEN_RANGE)
}

internal fun throwNegBitIndex(): Nothing {
    throw IllegalArgumentException(ERR_MSG_NEG_BITINDEX)
}

internal fun throwBoundsCheckViolation(): Nothing {
    throw IllegalArgumentException(ERR_MSG_BOUNDS_CHECK_VIOLATION)
}

internal fun throwBoundsCheckViolation_Int(): Int {
    throw IllegalArgumentException(ERR_MSG_BOUNDS_CHECK_VIOLATION)
}

internal fun throwBadKnuthArgument(): Nothing {
    throw IllegalArgumentException(ERR_MSG_BAD_KNUTH_ARGUMENT)
}

internal fun throwHashCodeUnsupported(): Nothing {
    throw UnsupportedOperationException(ERR_MSG_HASH_CODE_UNSUPPORTED)
}

