// Top-level build file where you can add configuration options common to all sub-projects/modules.
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.jvm)
    alias(libs.plugins.serialization)
    alias(libs.plugins.firebase) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val nodeVersion = versionCatalogs
    .named("libs")
    .findVersion("node")
    .get()
    .requiredVersion
val nodeArtifactDir = layout.projectDirectory
    .dir("libs/network/mysterium/provider-mobile-node/$nodeVersion")
    .asFile
val nodeAarFile = File(nodeArtifactDir, "provider-mobile-node-$nodeVersion.aar")
val nodePomFile = File(nodeArtifactDir, "provider-mobile-node-$nodeVersion.pom")

val downloadProviderMobileNode by tasks.registering {
    group = "build setup"
    description = "Downloads provider-mobile-node AAR + POM from the GitHub release if missing."

    inputs.property("version", nodeVersion)
    outputs.files(nodeAarFile, nodePomFile)
    outputs.upToDateWhen { nodeAarFile.exists() && nodePomFile.exists() }

    doLast {
        nodeArtifactDir.mkdirs()
        val baseUrl = "https://github.com/mysteriumnetwork/node/releases/download/$nodeVersion"
        listOf(nodeAarFile, nodePomFile).forEach { f ->
            if (f.exists()) return@forEach
            logger.lifecycle("Downloading ${f.name} from $baseUrl/${f.name}")
            val tmp = File(f.parentFile, "${f.name}.tmp")
            try {
                URI("$baseUrl/${f.name}").toURL().openStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                Files.move(
                    tmp.toPath(), f.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } finally {
                tmp.delete()
            }
        }
    }
}

subprojects {
    listOf("com.android.application", "com.android.library").forEach { pluginId ->
        plugins.withId(pluginId) {
            tasks.named("preBuild") {
                dependsOn(rootProject.tasks.named("downloadProviderMobileNode"))
            }
        }
    }
}
