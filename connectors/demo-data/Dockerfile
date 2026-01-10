FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :connectors:demo-data:shadowJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/connectors/demo-data/build/libs/*-all.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
