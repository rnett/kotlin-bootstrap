import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    id("com.vanniktech.maven.publish") version "0.15.1"
    id("org.jetbrains.dokka") version "1.4.32"
    `java-gradle-plugin`
    `kotlin-dsl`
}

group = "com.github.rnett.kotlin-bootstrap"
version = "0.0.2-SNAPSHOT"
description = "A Gradle settings plugin to use Kotlin bootstrap versions"

repositories {
    mavenCentral()
}

kotlin {
    target {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
    }
}

dependencies {
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
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
            name.set("Kotlin Bootstrap Gradle Plugin")
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