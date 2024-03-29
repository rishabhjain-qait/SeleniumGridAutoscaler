FROM openjdk:11

LABEL maintainer="rishabhans10@gmail.com"

VOLUME /tmp

ARG JAR_FILE=target/*.jar

COPY ${JAR_FILE} app/grid-utils.jar

WORKDIR app

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","grid-utils.jar"]