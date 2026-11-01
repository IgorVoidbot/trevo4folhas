# syntax=docker/dockerfile:1

# TREVO4FOLHAS — reproducible multi-stage image (PRD 8.2/8.6, DevOps persona).
#
# Stages:
#   1. assets  — Node.js + `npm ci` (lockfile) compiles Tailwind CSS.
#                Node.js is a build tool only and never ships in the image.
#   2. build   — Maven Wrapper packages target/trevo4folhas.jar with the
#                freshly compiled CSS copied into the resources first.
#   3. runtime — minimal maintained Eclipse Temurin 17 JRE (Alpine), non-root.
#
# Base images are pinned by digest so rebuilds are reproducible; bump the
# digests deliberately when updating the toolchain. Availability of the
# Alpine variants was verified before pinning. Runtime Java is 17, the same
# baseline as the compiled bytecode (never lower).
#
# Tests are intentionally NOT run inside `docker build`: the integration
# suite needs a Docker daemon (Testcontainers, real PostgreSQL 16) and would
# require daemon-in-daemon support. Tests are executed and reported outside
# the image build (see the task card); nothing is silently omitted.

# Fixed technical packaging epoch; not a business/acceptance timestamp.
ARG SOURCE_DATE_EPOCH=1767225600
ARG NODE_IMAGE=docker.io/library/node:22-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914
ARG JDK_IMAGE=docker.io/library/eclipse-temurin:17-jdk-alpine@sha256:5a7fe9fcd65085bbf218a4060f08f1346b8bfd13d9f9942e2db6719cc8a11e22
ARG JRE_IMAGE=docker.io/library/eclipse-temurin:17-jre-alpine@sha256:c4a33e6ddb738fe4be0a28a613ea0940cec42d25d7263c3e2238c43354cb19b0

# --- Stage 1: assets (build tool only) ------------------------------------
FROM ${NODE_IMAGE} AS assets
WORKDIR /build

# Dependency layer cached separately from sources (lockfile-driven install).
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Sources scanned by Tailwind: input.css declares @source globs over
# templates and static/js, so they must exist for a deterministic build.
COPY src/frontend/styles ./src/frontend/styles
COPY src/main/resources/templates ./src/main/resources/templates
COPY src/main/resources/static ./src/main/resources/static
RUN npm run css:build

# --- Stage 2: Java build (Maven Wrapper) ----------------------------------
FROM ${JDK_IMAGE} AS build
WORKDIR /build

# Wrapper first so the dependency layer is cached until pom.xml changes.
COPY mvnw mvnw.cmd ./
COPY .mvn ./.mvn
COPY pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src ./src
# Fresh compiled CSS replaces the repository copy before packaging so the
# JAR always carries CSS built from src/frontend/styles/input.css.
COPY --from=assets /build/src/main/resources/static/css/app.css src/main/resources/static/css/app.css
RUN ./mvnw -B -ntp -DskipTests package

# --- Stage 3: runtime ----------------------------------------------------
FROM ${JRE_IMAGE} AS runtime

# Least privilege: dedicated non-root runtime user (no shell login needed).
RUN addgroup -S trevo && adduser -S -G trevo -u 10001 trevo

WORKDIR /app
COPY --from=build /build/target/trevo4folhas.jar /app/trevo4folhas.jar

USER trevo:trevo
EXPOSE 8080

# Exec form so the JVM is PID 1 and receives SIGTERM directly.
ENTRYPOINT ["java", "-jar", "/app/trevo4folhas.jar"]
