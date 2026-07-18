FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system --gid 10002 cloudmold \
    && useradd --system --uid 10002 --gid cloudmold --home-dir /opt/cloudmold --shell /usr/sbin/nologin cloudmold \
    && mkdir -p /opt/cloudmold/logs/nacos \
    && chown -R cloudmold:cloudmold /opt/cloudmold

WORKDIR /opt/cloudmold
COPY --chown=cloudmold:cloudmold ./target/cloudmold-hsf-mcp-server.jar app.jar

USER 10002:10002
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms128m -Xmx384m -Dfile.encoding=UTF-8 -Ddubbo.application.migration.step=FORCE_INTERFACE -Ddubbo.application.serialize-check-status=STRICT -Ddubbo.application.check-serializable=false -Ddubbo.hessian.allowNonSerializable=true"

ENTRYPOINT ["/bin/bash", "-c", "export CLOUDMOLD_RPC_SHARED_SECRET=\"$(</run/secrets/cloudmold_rpc_shared_secret)\"; export CLOUDMOLD_MCP_BEARER_TOKEN=\"$(</run/secrets/cloudmold_mcp_bearer_token)\"; exec java ${JAVA_OPTS} -jar app.jar"]
