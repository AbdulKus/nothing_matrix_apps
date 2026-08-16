plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseStoreFile = providers.environmentVariable("GLYPH_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("GLYPH_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("GLYPH_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("GLYPH_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val buildGitSha = providers.environmentVariable("GITHUB_SHA").orElse("unknown").get()

android {
    namespace = "com.abdulkus.glyphlab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.abdulkus.glyphlab"
        minSdk = 34
        targetSdk = 36
        versionCode = 19
        versionName = "0.4.0"
        buildConfigField("String", "GIT_SHA", "\"$buildGitSha\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storeType = "PKCS12"
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
