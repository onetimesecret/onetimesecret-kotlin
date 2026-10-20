pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Auto-provisions the JDK declared in the toolchain block so contributors
    // and CI do not need a specific JDK preinstalled.
    // Version catalogs are not available in the settings `plugins` block.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "onetimesecret-kotlin-root"

// The published library module. The repository is `onetimesecret-kotlin`;
// the Maven artifact is `com.onetimesecret:onetimesecret-kotlin`.
include(":onetimesecret-kotlin")
