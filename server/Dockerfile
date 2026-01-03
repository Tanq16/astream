FROM golang:1.25-alpine AS builder
WORKDIR /app
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o astream .

FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/astream .
EXPOSE 8080
CMD ["./astream"]
