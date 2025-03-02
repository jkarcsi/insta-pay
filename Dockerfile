# Stage 1: Build the application using JDK 17
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline  # optional, due to caching dependencies
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Run the application using JRE 17
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
COPY --from=build /app/target/insta-pay-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080 5005
ENTRYPOINT ["java", "-jar", "app.jar"]

