# Build the jar first: ./mvnw -pl ingest-service -am package -DskipTests

# Split the fat jar into layers, so a code change rebuilds a few hundred KB instead of every dependency
FROM eclipse-temurin:17-jre AS layers
WORKDIR /build
COPY target/ingest-service-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:17-jre
WORKDIR /app
# Least likely to change first, so Docker's layer cache does the most work
COPY --from=layers /build/extracted/dependencies/ ./
COPY --from=layers /build/extracted/spring-boot-loader/ ./
COPY --from=layers /build/extracted/snapshot-dependencies/ ./
COPY --from=layers /build/extracted/application/ ./
USER 1001
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
