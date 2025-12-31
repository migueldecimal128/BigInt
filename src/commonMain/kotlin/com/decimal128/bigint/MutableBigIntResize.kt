package com.decimal128.bigint

import kotlin.jvm.JvmInline

@JvmInline
value class ResizeOperation private constructor(val index: Int) {
    companion object {
        val MAGIA               = ResizeOperation(0)
        val TMP1_MUL            = ResizeOperation(1)
        val TMP1_SQR            = ResizeOperation(2)
        val TMP1_KNUTH_DIVIDEND = ResizeOperation(3)
        val TMP1_KARATSUBA_SQ   = ResizeOperation(4)
        val TMP2_KNUTH_DIVISOR  = ResizeOperation(5)
        val TMP2_KARATSUBA_Z1   = ResizeOperation(6)

        const val COUNT = 7
    }
}

@JvmInline
value class ResizeEvent private constructor(val index: Int) {
    companion object {
        val INITIAL_UNHINTED = ResizeEvent(0)
        val INITIAL_HINTED = ResizeEvent(1)
        val REPEAT_UNHINTED = ResizeEvent(2)
        val REPEAT_HINTED = ResizeEvent(3)
        const val COUNT = 4
    }
}