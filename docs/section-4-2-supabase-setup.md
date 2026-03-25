# Section 4.2 - Supabase Database Setup

## What This Section Teaches

This section configures the Spring Boot app to connect to a **Supabase PostgreSQL** database. It also introduces **Spring profiles** for separating dev and production configurations.

## What Changed

### Switched from `application.properties` to `application.yml`

Spring Boot supports both `.properties` and `.yml` config formats. YAML is preferred for complex configs because it's more readable with nested structures.

**Before:** `application.properties`
```
spring.application.name=chirp
```

**After:** `application.yml`
```yaml
spring:
  application:
    name: chirp
  datasource:
    url: jdbc:postgresql://...
    # ... nested config is much cleaner in YAML
```

### Three Config Files Created

| File | Purpose |
|---|---|
| `application.yml` | **Base config** — shared across all environments (database connection, pool settings, Hibernate) |
| `application-dev.yml` | **Dev profile** — `ddl-auto: update` (Hibernate auto-updates schema) |
| `application-prod.yml` | **Prod profile** — `ddl-auto: validate` (Hibernate only validates, never modifies schema) |

## Config Breakdown

### Database Connection (`spring.datasource`)

```yaml
url: jdbc:postgresql://db.wxufamvuixbezaecetbe.supabase.co:5432/postgres
username: postgres
password: ${POSTGRES_PASSWORD}
driver-class-name: org.postgresql.Driver
```

- **Supabase** provides a hosted PostgreSQL database. The URL points to your Supabase project's database.
- **`${POSTGRES_PASSWORD}`** — The password is read from an **environment variable**, not hardcoded. This is a security best practice. You'll set this in your run configuration or `.env` file.
- **`org.postgresql.Driver`** — The JDBC driver class for PostgreSQL.

> **Important:** You'll need to replace the Supabase URL with your own project's database URL. Create a free project at [supabase.com](https://supabase.com) and find the connection string in Project Settings > Database.

### Connection Pool (`spring.datasource.hikari`)

**HikariCP** is the default connection pool in Spring Boot. It manages a pool of reusable database connections instead of creating a new one for every query.

```yaml
hikari:
  maximum-pool-size: 10      # Max 10 simultaneous connections
  minimum-idle: 5             # Keep at least 5 connections ready
  connection-timeout: 20000   # Wait max 20s to get a connection (ms)
  idle-timeout: 300000        # Close idle connections after 5 min (ms)
  max-lifetime: 12000000      # Recycle connections after ~3.3 hours (ms)
  validation-timeout: 5000    # Max 5s to validate a connection is alive (ms)
  pool-name: SpringBootHikariCP
```

**Why a connection pool?**
Opening a database connection is expensive (TCP handshake, authentication, SSL). A pool keeps connections open and reuses them, dramatically improving performance.

### Prepared Statement Cache

```yaml
data-source-properties:
  prepareThreshold: 5                    # After 5 uses, cache the statement
  preparedStatementCacheQueries: 256     # Cache up to 256 prepared statements
  preparedStatementCacheSizeMiB: 5       # Max 5 MB for cached statements
```

**Prepared statements** are SQL queries that the database pre-compiles. Caching them avoids re-parsing the same query repeatedly. This is especially helpful when your app executes the same queries thousands of times.

### Network Settings

```yaml
tcpKeepAlive: true    # Prevent firewall from dropping idle connections
socketTimeout: 30     # Max 30s waiting for a database response
connectTimeout: 10    # Max 10s to establish a connection
```

These prevent hanging connections and ensure your app fails fast if the database is unreachable.

### Hibernate / JPA Settings

```yaml
jpa:
  properties:
    hibernate:
      dialect: org.hibernate.dialect.PostgreSQLDialect
      format_sql: true     # Pretty-print SQL in logs
      show_sql: true       # Log every SQL query
```

- **`PostgreSQLDialect`** — Tells Hibernate to generate PostgreSQL-specific SQL
- **`show_sql` / `format_sql`** — Useful during development to see what queries Hibernate generates. You'd typically disable these in production.

## Spring Profiles Explained

Spring profiles let you have different configurations for different environments.

**How they work:**
- `application.yml` — Always loaded (base config)
- `application-dev.yml` — Only loaded when the `dev` profile is active
- `application-prod.yml` — Only loaded when the `prod` profile is active

Profile-specific files **override** values in the base `application.yml`.

**How to activate a profile:**
```bash
# Via environment variable
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun

# Via command line argument
./gradlew bootRun --args='--spring.profiles.active=dev'

# Via IntelliJ run configuration
# Add: --spring.profiles.active=dev to Program Arguments
```

### `ddl-auto` Values

| Value | What it does | When to use |
|---|---|---|
| `update` | Adds new columns/tables, never deletes | Development |
| `validate` | Only checks schema matches entities, changes nothing | Production |
| `create` | Drops and recreates schema every startup | Testing |
| `create-drop` | Like `create`, but also drops on shutdown | Unit tests |
| `none` | Does nothing | When using migration tools (Flyway/Liquibase) |

## What You Need to Do

1. **Create a Supabase account** at [supabase.com](https://supabase.com) if you haven't already
2. **Create a new project** and note down the database password
3. **Update the JDBC URL** in `application.yml` with your project's database URL (found in Supabase dashboard > Project Settings > Database > Connection string > JDBC)
4. **Set the environment variable** `POSTGRES_PASSWORD` with your database password in your IDE run configuration
