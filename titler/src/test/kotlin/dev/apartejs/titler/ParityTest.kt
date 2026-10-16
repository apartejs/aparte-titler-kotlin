// Equality test: the Kotlin runtime must give exactly the titles of the Python
// reference implementation and of the JavaScript runtime, message for message,
// on 1,496 messages in five languages (French, English, Spanish, Polish, Finnish).
//
// int3 runs everywhere. The three other precisions run when APARTE_TITLER_ROOT
// points at a checkout of the aparte-titler repositories (the model files are on
// Hugging Face, not in this package), and are reported as skipped otherwise.
//
//     ./gradlew test
//     APARTE_TITLER_ROOT=../aparte-titler ./gradlew test

package dev.apartejs.titler

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val LATIN = "/dev/apartejs/titler/latin/titler-v1.2-latin-int3.bin"

private class Reference(val text: String, val title: String, val budget6: String?)

private fun references(stream: InputStream): List<Reference> =
    stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
        lines.filter { it.isNotBlank() }.map {
            val m = Json.parse(it) as Map<*, *>
            Reference(m["text"] as String, m["title"] as String, m["title_budget6"] as String?)
        }.toList()
    }

/** Every title that differs from the reference, hybrid and budget 6. */
private fun mismatches(titler: Titler, refs: List<Reference>): List<String> {
    val out = ArrayList<String>()
    for (r in refs) {
        val mine = titler.title(r.text)
        if (mine != r.title) out += "hybrid  expected «${r.title}» got «$mine» — ${r.text.take(60)}"
        if (r.budget6 != null) {
            val mine6 = titler.title(r.text, budget = 6)
            if (mine6 != r.budget6) out += "budget6 expected «${r.budget6}» got «$mine6» — ${r.text.take(60)}"
        }
    }
    return out
}

class ParityTest {
    private fun resource(path: String): InputStream =
        ParityTest::class.java.getResourceAsStream(path) ?: error("test resource $path is missing")

    private val latin: Titler by lazy { resource(LATIN).use { Titler.load(it) } }

    @Test
    fun readsTheModelHeader() {
        assertEquals("1.2.0", latin.header.version)
        assertEquals("latin", latin.header.model)
        assertEquals("int3", latin.header.precision)
        assertEquals(12288, latin.header.vocab)
        assertEquals(17, latin.header.languages?.size)
    }

    @Test
    fun givesTheReferenceTitlesAtInt3() {
        val refs = resource("/latin-int3.jsonl").use { references(it) }
        assertEquals(1496, refs.size)
        val bad = mismatches(latin, refs)
        assertTrue(bad.isEmpty(), "${bad.size} of ${refs.size * 2} titles differ at int3:\n" + bad.take(10).joinToString("\n"))
    }

    @Test
    fun givesTheReferenceTitlesAtTheOtherPrecisions() {
        val root = System.getenv("APARTE_TITLER_ROOT")
        assumeTrue(root != null, "set APARTE_TITLER_ROOT to the aparte-titler checkout to test fp32, int8 and int4")
        for (precision in listOf("fp32", "int8", "int4")) {
            val bin = File(root, "hf-model/v1.2/titler-v1.2-latin-$precision.bin")
            val refs = File(root, "model/tests/references/latin-$precision.jsonl")
            assertTrue(bin.exists() && refs.exists(), "$precision: model or references not found under $root")
            val bad = mismatches(Titler.load(bin), refs.inputStream().use { references(it) })
            assertTrue(bad.isEmpty(), "${bad.size} titles differ at $precision:\n" + bad.take(10).joinToString("\n"))
        }
    }

    @Test
    fun anEmptyOrPunctuationOnlyMessageGivesAnEmptyTitle() {
        assertEquals("", latin.title(""))
        assertEquals("", latin.title("   ...  !!  "))
    }

    @Test
    fun stopWordsNeverLeaveFewerThanThreeWords() {
        val message = "Peux-tu m'expliquer la photosynthèse chez les plantes ?"
        val words = latin.title(message).split(" ").toSet()
        assertTrue(latin.title(message, stopWords = words).split(" ").size >= 3)
    }

    @Test
    fun rejectsAFileThatIsNotAModel() {
        assertFailsWith<TitlerException> { Titler("not a model".toByteArray()) }
    }
}
