# KojaCraft Orchestrator Connector

The in-server component that registers a Minecraft server with the orchestrator
and reports status. Ships as **both a Spigot/Paper/Bukkit plugin and a mod**
(Forge / Fabric / NeoForge / Quilt), built to be version-agnostic.

## Design — why it's version-agnostic

```
core/        pure Java 8, NO Minecraft API
             ├─ ConnectorConfig   reads env + data.yml + game.json
             ├─ RedisBus          Jedis publisher (shaded + relocated)
             ├─ Protocol          packet ids/channels (mirror intranet.rs)
             ├─ OrchestratorConnector  lifecycle: Start/End + heartbeat status
             ├─ PlatformHooks     the small per-platform surface
             └─ ReflectiveServerHooks  reads player counts off MinecraftServer
                                       reflectively (loader-neutral)
bukkit/      JavaPlugin  -> getPlayerCount via Bukkit API (1 jar, all versions)
fabric/      DedicatedServerModInitializer (also loads under Quilt)
forge/       @Mod, ServerStarted/StoppingEvent
neoforge/    @Mod, ServerStarted/StoppingEvent
```

All orchestrator communication, config loading and packet shaping live in
`core`, which has **no Minecraft dependency at all** — so it is byte-for-byte
identical on every version. Each platform module only supplies a handful of
calls (player count, max slots, data dir, a scheduler) via `PlatformHooks`.

- **Bukkit**: compiled against an old API and using only long-stable calls, the
  single plugin jar runs on every Minecraft version.
- **Mods**: player data is read reflectively from the `MinecraftServer`
  instance (`ReflectiveServerHooks`), trying the method names used across
  mapping sets, so one jar covers as many versions as possible and degrades
  gracefully (falling back to `MAX_PLAYERS`) on anything unrecognised.

## What it does

On server start it publishes `MINECRAFT_SERVER_UPDATE/Start` (flipping the
server to `RUNNING` in the orchestrator and triggering proxy registration), then
emits periodic status reports (player count / max slots) on
`orchestrator:server_status`. On shutdown it publishes
`MINECRAFT_SERVER_UPDATE/End` so the orchestrator deregisters the server.

Configuration is resolved automatically from the environment the Docker image
sets plus the `data.yml` / `game.json` the orchestrator writes — no manual
config needed.

## Build

```bash
./gradlew :bukkit:shadowJar     # build/libs/koja-orchestrator-connector-bukkit-*.jar
./gradlew :fabric:build         # fabric mod
./gradlew :forge:build          # forge mod
./gradlew :neoforge:build       # neoforge mod
```

Reference loader/MC versions used only at compile time are in
`gradle.properties`. Publish the artifacts and point the image's
`ORCHESTRATOR_CONNECTOR_URL` at the right one (plugin for Paper templates, mod
for modded templates) — the image drops it into `plugins/` or `mods/`
automatically.

## Adjusting protocol wiring

Channel names and packet ids are centralized in
`core/.../Protocol.java`. If a deployment uses different orchestrator channels,
change them there only. The host-agent client UUID can be supplied via the
`CLIENT_UUID` env for exact running-server bookkeeping; otherwise a stable UUID
is derived from the server name.
