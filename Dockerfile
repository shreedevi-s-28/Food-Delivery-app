
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY backend/src/ /app/src/

RUN mvn -q dependency:get \
    -Dartifact=org.postgresql:postgresql:42.7.8

RUN mkdir -p /app/out && \
    javac \
    -cp /root/.m2/repository/org/postgresql/postgresql/42.7.8/postgresql-42.7.8.jar \
    -d /app/out /app/src/*.java && \
    jar --create --file /app/app.jar \
    --main-class Main -C /app/out .

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/app.jar /app/app.jar
COPY --from=build \
    /root/.m2/repository/org/postgresql/postgresql/42.7.8/postgresql-42.7.8.jar \
    /app/postgresql.jar

ENV PORT=8080

EXPOSE 8080

CMD ["sh", "-c", "java -cp '/app/app.jar:/app/postgresql.jar' Main"]
