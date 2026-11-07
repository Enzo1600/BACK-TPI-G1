FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY auxiruta-commons/ auxiruta-commons/
COPY assistance-service/ assistance-service/
RUN chmod +x auxiruta-commons/mvnw assistance-service/mvnw && ./auxiruta-commons/mvnw -B -ntp -f auxiruta-commons/pom.xml install && ./assistance-service/mvnw -B -ntp -f assistance-service/pom.xml verify
FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system auxiruta && useradd --system --gid auxiruta --home-dir /app auxiruta
WORKDIR /app
COPY --from=build /workspace/assistance-service/target/app.jar app.jar
USER auxiruta
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
