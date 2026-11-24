FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

RUN apt-get update -qq \
    && apt-get install -y -qq maven \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml && \
    echo '<settings><mirrors><mirror>' >> /root/.m2/settings.xml && \
    echo '<id>aliyun</id><mirrorOf>central</mirrorOf>' >> /root/.m2/settings.xml && \
    echo '<url>https://maven.aliyun.com/repository/public</url>' >> /root/.m2/settings.xml && \
    echo '</mirror></mirrors></settings>' >> /root/.m2/settings.xml

COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/ecommerce-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
