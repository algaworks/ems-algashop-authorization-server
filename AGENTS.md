# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is the **Authorization Server**, a Spring Authorization Server implementation that provides OAuth2 and OpenID Connect (OIDC) capabilities for the Algashop platform. It manages user authentication, authorization, and token issuance.

**Key Responsibilities:**
- User account management (CRUD operations)
- OAuth2 token generation and validation
- Session management
- OIDC user info endpoints
- Security policy enforcement (role-based access control via scopes)

**Technology Stack:**
- Java 25, Spring Boot 4.0.3
- Spring Authorization Server
- PostgreSQL (with Flyway migrations)
- Spring Security OAuth2
- Lombok, Apache Commons Lang

## Architecture

### Hexagonal Architecture

The codebase follows **hexagonal (ports & adapters)** architecture:

```
domain/model/          → Business logic (entities, aggregates, value objects)
application/           → Use cases (application services)
infrastructure/        → Adapters (persistence, security, external integrations)
presentation/          → REST Controllers
```

**Key Layers:**

1. **Domain Layer** (`domain/model/`)
   - `AuthUser` — User aggregate root with embedded logic for passwords, verification tokens
   - `AuthUserRepository` — Port for user persistence
   - `AuthUserPasswordManager` — Password encryption/generation
   - `VerificationTokenHasher` — Token hashing (for verification & password reset flows)
   - `AuthUserType` — Enum representing user types (affects authorization rules)

2. **Application Layer** (`application/`)
   - `AuthUserManagementApplicationService` — Create, update, delete users; enforces security policies during operations
   - `AuthUserQueryService` — Query users with filters and pagination; read-only operations
   - `PasswordManagementApplicationService` — Password change/reset workflows
   - `SecurityChecks` — Authorization logic (can user register type X, edit type Y, etc.)
   - Input/Output DTOs — Data transfer between controllers and services

3. **Infrastructure Layer** (`infrastructure/`)
   - **Persistence**: JPA/Hibernate implementations of repositories and query services
   - **Security**: 
     - Custom annotations (`@CanReadUsers`, `@CanWriteUsers`, `@CanAccessOwnProfile`) — Method-level authorization
     - OAuth2 configuration and token customization
     - JWT token converter and scope handling
     - OIDC user info mapping
     - CORS, session, and cookie configuration

4. **Presentation Layer** (`presentation/`)
   - `UserController` — REST endpoints for user CRUD (`POST /api/v1/users`, `GET /api/v1/users/{userId}`, etc.)
   - `ApiExceptionHandler` — Centralized exception handling

### Security Model

- Uses **Spring Security method-level annotations** (`@PreAuthorize`) on controller methods
- **Scopes-based authorization**: Endpoints require OAuth2 scopes like `SCOPE_users:read`, `SCOPE_users:write`
- **SecurityChecks service** enforces business rules (e.g., cannot register user of certain types, cannot change type)
- **Verification tokens** are hashed and stored; used for email verification and password reset flows

## Build Commands

```bash
cd microservices/authorization-server

# Compile and run all tests (unit + integration)
./gradlew build

# Compile only
./gradlew classes

# Run unit tests only
./gradlew test

# Run integration tests (if added, marked with *IT.java)
./gradlew integrationTest

# Build runnable JAR
./gradlew bootJar

# Build multi-platform Docker image (linux/arm64, linux/amd64)
./gradlew dockerBuild

# Run a single test class
./gradlew test --tests "com.algaworks.algashop.authorizationserver.presentation.UserControllerTest"

# Run with specific Spring profile
./gradlew build -Pprofile=docker-env
```

## Running Locally

**Start infrastructure (Postgres, Redis, WireMock, etc.):**
```bash
cd ../..  # Go to monorepo root
docker compose -f docker-compose.tools.yml up -d
```

**Run the application:**
```bash
# From authorization-server directory
./gradlew bootRun

# With specific profile
SPRING_PROFILES_ACTIVE=docker ./gradlew bootRun
```

The server starts on **port 8081** (configured in application.yml).

**Required /etc/hosts entries** (if not already set):
```
127.0.0.1 authorization-server
```

## Project Structure

