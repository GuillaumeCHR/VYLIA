// Moteur de score VYLIA, sans Android : règles, score, badges et motifs de sécurité. L'app et l'outil de l'annuaire
// public (:directory-cli) l'utilisent tous deux : une même app a la même note dans l'app et dans l'annuaire.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

// Bytecode Java 17, comme l'app (le JDK installé peut être plus récent).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit4)
    testFixturesImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    workingDir = rootProject.projectDir
}
