plugins {
    kotlin("jvm") version "2.0.21"
    application
}

dependencies {
    implementation("dev.apartejs:titler-latin:1.2.0")
}

application {
    mainClass.set("MainKt")
}
