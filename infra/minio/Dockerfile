ARG MINIO_RELEASE=RELEASE.2025-10-15T17-29-55Z

FROM golang:1.24.8-alpine3.22 AS build
ARG MINIO_RELEASE

RUN apk add --no-cache git
WORKDIR /src
RUN git clone --depth 1 --branch ${MINIO_RELEASE} https://github.com/minio/minio.git .

ENV CGO_ENABLED=0 GOTOOLCHAIN=local
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go build -trimpath -ldflags "$(go run buildscripts/gen-ldflags.go)" -o /out/minio .

FROM alpine:3.22

RUN apk add --no-cache ca-certificates curl \
 && addgroup -S minio && adduser -S -G minio minio \
 && mkdir -p /data && chown minio:minio /data

COPY --from=build /out/minio /usr/local/bin/minio

USER minio
EXPOSE 9000 9001

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=10 \
  CMD curl -fsS http://localhost:9000/minio/health/live || exit 1

ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]