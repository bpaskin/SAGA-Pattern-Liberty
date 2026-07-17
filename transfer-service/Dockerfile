# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---- Runtime stage ----
FROM icr.io/appcafe/open-liberty:kernel-slim-java17-openj9-ubi

# Copy the application WAR
COPY --chown=1001:0 --from=build /build/target/transfer-service.war /config/dropins/

# Copy the server config
COPY --chown=1001:0 src/main/liberty/config/server.xml /config/server.xml

# Run the Liberty feature install for this server config
RUN features.sh

EXPOSE 9083 9446

ENV WITHDRAWAL_SERVICE_URL=http://localhost:9082/withdrawal \
    DEPOSIT_SERVICE_URL=http://localhost:9081/deposit \
    LRA_COORDINATOR_URL=http://localhost:8070/lra-coordinator
