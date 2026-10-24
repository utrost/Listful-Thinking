# syntax=docker/dockerfile:1

FROM node:24-alpine@sha256:44fce696f38ba8c987c4db89954051000bbd05ffd8d8659ba15ed4a8602e937f AS frontend-build
WORKDIR /workspace/frontend
ARG SOURCE_CODE_URL=https://github.com/utrost/Listful-Thinking
ENV VITE_SOURCE_CODE_URL=${SOURCE_CODE_URL}
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS backend-build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:resolve
COPY backend/ ./
COPY --from=frontend-build /workspace/frontend/dist/ ./src/main/resources/static/
RUN mvn -B package -DskipTests

FROM amazoncorretto:17-alpine@sha256:80d268d89c10861f33792c7845bbd7c8494678a600f031988e135fe27eae3b48
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
