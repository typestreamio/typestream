FROM gradle:8-jdk21 AS build
WORKDIR /app

# Copy gradle wrapper and build configuration first (changes rarely)
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts ./
COPY gradle/ gradle/

# Copy buildSrc (convention plugins - changes rarely)
COPY buildSrc/ buildSrc/

# Copy only build.gradle.kts files to download dependencies
COPY connectors/demo-data/build.gradle.kts connectors/demo-data/

# Download dependencies (cached unless build files change)
RUN gradle :connectors:demo-data:dependencies --no-daemon || true

# Now copy source code (changes frequently)
COPY connectors/demo-data/src/ connectors/demo-data/src/

# Build the application
RUN gradle :connectors:demo-data:shadowJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/connectors/demo-data/build/libs/*-all.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
