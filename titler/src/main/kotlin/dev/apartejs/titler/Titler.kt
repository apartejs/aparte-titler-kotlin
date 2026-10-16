// aparte-titler — a conversation title from its first message, on-device.
//
// One message in, a title of 3 to 6 words out — words copied verbatim from the
// message, never invented. Reads the `.bin` model files published at
// https://huggingface.co/apartejs/aparte-titler (format 1). No dependency.
//
//     val titler = Titler.load(File("titler-v1.2-fr-int3.bin"))
//     titler.title("Peux-tu m'expliquer la photosynthèse chez les plantes ?")
//     // "m'expliquer photosynthèse plantes"
//
// It gives exactly the titles of the JavaScript runtime (@aparte/titler on npm)
// and of the Python reference implementation; the tests check it on 1,496 messages.

package dev.apartejs.titler

import java.io.File
import java.io.InputStream

/** Characters stripped from the edges of a word only: "-Don't" → "Don't", while "rendez-vous" and "c++" are left whole. */
private val PUNCTUATION: Set<Int> = codePoints(".,;:!?\"'()[]{}<>*`“”‘’«»…-–—").toSet()

/** A hyphen or an apostrophe glued between two letters keeps a word whole ("Pourrais-tu", "l'école"). */
private val JOINERS: Set<Int> = setOf('-'.code, '\''.code, '’'.code)

/**
 * A word of the message and how strongly the model wants it in the title.
 *
 * @property word the word as it appears in the message, edge punctuation removed.
 * @property score the model's probability that this word belongs in the title, from 0 to 1.
 * @property start where the word starts, in code points of the message after trimming and truncation.
 * @property end where the word ends (exclusive), in the same units.
 */
public class ScoredWord(public val word: String, public val score: Double, public val start: Int, public val end: Int) {
    override fun equals(other: Any?): Boolean =
        other is ScoredWord && word == other.word && score == other.score && start == other.start && end == other.end

    override fun hashCode(): Int = ((word.hashCode() * 31 + score.hashCode()) * 31 + start) * 31 + end
    override fun toString(): String = "ScoredWord(word=$word, score=$score, start=$start, end=$end)"
}

/**
 * The titler: load a model file once, then title as many messages as you like.
 * It holds no mutable state and can be shared across threads.
 */
public class Titler(bytes: ByteArray) {
    /** The model file's header: version, scope, languages, precision, dimensions. */
    public val header: ModelHeader

    private val tokenizer: Tokenizer
    private val model: Model
    private val threshold: Double
    private val minWords: Int
    private val maxWords: Int
    private val keepAllUpTo: Int

    init {
        val data = readModel(bytes)
        header = data.header
        tokenizer = Tokenizer(data.header, data.merges)
        model = Model(data.header, data.weights)
        // a 1.0 file carries no `decoding` block and decodes with exactly these values
        val d = header.decoding
        threshold = d?.threshold ?: 0.5
        minWords = d?.minWords ?: 3
        maxWords = d?.maxWords ?: 6
        keepAllUpTo = d?.keepAllUpTo ?: 4
    }

    public companion object {
        /** Load a model from a `.bin` stream, e.g. an Android asset. The stream is read to its end, not closed. */
        @JvmStatic
        public fun load(stream: InputStream): Titler = Titler(stream.readBytes())

        /** Load a model from a `.bin` file. */
        @JvmStatic
        public fun load(file: File): Titler = Titler(file.readBytes())
    }

    /** Every word of the message with its score, in text order. */
    public fun words(message: String): List<ScoredWord> {
        // Python's message.strip()[:maxChars]: Python's whitespace, cut in code points
        val all = codePoints(message)
        var a = 0
        var b = all.size
        while (a < b && isPythonSpace(all[a])) a++
        while (b > a && isPythonSpace(all[b - 1])) b--
        val text = all.copyOfRange(a, minOf(b, a + header.maxChars))

        val enc = tokenizer.encode(text)
        if (enc.ids.isEmpty()) return emptyList()
        val z = model.logits(enc.ids)

        val words = ArrayList<ScoredWord>()
        val ids = wordIds(text, enc.starts, enc.ends)
        var first = 0
        while (first < ids.size) {                    // tokens first..<last form one word
            var last = first + 1
            while (last < ids.size && ids[last] == ids[first]) last++
            val start = enc.starts[first]
            val end = enc.ends[last - 1]
            if ((start until end).any { isWordChar(text[it]) }) {   // punctuation-only groups are never a title word
                var sum = 0.0
                for (i in first until last) sum += z[i]            // the decision is per word: mean of its token logits
                val score = 1 / (1 + Math.exp(-sum / (last - first)))
                words += ScoredWord(trimmed(text, start, end), score, start, end)
            }
            first = last
        }
        return words
    }

    /**
     * The title of a conversation, from its first message.
     *
     * By default the hybrid decoding: among the six best-scored words, those the
     * model is at least half sure of, never fewer than three, and every word of a
     * message that has four or fewer.
     *
     * @param message the user's first message — not a system prompt, not the history.
     * @param budget keep exactly this many best-scored words instead, the decoding version 1.0 shipped.
     * @param stopWords words never to keep (case-insensitive), as long as at least three words remain.
     * @return the title, words in message order, or an empty string when the message has no word at all.
     */
    @JvmOverloads
    public fun title(message: String, budget: Int? = null, stopWords: Set<String> = emptySet()): String {
        var words = words(message)
        if (words.isEmpty()) return ""
        if (stopWords.isNotEmpty()) {
            val banned = stopWords.map { it.lowercase() }.toSet()
            val kept = words.filter { it.word.lowercase() !in banned }
            if (kept.size >= minWords) words = kept
        }
        val byScore = words.sortedByDescending { it.score }   // stable: ties keep text order
        val chosen = when {
            budget != null -> byScore.take(maxOf(0, budget))
            words.size <= keepAllUpTo -> words
            else -> {
                val top = byScore.take(maxWords)
                val sure = top.filter { it.score >= threshold }
                if (sure.size < minWords) top.take(minWords) else sure
            }
        }
        return chosen.sortedBy { it.start }.map { it.word }.filter { it.isNotEmpty() }.joinToString(" ")
    }
}

/** Strip Python whitespace, then edge punctuation, from text[start..<end]. */
private fun trimmed(text: IntArray, start: Int, end: Int): String {
    var a = start
    var b = end
    while (a < b && isPythonSpace(text[a])) a++
    while (b > a && isPythonSpace(text[b - 1])) b--
    while (a < b && text[a] in PUNCTUATION) a++
    while (b > a && text[b - 1] in PUNCTUATION) b--
    return stringOf(text, a, b)
}

/**
 * The word each token belongs to. A new word starts on a token whose first character is a
 * space or not a letter or digit — except a joiner glued between two letters, which keeps the word whole.
 */
private fun wordIds(text: IntArray, starts: IntArray, ends: IntArray): IntArray {
    val out = IntArray(starts.size)
    var num = -1
    var previousEnd = -1
    for (i in starts.indices) {
        val a = starts[i]
        var begins = true
        if (ends[i] > a) {
            val c = text[a]
            begins = i == 0 || isPythonSpace(c) || !isWordChar(c)
            if (begins && i > 0 && c in JOINERS && a == previousEnd && a > 0 && isWordChar(text[a - 1]) &&
                a + 1 < text.size && isWordChar(text[a + 1])
            ) {
                begins = false
            }
        }
        if (begins) num++
        out[i] = num
        previousEnd = ends[i]
    }
    return out
}
