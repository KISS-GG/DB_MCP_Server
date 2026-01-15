FROM ccr.ccs.tencentyun.com/shared/liberica-jdk17:alpine-clamav

WORKDIR /app

ARG JAR_FILE=../target/db-mcp-server-0.0.1.jar

COPY ${JAR_FILE} app.jar

EXPOSE 18011

ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms512m -Xmx1024m -Djava.security.egd=file:/dev/./urandom"

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
