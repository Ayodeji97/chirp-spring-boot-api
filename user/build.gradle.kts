plugins {
    `java-library`
    id("chirp.spring-boot-service")
    kotlin("plugin.jpa")
}

group = "com.danzucker"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.common)

    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.postgresql)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}