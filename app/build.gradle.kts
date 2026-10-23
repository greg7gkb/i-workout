import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Secrets live in local.properties (gitignored). See README.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.greg7gkb.iworkout"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.greg7gkb.iworkout"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "0.3"

        buildConfigField("String", "AIRTABLE_TOKEN", "\"${localProps.getProperty("airtable.token", "")}\"")
        buildConfigField("String", "AIRTABLE_BASE_ID", "\"appy16TLEgUqF2KWV\"")
    }

    // Dedicated release key, configured in local.properties (release.storeFile etc.; see README).
    // Updates must be signed with the same key, so back up the keystore and its passwords.
    val releaseStore = localProps.getProperty("release.storeFile")?.let { rootProject.file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinking for a small APK; debug is untouched.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a configured release key (e.g. a fresh clone), fall back to the debug key so it still builds.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // Prebuilt in androidx.graphics; AGP can't strip it without an NDK and warns on every build.
        jniLibs.keepDebugSymbols += "**/libandroidx.graphics.path.so"
    }

    lint {
        // Strict: run every check (including optional ones) and fail on any warning.
        checkAllWarnings = true
        warningsAsErrors = true
        abortOnError = true
        // Calling an API above minSdk is fatal, so it also fails every assembleRelease (lintVital).
        fatal += "NewApi"
        // "A newer version is available" depends on the day lint runs, not on the code, so it would fail CI
        // whenever anything upstream releases. Dependabot handles updates instead (docs/FOUNDATION.md).
        disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
    }

    testOptions {
        // Robolectric runs Room and other Android code on the JVM; it needs the merged resources.
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // JVM tests only (./gradlew test); none of this ships in the app.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}
