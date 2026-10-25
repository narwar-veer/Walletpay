FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8086
ENTRYPOINT ["java","-jar","/app.jar"]
