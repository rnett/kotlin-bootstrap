import com.rnett.bootstrap.kotlinBootstrap

pluginManagement {
    includeBuild("..")
}

plugins {
    id("com.github.rnett.kotlin-bootstrap")
}

kotlinBootstrap {
//    generateGithubWorkflow()
}

rootProject.name = "test"

