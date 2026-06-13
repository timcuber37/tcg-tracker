# ---- build stage: compile to an executable boot jar ----
FROM eclipse-temurin:23-jdk AS build
WORKDIR /app
COPY . .
# Normalize line endings + make the wrapper executable (it may arrive with CRLF
# from a Windows checkout), then build. Tests need infra, so skip them here.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon clean bootJar -x test

# ---- runtime stage: JRE only ----
FROM eclipse-temurin:23-jre AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir -p static/cards
EXPOSE 8080
# SPRING_PROFILES_ACTIVE (web|consumer|sync) selects the role at runtime.
ENTRYPOINT ["java", "-jar", "app.jar"]
