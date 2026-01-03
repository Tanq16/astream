FROM golang:1.22-alpine AS builder
WORKDIR /app
RUN apk add --no-cache git
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o astream .

FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/astream .
EXPOSE 8080
CMD ["./astream"]
