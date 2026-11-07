FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY auxiruta-commons/ auxiruta-commons/
COPY fleet-service/ fleet-service/
RUN chmod +x auxiruta-commons/mvnw fleet-service/mvnw && ./auxiruta-commons/mvnw -B -ntp -f auxiruta-commons/pom.xml install && ./fleet-service/mvnw -B -ntp -f fleet-service/pom.xml verify
FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system auxiruta && useradd --system --gid auxiruta --home-dir /app auxiruta
WORKDIR /app
COPY --from=build /workspace/fleet-service/target/app.jar app.jar
USER auxiruta
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
