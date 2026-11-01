FROM eclipse-temurin:21-jre
WORKDIR /app
# 所有模块一个服务；单独部署某个服务时改为对应模块的 jar，如 tio-chat-im/target/tio-chat-im.jar
COPY tio-chat-all/target/tio-chat-server.jar /app/tio-chat-server.jar
ENV APP_TIME_ZONE=Asia/Shanghai
EXPOSE 18760
ENTRYPOINT ["java", "-jar", "/app/tio-chat-server.jar"]
