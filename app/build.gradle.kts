plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Signing credentials live outside this repository, in ~/.gradle/gradle.properties, and are never committed.
// Without them the release build stays unsigned, so anyone can still clone and build this project.
val readerStoreFile: String? = (findProperty("READER_STORE_FILE") as String?)?.takeIf { File(it).isFile }
val readerStorePassword = findProperty("READER_STORE_PASSWORD") as String?
val readerKeyAlias = findProperty("READER_KEY_ALIAS") as String?
val readerKeyPassword = findProperty("READER_KEY_PASSWORD") as String?
val canSign = readerStoreFile != null && readerStorePassword != null && readerKeyAlias != null && readerKeyPassword != null

android {
    namespace = "de.schimmilab.accessiblereader"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "de.schimmilab.accessiblereader"
        minSdk = 26
        targetSdk = 36
        versionCode = 29
        versionName = "0.9.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (canSign) {
        signingConfigs {
            create("release") {
                storeFile = File(readerStoreFile!!)
                storePassword = readerStorePassword
                keyAlias = readerKeyAlias
                keyPassword = readerKeyPassword
                // v3 carries the proof needed to rotate this key later. Without it the project would be
                // tied to this one key forever, and it exists on a single machine.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            // Left off deliberately: Media3 and PDFBox use reflection, and shrinking them without
            // tested keep rules risks breaking playback in a build nobody can debug.
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/NOTICE") }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.9.2")
    implementation("androidx.media3:media3-session:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Text recognition for scanned pages. The model ships inside the APK, so it runs with no network at all;
    // the manifest strips the INTERNET permission the library brings along.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
