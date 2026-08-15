# syntax=docker/dockerfile:1
#
# petri-server — multi-stage Maven reactor build.
#
# Build context MUST be the repository root, not petri-server/, because
# this is a multi-module reactor build and Maven needs every module's pom.xml on disk to resolve
# reactor topology, even though `-pl petri-server -am` only compiles petri-server and its actual
# dependencies (petri-math, petri-api, petri-model). petri-swing-ui is copied in for that reason
# alone; it is never built.
#
#   docker build -f petri-server/Dockerfile -t petri-server:local .

# ---- build stage ----------------------------------------------------------
# JDK 25 (LTS) — the reactor's <maven.compiler.source/target> stays pinned to
# 23 in pom.xml; a newer JDK compiling to an older --release is fine, it just
# must not itself be an unsupported non-LTS build (23/24 are already EOL).
FROM maven:3.9.16-eclipse-temurin-25-noble AS build
WORKDIR /build

# Cache dependency resolution in its own layer: copy only POMs first.
COPY pom.xml ./
COPY petri-api/pom.xml petri-api/pom.xml
COPY petri-math/pom.xml petri-math/pom.xml
COPY petri-model/pom.xml petri-model/pom.xml
COPY petri-swing-ui/pom.xml petri-swing-ui/pom.xml
COPY petri-server/pom.xml petri-server/pom.xml
RUN mvn -B -pl petri-server -am dependency:go-offline

# Now bring in sources and build.
COPY petri-api petri-api
COPY petri-math petri-math
COPY petri-model petri-model
COPY petri-swing-ui petri-swing-ui
COPY petri-server petri-server
RUN mvn -B -pl petri-server -am -DskipTests package

# ---- runtime stage ----------------------------------------------------------
FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app

# curl is not present on the base image; installed solely so an external orchestrator
# (docker-compose healthcheck) can probe /actuator/health without a JVM-based client.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin petri

COPY --from=build /build/petri-server/target/petri-server.jar /app/app.jar

USER petri
EXPOSE 8080
# MaxRAMPercentage caps the heap relative to the container's cgroup memory
# limit (set via docker-compose's mem_limit) instead of the host's, so a
# large/adversarial simulation payload can't grow the JVM heap unbounded.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
