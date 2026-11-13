// Outil de l'annuaire public : rapports Exodus → notes VYLIA (module :scoring) → directory.json → site statique.
// Lancé chaque semaine par .github/workflows/directory.yml ; voir directory/README.md.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

// Bytecode Java 17, comme l'app (le JDK installé peut être plus récent).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

application {
    mainClass.set("com.vylia.directory.MainKt")
}

dependencies {
    implementation(project(":scoring"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":scoring")))
}

tasks.test {
    // Les tests lisent les règles et les données de l'app depuis la racine du dépôt.
    workingDir = rootProject.projectDir
    // Ces fichiers sont lus directement sur le disque : Gradle doit relancer les tests quand l'un change.
    inputs.dir(rootProject.file("app/src/main/assets")).withPropertyName("appAssets").withPathSensitivity(PathSensitivity.RELATIVE)
}
