import java.util.Properties

plugins {
    id("com.android.application")
}

val teamName = (findProperty("safepulse.teamName") as String?)?.trim().takeUnless { it.isNullOrEmpty() } ?: "TEAM"
val usePleosSdk = (findProperty("safepulse.pleosSdk") as String?)?.toBoolean() ?: true

// 서명 정보는 저장소에 올리지 않는다(keystore.properties는 .gitignore).
// 없으면 디버그 키로 서명한 release APK를 만든다. 에뮬레이터 설치·시연에는 문제없다.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.safepulse.app"
    compileSdk = 35

    // AAOS CarPropertyManager(차속·기어·EPB)를 쓰기 위한 플랫폼 라이브러리
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.safepulse.app"
        minSdk = 28 // Pleos 요구사항: 차량 보안(사이닝 키 로테이션) 때문에 API 28 이상
        targetSdk = 34 // Pleos Connect Emulator 2.1.0 = Android 14
        versionCode = 1
        versionName = "0.1.0-mvp"
        buildConfigField("boolean", "PLEOS_SDK", usePleosSdk.toString())
        buildConfigField("String", "TEAM_NAME", "\"$teamName\"")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Pleos SDK가 리플렉션을 쓰므로 난독화·축소는 끈다(공식 샘플과 동일).
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // 마감 직전 빌드가 lint 경고로 막히지 않게 한다. 경고는 리포트로 확인.
        abortOnError = false
    }
}

dependencies {
    implementation(project(":core"))
    if (usePleosSdk) {
        // 공식 샘플(pleos-sample-vehicle-app)과 같은 버전
        implementation("ai.pleos.playground:Vehicle:2.3.2")
    }
    testImplementation("junit:junit:4.13.2")
}

// 공모전 규정: apk/ 폴더에 "팀명_앱이름_release.apk"
tasks.register<Copy>("exportReleaseApk") {
    group = "safepulse"
    description = "release APK를 빌드해 apk/${teamName}_SafePulse_release.apk 로 복사한다."
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "${teamName}_SafePulse_release.apk" }
}
