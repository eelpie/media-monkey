FROM openjdk:8-jre
COPY target/squadlist-api-0.0.1-SNAPSHOT.jar /opt/squadlist-api/squadlist-api-0.0.1-SNAPSHOT.jar
CMD ["java","-jar","/opt/squadlist-api/squadlist-api-0.0.1-SNAPSHOT.jar", "--spring.config.location=/opt/squadlist-api/conf/squadlist-api.properties"]
