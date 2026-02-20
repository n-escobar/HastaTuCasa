plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.hastatucasa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.hastatucasa"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // ── Product Flavors ───────────────────────────────────────────────────────
    flavorDimensions += "audience"

    productFlavors {
        create("shopper") {
            dimension = "audience"
            applicationIdSuffix = ".shopper"
            versionNameSuffix = "-shopper"
            resValue("string", "app_flavor_label", "HastaTuCasa")
        }
        create("deliverer") {
            dimension = "audience"
            applicationIdSuffix = ".deliverer"
            versionNameSuffix = "-deliverer"
            resValue("string", "app_flavor_label", "HastaTuCasa · Deliverer")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // ── Compose BOM ──────────────────────────────────────────────────────────
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // ── Core ──────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── Navigation ────────────────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Image loading ─────────────────────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── Debug ─────────────────────────────────────────────────────────────────
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ── Unit testing ──────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")

    // Coroutines test utilities (runTest, TestDispatcher, advanceUntilIdle, etc.)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Turbine — Flow testing (awaitItem, test { }, expectNoEvents, etc.)
    testImplementation("app.cash.turbine:turbine:1.1.0")
}