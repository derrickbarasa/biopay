// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.3.2" apply false
}

// OneDrive can briefly lock DEX files while Gradle replaces them. Keep generated build output
// in unsynchronised local storage on Windows; other environments retain Gradle's default path.
providers.environmentVariable("LOCALAPPDATA").orNull?.let { localAppData ->
    val localBuildRoot = file("$localAppData/BioPay/gradle-build/agent")
    layout.buildDirectory.set(localBuildRoot.resolve("root"))
    subprojects {
        layout.buildDirectory.set(localBuildRoot.resolve(name))
    }
}
