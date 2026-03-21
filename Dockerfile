FROM eclipse-temurin:25-jdk AS build

ARG MAVEN_VERSION=3.9.9
ADD https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz /tmp/maven.tar.gz
RUN tar -xzf /tmp/maven.tar.gz -C /opt && rm /tmp/maven.tar.gz
ENV PATH="/opt/apache-maven-${MAVEN_VERSION}/bin:${PATH}"

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:resolve -B
COPY src src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
