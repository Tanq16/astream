# astream

WIP - Android app to screen share to LAN server via websockets

Intention - I wanted something that i could use my tablet to draw and share on meetings, etc. Existing solutions exist but kept stopping, relied on background services, etc. which caused Android to shut them down, or sharing format wasn't efficient enough.

## Build

```bash
git clone https://github.com/tanq16/astream && \
cd astream/android && \
./gradlew assembleDebug
```

Install the apk and start the server at -

```bash
cd astream/server && go run main.go
```
