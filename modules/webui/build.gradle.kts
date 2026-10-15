plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

application {
    // Dev-only dogfood entry; the supported entry point is the Gradle plugin (modules/gradle-plugin).
    mainClass = "org.example.constellation.webui.DevMainKt"
}

tasks.test {
    useJUnitPlatform()
}
