plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

kotlin {
    explicitApi()
}

group = "com.glomopay"
version = "1.0.0"

val mixpanelToken = providers.gradleProperty("MIXPANEL_TOKEN")
    .orElse(providers.environmentVariable("MIXPANEL_TOKEN"))
    .orElse("")
val sentryDsn = providers.gradleProperty("SENTRY_DSN")
    .orElse(providers.environmentVariable("SENTRY_DSN"))
    .orElse("")

android {
    namespace = "com.glomopay.sdk.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        resValue("string", "glomopay_sdk_version", project.version.toString())
        resValue("string", "glomopay_mixpanel_token", mixpanelToken.get())
        resValue("string", "glomopay_sentry_dsn", sentryDsn.get())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("com.glomopay", "glomo-android-sdk", version.toString())

    pom {
        name.set("Glomo Android SDK")
        description.set("Glomo Android SDK for cross-border payments")
        url.set("https://github.com/glomopay/glomo-android-sdk")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("glomo")
                name.set("Glomo")
                url.set("https://glomopay.com")
            }
        }
        scm {
            url.set("https://github.com/glomopay/glomo-android-sdk")
            connection.set("scm:git:git://github.com/glomopay/glomo-android-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/glomopay/glomo-android-sdk.git")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.scottyab:rootbeer-lib:0.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.sentry:sentry:8.50.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    testImplementation("org.json:json:20240303")
}
