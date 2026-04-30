pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        exclusiveContent {
            forRepository {
                maven {
                    url = uri("libs")
                }
            }
            filter {
                includeModule("network.mysterium", "provider-mobile-node")
            }
        }
    }
}
rootProject.name = "MystNodes"
include(":app")
include(":node")
