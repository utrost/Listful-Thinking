# syntax=docker/dockerfile:1

FROM node:24-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/ ./
COPY --from=frontend-build /workspace/frontend/dist/ ./src/main/resources/static/
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN mkdir -p /app/data && chown -R 1000:1000 /app
COPY --from=backend-build /workspace/backend/target/listful-thinking-*.jar /app/listful-thinking.jar
USER 1000:1000
EXPOSE 8080
ENV LISTFUL_DB_PATH=/app/data/listful-thinking.sqlite
ENTRYPOINT ["java", "-jar", "/app/listful-thinking.jar"]
