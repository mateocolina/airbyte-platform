ARG JAVA_BASE_IMAGE_VERSION=3.3.8

FROM scratch as builder
WORKDIR /app
ADD airbyte-app.tar /app

FROM airbyte/airbyte-base-java-image:${JAVA_BASE_IMAGE_VERSION}

ARG DOCKER_BUILD_ARCH=amd64

# Don't change this manually.  Bump version expects to make moves based on this string
ARG VERSION=dev

ENV APPLICATION airbyte-connector-sidecar
ENV VERSION=${VERSION}

COPY --chown=airbyte:airbyte WellKnownTypes.json /app
COPY --chown=airbyte:airbyte --from=builder /app /app
USER airbyte:airbyte

# wait for upstream dependencies to become available before starting server
CMD ["/bin/bash", "-c", "/app/airbyte-app/bin/${APPLICATION}"]
