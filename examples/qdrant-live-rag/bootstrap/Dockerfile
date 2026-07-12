# Tiny one-shot image: curl + jq + grpcurl, plus the bootstrap scripts and pipeline.
# Build context is the example root so we can COPY both bootstrap/ and pipeline/.
FROM fullstorydev/grpcurl:latest AS grpcurl

FROM alpine:3.20
RUN apk add --no-cache bash curl jq
COPY --from=grpcurl /bin/grpcurl /usr/local/bin/grpcurl
WORKDIR /work
COPY bootstrap/bootstrap.sh bootstrap/target.sh ./
COPY pipeline/ ./pipeline/
RUN chmod +x bootstrap.sh target.sh
ENTRYPOINT ["./bootstrap.sh"]
