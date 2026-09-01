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
val configuredApiBaseUrl = providers.gradleProperty("biopayApiBaseUrl")
    .orElse(providers.environmentVariable("BIOPAY_MOBILE_API_BASE_URL"))
    .orElse(dotEnvApiBaseUrl ?: "http://10.0.2.2:7730/biopay")
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
    androidResources {
        // The prototype face-embedder weights (~98MB, see assets/face/) gain nothing from AAPT's
        // default deflate pass and it only slows the build -- store them uncompressed.
        noCompress += listOf("onnx", "data")
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
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    "morphoSmart642Implementation"(fileTree(mapOf("dir" to "src/morphoSmart642/libs", "include" to listOf("*.jar"))))
    "morphoSmart615Implementation"(fileTree(mapOf("dir" to "src/morphoSmart615/libs", "include" to listOf("*.jar"))))
}

// ---- Prototype face-embedding model (NOT committed to git) --------------------------------
//
// VirtuoTuring/virtuoturing-face-embedder (Hugging Face, MIT license). This is an explicitly
// unvalidated prototype -- see MlKitFaceRecognitionEngine's javadoc and progress.md for why.
// The weights are ~98MB; checking that into git would permanently bloat a ~30MB-packed repo for
// an artifact expected to be thrown away if IDEMIA MorphoKit licensing comes through instead. So
// it's fetched here (idempotent, sha256-verified) rather than committed; app/src/main/assets/face/
// is .gitignore'd.
val faceEmbedderAssetsDir = layout.projectDirectory.dir("src/main/assets/face").asFile
val faceEmbedderModelFiles = mapOf(
    "virtuoturing.onnx" to Pair(
        "https://huggingface.co/VirtuoTuring/virtuoturing-face-embedder/resolve/main/virtuoturing.onnx",
        "c0e9d280e0dd4051b4c95f47b0a317937151e455e03a0354fede8f3423973c2d"
    ),
    "best_embedder.onnx.data" to Pair(
        "https://huggingface.co/VirtuoTuring/virtuoturing-face-embedder/resolve/main/best_embedder.onnx.data",
        "a811d5f8b7543dc7a10a118dead56ab5999a655893bab09a5a1cb2451d4a8873"
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
    description = "Downloads the prototype face-embedding model into assets/face/ if missing, verifying sha256."
    outputs.dir(faceEmbedderAssetsDir)
    doLast {
        faceEmbedderAssetsDir.mkdirs()
        faceEmbedderModelFiles.forEach { (fileName, urlAndSha) ->
            val (url, expectedSha256) = urlAndSha
            val dest = File(faceEmbedderAssetsDir, fileName)
            if (dest.exists() && sha256Of(dest) == expectedSha256) return@forEach
            logger.lifecycle("Downloading prototype face-embedder asset: $fileName")
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
