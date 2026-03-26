plugins {
    `java-library`
    id("chirp.kotlin-common")
    id("org.springframework.boot")
}

group = "com.danzucker"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlin.reflect)
    api(libs.jackson.module.kotlin)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}