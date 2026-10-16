// The runtime alone: reads any aparte-titler model file. No dependency but the Kotlin standard library.

plugins {
    kotlin("jvm")
}

// The parity tests run on the model that titler-latin bundles, without a second copy of it.
sourceSets.test {
    resources.srcDir("../titler-latin/src/main/resources")
}
