import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.components.resources)
            api("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            api("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
            api("org.jetbrains.androidx.navigation:navigation-compose:2.9.1")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            api("androidx.room:room-runtime:2.7.2")
            api("androidx.sqlite:sqlite-bundled:2.5.2")
            api("androidx.datastore:datastore-preferences-core:1.1.7")
            api("com.squareup.okio:okio:3.9.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:atomicfu:0.27.0")
            implementation("io.ktor:ktor-client-core:3.2.3")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.2.3")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.2.3")
        }
    }
}

android {
    namespace = "com.moblized.nutritrack.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Room's generated code for each platform.
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, "androidx.room:room-compiler:2.7.2") }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.moblized.nutritrack.resources"
    generateResClass = always
}
