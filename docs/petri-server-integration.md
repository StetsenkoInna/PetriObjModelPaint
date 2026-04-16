# petri-server — Integration Guide

`petri-server` is a self-contained Spring Boot service that exposes Petri net simulation
over HTTP.  It accepts a PNML document, runs the discrete-event simulator, and delivers
results through one of two transports:

| Transport | Best for |
|-----------|----------|
| **SSE streaming** (`POST /stream`) | Web frontends, async pipelines, microservice calls |
| **WebSocket / STOMP** (`/ws`) | Real-time dashboards, interactive UIs |

Both transports share the same pause / resume / stop REST control endpoints.

---

## Base URL

```
http://localhost:8080
```

Change the port in `petri-server/src/main/resources/application.yml` (`server.port`).

---

## PNML document requirements

Every simulation call requires a PNML string (`netXml`).  The parser expects the
standard ISO/IEC 15909 envelope:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<pnml>
  <net id="net1" type="http://www.pnml.org/version-2009/grammar/ptnet">
    <place id="p1">
      <name><text>Queue</text></name>
      <initialMarking><text>3</text></initialMarking>
      <toolspecific tool="PetriObjModel">
        <graphics><position x="100" y="200"/></graphics>
      </toolspecific>
    </place>
    <transition id="t1">
      <name><text>Service</text></name>
      <toolspecific tool="PetriObjModel">
        <timeParams distribution="exp" mean="2.0" deviation="0"/>
        <priority>0</priority>
        <probability>1.0</probability>
      </toolspecific>
    </transition>
    <arc id="a1" source="p1" target="t1">
      <inscription><text>1</text></inscription>
    </arc>
    <arc id="a2" source="t1" target="p1out">
      <inscription><text>1</text></inscription>
    </arc>
  </net>
</pnml>
```

The `id` attributes on `<place>` and `<transition>` become the keys in `markings` and
`buffers` maps returned by the simulation.

---

## SSE Streaming Endpoint

### Request

```
POST /api/v1/simulation/stream
Content-Type: application/json
Accept: text/event-stream
```

**Body**

```json
{ "netXml": "<pnml>...</pnml>" }
```

**Query parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `simulationTime` | `double` | `3600.0` | Total simulation time units |
| `timeStep` | `double` | `1.0` | **Time-based mode**: emit a snapshot every this many simulation units. Total snapshots ≈ `simulationTime / timeStep`. |
| `snapshotInterval` | `int` | — | **Step-based mode**: emit a snapshot every N transition firings. When set, overrides `timeStep`. |
| `animationDelayMs` | `long` | `0` | Real-time pause (ms) after each snapshot. Use `timeStep * 1000 / simSpeed` for proportional animation. `0` = stream at full speed (batch mode). |

Exactly one snapshot mode is active per request:

```
snapshotInterval set  →  step-based
snapshotInterval absent →  time-based (timeStep used)
```

---

### Event lifecycle

```
event: session       ← always first; carries sessionId for control calls
data: {"sessionId":"550e8400-..."}

