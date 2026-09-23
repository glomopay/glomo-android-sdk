plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.glomopay.sdk.android.sampleApp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.glomopay.sdk.android.sampleApp"
        minSdk = 24
        // Override with -PMERCHANT_TARGET_SDK=34, 35 or 36 for the support matrix.
        targetSdk = providers.gradleProperty("MERCHANT_TARGET_SDK").orElse("36").get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":glomo-android-sdk"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}
