# Repository Guidelines

## Project Structure & Module Organization

Sunflower Class uses Java 21 and Spring Boot/Cloud. `parent/pom.xml` centralizes dependencies; `base/` provides shared responses, security, exceptions, and utilities. `content/`, `media/`, `search/`, `learning/`, `auth/`, and `orders/` contain `*_api` (controllers/startup), `*_service` (business logic/MyBatis), and `*_model` (DTOs/entities). `gateway/` routes requests.

Java sources are in `src/main/java`, configuration and mapper XML in `src/main/resources`, and tests in `src/test/java`. Vue applications live in `frontend-admin/` and `frontend-student/`; shared frontend code lives in `frontend-shared/`. Consult `docs/` for requirements, deployment, and acceptance procedures.

## Build, Test, and Development Commands

Use JDK 21 and installed Maven; there is no root aggregator or Maven wrapper. From the repository root:

```sh
mvn -f parent/pom.xml install
mvn -f base/pom.xml install
mvn -f content/pom.xml install
mvn -f gateway/pom.xml verify
mvn -f content/content_api/pom.xml spring-boot:run
```

Install parent and base first, then build affected services by substituting their module paths. `install` builds, tests, and installs artifacts; `verify` runs build checks.

Inside each frontend, run `npm ci`, `npm run dev`, or `npm run build` to install dependencies, start Vite, or type-check and bundle. Development ports are 5173 (admin) and 5174 (student). At the root, run `npm ci`, then `npm run format` or `npm run format:check`.

## Coding Style & Naming Conventions

Use four-space Java and two-space Vue/TypeScript indentation; follow `.prettierrc.json`. Keep `com.sunflower_class` packages, PascalCase classes, camelCase members, and `Controller`, `Service`, `Mapper`, or `Dto` suffixes.

Use ordinary model classes with private fields and Lombok `@Data`, explicit types/imports, and no Java records. Controllers/services use `@Autowired` field injection; initialize configuration-dependent resources in `@PostConstruct`. Keep SQL in typed MyBatis mappers and preserve transactions, locking, and idempotency. Use Chinese API annotations and comments for non-obvious rules. Save UTF-8 and run `python scripts/check-utf8.py`.

## Testing Guidelines

Use JUnit Jupiter and `@SpringBootTest` where integration context is needed. Name tests `*Test` or `*Tests` and assert changed behavior. Run `mvn -f content/content_service/pom.xml test` after installing dependencies. Frontend builds include `vue-tsc`; no coverage threshold is configured. Provision infrastructure for integration checks.

## Commit & Pull Request Guidelines

History uses brief Chinese and English subjects without a mandatory prefix. Write specific action-oriented subjects. PRs should describe affected modules, behavior, configuration changes, linked issues, and validation results or blockers; include screenshots for UI changes.

## Security & Configuration

Keep credentials external and exclude generated logs. Configure Nacos and required MySQL, RabbitMQ, MinIO, Elasticsearch, Redis, or FFmpeg infrastructure using `docs/DEPLOYMENT.md` and service guides. Frontends use real Gateway APIs without demo fallback or browser storage.
