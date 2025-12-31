package com.decimal128.bigint

internal object BigIntStats {
    val MUTABLE_RESIZE_COUNTS =
        IntArray(MutableResizeOperation.COUNT * MutableResizeEvent.COUNT)


    enum class MutableResizeOperation {
        RESIZE_MAGIA,
        RESIZE_TMP1_MUL,
        RESIZE_TMP1_SQR,
        RESIZE_TMP1_KNUTH_DIVIDEND,
        RESIZE_TMP1_KARATSUBA_SQR,
        RESIZE_TMP2_KNUTH_DIVISOR,
        RESIZE_TMP2_KARATSUBA_Z1;

        companion object {
            val values = MutableResizeOperation.values()
            val COUNT = values.size
        }
    }

    enum class MutableResizeEvent {
        INITIAL_UNHINTED,
        INITIAL_HINTED,
        REPEAT_UNHINTED,
        REPEAT_HINTED;

        companion object {
            val values = values()
            const val COUNT = 4
        }
    }
}

