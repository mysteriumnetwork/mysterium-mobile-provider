import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

// Download provider-mobile-node (AAR + POM) BEFORE any project is configured.
//
// Gradle resolves the dependency graph of :app:releaseRuntimeClasspath while it builds the
// task graph, which is before any task (including downloadProviderMobileNode) has run. If the
// POM isn't on disk yet, that failure is cached and :app:mergeReleaseNativeLibs re-throws it
// even though the download task finished first. Fetching here (settings are evaluated before
// project configuration) guarantees the files exist in time. Already-present files are skipped,
// so this is a no-op on cached/local builds and downloadProviderMobileNode stays UP-TO-DATE.
val nodeVersion: String = File(settingsDir, "gradle/libs.versions.toml").readLines()
    .firstNotNullOfOrNull { Regex("^node\\s*=\\s*\"([^\"]+)\"").find(it.trim())?.groupValues?.get(1) }
    ?: throw GradleException("Could not read 'node' version from gradle/libs.versions.toml")
val nodeLibsDir = File(settingsDir, "libs/network/mysterium/provider-mobile-node/$nodeVersion")
nodeLibsDir.mkdirs()
listOf("aar", "pom").forEach { ext ->
    val target = File(nodeLibsDir, "provider-mobile-node-$nodeVersion.$ext")
    if (target.isFile && target.length() > 0) return@forEach
    val url = "https://github.com/mysteriumnetwork/node/releases/download/$nodeVersion/${target.name}"
    println("Downloading ${target.name} from $url")
    val tmp = File(nodeLibsDir, "${target.name}.tmp")
    try {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        if (conn.responseCode !in 200..299) {
            throw GradleException("Failed to download $url: HTTP ${conn.responseCode} ${conn.responseMessage}")
        }
        conn.inputStream.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        Files.move(
            tmp.toPath(), target.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
        )
    } finally {
        tmp.delete()
    }
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
