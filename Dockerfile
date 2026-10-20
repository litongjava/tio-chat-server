FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/tio-chat-server.jar /app/tio-chat-server.jar
ENV APP_TIME_ZONE=Asia/Shanghai
EXPOSE 18760
ENTRYPOINT ["java", "-jar", "/app/tio-chat-server.jar"]
