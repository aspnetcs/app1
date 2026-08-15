FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build/backend

# Build from backend reactor so local module dependency `common` can be resolved.
COPY backend/pom.xml ./pom.xml
COPY backend/common/pom.xml ./common/pom.xml
COPY backend/common/src ./common/src
COPY backend/admin-api/pom.xml ./admin-api/pom.xml
COPY backend/platform-api/pom.xml ./platform-api/pom.xml
COPY backend/platform-api/src ./platform-api/src

RUN mvn -pl platform-api -am dependency:go-offline -B || true
RUN mvn -pl platform-api -am package -DskipTests -B && mv platform-api/target/*.jar /build/app.jar

FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends \
    fonts-wqy-zenhei fonts-noto-cjk-extra \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
