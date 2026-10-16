package dev.apartejs.titler

// Byte-level BPE, as GPT-2 and the Hugging Face `tokenizers` ByteLevel model do it,
// reproducing the JavaScript runtime exactly. Positions are counted in code points
// of the prepared text: the JavaScript runtime counts UTF-16 units, but every slice
// it takes falls on the same characters, so titles are identical.

// ---- character classes

private val LETTERS = (1 shl Character.UPPERCASE_LETTER.toInt()) or (1 shl Character.LOWERCASE_LETTER.toInt()) or
    (1 shl Character.TITLECASE_LETTER.toInt()) or (1 shl Character.MODIFIER_LETTER.toInt()) or
    (1 shl Character.OTHER_LETTER.toInt())
private val NUMBERS = (1 shl Character.DECIMAL_DIGIT_NUMBER.toInt()) or (1 shl Character.LETTER_NUMBER.toInt()) or
    (1 shl Character.OTHER_NUMBER.toInt())

/** `\p{L}`: a letter of any script. */
internal fun isLetter(c: Int): Boolean = ((LETTERS ushr Character.getType(c)) and 1) != 0

/** `\p{N}`: a number of any kind. */
internal fun isNumber(c: Int): Boolean = ((NUMBERS ushr Character.getType(c)) and 1) != 0

internal fun isWordChar(c: Int): Boolean = ((LETTERS or NUMBERS) ushr Character.getType(c)) and 1 != 0

/** JavaScript's `\s` (WhiteSpace and LineTerminator), used by the pre-tokenizer only. */
internal fun isJSSpace(c: Int): Boolean = when (c) {
    in 0x09..0x0D, 0x20, 0xA0, 0x1680, in 0x2000..0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF -> true
    else -> false
}

/**
 * Python's `str.isspace()`, used to trim a message and its words. It differs from
 * JavaScript's `\s`: it adds U+001C–U+001F and U+0085, and lacks U+FEFF.
 */
internal fun isPythonSpace(c: Int): Boolean = when (c) {
    in 0x09..0x0D, in 0x1C..0x1F, 0x20, 0x85, 0xA0, 0x1680, in 0x2000..0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
    else -> false
}

private fun isOther(c: Int): Boolean = !isJSSpace(c) && !isWordChar(c)

/** The code points of a string. A lone surrogate stays a code point of its own, as in JavaScript. */
internal fun codePoints(s: String): IntArray {
    val out = IntArray(s.codePointCount(0, s.length))
    var i = 0
    var k = 0
    while (i < s.length) {
        val c = s.codePointAt(i)
        out[k++] = c
        i += Character.charCount(c)
    }
    return out
}

internal fun stringOf(cps: IntArray, from: Int, to: Int): String = String(cps, from, to - from)

// ---- pre-tokenization

private const val SPACE = ' '.code
private const val APOSTROPHE = '\''.code
private val CONTRACTIONS = arrayOf("s", "t", "re", "ve", "m", "ll", "d").map { codePoints(it) }

/** `` ?X+``: an optional ASCII space, then at least one character of the class. The end, or -1. */
private inline fun run(s: IntArray, i: Int, inClass: (Int) -> Boolean): Int {
    val n = s.size
    var j = i
    if (s[j] == SPACE && j + 1 < n && inClass(s[j + 1])) j++
    if (!inClass(s[j])) return -1
    while (j < n && inClass(s[j])) j++
    return j
}

/**
 * GPT-2's split, the JavaScript regex
 * `'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+`
 * written out as a scanner. At each position the alternatives are tried in that
 * order and the first that matches wins, as a regex alternation does.
 * Pieces are returned as start and end positions, two per piece.
 */
internal fun pretokenize(s: IntArray): IntArray {
    val pieces = IntList()
    val n = s.size
    var i = 0
    outer@ while (i < n) {
        if (s[i] == APOSTROPHE) {
            for (c in CONTRACTIONS) {
                if (i + 1 + c.size <= n && c.indices.all { s[i + 1 + it] == c[it] }) {
                    pieces.add(i); pieces.add(i + 1 + c.size)
                    i += 1 + c.size
                    continue@outer
                }
            }
        }
        var j = run(s, i, ::isLetter)
        if (j < 0) j = run(s, i, ::isNumber)
        if (j < 0) j = run(s, i, ::isOther)
        if (j < 0) {
            // whitespace: `\s+(?!\S)` keeps the last space for the word that follows
            // when the run is longer than one; otherwise `\s+` takes the whole run.
            j = i
            while (j < n && isJSSpace(s[j])) j++
            if (j < n && j - i >= 2) j--
        }
        pieces.add(i); pieces.add(j)
        i = j
    }
    return pieces.toArray()
}

