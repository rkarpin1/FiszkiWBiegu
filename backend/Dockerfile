FROM rust:1.87-slim AS builder
WORKDIR /app
COPY Cargo.toml Cargo.lock ./
RUN mkdir src && echo "fn main() {}" > src/main.rs && \
    cargo build --release && \
    rm -f target/release/deps/fiszki_w_biegu*
COPY src ./src
RUN cargo build --release

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y ca-certificates && \
    rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/release/fiszki-w-biegu /usr/local/bin/fiszki-w-biegu
EXPOSE 8080
CMD ["fiszki-w-biegu"]
