# Section 3.6 - Spring Boot App Convention Plugin

## What This Section Teaches

This section creates the **third and final convention plugin** — one specifically for the `app` module, which is the entry point of your Spring Boot application. It also dramatically simplifies the `app/build.gradle.kts` by moving all boilerplate into the plugin.

## What Was Created

### `chirp.spring-boot-app.gradle.kts` — The App Convention Plugin

```kotlin
plugins {
    id("chirp.spring-boot-service")    // Inherits everything from service plugin
    id("org.springframework.boot")      // Spring Boot (bootJar, bootRun)
    kotlin("plugin.spring")             // Makes Spring classes open
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
```

**What makes this different from `chirp.spring-boot-service`?**

| Feature | `chirp.kotlin-common` | `chirp.spring-boot-service` | `chirp.spring-boot-app` |
|---|---|---|---|
| Kotlin JVM + compiler config | Yes | Yes (inherited) | Yes (inherited) |
| Spring dependency management | Yes | Yes (inherited) | Yes (inherited) |
| Spring Boot plugin | No | Yes | Yes (inherited) |
| Common deps (web, test, etc.) | No | Yes | Yes (inherited) |
| `allOpen` for JPA entities | No | No | **Yes** |
| Java toolchain declaration | No | No | **Yes** |

The `allOpen` block is the key addition — it tells the Kotlin compiler to make any class annotated with `@Entity`, `@MappedSuperclass`, or `@Embeddable` **open** (non-final). This is required because JPA/Hibernate needs to create proxy subclasses of your entities, which is impossible if the classes are `final` (Kotlin's default).

## What Changed

### `app/build.gradle.kts` — Massively Simplified

**Before (22+ lines of plugins, config, and boilerplate):**
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
}
// + java toolchain, repositories, kotlin compiler options, allOpen, test config...
```

**After (just 4 lines of real config):**
```kotlin
plugins {
    id("chirp.spring-boot-app")
}

dependencies {
    implementation(projects.user)
    implementation(projects.chat)
    implementation(projects.notification)
    implementation(projects.common)
}
```

All the configuration is now in the convention plugin. The only thing left in `app/build.gradle.kts` is what's **unique** to the app module: its dependencies on the other modules.

### `settings.gradle.kts` — Added Typesafe Project Accessors

```kotlin
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
```

This enables the `projects.user`, `projects.chat`, etc. syntax instead of `project(":user")`. It's a Gradle feature that generates type-safe accessors for your modules, giving you IDE auto-completion and compile-time safety.

## The Complete Plugin Hierarchy

```
chirp.kotlin-common
│   Kotlin JVM, Spring plugin, dependency management,
│   JVM 21 toolchain, compiler options, JUnit 5
│
├── chirp.spring-boot-service (extends kotlin-common)
│   │   Spring Boot plugin, common deps
│   │   (kotlin-reflect, stdlib, web, test)
│   │
│   └── chirp.spring-boot-app (extends spring-boot-service)
│           allOpen for JPA entities, Java toolchain
│
│   Used by: app module
│
├── chirp.spring-boot-service
│   Used by: user, chat, notification modules
│
└── chirp.kotlin-common (standalone)
    Used by: common module
```

## Why the `app` Module Has Its Own Plugin

The `app` module is special:
- It's the **only runnable module** — it has the `main()` function and `@SpringBootApplication`
- It **depends on all other modules** and wires everything together
- It needs `allOpen` for JPA entities because it configures the persistence layer
- Other service modules (user, chat, notification) don't need `allOpen` — they deal with service logic, not entity definitions directly

## What `allOpen` Does

Kotlin classes are `final` by default. But JPA/Hibernate creates proxy classes that extend your entity classes for lazy loading and change detection. If your entity is `final`, Hibernate can't create these proxies.

The `allOpen` block tells the Kotlin compiler: "Any class annotated with these JPA annotations should be compiled as `open` (non-final)." This is the same thing the `kotlin-jpa` plugin does, but configured explicitly.

## What Typesafe Project Accessors Give You

Without it:
```kotlin
implementation(project(":user"))  // String-based, no compile-time check
```

With it:
```kotlin
implementation(projects.user)  // Type-safe, IDE auto-completion
```

If you rename a module, the old accessor won't compile — catching the error immediately instead of at runtime.
