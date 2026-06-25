FROM postgres:16-alpine

RUN apk upgrade --no-cache \
    && apk add --no-cache su-exec \
    && rm /usr/local/bin/gosu \
    && ln -s /sbin/su-exec /usr/local/bin/gosu
