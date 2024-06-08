plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
}

android {
    namespace = "com.teslamotors.protocol"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.teslamotors.protocol"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // protobuf
    implementation("com.google.protobuf:protobuf-javalite:3.23.0")
    implementation("com.google.protobuf:protobuf-kotlin-lite:3.23.0")
    implementation("com.google.protobuf:protoc:3.20.1")

}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.20.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("kotlin")
                create("java") { option("lite") }
            }
        }
    }
}

