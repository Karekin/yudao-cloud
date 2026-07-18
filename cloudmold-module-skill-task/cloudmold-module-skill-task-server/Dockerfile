FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system --gid 10003 cloudmold \
    && useradd --system --uid 10003 --gid cloudmold --home-dir /opt/cloudmold --shell /usr/sbin/nologin cloudmold \
    && mkdir -p /opt/cloudmold/logs/nacos \
    && chown -R cloudmold:cloudmold /opt/cloudmold

WORKDIR /opt/cloudmold
COPY --chown=cloudmold:cloudmold ./target/cloudmold-module-skill-task-server.jar app.jar

USER 10003:10003
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms192m -Xmx512m -Dfile.encoding=UTF-8 -Ddubbo.application.migration.step=FORCE_INTERFACE -Ddubbo.application.serialize-check-status=STRICT -Ddubbo.application.check-serializable=false -Ddubbo.hessian.allowNonSerializable=true"

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
  CMD test -s /tmp/cloudmold-skill-task-ready || exit 1

ENTRYPOINT ["/bin/bash", "-c", "export CLOUDMOLD_RPC_SHARED_SECRET=\"$(</run/secrets/cloudmold_rpc_shared_secret)\"; export CLOUDMOLD_DB_PASSWORD=\"$(</run/secrets/cloudmold_mysql_password)\"; exec java ${JAVA_OPTS} -jar app.jar"]
