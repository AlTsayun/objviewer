import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    id("org.jetbrains.compose") version "0.4.0"
}

group = "com.bsuir"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
    implementation("org.jetbrains.kotlinx:multik-api:0.0.1")
    implementation("org.jetbrains.kotlinx:multik-default:0.0.1")
    implementation("org.ejml:ejml-kotlin:0.41")
    implementation("org.ejml:ejml-all:0.41")
    implementation("org.apache.commons:commons-math3:3.6.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}

compose.desktop {
    application {
        mainClass = "com.bsuir.objviewer.gui.ObjViewerApplication"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "objviewer"
            packageVersion = "1.0.0"
        }
    }
}
