plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.beomseok.pvc.capture"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // org.webrtc 타입이 공개 API에 드러나므로 api로 노출한다.
    api(libs.webrtc)
    api(libs.coroutines.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}
