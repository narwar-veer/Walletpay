FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN mkdir -p /app/uploads/kyc && chown -R appuser:appgroup /app
USER appuser
ARG JAR_FILE=target/*.jar
COPY --chown=appuser:appgroup ${JAR_FILE} app.jar
EXPOSE 8086
ENTRYPOINT ["java","-jar","/app.jar"]
