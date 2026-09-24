# Repository Guidelines

## Project Structure & Module Organization

Sunflower Class is a Java 21 Spring Boot/Cloud backend. `parent/pom.xml` centralizes dependency versions; `base/` contains shared responses, exceptions, configuration, and FFmpeg utilities. `content/` and `media/` each aggregate three modules: `*_api` for REST controllers and application entry points, `*_service` for business logic and MyBatis mappers, and `*_model` for DTOs and persistence objects. `gateway/` provides Spring Cloud Gateway routing.

Java sources live in `src/main/java`; configuration, logging, and mapper XML live in `src/main/resources`. Tests reside in `src/test/java`, with content test configuration in `src/test/resources`. There is no frontend or dedicated static-asset directory.

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

Current POMs reference `com.alibaba:fastjson` without an active managed version, which can block Maven validation. Report this blocker rather than claiming a successful build.

## Coding Style & Naming Conventions

Use four-space Java indentation and existing `com.sunflower_class` packages. Use PascalCase classes, camelCase methods and fields, and descriptive suffixes such as `Controller`, `Service`, `ServiceImpl`, `Mapper`, and `Dto`. Keep mapper XML namespaces aligned with interfaces. Follow existing Lombok usage. No formatter or lint configuration is checked in; match surrounding code.

## Testing Guidelines

Existing tests use JUnit Jupiter and `@SpringBootTest`. Name tests `*Test` or `*Tests`, mirroring production packages. Add assertions for changed behavior rather than print-only checks. Integration tests need configured infrastructure. No coverage threshold is configured.

## Commit & Pull Request Guidelines

History uses short imperative subjects such as `add ffmpeg` and `sort pom`, without a mandatory prefix. Write specific subjects. PRs should describe affected modules, behavior changes, configuration needs, linked issues when applicable, and validation results or blockers.

## Configuration & Security

Development configuration imports Nacos settings. Provision MySQL, RabbitMQ, MinIO, and Docker/FFmpeg as required by the affected feature. Keep credentials in external configuration; do not commit secrets or generated logs.
