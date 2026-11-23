plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "mx.radar.yolo"
    compileSdk = 35

    defaultConfig {
        applicationId = "mx.radar.yolo"
        minSdk = 29 // MediaStore RELATIVE_PATH (guardar tickets en la galería sin permisos)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Los .tflite se mapean a memoria directo desde el APK: no deben comprimirse
    androidResources { noCompress += "tflite" }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.mlkit.text)
    implementation(libs.coroutines.play)
    implementation(libs.datastore)
}
