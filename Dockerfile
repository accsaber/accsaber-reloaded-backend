FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src src

RUN ./mvnw package
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=build /app/target/dependency/BOOT-INF/lib /app/lib
COPY --from=build /app/target/dependency/META-INF /app/META-INF
COPY --from=build /app/target/dependency/BOOT-INF/classes /app

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-cp", "/app:/app/lib/*", "com.accsaber.backend.BackendApplication"]
