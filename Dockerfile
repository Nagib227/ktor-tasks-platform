FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/install/ktor-tasks-platform /app
ENV PORT=8080
ENV HOST=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["/app/bin/ktor-tasks-platform"]
