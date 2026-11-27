FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S -G app -u 10001 app
COPY --from=build --chown=app:app /workspace/target/help-desk-bot-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 9191
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.io.tmpdir=/tmp"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1:9191/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
