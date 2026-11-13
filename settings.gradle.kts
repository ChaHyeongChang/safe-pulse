pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Connect SDK (Vehicle SDK)
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}

rootProject.name = "SafePulse"
include(":core")
// CI에서 판별 엔진 테스트만 돌릴 때는 SAFEPULSE_CORE_ONLY=1 (Android SDK 불필요)
if (System.getenv("SAFEPULSE_CORE_ONLY") == null) {
    include(":app")
}
