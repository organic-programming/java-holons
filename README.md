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
| `Transport` | `listen(uri)`, `scheme(uri)` — URI-based listener factory |
| `Serve` | `parseFlags(args)` — CLI arg extraction |
| `Identity` | `parseHolon(path)` — HOLON.md parser with SnakeYAML |