// ---- BPE

/** The UTF-8 bytes of a code point; a lone surrogate becomes U+FFFD, as JavaScript's TextEncoder does. */
private fun utf8(c: Int, out: IntList) {
    val v = if (c in 0xD800..0xDFFF) 0xFFFD else c
    when {
        v < 0x80 -> out.add(v)
        v < 0x800 -> { out.add(0xC0 or (v shr 6)); out.add(0x80 or (v and 0x3F)) }
        v < 0x10000 -> { out.add(0xE0 or (v shr 12)); out.add(0x80 or ((v shr 6) and 0x3F)); out.add(0x80 or (v and 0x3F)) }
        else -> {
            out.add(0xF0 or (v shr 18)); out.add(0x80 or ((v shr 12) and 0x3F))
            out.add(0x80 or ((v shr 6) and 0x3F)); out.add(0x80 or (v and 0x3F))
        }
    }
}

internal class Encoded(val ids: IntArray, val starts: IntArray, val ends: IntArray)

internal class Tokenizer(header: ModelHeader, merges: IntArray) {
    private val byteIds = header.byteIds
    private val base = header.mergesBase
    private val rank = IntIntMap(merges.size / 2).apply {
        for (k in 0 until merges.size / 2) put(merges[2 * k] * 65536 + merges[2 * k + 1], k)
    }

    /** Token ids and, for each, the half-open range of code points it covers. */
    fun encode(text: IntArray): Encoded {
        val ids = IntList()
        val starts = IntList()
        val ends = IntList()
        val pieces = pretokenize(text)
        val bytes = IntList()
        val tokens = IntList()
        val lengths = IntList()
        val charOfByte = IntList()
        for (p in 0 until pieces.size / 2) {
            tokens.clear(); lengths.clear(); charOfByte.clear()
            for (k in pieces[2 * p] until pieces[2 * p + 1]) {
                bytes.clear()
                utf8(text[k], bytes)
                for (b in 0 until bytes.size) {
                    tokens.add(byteIds[bytes[b]]); lengths.add(1); charOfByte.add(k)
                }
            }
            while (tokens.size > 1) {                // merge the lowest-ranked pair first
                var best = -1
                var at = -1
                for (j in 0 until tokens.size - 1) {
                    val r = rank.get(tokens[j] * 65536 + tokens[j + 1])
                    if (r >= 0 && (best < 0 || r < best)) { best = r; at = j }
                }
                if (best < 0) break
                tokens[at] = base + best
                lengths[at] = lengths[at] + lengths[at + 1]
                tokens.removeAt(at + 1); lengths.removeAt(at + 1)
            }
            var pos = 0
            for (t in 0 until tokens.size) {
                ids.add(tokens[t])
                starts.add(charOfByte[pos])
                ends.add(charOfByte[pos + lengths[t] - 1] + 1)
                pos += lengths[t]
            }
        }
        return Encoded(ids.toArray(), starts.toArray(), ends.toArray())
    }
}

// ---- small primitive collections, to keep boxing out of the hot loops

internal class IntList(capacity: Int = 16) {
    private var a = IntArray(capacity)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == a.size) a = a.copyOf(a.size * 2)
        a[size++] = v
    }

    operator fun get(i: Int): Int = a[i]
    operator fun set(i: Int, v: Int) { a[i] = v }

    fun removeAt(i: Int) {
        System.arraycopy(a, i + 1, a, i, size - i - 1)
        size--
    }

    fun clear() { size = 0 }
    fun toArray(): IntArray = a.copyOf(size)
}

/** An open-addressing map from non-negative ints to ints; [get] returns -1 when absent. Later puts win. */
internal class IntIntMap(expected: Int) {
    private val keys: IntArray
    private val values: IntArray
    private val mask: Int

    init {
        var capacity = 16
        while (capacity < expected * 2) capacity = capacity shl 1
        keys = IntArray(capacity) { -1 }
        values = IntArray(capacity)
        mask = capacity - 1
    }

    private fun slot(key: Int): Int {
        var h = key * -0x61c88647
        h = h xor (h ushr 16)
        return h and mask
    }

    fun put(key: Int, value: Int) {
        var i = slot(key)
        while (keys[i] != -1 && keys[i] != key) i = (i + 1) and mask
        keys[i] = key
        values[i] = value
    }

    fun get(key: Int): Int {
        var i = slot(key)
        while (true) {
            val k = keys[i]
            if (k == key) return values[i]
            if (k == -1) return -1
            i = (i + 1) and mask
        }
    }
}
