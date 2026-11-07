// Bouclier VYLIA : VPN local qui filtre les requêtes DNS sur le téléphone, et sa tuile des réglages rapides.
// Module à part pour pouvoir le retirer d'une variante distribuée sur Google Play. Il ne dépend pas de :app :
// l'application lui fournit son contrôleur (ShieldHost) et réagit au premier blocage par un rappel.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vylia.privacy.shield"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
}
