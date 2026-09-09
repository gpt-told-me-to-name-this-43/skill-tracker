# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Dependencies resolve from the POM alone, so they stay cached while only sources change.
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline

COPY backend/src src
RUN ./mvnw -B -ntp -DskipTests -Dspotless.check.skip=true package \
    && cp target/skill-tracker-*.jar app.jar

FROM eclipse-temurin:21-jre AS runtime

# The API never needs root, and the upload directory is the only path it writes to.
RUN useradd --system --create-home --uid 10001 app
WORKDIR /app

COPY --from=builder --chown=app:app /build/app.jar app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app

USER app
EXPOSE 8000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
