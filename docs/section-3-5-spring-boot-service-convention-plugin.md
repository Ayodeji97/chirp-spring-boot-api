# Section 3.5 - Spring Boot Service Convention Plugin

## What This Section Teaches

Building on the `chirp.kotlin-common` plugin from the previous section, this lesson creates a **second convention plugin** specifically for modules that are Spring Boot services (user, chat, notification). It also shows how different modules can use **different** convention plugins based on their role.

## What Was Created

### `chirp.spring-boot-service.gradle.kts` — New Convention Plugin

```kotlin
plugins {
    id("chirp.kotlin-common")          // Reuses our Kotlin common config
    id("org.springframework.boot")      // Adds Spring Boot plugin
    id("io.spring.dependency-management") // Spring dependency management
}

dependencies {
    "implementation"(libraries.findLibrary("kotlin-reflect").get())
    "implementation"(libraries.findLibrary("kotlin-stdlib").get())
    "implementation"(libraries.findLibrary("spring-boot-starter-web").get())

    "testImplementation"(libraries.findLibrary("spring-boot-starter-test").get())
    "testImplementation"(libraries.findLibrary("kotlin-test-junit5").get())
    "testRuntimeOnly"(libraries.findLibrary("junit-platform-launcher").get())
}
```

**Key points:**
- It **applies `chirp.kotlin-common` first** — convention plugins can build on top of each other! This means every Spring Boot service module automatically gets all the Kotlin config (JVM 21, compiler flags, etc.) plus the Spring-specific stuff.
- It adds **common dependencies** that every Spring Boot service needs: Kotlin reflection, stdlib, Spring Boot web starter, and test dependencies.
- Dependencies use the string-based syntax `"implementation"(...)` instead of the regular `implementation(...)` because convention plugins resolve dependency configurations differently.

### What Changed in Module Build Files

**user, chat, notification** modules now use the service plugin:

```kotlin
plugins {
    `java-library`
    id("chirp.spring-boot-service")  // Gets everything: Kotlin + Spring Boot + common deps
    kotlin("plugin.jpa")             // JPA-specific: makes entity classes open
}
```

**common** module uses only the Kotlin common plugin:

```kotlin
plugins {
    `java-library`
    id("chirp.kotlin-common")           // Just Kotlin config, no Spring Boot runner
    id("org.springframework.boot")       // Spring Boot for dependency management only
}
```

## Why `common` Is Different

The `common` module holds shared code (like data models, utilities) that other modules depend on. It:
- Does NOT need to be a runnable Spring Boot app (no `chirp.spring-boot-service`)
- Still needs Spring Boot on the classpath for things like annotations
- Uses `java-library` so other modules can use its classes via `api` or `implementation` dependencies

## The Plugin Hierarchy

```
chirp.kotlin-common
├── Kotlin JVM + Spring plugin
├── Dependency management with Spring BOM
├── JVM 21 toolchain
├── Compiler options (-Xjsr305=strict)
└── JUnit 5 test config

    chirp.spring-boot-service (extends kotlin-common)
    ├── Everything from kotlin-common ↑
    ├── Spring Boot plugin (bootJar, bootRun tasks)
    ├── Spring dependency management
    └── Common dependencies:
        ├── kotlin-reflect
        ├── kotlin-stdlib
        ├── spring-boot-starter-web
        ├── spring-boot-starter-test
        ├── kotlin-test-junit5
        └── junit-platform-launcher
```

## What `java-library` Does

The `java-library` plugin (applied in each module) gives you two dependency scopes:
- **`api`** — dependencies exposed to consumers (other modules that depend on this one)
- **`implementation`** — dependencies only used internally

This is important in a multi-module project because it controls what's visible across module boundaries.

## Before vs After

**Before (user/build.gradle.kts):**
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.spring)
}
// + repositories, kotlin toolchain, test config...
```

**After:**
```kotlin
plugins {
    `java-library`
    id("chirp.spring-boot-service")
    kotlin("plugin.jpa")
}
```

All the boilerplate is now hidden inside the convention plugins. Each module only declares what's **unique** to it.

## Why String-Based Dependency Syntax in Convention Plugins?

In the convention plugin, dependencies use `"implementation"(...)` with quotes instead of `implementation(...)`. This is because inside precompiled script plugins, the Kotlin DSL accessors for dependency configurations aren't generated automatically — you need to reference them as strings.
