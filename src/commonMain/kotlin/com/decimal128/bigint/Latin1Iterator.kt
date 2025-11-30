// SPDX-License-Identifier: MIT

package com.decimal128.bigint

/**
 * Abstract iterator over a sequence of characters stored in Latin-1 (ISO-8859-1) format.
 *
 * Provides sequential and reverse iteration over a contiguous range of characters,
 * as well as utility functions to inspect or reset the current position.
 *
 * The iterator is zero-based with respect to the underlying sequence and
 * tracks its position internally.
 *
 * @property off the starting offset in the underlying sequence
 * @property len the number of characters to iterate over
 *
 * @constructor Initializes the iterator for a given slice of size `size`.
 * Throws [IllegalArgumentException] if the range `[off, off + len)` is invalid.
 *
 * @param size the total size of the underlying character sequence
 * @param off the starting offset of the iteration window
 * @param len the length of the iteration window
 */
abstract class Latin1Iterator(size: Int, val off: Int, val len: Int) {
    private var i = 0

    /** Returns true if there are more characters to read in the forward direction. */
    fun hasNext() = (i < len)

    /** Returns the current character without advancing the iterator, or '\u0000' if at end. */
    fun peek() = if (i < len) getCharAt(off + i) else '\u0000'

    /** Returns the number of remaining characters from the current position to the end. */
    fun remainingLen() = len - i

    /** Returns the next character and advances the iterator, or '\u0000' if at end. */
    fun nextChar() = if (i < len) getCharAt(off + i++) else '\u0000'

    /** Moves the iterator backward by one and returns that character, or '\u0000' if at start. */
    fun prevChar() = if (i > 0) getCharAt(off + --i) else '\u0000'

    /** Resets the iterator to the initial position and returns `this`. */
    fun reset(): Latin1Iterator { i = 0; return this }

    /** Returns the length of the iteration window. */
    fun length() = len

    /** Returns the character at the specified absolute index in the underlying sequence. */
    abstract fun getCharAt(i: Int): Char

    /** Returns a string representation of the iterator's current window. */
    abstract override fun toString(): String

    init { require(off >= 0 && len >= 0 && off <= size - len) { "Invalid offset/length range" } }
}

/**
 * [Latin1Iterator] implementation backed by a [String].
 *
 * Provides sequential and reverse iteration over a contiguous substring of [s].
 * The iteration respects the specified offset and length.
 *
 * @property s the underlying string to iterate over
 * @param off the starting offset of the iteration window in the string
 * @param len the length of the iteration window
 *
 * @constructor Creates an iterator for the substring `s[off..off+len-1]`.
 * @throws IllegalArgumentException if the offset and length are out of bounds
 */
class StringLatin1Iterator(private val s: String, off: Int, len: Int) : Latin1Iterator(s.length, off, len) {

    /** Convenience constructor to iterate over the entire string. */
    constructor(s: String) : this(s, 0, s.length)

    /** Returns the character at the given absolute index in the underlying string. */
    override fun getCharAt(i: Int) = s[i]

    /** Returns the full string representation of this iterator. */
    override fun toString() = s.toString()
}

/**
 * [Latin1Iterator] implementation backed by a [CharSequence].
 *
 * Provides sequential and reverse iteration over a contiguous subsequence of [csq],
 * using the specified offset and length.
 *
 * @property csq the underlying [CharSequence] to iterate over
 * @param off the starting offset of the iteration window in the sequence
 * @param len the length of the iteration window
 *
 * @constructor Creates an iterator for the subsequence `csq[off..off+len-1]`.
 * @throws IllegalArgumentException if the offset and length are out of bounds
 */
class CharSequenceLatin1Iterator(private val csq: CharSequence, off: Int, len: Int) : Latin1Iterator(csq.length, off, len) {

    /** Convenience constructor to iterate over the entire sequence. */
    constructor(csq: CharSequence) : this(csq, 0, csq.length)

    /** Returns the character at the given absolute index in the underlying [CharSequence]. */
    override fun getCharAt(i: Int) = csq[i]

    /** Returns the string representation of the underlying [CharSequence]. */
    override fun toString() = csq.toString()
}


/**
 * [Latin1Iterator] implementation backed by a [CharArray].
 *
 * Provides sequential and reverse iteration over a contiguous subsequence of [chars],
 * using the specified offset and length.
 *
 * @property chars the underlying [CharArray] to iterate over
 * @param off the starting offset of the iteration window in the array
 * @param len the length of the iteration window
 *
 * @constructor Creates an iterator for the subsequence `chars[off..off+len-1]`.
 * @throws IllegalArgumentException if the offset and length are out of bounds
 */
class CharArrayLatin1Iterator(private val chars: CharArray, off: Int, len: Int) : Latin1Iterator(chars.size, off, len) {

    /** Convenience constructor to iterate over the entire array. */
    constructor(chars: CharArray) : this(chars, 0, chars.size)

    /** Returns the character at the given absolute index in the underlying [CharArray]. */
    override fun getCharAt(i: Int) = chars[i]

    /** Returns the string representation of the subsequence covered by this iterator. */
    override fun toString() = chars.concatToString(off, off + len)
}


/**
 * [Latin1Iterator] implementation backed by a [ByteArray].
 *
 * Provides sequential and reverse iteration over a contiguous subsequence of [bytes],
 * interpreting each byte as a single character. Suitable for ASCII, Latin-1, or
 * UTF-8 data where all characters are in the 0–255 range.
 *
 * @property bytes the underlying [ByteArray] to iterate over
 * @param off the starting offset of the iteration window in the array
 * @param len the length of the iteration window
 *
 * @constructor Creates an iterator for the subsequence `bytes[off..off+len-1]`.
 * @throws IllegalArgumentException if the offset and length are out of bounds
 */
class ByteArrayLatin1Iterator(private val bytes: ByteArray, off: Int, len: Int) : Latin1Iterator(bytes.size, off, len) {

    /** Convenience constructor to iterate over the entire array. */
    constructor(bytes: ByteArray) : this(bytes, 0, bytes.size)

    /** Returns the character at the given absolute index in the underlying [ByteArray]. */
    override fun getCharAt(i: Int) = (bytes[i].toInt() and 0xFF).toChar()

    /** Returns the string representation of the subsequence covered by this iterator. */
    override fun toString() = bytes.decodeToString(off, off + len)
}
