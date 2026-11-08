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
    }
}

rootProject.name = "VYLIA"
include(":app")
// Bouclier VYLIA (VPN local, tuile des réglages rapides) : isolé pour pouvoir le retirer d'une variante Play.
include(":shield")
// Moteur de score, en Kotlin/JVM pur : partagé par l'app et par l'outil de l'annuaire public.
include(":scoring")
// Annuaire public des notes d'apps populaires : génération hebdomadaire hors de l'appareil (voir directory/).
include(":directory-cli")
