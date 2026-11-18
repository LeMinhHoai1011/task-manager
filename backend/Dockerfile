# Build the Spring Boot jar inside Docker so a clean Linux VPS only needs
# Docker + Compose (no Maven/JDK on the host).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
ENV MAVEN_OPTS="-Xmx512m -XX:MaxMetaspaceSize=256m"

# Cache runtime dependency downloads without resolving every optional plugin.
COPY pom.xml ./
COPY app/pom.xml app/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl app -am dependency:resolve -DincludeScope=runtime

COPY app/src app/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl app -am package -Dmaven.test.skip=true

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
