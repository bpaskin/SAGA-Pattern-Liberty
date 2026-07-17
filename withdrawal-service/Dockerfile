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
COPY --chown=1001:0 --from=build /build/target/withdrawal-service.war /config/dropins/

# Copy the server config
COPY --chown=1001:0 src/main/liberty/config/server.xml /config/server.xml

# Download PostgreSQL JDBC driver to the shared resources directory
USER root
RUN mkdir -p /opt/ol/wlp/usr/shared/resources/jdbc \
 && curl -fSL "https://jdbc.postgresql.org/download/postgresql-42.7.3.jar" \
         -o /opt/ol/wlp/usr/shared/resources/jdbc/postgresql-42.7.3.jar
USER 1001

# Run the Liberty feature install for this server config
RUN features.sh

EXPOSE 9082 9445

ENV DB_HOST=localhost \
    DB_PORT=5432 \
    DB_NAME=sagadb \
    DB_USER=saga \
    DB_PASSWORD=saga \
    LRA_COORDINATOR_URL=http://localhost:8070/lra-coordinator
