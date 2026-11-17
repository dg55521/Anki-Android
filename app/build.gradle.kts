plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.anki"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.ankidroid.xkqvmw"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        getByName("debug") {
            val ks = rootProject.file("debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
}

abstract class CopyApkToRootTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputFile
    abstract val destinationApk: RegularFileProperty

    @TaskAction
    fun copyApk() {
        val apk = sourceDir.get().asFile
            .listFiles { file -> file.isFile && file.extension == "apk" }
            ?.firstOrNull()
            ?: return
        apk.copyTo(destinationApk.get().asFile, overwrite = true)
    }
}

val copyDebugApkToRoot by tasks.registering(CopyApkToRootTask::class) {
    sourceDir.set(layout.buildDirectory.dir("outputs/apk/debug"))
    destinationApk.set(rootProject.layout.projectDirectory.file("app-debug.apk"))
    mustRunAfter("packageDebug", "createDebugApkListingFileRedirect")
}

val copyReleaseApkToRoot by tasks.registering(CopyApkToRootTask::class) {
    sourceDir.set(layout.buildDirectory.dir("outputs/apk/release"))
    destinationApk.set(rootProject.layout.projectDirectory.file("app-release.apk"))
    mustRunAfter("packageRelease", "createReleaseApkListingFileRedirect")
}

tasks.configureEach {
    when (name) {
        "assembleDebug" -> finalizedBy(copyDebugApkToRoot)
        "assembleRelease" -> finalizedBy(copyReleaseApkToRoot)
    }
}

