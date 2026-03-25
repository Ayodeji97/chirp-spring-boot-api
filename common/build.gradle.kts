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
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}