# Repository Guidelines

## Project Structure & Module Organization

Sunflower Class is a Java 21 Spring Boot/Cloud backend. `parent/pom.xml` centralizes dependency versions; `base/` contains shared responses, exceptions, configuration, and FFmpeg utilities. `content/` and `media/` each aggregate three modules: `*_api` for REST controllers and application entry points, `*_service` for business logic and MyBatis mappers, and `*_model` for DTOs and persistence objects. `gateway/` provides Spring Cloud Gateway routing.

Java sources live in `src/main/java`; configuration and mapper XML live in `src/main/resources`; tests live in `src/test/java`. `frontend-admin/` is the institution/reviewer Vue app and `frontend-student/` is the learner Vue app. Each has its own `src/`, `package.json`, and lockfile. Product requirements and the implementation checklist are in `docs/`.

## Build, Test, and Development Commands

Use JDK 21 and an installed Maven; no Maven wrapper or root aggregator POM is present. Run commands from the repository root:

```sh
mvn -f parent/pom.xml install
mvn -f base/pom.xml install
mvn -f content/pom.xml install
mvn -f media/pom.xml install
mvn -f gateway/pom.xml verify
```

Install the parent and shared base first. Content and media builds compile their child modules, run tests, and install artifacts locally; gateway verification runs its build checks.

- `mvn -f content/content_service/pom.xml test`: run content service tests after installing dependencies.
- `mvn -f gateway/pom.xml test`: run gateway tests.
- `mvn -f content/content_api/pom.xml spring-boot:run`: start the content API; substitute `media/media_api` or `gateway` for other applications.

For each frontend, run `npm ci`, `npm run dev`, and `npm run build` inside its directory. Development ports are 5173 (admin) and 5174 (student). Both use real APIs through Gateway, without browser storage or demo fallback. See `docs/LIVE_INTEGRATION.md`.

## Coding Style & Naming Conventions

Use four-space Java indentation and existing `com.sunflower_class` packages. Use PascalCase classes, camelCase methods and fields, and suffixes such as `Controller`, `Service`, `Mapper`, and `Dto`. Keep mapper XML namespaces aligned with interfaces. Vue and TypeScript use two-space indentation. Explain non-obvious business rules with Chinese comments. Run `npm ci` at the repository root to install Prettier and its Java plugin, then `npm run format` to format sources or `npm run format:check` to check them. Rules are in `.prettierrc.json`.

## Testing Guidelines

Backend tests use JUnit Jupiter and `@SpringBootTest`; name them `*Test` or `*Tests`, mirroring production packages. Add assertions for changed behavior rather than print-only checks. Frontend builds run `vue-tsc`; add focused interaction tests when real workflows arrive. Integration tests need configured infrastructure. No coverage threshold is configured.

## Commit & Pull Request Guidelines

History uses short imperative subjects such as `add ffmpeg` and `sort pom`, without a mandatory prefix. Write specific subjects. PRs should describe affected modules, behavior changes, configuration needs, linked issues when applicable, and validation results or blockers.

## Configuration & Security

Development configuration imports Nacos settings. Provision MySQL, RabbitMQ, MinIO, and Docker/FFmpeg as required by the affected feature. Keep credentials in external configuration; do not commit secrets or generated logs.

## Codex Engineering Rules

### Highest-Priority Principle

**Before writing any new code, prove that new code is actually necessary.**

Always evaluate in this order:

**Need → Reuse → Standard Library → Native Capability → Existing Dependency → Minimal Implementation**

**Do not skip directly to writing new code.**

---

### 1. Does this really need to exist?

- If not, do not implement it.
- Follow YAGNI (You Aren't Gonna Need It).
- Unless explicitly required by the current task, do not add:
  - Features that may only be needed in the future
  - Premature abstractions
  - Unnecessary configuration options
  - Extension points
  - Designs added merely “for future convenience”

---

### 2. Does this already exist in the codebase?

- Before writing new code, search the relevant code, related directories, and global references.
- Prefer reusing or extending existing:
  - Utility functions
  - Components
  - Services
  - Helpers
  - Shared modules
  - Existing patterns
  - Existing abstractions

If an existing implementation can satisfy the requirement, do not rewrite it.

Do not create a parallel implementation with duplicate functionality unless there is a clear and specific reason.

**Prefer modifying or extending an existing implementation over creating a new file, module, helper, service, or abstraction.**

---

### 3. Can the standard library solve it?

Prefer the standard library provided by the current programming language or runtime.

If the standard library can reliably handle the functionality, do not reimplement it.

Do not create unnecessary Helpers, Utils, or utility classes merely to wrap standard-library functionality.

---

### 4. Does the native platform or framework already support it?

Prefer existing native capabilities, such as:

- Browser-native APIs
- Operating system capabilities
- Database-native features
- Features provided by the current framework
- Runtime-native capabilities

If the platform already supports it, do not reimplement it in application code.

---

### 5. Does an installed dependency already provide it?

Before writing new code or installing a new dependency, check the dependencies already installed in the project.

If an existing dependency can reasonably solve the problem, use it directly.

Do not:

- Reinvent the wheel
- Install another third-party library for the same functionality
- Introduce a second similar library when an existing one already solves the problem

---

### 6. Only write the minimum implementation when none of the above works

If:

There is no existing implementation in the codebase<br>
→ The standard library cannot solve it<br>
→ The native platform cannot solve it<br>
→ Existing installed dependencies cannot solve it

Only then may you add new code.

And implement only:

**The minimum version required for the current requirement to work correctly.**

Avoid:

- Premature abstraction
- Overengineering
- Unnecessary code layers
- Wrappers
- Factories
- Managers
- Services
- Generic systems
- Unnecessary configuration
- Unnecessary interfaces
- Building features in advance for hypothetical future requirements

---

### Required Development Workflow

When handling any development task:

1. Read the existing code relevant to the task first.
2. Search relevant directories, similar implementations, and global references.
3. Check whether an existing implementation can be reused.
4. Check whether the standard library can be used.
5. Check whether the framework, browser, operating system, database, or platform provides native support.
6. Check the dependencies already installed in the project.
7. Only write a new implementation after confirming that none of the above options are suitable.
8. Prefer modifying or extending existing code over creating new structures.
9. Keep new code to the minimum necessary.
10. Keep the scope of changes as small as possible.
11. Do not refactor unrelated code while working on the task.

---

### Principles for Choosing Between Multiple Solutions

If multiple solutions are viable, prefer the one with:

- Less new code
- Fewer new abstractions
- Fewer new dependencies
- Less configuration
- Fewer layers
- Less indirection
- Greater reuse of existing code
- Smaller changes to the current code structure

If no code change is actually necessary, explicitly state:

**“No code changes are required for this task.”**

Do not force unnecessary code changes just to produce an implementation.

---

### Abstraction Principle

Do not add a new abstraction layer simply because:

**“This makes the architecture cleaner.”**

Every new abstraction must solve a real and clearly existing problem in the current task.

Do not create abstractions merely for:

- Possible future extensibility
- Possible future use
- Making the architecture look cleaner
- Better alignment with a design pattern

Prefer simple, direct, concrete code until a real requirement justifies abstraction.

---

### Decision Sequence Before Writing Code

Before implementing any feature, always evaluate in this order:

**Does this really need to exist?**

↓

**Does it already exist in the project?**

↓

**Can an existing implementation be reused?**

↓

**Can the standard library solve it?**

↓

**Can the native platform / framework solve it?**

↓

**Can an existing dependency solve it?**

↓

**If none of the above can solve it, write the minimum new implementation.**

Do not skip directly to “write new code.”
