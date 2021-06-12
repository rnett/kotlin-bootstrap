import com.rnett.future.testing.kotlinFutureVersion
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
}

group = "com.github.rnett.kotlin-bootstrap"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnit()
}

dependencies {
    implementation(kotlin("compiler-embeddable", "1.5.10"))
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

println("Kotlin plugin version: ${getKotlinPluginVersion()}")
println("Kotlin future version: $kotlinFutureVersion")