ARG JAVA_BASE_IMAGE=docker.io/openjdk:8-jdk-alpine
FROM ${JAVA_BASE_IMAGE}

LABEL maintainer="Lev Shulman <lshulman@redhat.com>"

RUN mkdir /app

COPY operator-catalog-tools.jar /app/
 
EXPOSE 8080
WORKDIR /app/
CMD java -jar operator-catalog-tools.jar
