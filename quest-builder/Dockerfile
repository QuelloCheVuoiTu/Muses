# Stage 1: Build the application using Gradle
FROM eclipse-temurin:21-jdk AS build

# Set the working directory
WORKDIR /usr/src/app

# Copy the entire project to the working directory
COPY . .

# Grant execution rights to the Gradle wrapper
RUN chmod +x ./gradlew

# Build the application
RUN ./gradlew build


# Stage 2: Create the final image with Wildfly
FROM quay.io/wildfly/wildfly:36.0.1.Final-jdk21

# Copy the built WAR file from the build stage to the Wildfly deployments directory
COPY --from=build /usr/src/app/app/build/libs/quest-builder-0.1-SNAPSHOT.war /opt/jboss/wildfly/standalone/deployments/quest-builder.war

EXPOSE 6000 9990

CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-Djboss.http.port=6000", "-Djboss.management.http.port=9990"]