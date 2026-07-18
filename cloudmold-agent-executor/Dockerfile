FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system --gid 10001 cloudmold \
    && useradd --system --uid 10001 --gid cloudmold --home-dir /opt/cloudmold --shell /usr/sbin/nologin cloudmold \
    && mkdir -p /opt/cloudmold/logs/nacos \
    && chown -R cloudmold:cloudmold /opt/cloudmold

WORKDIR /opt/cloudmold
COPY --chown=cloudmold:cloudmold ./target/cloudmold-agent-executor-exec.jar app.jar

USER 10001:10001
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms128m -Xmx384m -Dfile.encoding=UTF-8 -Ddubbo.application.serialize-check-status=STRICT -Ddubbo.application.check-serializable=false -Ddubbo.hessian.allowNonSerializable=true"

ENTRYPOINT ["/bin/bash", "-c", "export CLOUDMOLD_RPC_SHARED_SECRET=\"$(</run/secrets/cloudmold_rpc_shared_secret)\"; exec java ${JAVA_OPTS} -jar app.jar \"$@\"", "--"]
