
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY backend/src/ /app/src/

RUN mvn -q dependency:get \
    -Dartifact=com.oracle.database.jdbc:ojdbc11:23.9.0.25.07

RUN mkdir -p /app/out && \
    javac -cp /root/.m2/repository/com/oracle/database/jdbc/ojdbc11/23.9.0.25.07/ojdbc11-23.9.0.25.07.jar \
    -d /app/out /app/src/*.java && \
    jar --create --file /app/app.jar --main-class Main -C /app/out .

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/app.jar /app/app.jar
COPY --from=build /root/.m2/repository/com/oracle/database/jdbc/ojdbc11/23.9.0.25.07/ojdbc11-23.9.0.25.07.jar /app/ojdbc11.jar

ENV PORT=8080

EXPOSE 8080

CMD ["sh", "-c", "java -cp '/app/app.jar:/app/ojdbc11.jar' Main"]
