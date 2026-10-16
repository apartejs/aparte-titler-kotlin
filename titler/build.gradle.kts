// The runtime alone: reads any aparte-titler model file. No dependency but the Kotlin standard library.

plugins {
    kotlin("jvm")
}

description = "Conversation titles from the first message, on-device, in 17 languages: the runtime for aparte-titler models"

// The parity tests run on the model that titler-latin bundles, without a second copy of it.
sourceSets.test {
    resources.srcDir("../titler-latin/src/main/resources")
}