```
src/main/java/com/algaworks/algashop/authorizationserver/
├── domain/model/
│   ├── user/
│   │   ├── AuthUser.java                  # User aggregate with domain logic
│   │   ├── AuthUserRepository.java        # Persistence port
│   │   ├── AuthUserPasswordManager.java   # Password operations
│   │   ├── VerificationTokenHasher.java   # Token generation/hashing
│   │   └── AuthUserType.java              # Enum
│   ├── AbstractAuditableAggregateRoot.java
│   ├── DomainException.java
│   └── IdGenerator.java
├── application/
│   ├── user/
│   │   ├── management/
│   │   │   ├── AuthUserManagementApplicationService.java  # Use cases: create/update/delete
│   │   │   ├── PasswordManagementApplicationService.java
│   │   │   ├── AuthUserInput.java
│   │   │   ├── AuthUserUpdateInput.java
│   │   │   └── AuthUserEmailAlreadyInUseException.java
│   │   └── query/
│   │       ├── AuthUserQueryService.java  # Read-only queries
│   │       ├── AuthUserOutput.java
│   │       ├── AuthUserFilter.java
│   │       └── PageModel.java
│   └── security/SecurityChecks.java       # Authorization logic
├── infrastructure/
│   ├── persistence/                       # JPA implementations
│   ├── security/                          # OAuth2, JWT, OIDC, CORS, etc.
│   └── ...
├── presentation/
│   ├── UserController.java                # REST API
│   └── ApiExceptionHandler.java
└── AuthorizationServerApplication.java
```

## Testing

Currently minimal test coverage. When adding tests:
- **Unit tests** (`*Test.java`): Test application services, domain logic, validators
- **Integration tests** (`*IT.java`): Use TestContainers for embedded databases (when needed)
- Place tests in `src/test/java` mirroring the source structure
- Use `@SpringBootTest` for full context, `@WebMvcTest` for controller isolation

Example:
```java
@SpringBootTest
class AuthUserManagementApplicationServiceTest {
    @Test
    void shouldThrowExceptionWhenEmailAlreadyInUse() { ... }
}
```

## Key Concepts

### DTOs vs Entities
- **Input DTOs** (`AuthUserInput`, `AuthUserUpdateInput`) — Validated, immutable data from clients
- **Output DTOs** (`AuthUserOutput`) — API response objects, often mapped from entities
- **Entities** (`AuthUser`) — Domain objects with behavior; encapsulate invariants and state transitions

### Verification Tokens
Tokens are **hashed before storage** to prevent plaintext exposure:
1. `VerificationTokenHasher.generate()` → plaintext token (sent to user)
2. `VerificationTokenHasher.hash(plaintext)` → hashed token (stored in DB)
3. On verification: hash provided token and compare with stored hash

**Note:** Currently tokens are printed to console (`System.out.println`) as a TODO; should be sent via email.

### Spring Profiles
The application uses layered profiles:
- `base` — Common configuration
- `development-env` — Local development overrides
- `docker-env` — Docker Compose overrides (sets DB URLs, etc.)
- `production-env` — Production settings

Activate via `SPRING_PROFILES_ACTIVE=docker` or in application.yml.

## Recent Changes

- **User Management API**: Application/presentation layers added for `/api/v1/users`
  - CRUD endpoints for user accounts
  - Role-based access control via custom security annotations
  - Email verification and password reset flow scaffolding

## Common Tasks

### Adding a New User Endpoint
1. Add method to `AuthUserManagementApplicationService` or `AuthUserQueryService`
2. Create input/output DTO if needed
3. Add endpoint to `UserController` with `@SecurityAnnotations.CanXxx` annotation
4. Add validation rules in DTOs using `@NotBlank`, `@Email`, etc.

### Enforcing Security Rules
- Update `SecurityChecks` service to define who can register/edit what user types
- Use `@SecurityAnnotations.CanReadUsers` / `@CanWriteUsers` on controller methods
- Add `@PreAuthorize` expressions if fine-grained control is needed

### Modifying the User Entity
- Add fields to `AuthUser` (with JPA `@Column` annotations)
- Create Flyway migration script in `src/main/resources/db/migration/`
- Update DTOs and query service to expose new fields
- Ensure migrations are backward-compatible (avoid dropping columns)

## Database

Migrations run automatically on startup via **Flyway**.

Existing migrations:
- V1–V2: OAuth2 authorization and consent schema
- V3–V4: User accounts and session tables
- V5–V6: User type and OAuth2 client scopes/mapping

**To add a migration:**
1. Create `src/main/resources/db/migration/V{n}__description.sql`
2. Use snake_case for table/column names
3. Test locally: restart the application (Flyway will execute on startup)

## Dependencies & External Integrations

- **Spring Authorization Server** — Handles OAuth2 token flows
- **PostgreSQL JDBC driver** — Database access
- **Spring Session JDBC** — Distributed session storage (across servers)
- **Spring Actuator** — Metrics and health checks

## Notes for Future Work

- Email integration for verification/password reset tokens (currently logged to console)
- Integration tests using TestContainers
- Expand SecurityChecks for more granular role-based rules
- Add audit logging for sensitive operations (create/update/delete users)
- OIDC user info customization based on scopes requested