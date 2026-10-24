# syntax=docker/dockerfile:1

FROM node:24-alpine@sha256:44fce696f38ba8c987c4db89954051000bbd05ffd8d8659ba15ed4a8602e937f AS frontend-build
WORKDIR /workspace/frontend
ARG SOURCE_CODE_URL=https://github.com/utrost/Listful-Thinking
ENV VITE_SOURCE_CODE_URL=${SOURCE_CODE_URL}
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584 AS backend-build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:resolve
COPY backend/ ./
COPY --from=frontend-build /workspace/frontend/dist/ ./src/main/resources/static/
RUN mvn -B package -DskipTests

FROM amazoncorretto:27-alpine@sha256:6fba5f9f2f4730e2d3e81055ce0378a8587c12a48611ee545c5db04c3a28f889
WORKDIR /app
ARG SOURCE_CODE_URL=https://github.com/utrost/Listful-Thinking
LABEL org.opencontainers.image.source="${SOURCE_CODE_URL}" \
      org.opencontainers.image.licenses="AGPL-3.0-only"
RUN apk --no-cache upgrade
RUN mkdir -p /app/data && chmod 700 /app/data && chown -R 1000:1000 /app
COPY --from=backend-build /workspace/backend/target/listful-thinking-*.jar /app/listful-thinking.jar
COPY --chmod=0444 LICENSE /app/LICENSE
COPY --chmod=0555 scripts/container-entrypoint.sh /app/entrypoint.sh
USER 1000:1000
EXPOSE 8080
ENV LISTFUL_DB_PATH=/app/data/listful-thinking.sqlite
ENTRYPOINT ["/app/entrypoint.sh"]
