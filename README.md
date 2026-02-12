---
# Cartouche v1
title: "java-holons — Java SDK for Organic Programming"
author:
  name: "B. ALTER"
  copyright: "© 2026 Benoit Pereira da Silva"
created: 2026-02-12
revised: 2026-02-12
lang: en-US
origin_lang: en-US
translation_of: null
translator: null
access:
  humans: true
  agents: false
status: draft
---
# java-holons

**Java SDK for Organic Programming** — transport, serve, and identity
utilities for building holons in Java.

## Build & Test

```bash
gradle test
```

## API surface

| Class | Description |
|-------|-------------|
| `Transport` | `parseURI(uri)`, `listen(uri)`, `scheme(uri)` — URI parser + listener variants |
| `Serve` | `parseFlags(args)` — CLI arg extraction |
| `Identity` | `parseHolon(path)` — HOLON.md parser with SnakeYAML |

## Transport support

| Scheme | Support |
|--------|---------|
| `tcp://<host>:<port>` | Bound server socket (`Transport.TcpListener`) |
| `unix://<path>` | Parsed; runtime binding requires Unix-domain capable gRPC stack |
| `stdio://` | Listener marker (`Transport.StdioListener`) |
| `mem://` | Listener marker (`Transport.MemListener`) |
| `ws://<host>:<port>` | Listener metadata (`Transport.WSListener`) |
| `wss://<host>:<port>` | Listener metadata (`Transport.WSListener`) |
