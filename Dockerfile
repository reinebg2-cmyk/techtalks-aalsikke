# Aal Sikke: multi-stage build (KAN-359)
# Stage 1 builds the jar with Maven; stage 2 runs it on a small Java 17 runtime.
# Configuration comes from environment variables (see application-prod.properties).

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Dependencies first, so they are cached when only the source changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# mvnw may have Windows line endings, which Linux can't run
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in CI on every PR, not in the image build
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

# Don't run as root
RUN useradd --system --uid 1001 aalsikke
COPY --from=build /app/target/*.jar app.jar
USER aalsikke

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# Use up to 75% of the container's memory limit
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]