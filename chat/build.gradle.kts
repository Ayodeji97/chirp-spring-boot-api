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
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}