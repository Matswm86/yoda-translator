plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "no.mwm.yoda"
    compileSdk = 35

    defaultConfig {
        applicationId = "no.mwm.yoda"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        // Phones only: skips the x86_64 copy of the LiteRT-LM native library (~25 MB).
        ndk { abiFilters += "arm64-v8a" }
    }

    // One committed debug key, so every CI build installs over the last one
    // and the imported model file (about 1 GB) survives app updates.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // On-device runtime for the fine-tuned Gemma 3 1B (.litertlm).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("failed")
    }
    // Forward the corpus batch-tool properties into the test JVM; -D on the
    // Gradle command line only reaches the Gradle daemon otherwise. Declaring
    // them as inputs also stops Gradle calling the task UP-TO-DATE when only
    // the input file changed. See RuleEngineBatchTool.
    listOf("yoda.batch.in", "yoda.batch.out").forEach { key ->
        System.getProperty(key)?.let {
            systemProperty(key, it)
            inputs.property(key, it)
            outputs.upToDateWhen { false }
        }
    }
}
