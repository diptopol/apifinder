FROM alpine/git
WORKDIR /app
RUN git clone https://902254384521fb75321caee1d643f652df3f0dfd@github.com/ameyaKetkar/JarAnalyzer.git

FROM maven:3.6-jdk-11-slim
WORKDIR /app
COPY --from=0 /app/JarAnalyzer /app
RUN mvn clean install -DskipTests
RUN  mvn exec:java -Dexec.mainClass="ca.concordia.jaranalyzer.Runner"
#
#FROM openjdk:8-jre-alpine
#WORKDIR /app
#COPY --from=1 /app/target/spring-petclinic-1.5.1.jar /app (4)
#CMD ["java -jar spring-petclinic-1.5.1.jar"] (5)