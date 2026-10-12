# syntax=docker/dockerfile:1

# ---- Build + testes ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
# "package" executa os testes (se houver) e falha o build caso quebrem
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp clean package \
 && mv target/homepage-backend-*.jar target/app.jar

# ---- Runtime ----
FROM eclipse-temurin:25-jre
RUN useradd --system --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
