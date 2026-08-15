FROM node:20-bookworm-slim AS admin-build
WORKDIR /build/admin

COPY admin/package.json ./package.json
COPY admin/package-lock.json ./package-lock.json
RUN npm ci --no-audit --no-fund
COPY admin/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build/backend

# Build from backend reactor so local module dependency `common` can be resolved.
COPY backend/pom.xml ./pom.xml
COPY backend/common/pom.xml ./common/pom.xml
COPY backend/common/src ./common/src
COPY backend/platform-api/pom.xml ./platform-api/pom.xml
COPY backend/admin-api/pom.xml ./admin-api/pom.xml
COPY backend/admin-api/src ./admin-api/src

RUN mvn -pl admin-api -am dependency:go-offline -B || true
RUN mvn -pl admin-api -am package -DskipTests -B && mv admin-api/target/*.jar /build/app.jar

FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends \
    fonts-wqy-zenhei fonts-noto-cjk-extra \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
# Keep the Round 5 runtime contract, but generate the bundle during image build.
COPY --from=admin-build /build/admin/dist /app/admin-dist
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
