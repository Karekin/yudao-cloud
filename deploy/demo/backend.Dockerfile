FROM eclipse-temurin:17-jre-jammy

ARG VCS_REF=local
LABEL org.opencontainers.image.title="CloudMold backend" \
      org.opencontainers.image.revision="${VCS_REF}"

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 cloudmold

WORKDIR /app
COPY --chown=cloudmold:cloudmold yudao-server.jar app.jar

USER cloudmold
ENV TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8" \
    SPRING_PROFILES_ACTIVE=local

EXPOSE 48080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=8 \
  CMD curl --fail --silent http://127.0.0.1:48080/actuator/health/readiness >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
