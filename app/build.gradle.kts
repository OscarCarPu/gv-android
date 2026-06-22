import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val envDev =
    Properties().apply {
        val envFile = rootProject.file(".env")
        if (envFile.exists()) load(envFile.inputStream())
    }

val envProd =
    Properties().apply {
        val envFile = rootProject.file(".env.prod")
        if (envFile.exists()) load(envFile.inputStream())
    }

val keystore =
    Properties().apply {
        val ksFile = rootProject.file("keystore.properties")
        if (ksFile.exists()) load(ksFile.inputStream())
    }

val version =
    Properties().apply {
        val f = rootProject.file("version.properties")
        if (f.exists()) load(f.inputStream())
    }

android {
    namespace = "com.gv.app"
    compileSdk = 35
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.gv.app"
        minSdk = 35
        targetSdk = 35
        versionCode = version.getProperty("versionCode", "1").toInt()
        versionName = version.getProperty("versionName", "1.0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystore.getProperty("storeFile", "gv.jks"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "BASE_URL", "\"${envDev.getProperty("BASE_URL")}\"")
            buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${envDev.getProperty("SPOTIFY_CLIENT_ID", "")}\"")
            buildConfigField("String", "SPOTIFY_CLIENT_SECRET", "\"${envDev.getProperty("SPOTIFY_CLIENT_SECRET", "")}\"")
            buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"${envDev.getProperty("SPOTIFY_REDIRECT_URI", "")}\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "BASE_URL", "\"${envProd.getProperty("BASE_URL")}\"")
            buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${envProd.getProperty("SPOTIFY_CLIENT_ID", "")}\"")
            buildConfigField("String", "SPOTIFY_CLIENT_SECRET", "\"${envProd.getProperty("SPOTIFY_CLIENT_SECRET", "")}\"")
            buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"${envProd.getProperty("SPOTIFY_REDIRECT_URI", "")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    // Export the Room schema so migrations can be authored against committed snapshots.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-test")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.browser:browser:1.8.0")

    // Offline foundation: local cache + background sync.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.work:work-testing:2.9.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}
