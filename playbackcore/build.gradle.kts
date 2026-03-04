plugins {
    id("com.android.library")
}

android {
    namespace = "com.lumera.playbackcore"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += "MissingTranslation"
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    val media3Version = "1.10.0-beta01"

    api("androidx.media3:media3-common:$media3Version")
    api("androidx.media3:media3-container:$media3Version")
    api("androidx.media3:media3-datasource:$media3Version")
    api("androidx.media3:media3-datasource-okhttp:$media3Version")
    api("androidx.media3:media3-decoder:$media3Version")
    api("androidx.media3:media3-exoplayer-dash:$media3Version") {
        exclude(group = "androidx.media3", module = "media3-exoplayer")
    }
    api("androidx.media3:media3-exoplayer-hls:$media3Version") {
        exclude(group = "androidx.media3", module = "media3-exoplayer")
    }
    api("androidx.media3:media3-exoplayer-rtsp:$media3Version") {
        exclude(group = "androidx.media3", module = "media3-exoplayer")
    }
    api("androidx.media3:media3-exoplayer-smoothstreaming:$media3Version") {
        exclude(group = "androidx.media3", module = "media3-exoplayer")
    }
    api("androidx.media3:media3-extractor:$media3Version")
    api("androidx.media3:media3-session:$media3Version")
    api("androidx.media3:media3-ui:$media3Version")
}
