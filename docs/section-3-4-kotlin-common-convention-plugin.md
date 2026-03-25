# Section 3.4 - Kotlin Common Convention Plugin

## What is a Convention Plugin?

A **convention plugin** is a way to avoid duplicating the same Gradle configuration across multiple modules. Instead of copy-pasting the same plugins, compiler settings, and repository declarations into every module's `build.gradle.kts`, you define them **once** in a shared plugin. Then each module just applies that single plugin.

> Think of it like a base class for your Gradle configs — define shared behavior once, inherit it everywhere.

## What Was Created

### 1. `VersionCatalogExt.kt` — Version Catalog Helper

```kotlin
val Project.libraries: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
```

**What it does:** Convention plugins (precompiled script plugins) can't use the nice `libs.versions.toml` syntax like `libs.plugins.kotlin.jvm` that you use in regular `build.gradle.kts` files. This extension property gives you access to the version catalog from within convention plugins by creating a `libraries` property on any `Project`.

**Why it's needed:** Inside a convention plugin, you need to look up versions dynamically. For example, `libraries.findVersion("spring-boot").get()` fetches the Spring Boot version from your `libs.versions.toml` file.

### 2. `chirp.kotlin-common.gradle.kts` — The Convention Plugin

This is the main file. The filename `chirp.kotlin-common.gradle.kts` means it registers as a Gradle plugin with ID **`chirp.kotlin-common`**. Any module can apply it with:

```kotlin
plugins {
    id("chirp.kotlin-common")
}
```

Here's what the plugin configures:

| Configuration | What it does |
|---|---|
| `kotlin("jvm")` | Applies the Kotlin JVM plugin so the module can compile Kotlin code |
| `kotlin("plugin.spring")` | Makes Spring-annotated classes `open` automatically (Kotlin classes are `final` by default, but Spring needs them open for proxying) |
| `id("io.spring.dependency-management")` | Enables Spring's dependency management so you don't have to specify versions for Spring libraries |
| `repositories { mavenCentral() }` | Tells Gradle where to download dependencies |
| `dependencyManagement { imports { mavenBom(...) } }` | Imports the Spring Boot BOM (Bill of Materials) — this is what lets you declare Spring dependencies without specifying versions, because the BOM already defines compatible versions for everything |
| `jvmToolchain(21)` | Sets Java 21 as the target JDK |
| `-Xjsr305=strict` | Makes Kotlin treat Java nullability annotations strictly (better null safety when calling Java code) |
| `-Xannotation-default-target=param-property` | Controls where annotations are placed on Kotlin data class properties that are also constructor parameters |
| `useJUnitPlatform()` | Configures tests to run with JUnit 5 |

## How It Fits Together

```
gradle/libs.versions.toml          ← Defines all versions (single source of truth)
       ↓
build-logic/                       ← Included build that hosts convention plugins
  ├── build.gradle.kts             ← Declares plugin dependencies (Kotlin, Spring Boot gradle plugins)
  ├── settings.gradle.kts          ← Configures repos for resolving those plugins
  └── src/main/kotlin/
      ├── VersionCatalogExt.kt     ← Helper to access version catalog in plugins
      └── chirp.kotlin-common.gradle.kts  ← Shared Kotlin/Spring config
              ↓
       Any module can now do:
       plugins { id("chirp.kotlin-common") }
       ...and get all that config for free
```

## Key Concept: Precompiled Script Plugins

When you put a `.gradle.kts` file inside `build-logic/src/main/kotlin/`, Gradle compiles it into a plugin. The filename determines the plugin ID:
- `chirp.kotlin-common.gradle.kts` → plugin ID `chirp.kotlin-common`
- `chirp.spring-boot-app.gradle.kts` → plugin ID `chirp.spring-boot-app`

This is called a **precompiled script plugin**. It's the simplest way to create convention plugins without writing a full plugin class.

## Before vs After

**Before (each module repeats all this):**
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
}
repositories { mavenCentral() }
kotlin { jvmToolchain(21) }
// ... more boilerplate
```

**After (each module just does this):**
```kotlin
plugins {
    id("chirp.kotlin-common")
}
```

All the shared configuration lives in one place. If you need to change the JVM target or add a compiler flag, you change it once in the convention plugin and every module gets the update.
