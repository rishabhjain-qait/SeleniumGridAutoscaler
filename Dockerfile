FROM prod-nexus.sprinklr.com:8123/spr-centos7-jdk11:latest

LABEL maintainer="rishabh.jain@sprinklr.com , ish.abbi@sprinklr.com"

VOLUME /tmp

ARG JAR_FILE=target/*.jar

COPY ${JAR_FILE} app/grid-utils.jar

WORKDIR app

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","grid-utils.jar"]