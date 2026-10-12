# syntax=docker/dockerfile:1
ARG ALPINE_VERSION=3.24
ARG TINC_REF=211e3dfaef32d8736962e25f0b096dad951b7104

FROM alpine:${ALPINE_VERSION} AS build
ARG TINC_REF

RUN apk add --no-cache \
    build-base curl linux-headers lz4-dev lzo-dev meson ninja \
    openssl-dev pkgconf zlib-dev

WORKDIR /src
RUN curl --fail --location --retry 3 \
      "https://codeload.github.com/gsliepen/tinc/tar.gz/${TINC_REF}" \
      --output /tmp/tinc.tar.gz \
    && tar -xzf /tmp/tinc.tar.gz --strip-components=1 \
    && meson setup /build \
      --prefix=/usr --sysconfdir=/etc --localstatedir=/var \
      --buildtype=release \
      -Drunstatedir=/run \
      -Ddocs=disabled -Dtests=disabled -Dsystemd=disabled \
      -Dcurses=disabled -Dreadline=disabled \
      -Dzlib=enabled -Dlzo=enabled -Dlz4=enabled \
    && meson compile -C /build \
    && DESTDIR=/out meson install -C /build

FROM alpine:${ALPINE_VERSION}
ARG TINC_REF

LABEL org.opencontainers.image.title="Private tinc VPN" \
      org.opencontainers.image.description="tinc 1.1 with UDP mesh networking and TCP fallback" \
      org.opencontainers.image.source="https://github.com/gsliepen/tinc" \
      org.opencontainers.image.revision="${TINC_REF}" \
      org.opencontainers.image.licenses="GPL-2.0-or-later"

RUN apk upgrade --no-cache \
    && apk add --no-cache iproute2-minimal libcrypto3 libssl3 lz4-libs lzo zlib \
       python3 libqrencode-tools \
    && mkdir -p /etc/tinc /usr/share/licenses/tinc

COPY --from=build /out/usr/sbin/tinc /usr/sbin/tinc
COPY --from=build /out/usr/sbin/tincd /usr/sbin/tincd
COPY --from=build /src/COPYING /src/COPYING.README /usr/share/licenses/tinc/
COPY --chmod=755 container/setup.py /usr/local/bin/tincer-setup

EXPOSE 655/tcp 655/udp
VOLUME ["/etc/tinc"]
ENTRYPOINT ["/usr/sbin/tincd"]
CMD ["--no-detach", "--debug=1", "--net=mesh"]
