// Runnable end-to-end example. Not published; it resolves the SDK from this
// build so it always exercises the current source.
//
//   ONETIME_BASE_URL=https://eu.onetimesecret.com ./gradlew :examples:roundtrip:run
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(
        libs.versions.java.toolchain
            .get()
            .toInt(),
    )
}

dependencies {
    implementation(project(":onetimesecret-kotlin"))
}

application {
    mainClass.set("com.onetimesecret.examples.RoundTripKt")
}
