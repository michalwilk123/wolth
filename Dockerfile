FROM openjdk:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/wolth-0.0.1-SNAPSHOT-standalone.jar /wolth/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/wolth/app.jar"]
