FROM bellsoft/liberica-runtime-container:jre-21-crac-slim-glibc

RUN addgroup --system --gid 800 appuser && adduser --system --uid 800 appuser

RUN mkdir -p /docs && chown appuser:appuser /docs

RUN chmod 755 /docs

USER appuser

COPY ./api-gateway-app/build/libs/app.jar /app.jar

EXPOSE 8080 9010 9090 7000

ENTRYPOINT [
    "java", \
    "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/var/dumps", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dcom.sun.management.jmxremote", \
    "-Dcom.sun.management.jmxremote.port=9010", \
    "-Dcom.sun.management.jmxremote.rmi.port=9010", \
    "-Dcom.sun.management.jmxremote.local.only=false", \
    "-Dcom.sun.management.jmxremote.authenticate=false", \
    "-Dcom.sun.management.jmxremote.ssl=false", \
    "-Djava.rmi.server.hostname=0.0.0.0", \
    "-jar","/app.jar"
]
