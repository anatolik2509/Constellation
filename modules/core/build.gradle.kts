plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.10.1")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    // perf-smoke runs with the normal suite; exclude it with -PskipPerfSmoke
    useJUnitPlatform {
        if (project.hasProperty("skipPerfSmoke")) excludeTags("perf-smoke")
    }
    testLogging {
        events("failed", "skipped")
    }
}
