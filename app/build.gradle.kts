plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.dualcam"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.dualcam"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = 30   // 동시 카메라 API 요구사항
    }
    dependencies {
        implementation("com.google.mediapipe:tasks-vision:0.10.14")
        // ... 나머지 의존성
    }

}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    val cameraxVersion = "1.3.4"   // 1.3.0 이상이면 동시 카메라 지원, 최신 버전 확인 권장
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.appcompat:appcompat:1.7.0")
}