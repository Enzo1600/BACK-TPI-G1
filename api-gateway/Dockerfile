FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY auxiruta-commons/ auxiruta-commons/
COPY api-gateway/ api-gateway/
COPY docs/api/ docs/api/
RUN chmod +x auxiruta-commons/mvnw api-gateway/mvnw && ./auxiruta-commons/mvnw -B -ntp -f auxiruta-commons/pom.xml install && ./api-gateway/mvnw -B -ntp -f api-gateway/pom.xml verify
FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system auxiruta && useradd --system --gid auxiruta --home-dir /app auxiruta
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/app.jar app.jar
USER auxiruta
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
