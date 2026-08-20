plugins {
    alias(libs.plugins.android.application)
}

val configuredApiBaseUrl = providers.gradleProperty("biopayApiBaseUrl")
    .orElse("http://192.168.100.163:7730/biopay")
    .get()

android {
    namespace = "com.biopay.agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.biopay.agent"
        minSdk = 21
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        buildConfigField("String", "BIOPAY_API_BASE_URL", "\"${configuredApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Two Morpho SDK releases (com.morpho.morphosmart.sdk / com.morpho.android.usb) define the
    // SAME package + class names -- putting both jars on one classpath is a guaranteed duplicate-
    // class build failure. Product flavors keep them in mutually-exclusive compiled variants: the
    // BiometricDevice contract lives in main/, and each flavor supplies its own concrete adapter
    // (src/morphoSmart642/java/... vs src/morphoSmart615/java/...) plus its own SDK jar
    // (src/<flavor>/libs/*.jar). Adding a third scanner vendor later is "add a flavor, add its jar,
    // implement BiometricDeviceProvider in its source set" -- no changes to shared code.
    flavorDimensions += "biometricDevice"
    productFlavors {
        create("morphoSmart642") {
            dimension = "biometricDevice"
            versionNameSuffix = "-morpho642"
            buildConfigField("String", "BIOMETRIC_DEVICE_LABEL", "\"MorphoSmart 6.42 (USB)\"")
        }
        create("morphoSmart615") {
            dimension = "biometricDevice"
            versionNameSuffix = "-morpho615"
            buildConfigField("String", "BIOMETRIC_DEVICE_LABEL", "\"MorphoSmart 6.15 (Tablet)\"")
            ndk {
                abiFilters += listOf("armeabi", "armeabi-v7a", "arm64-v8a")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.cardview)
    implementation(libs.recyclerview)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.workmanager)
    implementation(libs.lifecycle.process)
    implementation(libs.mlkit.face.detection)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    "morphoSmart642Implementation"(fileTree(mapOf("dir" to "src/morphoSmart642/libs", "include" to listOf("*.jar"))))
    "morphoSmart615Implementation"(fileTree(mapOf("dir" to "src/morphoSmart615/libs", "include" to listOf("*.jar"))))
}
