<div align="center">
  <h1>mCases</h1>
  <p>Reliable, transparent reward cases for modern Minecraft servers.</p>

  <p>
    <a href="https://papermc.io/software/paper"><img alt="Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
  </p>
</div>

## What it does

- Provides configurable virtual cases and per-case keys.
- Shows every configured reward and its exact base chance before opening.
- Chooses the reward on the server before animation; closing a GUI cannot reroll it.
- Atomically consumes a key, updates the pity counter and journals the reward in H2.
- Recovers undelivered rewards when a player rejoins after a crash or disconnect.
- Supports weighted rarities, configurable pity and console-command rewards.
- Exposes case definitions, chances and key balances through Bukkit Services.

## Requirements

- Java 25
- Paper, Purpur or Folia 26.2

Vault, mCraft, mBadges, mColor and PlaceholderAPI are optional integration targets. The base plugin does not require them.

## Install

1. Put `mCases-1.0.0.jar` in `plugins`.
2. Start the server once.
3. Edit `plugins/mCases/cases.yml` and reload with `/case reload`.

Reward commands use `{player}` and `{uuid}` placeholders. Define only commands that are safe to execute once. The included economy command is an example and requires a compatible economy command provider.

## Commands

- `/case` — list cases.
- `/case <id>` — preview rewards, odds and open a case.
- `/case key give <player> <case> <amount>` — give virtual keys.
- `/case simulate <case> <rolls>` — audit the effective distribution, including pity.
- `/case reload` — validate and reload configuration.

Permissions are `mcases.use`, `mcases.simulate`, `mcases.reload` and `mcases.admin`.

## Reliability model

An opening is one database transaction: lock balance and pity state, select a reward, decrement the key, update pity, and insert an undelivered journal record. Commands run only after that transaction commits. The journal record is marked delivered afterwards. If the player disconnects first, the already selected reward is recovered instead of rolled again.

H2 work runs on a dedicated virtual thread and never performs database I/O on a server tick thread.

## Build

```bash
./gradlew clean build
```

The plugin JAR is written to `build/libs`; API artifacts are written to `api/build/libs`.

Licensed under the MIT License.
