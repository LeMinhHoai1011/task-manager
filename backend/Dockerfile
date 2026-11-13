# Build the Spring Boot jar inside Docker so a clean Linux VPS only needs
# Docker + Compose (no Maven/JDK on the host).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Cache dependencies separately from source changes.
COPY pom.xml ./
COPY app/pom.xml app/pom.xml
RUN mvn -B -q -pl app -am -DskipTests dependency:go-offline || true

COPY app/src app/src
RUN mvn -B -q -pl app -am package -DskipTests

# Runtime image: JRE + jar only.
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --create-home --shell /usr/sbin/nologin smarttask

WORKDIR /app
COPY --from=build /src/app/target/smart-task-app-0.0.1-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /app/var/files && chown -R smarttask:smarttask /app

USER smarttask
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseG1GC"

# sh -c so JAVA_OPTS can be overridden from the environment.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
