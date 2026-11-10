ARG MC_RELEASE=RELEASE.2025-08-13T08-35-41Z

FROM golang:1.24.8-alpine3.22 AS build
ARG MC_RELEASE

RUN apk add --no-cache git
WORKDIR /src
RUN git clone --depth 1 --branch ${MC_RELEASE} https://github.com/minio/mc.git .

ENV CGO_ENABLED=0 GOTOOLCHAIN=local
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go build -trimpath -tags kqueue -ldflags "$(go run buildscripts/gen-ldflags.go)" -o /out/mc .

FROM alpine:3.22

# redis = traz o redis-cli usado para indexar o catálogo
RUN apk add --no-cache ca-certificates redis \
 && addgroup -S app && adduser -S -G app app

COPY --from=build /out/mc /usr/local/bin/mc
COPY init.sh /opt/nordstay/init.sh
COPY seed/ /seed/
RUN sed -i 's/\r$//' /opt/nordstay/init.sh

USER app
ENV MC_CONFIG_DIR=/tmp/.mc

ENTRYPOINT ["sh", "/opt/nordstay/init.sh"]