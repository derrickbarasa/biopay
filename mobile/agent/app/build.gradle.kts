import java.io.File
import java.net.URL
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
}

fun readDotEnvValue(file: File, key: String): String? {
    if (!file.isFile) return null
    return file.useLines { lines ->
        lines.map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0 || line.substring(0, separator).trim() != key) null
                else line.substring(separator + 1).trim().removeSurrounding("\"")
            }
            .firstOrNull()
    }
}

val backendDotEnv = rootProject.layout.projectDirectory.file("../../backend/.env").asFile
val dotEnvApiBaseUrl = readDotEnvValue(backendDotEnv, "BIOPAY_MOBILE_API_BASE_URL")
val fallbackApiBaseUrl = dotEnvApiBaseUrl
    ?: providers.environmentVariable("BIOPAY_MOBILE_API_BASE_URL").orNull
    ?: "http://10.0.2.2:7730/biopay"
val configuredApiBaseUrl = providers.gradleProperty("biopayApiBaseUrl")
    // Keep the repository's explicit mobile endpoint ahead of a stale machine-wide value.
    .orElse(fallbackApiBaseUrl)
    .get()
val configuredOfflineAccessDays = (
    providers.gradleProperty("biopayOfflineAccessDays").orNull
        ?: readDotEnvValue(backendDotEnv, "BIOPAY_OFFLINE_ACCESS_DAYS")
        ?: providers.environmentVariable("BIOPAY_OFFLINE_ACCESS_DAYS").orNull
    )?.toIntOrNull()?.takeIf { it > 0 } ?: 60

android {
    namespace = "com.biopay.agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.biopay.agent"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        buildConfigField("String", "BIOPAY_API_BASE_URL", "\"${configuredApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("int", "BIOPAY_OFFLINE_ACCESS_DAYS", configuredOfflineAccessDays.toString())

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
            buildConfigField("String", "BIOMETRIC_DEVICE_LABEL", "\"IDEMIA embedded scanner (SDK 6.42)\"")
            // Without this, onnxruntime-android's x86/x86_64 emulator-only native libs get bundled
            // too -- ~70MB of dead weight on every real (ARM) field device. morphoSmart615 already
            // restricts to real-device ABIs; this flavor was just missing the same filter.
            ndk {
                abiFilters += listOf("armeabi", "armeabi-v7a", "arm64-v8a")
            }
        }
        create("morphoSmart615") {
            dimension = "biometricDevice"
            versionNameSuffix = "-morpho615"
            buildConfigField("String", "BIOMETRIC_DEVICE_LABEL", "\"MorphoSmart 6.15 (Tablet)\"")
            // The 6.15 tablets in the field are old stock still running Android 5.0/5.1 (API 21/22).
            // onnxruntime-android 1.22.0+ (what morphoSmart642 uses) declares minSdkVersion 24 in
            // its own manifest, which would force this flavor's floor to 24 too -- so this flavor
            // pins the last onnxruntime-android release that supports minSdk 21 instead (1.18.0,
            // see the dependencies block below and gradle/libs.versions.toml). Face verification
            // works the same way on both flavors; only the onnxruntime version differs.
            minSdk = 21
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
    androidResources {
        // The face-embedder weights (~37MB, see assets/face/) gain nothing from AAPT's default
        // deflate pass and it only slows the build -- store them uncompressed.
        noCompress += listOf("onnx")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true
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
    // Different onnxruntime-android versions per flavor -- see the minSdk comment on the
    // morphoSmart615 flavor above. Both resolve to the same ai.onnxruntime.* API that
    // OnnxFaceEmbedder.java (shared main/ source) uses.
    "morphoSmart642Implementation"(libs.onnxruntime.android)
    "morphoSmart615Implementation"(libs.onnxruntime.android.legacy)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    "morphoSmart642Implementation"(fileTree(mapOf("dir" to "src/morphoSmart642/libs", "include" to listOf("*.jar"))))
    "morphoSmart615Implementation"(fileTree(mapOf("dir" to "src/morphoSmart615/libs", "include" to listOf("*.jar"))))
}

// ---- Interim free face-embedding model (NOT committed to git) ------------------------------
//
// OpenCV Zoo's SFace (face_recognition_sface_2021dec.onnx, Apache 2.0). Replaces the earlier
// VirtuoTuring prototype: SFace has a published benchmark (99.60% LFW), is maintained by a
// reputable first-party source (opencv.org), and Apache 2.0 does not block commercial use --
// unlike InsightFace's buffalo_l/antelopev2, which score higher but are license-restricted to
// non-commercial research only. Still not IDEMIA MorphoKit: this is a benchmarked, commercially
// licensable bridge model while MorphoKit licensing is pending, not a claim of KYC-grade
// production validation for this deployment's actual population/devices/lighting -- see
// MlKitFaceRecognitionEngine's javadoc and progress.md for the full rationale and status.
// The weights are ~37MB; checking that into git would permanently bloat a ~30MB-packed repo for
// an artifact expected to be thrown away if IDEMIA MorphoKit licensing comes through instead. So
// it's fetched here (idempotent, sha256-verified) rather than committed; app/src/main/assets/face/
// is .gitignore'd. Both flavors ship onnxruntime (different versions -- see the dependencies
// block above), so this lives in the shared src/main/ tree, not a flavor-specific one.
val faceEmbedderAssetsDir = layout.projectDirectory.dir("src/main/assets/face").asFile
val faceEmbedderModelFiles = mapOf(
    "sface.onnx" to Pair(
        "https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx",
        "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79"
    )
)

fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

tasks.register("fetchFaceEmbedderModel") {
    description = "Downloads the face-embedding model into assets/face/ if missing, verifying sha256."
    outputs.dir(faceEmbedderAssetsDir)
    doLast {
        faceEmbedderAssetsDir.mkdirs()
        faceEmbedderModelFiles.forEach { (fileName, urlAndSha) ->
            val (url, expectedSha256) = urlAndSha
            val dest = File(faceEmbedderAssetsDir, fileName)
            if (dest.exists() && sha256Of(dest) == expectedSha256) return@forEach
            logger.lifecycle("Downloading face-embedder asset: $fileName")
            URL(url).openStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
            val actualSha256 = sha256Of(dest)
            if (actualSha256 != expectedSha256) {
                dest.delete()
                throw GradleException("Checksum mismatch for $fileName: expected $expectedSha256 but got $actualSha256")
            }
        }
    }
}

// Every variant's asset-merge AND lint-model-generation tasks read assets/face/ (lint inspects
// assets too); matching only "merge*Assets" left lint's own tasks with an undeclared/ambiguous
// dependency (caught by Gradle's task-graph validation). Hooking preBuild covers all of them.
tasks.named("preBuild") {
    dependsOn("fetchFaceEmbedderModel")
}
