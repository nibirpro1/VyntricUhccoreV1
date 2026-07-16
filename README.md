# VyntricUhccoreV1 (branding: VyntricUhc)

A companion add-on plugin for your UHC server that adds:

- **Game phases** — the plugin tracks four phases and broadcasts on every
  transition, shown live on the scoreboard (`{phase}` placeholder) and via
  `/vuhc timer status`:
  1. **Waiting to start** — before `/vuhc start` (or `/vuhc timer start`).
  2. **Grind Time** — game has started, PvP is off for
     `timers.pvp-default-seconds` (default 5 min) so people can spread out
     and gear up safely. Ends early with `/vuhc pvp force`, or change how
     long it lasts (even mid-grind) with `/vuhc pvp reset <time>` — that's
     the "set the PvP timer" command.
  3. **Active** — PvP is on, meetup/deathmatch timer counting down.
  4. **Deathmatch** — meetup timer hit zero; border should be closing in.
     Broadcasts `DEATHMATCH PHASE has begun!` to everyone. From this point,
     anyone above `deathmatch.height-limit` (default Y=90) takes damage every
     second and can't place blocks up there either — no sky-basing to dodge
     the fight. Configurable under `deathmatch:` in `config.yml`, and
     changeable live with `/vuhc highlimit <amount>` (or
     `/vuhc highlimit set <amount>`) — no reload or restart needed.
- **`/vuhc border set <amount> [timeduration]`** — sets the world border,
  centered on the world's spawn point. `/vuhc border set 200` instantly makes
  it a 200x200 border; add a duration to shrink/grow into it over time
  instead, e.g. `/vuhc border set 200 10m` or `/vuhc border set 200 600`
  (plain seconds and `10m`/`1h`-style values both work).
- **`/vuhc start`** — starts the actual UHC game: teleports every online player
  to a random, spread-out spot inside the world border, freezes everyone in
  place for `scatter.freeze-seconds` (default 15s) so nobody gets a head
  start, then releases everyone at the same moment and starts the meetup/PvP
  timer engine. Configurable under `scatter:` in `config.yml`.
- **`/vuhc meetup <time>`** — set the meetup (deathmatch) timer to an exact value,
  or `/vuhc meetup add <time>` / `/vuhc meetup remove <time>` to adjust it.
  Works **while the game is already running**, not just before start.
- **`/vuhc pvp force`** — instantly force-enable PvP, skipping the normal
  5-minute (configurable) wait after game start.
- **`/vuhc pvp reset <time>`** — reset/restart the pre-PvP countdown to a new value.
- **`/vuhc timer start|stop|status`** — controls this plugin's own timer engine
  (start it whenever your UHC game starts).
- **`/vuhc announcement <message>`** (alias `/vuh announcement`, `/vuhc announce`)
  — sends a big branded announcement to every online player: a fancy chat
  banner, a title/subtitle popup on their screen, and a notification sound.
- **Alt account detector** — tracks the IPs each account has joined from and
  alerts staff (permission `vyntricuhc.altalert`) when a duplicate IP joins.
  Check anytime with `/vuhc alts <player>`.
- **Simple login system** — `/register <password> <confirm>` and
  `/login <password>`, with movement/damage/chat/command freezing until
  logged in, and a kick-on-timeout.
- **Custom sidebar scoreboard + tab list** — branded "VYNTRIC UHC" sidebar
  showing online players, live meetup/PvP timers, kills and alive count;
  tab list gets a branded header/footer plus green/gray name coloring for
  alive vs. spectating players. Refreshes every second automatically. All of
  the text/layout lives in `scoreboard.yml` (created next to `config.yml` in
  the plugin's data folder on first run) so you can restyle it without
  touching any code.
- **`/vuhc reload`** — reloads both `config.yml` and `scoreboard.yml` from
  disk on the fly (no server restart needed). Runs the same startup
  validation as `onEnable`, so a bad value gets caught and logged instead of
  silently breaking something.
- **Cross-team ("teaming"/alliance) detector** — `/vuhc track <team>` or the
  standalone `/track <team>` (kept for muscle memory from the old
  Vyntric_Cross_Team_Tracker plugin, which this replaces). See
  "About the cross-team detector" below for what changed and why.
- **Leave-becomes-zombie** — once the game has started, a player who
  disconnects is replaced by a real zombie standing exactly where they were,
  instead of just safely vanishing. Kill the zombie before they reconnect and
  they're eliminated (dropped into spectator mode on rejoin, with a message).
  Reconnect before it's killed and they're safe — the zombie is removed and
  they're teleported back. `/vuhc revive <player>` lets an admin clear a
  mistaken/griefed elimination. All of it is configurable under
  `leave-zombie:` in `config.yml`.

- **Chunk preloader** — on server start, generates every chunk inside the
  world border up front (the full area the UHC border will ever shrink
  through), so nothing lags or half-loads later. While it's running, anyone
  trying to join gets kicked with a "still loading, try again in a few
  minutes" message instead of spawning into a half-generated world.
  Configurable under `chunk-preload:` in `config.yml`.

## About the cross-team detector

The original `Vyntric_Cross_Team_Tracker` plugin only counted PvP hits
between teams. That misses the actual thing "cross teaming" refers to on a
UHC server: two teams that agree **not** to fight each other. A pair of
teams that simply never engages produced zero data in a hits-only tracker
and was never flagged — exactly the case that matters most.

This version (`CrossTeamManager`) tracks two numbers per team pair, once a
second:

- **proximity time** — how long members of two different teams have spent
  within `cross-team-tracker.proximity-radius` blocks of each other
- **hits** — how many times they've actually damaged each other

A pair that racks up a lot of proximity time with a very low hit ratio
(`cross-team-tracker.max-hit-ratio`) gets auto-flagged to anyone with
`vyntricuhc.crossteamalert`, rate-limited by `alert-cooldown-seconds` so it
doesn't spam chat. `/vuhc track <team>` / `/track <team>` (permission
`vyntricuhc.crossteam`) show a full per-opponent-team breakdown, including a
`SUSPICIOUS` tag on any pair currently over the threshold.

