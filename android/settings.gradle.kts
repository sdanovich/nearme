pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Local platform-stack snapshots, for dev before/without GitHub Packages creds.
        mavenLocal()
        // Shared auth artifacts (com.danovich.platform:android-auth). Only added
        // when GitHub Packages credentials are present (CI, or
        // ~/.gradle/gradle.properties), so a clone without a token still builds
        // against mavenLocal.
        val gprUser = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
        val gprKey = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        if (gprUser != null && gprKey != null) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/sdanovich/platform-stack")
                credentials {
                    username = gprUser
                    password = gprKey
                }
            }
        }
    }
}
rootProject.name = "NearMe"
include(":app")