data: {"current_time":1.0,"step_number":42,...}   ← snapshot
data: {"current_time":2.0,"step_number":88,...}   ← snapshot
...
data: [DONE]                                       ← end of stream
```

### Snapshot event shape

```json
{
  "current_time":  1.0,
  "step_number":   42,
  "markings":  { "p1": 3, "p2": 0 },
  "buffers":   { "t1": 1, "t2": 0 },
  "progress":  0.001
}
```

| Field | Type | Description |
|-------|------|-------------|
| `current_time` | `double` | Simulation clock value at this snapshot |
| `step_number` | `int` | Total transition firings so far |
| `markings` | `Map<id, int>` | Token count per place (keyed by PNML `id`) |
| `buffers` | `Map<id, int>` | Active channel count per transition |
| `progress` | `double` | `current_time / simulationTime` ∈ [0, 1] |

---

### JavaScript (EventSource)

```js
async function runSimulation(netXml, opts = {}) {
  const {
    simulationTime = 3600,
    timeStep = 1.0,
    animationDelayMs = 0,
  } = opts;

  const params = new URLSearchParams({ simulationTime, timeStep, animationDelayMs });
  const res = await fetch(`http://localhost:8080/api/v1/simulation/stream?${params}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ netXml }),
  });

  let sessionId = null;
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buf = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });

    const lines = buf.split('\n');
    buf = lines.pop(); // keep incomplete line

    for (const line of lines) {
      if (line.startsWith('data: [DONE]')) return;
      if (line.startsWith('data: ')) {
        const payload = JSON.parse(line.slice(6));
        if (payload.sessionId) { sessionId = payload.sessionId; continue; }
        onFrame(payload);           // { current_time, step_number, markings, buffers, progress }
      }
    }
  }
}

function onFrame(frame) {
  console.log(`t=${frame.current_time}  progress=${(frame.progress * 100).toFixed(1)}%`);
  // update UI with frame.markings / frame.buffers
}
```

---

### Python (httpx)

```python
import json
import httpx

def run_simulation(net_xml: str, simulation_time: float = 3600.0, time_step: float = 1.0):
    params = {"simulationTime": simulation_time, "timeStep": time_step}
    with httpx.stream(
        "POST",
        "http://localhost:8080/api/v1/simulation/stream",
        params=params,
        json={"netXml": net_xml},
        headers={"Accept": "text/event-stream"},
        timeout=None,
    ) as r:
        buf = ""
        for chunk in r.iter_text():
            buf += chunk
            while "\n\n" in buf:
                event_block, buf = buf.split("\n\n", 1)
                for line in event_block.splitlines():
                    if line.startswith("data: [DONE]"):
                        return
                    if line.startswith("data: "):
                        frame = json.loads(line[6:])
                        if "sessionId" in frame:
                            continue
                        yield frame   # {"current_time", "step_number", "markings", "buffers", "progress"}

# Usage
for frame in run_simulation(pnml_string, simulation_time=500, time_step=5):
    print(f"t={frame['current_time']:.1f}  queue={frame['markings'].get('p1', 0)}")
```

---

### curl

```bash
# Stream, one snapshot per 10 sim-units, full speed
curl -N -X POST "http://localhost:8080/api/v1/simulation/stream?simulationTime=100&timeStep=10" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"netXml":"<pnml>...</pnml>"}'
```

---

## Session Control (pause / resume / stop)

After receiving `sessionId` from the first SSE event, use the REST control endpoints.
They work identically for both SSE and WebSocket sessions.

| Method | Path | Effect |
|--------|------|--------|
| `POST` | `/api/v1/simulation/{id}/pause` | Suspends the simulation thread |
| `POST` | `/api/v1/simulation/{id}/resume` | Resumes a paused simulation |
| `POST` | `/api/v1/simulation/{id}/stop` | Terminates; stream ends with `[DONE]` |
| `GET`  | `/api/v1/simulation/{id}/status` | Returns `{"status": "RUNNING"}` |

**Status values**: `PENDING` · `RUNNING` · `PAUSED` · `FINISHED` · `HALTED`

```bash
# Pause
curl -X POST http://localhost:8080/api/v1/simulation/550e8400-.../pause

# Resume
curl -X POST http://localhost:8080/api/v1/simulation/550e8400-.../resume

# Stop
curl -X POST http://localhost:8080/api/v1/simulation/550e8400-.../stop
```

---

## WebSocket / STOMP Endpoint

Endpoint: `ws://localhost:8080/ws` (SockJS fallback available)

### 1. Start a session via REST

```bash
curl -X POST http://localhost:8080/api/v1/simulation/start \
  -H "Content-Type: application/json" \
  -d '{"netXml":"<pnml>...</pnml>", "simulationTime": 3600}'
# → {"sessionId": "550e8400-..."}
```

### 2. Subscribe to topics

```
/topic/v1/sim/{sessionId}/steps    ← simulation step events
/topic/v1/sim/{sessionId}/status   ← status change events
```

### Step event shape (STOMP)

```json
{
  "sessionId": "550e8400-...",
  "currentTime": 42.5,
  "statistics": [
    { "petriObjId": 1, "elementName": "Queue", "min": 0, "max": 5, "avg": 1.23 }
  ]
}
```

> Note: The STOMP step shape carries rolling statistics (`min/max/avg`), whereas the SSE
> shape carries live markings and buffers.  Choose the transport that matches your use case.

### 3. Control via STOMP

Send to `/app/v1/sim/{sessionId}/control` with message body:

```
PAUSE
RESUME
STOP
```

### JavaScript (STOMP.js)

```js
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  onConnect: () => {
    client.subscribe(`/topic/v1/sim/${sessionId}/steps`, msg => {
      const event = JSON.parse(msg.body);
      console.log(event.currentTime, event.statistics);
    });
    client.subscribe(`/topic/v1/sim/${sessionId}/status`, msg => {
      console.log('status:', JSON.parse(msg.body).status);
    });
  },
});
client.activate();

// Control
client.publish({ destination: `/app/v1/sim/${sessionId}/control`, body: 'PAUSE' });
```

---

## Choosing a Transport

| Criterion | SSE | WebSocket/STOMP |
|-----------|-----|-----------------|
| Browser support | `fetch` / `EventSource` | Requires STOMP client library |
| Server-to-client only | Yes | Bidirectional |
| Session control | Separate REST calls | STOMP message |
| Event format | `markings` + `buffers` maps | `statistics` list |
| Snapshot frequency | configurable (`timeStep` / `snapshotInterval`) | every sim step |
| Recommended for | Microservice clients, web animations | Interactive dashboards |

---

## Integrating with text2pnml

`petri-server` can serve as a dedicated simulation backend for
[text2pnml](https://github.com/your-org/text2pnml) or any system that produces PNML.

**Workflow**

1. Export PNML from text2pnml:
   ```
   GET /api/v1/sessions/{id}/net   → session net JSON
   ```
   Then export to PNML via the agent tool `export_network` or the MCP `export_pnml` tool.

2. POST the PNML to petri-server:
   ```python
   frames = list(run_simulation(pnml_string, simulation_time=3600, time_step=1.0))
   ```

3. The `markings` keys in each frame match the place UUIDs from the text2pnml PNML export,
   so results can be mapped back to the original net without any ID translation.

**SSE event compatibility**

The SSE snapshot shape emitted by petri-server is identical to the one produced by
text2pnml's own `/sessions/{id}/simulate` endpoint:

```
current_time  step_number  markings  buffers  progress
```

This means the same frontend rendering code works against both backends.

---

## OpenAPI / Swagger

Interactive API docs are available at runtime:

| URL | Description |
|-----|-------------|
| `http://localhost:8080/docs` | Swagger UI |
| `http://localhost:8080/openapi.json` | OpenAPI 3 spec (JSON) |
