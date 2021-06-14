import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    kotlin("plugin.serialization") version "1.5.10"
    id("com.vanniktech.maven.publish") version "0.15.1"
    id("org.jetbrains.dokka") version "1.4.32"
    `java-gradle-plugin`
    `kotlin-dsl`
}

version = "0.0.10-SNAPSHOT"
group = "com.github.rnett.kotlin-future-testing"
description = "A Gradle settings plugin to use Kotlin future versions"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
}

kotlin {
    target {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
        explicitApi()
    }
    sourceSets.all {
        languageSettings {
            useExperimentalAnnotation("kotlin.contracts.ExperimentalContracts")
        }
    }
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

gradlePlugin {
    plugins {
        create("kotlinFutureTestingPlugin") {
            id = "com.github.rnett.kotlin-future-testing"
            displayName = "Kotlin Future Testing Plugin"
            description = "A plugin for testing future versions of Kotlin"
            implementationClass = "com.rnett.future.testing.KotlinFutureTestingPlugin"
        }
    }
}

afterEvaluate {
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "org.gradle.maven-publish")
    apply(plugin = "com.vanniktech.maven.publish")
    val project = this

    extensions.getByType<com.vanniktech.maven.publish.MavenPublishBaseExtension>().apply {
        if (!version.toString().toLowerCase().endsWith("snapshot")) {
            val stagingProfileId = project.findProperty("sonatypeRepositoryId")?.toString()
            publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.DEFAULT, stagingProfileId)
        }

        pom {
            name.set("Kotlin Future Testing Gradle Plugin")
            description.set(project.description)
            inceptionYear.set("2021")
            url.set("https://github.com/rnett/kotlin-bootstrap/")

            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }

            scm {
                url.set("https://github.com/rnett/kotlin-bootstrap.git")
                connection.set("scm:git:git://github.com/rnett/kotlin-bootstrap.git")
                developerConnection.set("scm:git:ssh://git@github.com/rnett/kotlin-bootstrap.git")
            }

            developers {
                developer {
                    id.set("rnett")
                    name.set("Ryan Nett")
                    url.set("https://github.com/rnett/")
                }
            }
        }
    }
}