# LAN+ Servers

Backend and relay servers for LAN+. This branch is standalone — no Minecraft/Forge; pure-JDK (Java 17).

## Build

```bash
./gradlew :backend:jar :relay:jar
```

- Backend (fat jar, bundles SQLite JDBC): `backend/build/libs/lanplus-backend-1.0.0.jar`
- Relay (zero-dependency): `relay/build/libs/lanplus-relay-1.0.0.jar`

## Run

Both are configured via environment variables (see `relay/src/.../RelayConfig.java` and `backend/src/.../BackendConfig.java`).

```bash
java -jar backend/build/libs/lanplus-backend-1.0.0.jar   # HTTP + WebSocket
java -jar relay/build/libs/lanplus-relay-1.0.0.jar       # control + Minecraft tunnel
```

## Docs

Wire contract and operation details live in `docs/dev/` of the main (mod) repository: `PROTOCOL.md`, `BACKEND_RELAY.md`, `SQLITE.md`.