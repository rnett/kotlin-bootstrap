import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    `java-gradle-plugin`
    `kotlin-dsl`
}

group = "com.github.rnett.kotlin-bootstrap"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin{
    target{
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
    }
}

dependencies{
    compileOnly(kotlin("gradle-plugin"))
    implementation(kotlin("gradle-plugin-api"))
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

gradlePlugin {
    plugins {
        create("kotlinBootstrapPlugin") {
            id = "com.github.rnett.kotlin-bootstrap"
            displayName = "Kotlin Bootstrap Plugin"
            description = "Kotlin Bootstrap Plugin"
            implementationClass = "com.rnett.bootstrap.KotlinBootstrapPlugin"
        }
    }
}