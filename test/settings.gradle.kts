import com.rnett.future.testing.kotlinFutureTesting

pluginManagement {
    includeBuild("..")
}

plugins {
    id("com.github.rnett.kotlin-future-testing")
}

kotlinFutureTesting {
}

rootProject.name = "test"