All thresholds live under `cross-team-tracker:` in `config.yml`.

## Important note on integration

This runs as its **own independent plugin** next to UHC-Core — it does not
reach into UHC-Core's internal/compiled classes (I only had your config
files, not UHC-Core's source/API jar, so I couldn't safely hook its private
timer fields). Practically this means:

- Use `/vuhc timer start` right when your UHC game starts (e.g. bind it in
  whatever starts your game, or run it manually) to begin this plugin's own
  meetup/PvP countdown and broadcasts.
- `/vuhc meetup ...` and `/vuhc pvp force` control **this plugin's** timers.

If you actually want this wired directly into UHC-Core's own internal
deathmatch/PvP timers (so its scoreboard placeholders like `%deathmatch%`
and `%pvp%` reflect the change), send me UHC-Core's source code or its API
jar and I'll hook directly into its `GameManager`/timer classes instead of
running a parallel timer.

## Build

1. Requires Java 17+ and Maven.
2. Edit `pom.xml` and set the `spigot-api` version to match your server
   (e.g. `1.20.4-R0.1-SNAPSHOT`, `1.21-R0.1-SNAPSHOT`, etc).
3. From the project root:
   ```
   mvn clean package
   ```
4. Take `target/VyntricUhccoreV1.jar` and drop it into your server's
   `plugins/` folder alongside UHC-Core, then restart.

## Config

See `src/main/resources/config.yml` — branding prefix, the `server-ip`
shown on the scoreboard/tab footer, default timer lengths, and
login/alt-detector settings all live there and are copied to
`plugins/VyntricUhccoreV1/config.yml` on first run.

## Permissions

- `vyntricuhc.admin` (default: op) — all `/vuhc` commands
- `vyntricuhc.altalert` (default: op) — receive alt-join alerts
- `vyntricuhc.crossteam` (default: op) — use `/track` and `/vuhc track`
- `vyntricuhc.crossteamalert` (default: op) — receive automatic teaming alerts
- `vyntricuhc.bounty` (default: op) — place bounties (currently bundled under `vyntricuhc.admin`'s gate too)
- `vyntricuhc.stats` (default: true) — use `/vuhc stats` and `/vuhc top` without needing full admin

## New in this update

### Auto-spectator mode
Once the game is running (`/vuhc timer start`), a player who dies via a real
`PlayerDeathEvent` is dropped into spectator mode on respawn instead of a normal
survival respawn, so they can keep watching. Toggle with `spectator-mode.enabled`
in config.yml. This is separate from the existing leave-zombie system, which
already forces spectator for players eliminated while offline.

### Game stats / leaderboard persistence
Kills, deaths, and K/D ratio are now saved to a real database — SQLite by default
(zero setup, stored as `stats.db` in the plugin folder), or MySQL if you flip
`stats-database.type` to `mysql` and fill in the connection details. The driver
for both is bundled in the jar, so nothing extra needs installing on the server.

- `/vuhc stats [player]` — show a player's saved kills/deaths/K-D
- `/vuhc top <kills|deaths|kdr>` — top 10 leaderboard

### PlaceholderAPI support
If PlaceholderAPI is installed, the `vyntric` expansion registers automatically
on enable (see the console log). Use these in any scoreboard/tablist/chat plugin:

```
%vyntric_phase%                 waiting | meetup | pvp
%vyntric_meetup_time%            seconds left, raw number
%vyntric_meetup_time_formatted%  mm:ss / hh:mm:ss
%vyntric_pvp_time%               seconds left before pvp, 0 once enabled
%vyntric_pvp_time_formatted%     mm:ss / hh:mm:ss, or "Enabled"
%vyntric_pvp_status%             "Enabled" or "Disabled"
%vyntric_kills%                  target player's saved kill count
%vyntric_deaths%                 target player's saved death count
%vyntric_kdr%                    target player's K/D ratio, 2dp
%vyntric_bounty%                 item summary of target's bounty, "" if none
%vyntric_has_bounty%             "Yes" or "No"
```

### Bounty system
`/vuhc bounty <player>` opens a chest GUI for you to drop in the items you want
to offer as the reward. Close the chest to confirm — this broadcasts to
everyone that the target now has a bounty, along with a summary of the reward
items. Whoever lands the killing blow on that player receives everything in the
pool (dropped on the ground if their inventory is full). If nothing was placed
before closing, no bounty is set. Config: `bounty.enabled`, `bounty.gui-rows`,
`bounty.gui-title`, `bounty.stack-bounties` (merge into an existing bounty vs.
replace it).

### Config validation on startup
`config.yml` is checked on every enable — out-of-range or missing values (bad
timer lengths, an invalid `stats-database.type`, etc.) are logged as a warning
and replaced with a safe default for that session, instead of crashing the
plugin or a manager silently misbehaving. Your file on disk is left untouched;
fix the flagged lines at your convenience.
