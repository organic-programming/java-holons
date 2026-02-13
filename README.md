---
# Cartouche v1
title: "java-holons — Java SDK for Organic Programming"
author:
  name: "B. ALTER"
  copyright: "© 2026 Benoit Pereira da Silva"
created: 2026-02-12
revised: 2026-02-13
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

**Java SDK for Organic Programming** — transport, serve, identity,
and Holon-RPC client utilities for building holons in Java.

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
| `HolonRPCClient` | `connect(url)`, `invoke(method, params)`, `register(method, handler)`, `close()` |

## Transport support

| Scheme | Support |
|--------|---------|
| `tcp://<host>:<port>` | Bound server socket (`Transport.TcpListener`) |
| `unix://<path>` | Parsed; runtime binding requires Unix-domain capable gRPC stack |
| `stdio://` | Listener marker (`Transport.StdioListener`) |
| `mem://` | Listener marker (`Transport.MemListener`) |
| `ws://<host>:<port>` | Listener metadata (`Transport.WSListener`) |
| `wss://<host>:<port>` | Listener metadata (`Transport.WSListener`) |

## Parity Notes vs Go Reference

Implemented parity:

- URI parsing and listener dispatch semantics
- Native runtime listener for `tcp://`
- Holon-RPC client protocol support over `ws://` / `wss://` (JSON-RPC 2.0, heartbeat, reconnect)
- Standard serve flag parsing
- HOLON identity parsing

Not currently achievable in this minimal Java core (justified gaps):

- `unix://` native runtime binding:
  - Requires a Unix-domain capable Java gRPC runtime stack (typically Netty native transport).
  - This SDK intentionally stays on pure JDK socket primitives.
- `stdio://` and `mem://` runtime listeners:
  - gRPC Java does not expose an official stdio/memory transport comparable to Go `net.Listener` abstractions.
- `ws://` / `wss://` runtime listener parity:
  - No official gRPC Java WebSocket server transport for HTTP/2 framing in the core stack.
  - Exposed as metadata only.
- Full gRPC transport parity (`Dial("tcp://...")`, `Dial("stdio://...")`, `Listen("stdio://...")`, and `Serve.Run()` wiring):
  - gRPC Java has no official stdio transport equivalent to Go `net.Listener` for process pipes.
  - A complete `serve.Run()` equivalent also needs custom signal + reflection wiring not yet included in this SDK core.
