// The runtime and the 17-language model, ready to use.

plugins {
    kotlin("jvm")
}

description = "Conversation titles from the first message, on-device: the aparte-titler runtime with the 17-language model (133 KB) bundled"

dependencies {
    api(project(":titler"))
}
