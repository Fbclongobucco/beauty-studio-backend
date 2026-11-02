# Dockerfile comum aos servicos. O modulo vem do build arg MODULE:
#   docker build --build-arg MODULE=booking-service -t beauty-studio/booking-service .

# ---- build ----
FROM eclipse-temurin:25.0.5_8-jdk-noble AS build
ARG MODULE
WORKDIR /workspace

# POMs primeiro: a camada de dependencias so e refeita quando um pom.xml muda
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY messaging-contracts/pom.xml messaging-contracts/
COPY discovery-server/pom.xml discovery-server/
COPY api-gateway/pom.xml api-gateway/
COPY booking-service/pom.xml booking-service/
COPY notification-service/pom.xml notification-service/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -q -B -pl ${MODULE} -am dependency:go-offline -DexcludeReactor=true || true

COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    test -n "${MODULE}" && ./mvnw -q -B -pl ${MODULE} -am package -DskipTests \
    && cp ${MODULE}/target/${MODULE}-*.jar /workspace/app.jar

# ---- runtime ----
FROM eclipse-temurin:25.0.5_8-jre-noble
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/app.jar /app/app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
