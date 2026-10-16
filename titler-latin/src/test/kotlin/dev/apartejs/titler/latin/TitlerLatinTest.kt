package dev.apartejs.titler.latin

import kotlin.test.Test
import kotlin.test.assertEquals

class TitlerLatinTest {
    @Test
    fun loadsTheBundledModelFromTheJar() {
        val titler = TitlerLatin.load()
        assertEquals("latin", titler.header.model)
        assertEquals("1.2.0", titler.header.version)
        assertEquals(
            "letter junior data analyst position bank",
            titler.title("Write me a cover letter for a junior data analyst position at a bank"),
        )
    }
}
