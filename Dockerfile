# Stage 1: build con Gradle 8.4 e JDK 21
FROM gradle:8.4.0-jdk21-alpine AS builder
WORKDIR /app

# 1) Copia solo i file di configurazione per cache delle dipendenze
COPY settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle

# 2) Pre‐scarica tutte le dipendenze (cache layer)
RUN gradle --no-daemon dependencies

# 3) Copia il sorgente e genera il jar
COPY src ./src
RUN gradle --no-daemon clean bootJar -x test

# Stage 2: immagine runtime leggera
FROM openjdk:21-jdk-slim
WORKDIR /app

# Copia il jar costruito
COPY --from=builder /app/build/libs/*.jar app.jar

# Porta Spring configurata (server.port: 8080)
EXPOSE 8080

# Avvia l’app
ENTRYPOINT ["java","-jar","app.jar"]
