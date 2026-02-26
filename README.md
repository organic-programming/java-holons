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
| `unix://<path>` | Native runtime listener + dial (`Transport.UnixListener`, `Transport.dialUnix`) |
| `stdio://` | Listener marker (`Transport.StdioListener`) |
| `mem://` | Native in-process listener + dial (`Transport.MemListener`, `Transport.memDial`) |
| `ws://<host>:<port>` | Listener metadata (`Transport.WSListener`) |
| `wss://<host>:<port>` | Listener metadata (`Transport.WSListener`) |

## Parity Notes vs Go Reference

Implemented parity:

- URI parsing and listener dispatch semantics
- Native runtime listener for `tcp://`
- Native runtime listener + dial for `unix://`
- Native in-process listener + dial for `mem://`
- Holon-RPC client protocol support over `ws://` / `wss://` (JSON-RPC 2.0, heartbeat, reconnect)
- Standard serve flag parsing
- HOLON identity parsing

Not currently achievable in this minimal Java core (justified gaps):

- `stdio://` runtime listener:
  - `stdio://` remains metadata-only because gRPC Java does not expose a public stdio HTTP/2 transport.
- `ws://` / `wss://` runtime listener parity:
  - No official gRPC Java WebSocket server transport for HTTP/2 framing in the core stack.
  - Exposed as metadata only.
- gRPC `Dial("ws://...")` / `Dial("wss://...")`:
  - gRPC Java has no official client transport that tunnels HTTP/2 frames directly over WebSocket.
  - Implementing this would require a custom bridge/proxy layer outside the minimal core.
- Full gRPC transport parity (`Dial("tcp://...")`, `Dial("stdio://...")`, `Listen("stdio://...")`, and `Serve.Run()` wiring):
  - gRPC Java has no official stdio transport equivalent to Go `net.Listener` for process pipes.
  - A complete `serve.Run()` equivalent also needs custom signal + reflection wiring not yet included in this SDK core.
