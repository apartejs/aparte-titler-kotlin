import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.4.21" apply false
}

// The middle number is the version of the model weights, as on npm and Hugging Face.
allprojects {
    group = "dev.apartejs"
    version = "1.2.0"
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        // Java 8 bytecode: runs on every Android version and every JVM.
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        extensions.configure<KotlinJvmProjectExtension> {
            explicitApi()
            compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
        }
        dependencies {
            "testImplementation"(kotlin("test"))
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }
}
