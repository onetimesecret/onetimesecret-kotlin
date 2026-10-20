import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.maven.publish) apply false
}

// ---------------------------------------------------------------------------
// Formatting: Spotless + ktlint. `./gradlew spotlessApply` fixes, `spotlessCheck`
// (part of `check`) fails CI on drift.
// ---------------------------------------------------------------------------
configure<SpotlessExtension> {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(mapOf("ktlint_code_style" to "ktlint_official"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    format("misc") {
        target("**/*.md", "**/*.yml", "**/*.yaml", "**/*.toml", ".gitignore", ".gitattributes")
        targetExclude("**/build/**", "**/.gradle/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ---------------------------------------------------------------------------
// Static analysis: detekt. Config lives in config/detekt/detekt.yml.
// ---------------------------------------------------------------------------
configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(files("onetimesecret-kotlin/src/main/kotlin", "onetimesecret-kotlin/src/test/kotlin"))
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "11"
    reports {
        html.required.set(true)
        sarif.required.set(true)
        txt.required.set(false)
        xml.required.set(false)
    }
}

// ---------------------------------------------------------------------------
// Public API surface tracking. `./gradlew apiDump` after an intentional change;
// `apiCheck` (part of `check`) fails CI on an unreviewed change.
// ---------------------------------------------------------------------------
apiValidation {
    ignoredProjects += listOf(rootProject.name)
    ignoredPackages += listOf("com.onetimesecret.sdk.internal")
}
