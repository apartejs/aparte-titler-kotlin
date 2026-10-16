import dev.apartejs.titler.latin.TitlerLatin

fun main() {
    val titler = TitlerLatin.load()
    val title = titler.title("Write me a cover letter for a junior data analyst position at a bank")
    println("Kotlin ${KotlinVersion.CURRENT}: $title")
    check(title == "letter junior data analyst position bank") { "unexpected title: $title" }
}
