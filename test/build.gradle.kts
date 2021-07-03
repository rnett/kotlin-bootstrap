import com.rnett.future.testing.kotlinFutureVersion
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.20"
}

group = "com.github.rnett.kotlin-future-testing"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnit()
}

dependencies {
    implementation(kotlin("compiler-embeddable", "1.5.20"))
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

kotlin {
    sourceSets.all {
        languageSettings {
            useExperimentalAnnotation("kotlin.contracts.ExperimentalContracts")
        }
    }
}

println("Kotlin plugin version: ${getKotlinPluginVersion()}")
println("Kotlin future version: $kotlinFutureVersion")