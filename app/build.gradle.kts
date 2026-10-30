import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.moblized.nutritrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moblized.nutritrack"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/nutritrack.jks")
            storePassword = "nutritrack"
            keyAlias = "nutritrack"
            keyPassword = "nutritrack"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Screenshot tests write PNGs to app/build/screenshots.
            it.systemProperty("roborazzi.test.record", "true")
            it.maxHeapSize = "3g"
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showStandardStreams = true
            }
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Screens, data and logic shared with the iPhone app.
    implementation(project(":shared"))

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okio:okio:3.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.29.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.29.0")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.9.4")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.4")
}
