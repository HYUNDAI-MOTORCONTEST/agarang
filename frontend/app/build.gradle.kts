plugins {
    // AGP 9는 Kotlin 지원이 내장(built-in Kotlin)되어 org.jetbrains.kotlin.android 플러그인을 따로 적용하지 않는다.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// debug 빌드의 백엔드 주소. 다른 주소로 붙이려면
// `./gradlew :frontend:app:installDebug -Phmg.backendUrl=http://10.0.2.2:9090` 처럼 바꾼다.
val debugBackendUrl: String = providers.gradleProperty("hmg.backendUrl").getOrElse("http://10.0.2.2:8081")

// release 서명. 키·비밀번호는 레포에 두지 않고 환경변수로만 받는다(*.jks는 gitignore).
// 네 값이 모두 있어야 서명 설정을 만든다. 없으면 assembleRelease 결과는 서명되지 않은 APK다.
val releaseStoreFile: String? = providers.environmentVariable("HMG_RELEASE_STORE_FILE").orNull
val releaseStorePassword: String? = providers.environmentVariable("HMG_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias: String? = providers.environmentVariable("HMG_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword: String? = providers.environmentVariable("HMG_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.hmgchallenge.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.hmgchallenge.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = providers.gradleProperty("hmg.testRunner")
            .getOrElse("com.hmgchallenge.app.navigation.NavigationSmokeInstrumentation")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // 차량 보안 요건(minSdk 28: 서명 키 로테이션·리니지)에 맞춰 v3 서명을 명시적으로 넣는다.
                // AGP 기본값으로는 minSdk 28에서 v2만 들어간다(GCP 빌드 apksigner로 확인).
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // 에뮬레이터에서 호스트 PC의 백엔드(localhost:8081)는 10.0.2.2로 접근한다.
            buildConfigField("String", "BACKEND_BASE_URL", "\"$debugBackendUrl\"")
            // `-Phmg.bundledData=true`면 debug도 백엔드 없이 APK 내장 시설 데이터를 쓴다(release 동작 재현용).
            buildConfigField("boolean", "BUNDLED_FACILITY_DATA",
                providers.gradleProperty("hmg.bundledData").getOrElse("false").toBoolean().toString())
        }
        release {
            isMinifyEnabled = false
            // 제출 빌드는 서버 없이 동작한다: 공개 시설·경로 접근성 데이터를 APK assets에서 읽는다.
            // 서버 주소는 쓰지 않지만 BuildConfig 필드는 debug와 맞춰 둔다.
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://example.invalid\"")
            buildConfigField("boolean", "BUNDLED_FACILITY_DATA", "true")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // CarUxRestrictionsManager 등 AAOS android.car API(SDK optional 라이브러리, compileOnly처럼 동작).
    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// 백엔드가 서빙하는 공개 시설·경로 접근성 JSON을 그대로 assets에 넣는다(사본을 따로 두지 않는다).
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory("../../backend/src/main/resources/facilities")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Pleos Connect Vehicle SDK
    implementation(libs.pleos.vehicle)
    // Pleos NaviHelper SDK (ETA·경유지·현재 위치·도착 이벤트). 권한 NAVI_ROUTE(조회)·NAVI_ROUTE_SEARCH(경로·경유지 변경)
    implementation(libs.pleos.navihelper)
    // 아래 3종은 Playground 표기상 "준비 중"이지만 공모전에서는 매니페스트 선언만으로 쓸 수 있다(docs/contest/rules.md 1장).
    // 제출 앱(release)은 쓰지 않는다. 연결 확인(src/debug/.../sdk/PleosSdkCheck.kt)과 권한은 debug 빌드에만 있다.
    // FusedLocation(위치), TextToSpeech·SpeechToText(음성 — ON_DEVICE 모드는 자격 증명 불필요. 서버 모드의 클라이언트 ID·시크릿은 레포에 넣지 말 것)
    implementation(libs.pleos.fusedlocation)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
}
