ARG JAVA_BASE_IMAGE=openjdk:8-jdk-alpine
FROM ${JAVA_BASE_IMAGE}

LABEL maintainer="Lev Shulman <lshulman@redhat.com>"

RUN mkdir /app

COPY operator-utils-api-service /app/
EXPOSE 8080
WORKDIR /app/
CMD ./mvnw spring-boot:run
