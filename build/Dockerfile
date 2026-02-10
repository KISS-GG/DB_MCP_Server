FROM bellsoft/liberica-openjdk-alpine:17

WORKDIR /app

ARG JAR_FILE=../target/db-mcp-server-0.0.1.jar

COPY ${JAR_FILE} app.jar

EXPOSE 8888

ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms512m -Xmx1024m -Djava.security.egd=file:/dev/./urandom"

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
