FROM gradle:8.3.0-jdk17 AS BUILD
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle test --info --no-daemon
RUN gradle clean --no-daemon
RUN gradle shadowJar --no-daemon

FROM openjdk:17
RUN mkdir /app
COPY --from=BUILD /home/gradle/src/build/libs/*.jar /app/app.jar
EXPOSE 80
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=90.0", "-XshowSettings:vm", "-jar", "/app/app.jar"]
