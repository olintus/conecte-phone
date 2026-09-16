pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "Linphone"
            url = uri("https://download.linphone.org/maven_repository")
            content { includeGroupByRegex("org\\.linphone.*") }
        }
    }
}

rootProject.name = "ConectePhone"
include(":app")
