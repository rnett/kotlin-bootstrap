import com.rnett.future.testing.kotlinFutureTesting

pluginManagement {
    includeBuild("..")
}

plugins {
    id("com.github.rnett.kotlin-future-testing")
}

kotlinFutureTesting {
    requireSameFeatureVersion()
    preferSameIncrementalVersion()

//    generateGithubWorkflows { both() }
}

rootProject.name = "test"

