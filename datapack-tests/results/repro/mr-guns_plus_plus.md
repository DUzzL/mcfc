# mr-guns_plus_plus: CRASH

- kind: function, packs: mr-guns_plus_plus
- mcfc final state: None (after load: None)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:45:08)
- compile: None/None functions compiled, partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- decisive mcfc error: java.lang.IllegalStateException: Failed to parse thepa:gun_0 from pack file/mr-guns_plus_plus.zip
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 0; not re-run (no findings)
- exceptions (non-ignorable lines): base1=91, base2=91, mcfc=91
- note: base1 run failed: TimeoutError: log pattern not seen: Loaded \d+ advancements
- note: base2 run failed: TimeoutError: log pattern not seen: Loaded \d+ advancements
- note: mcfc run failed: TimeoutError: log pattern not seen: Loaded \d+ advancements

## exceptions base1
- java.lang.IllegalStateException x89: Failed to parse thepa:gun_0 from pack file/mr-guns_plus_plus.zip
- java.util.concurrent.CompletionException x1: net.minecraft.ReportedException: Registry Loading
- net.minecraft.ReportedException x1: Registry Loading

## exceptions base2
- java.lang.IllegalStateException x89: Failed to parse thepa:gun_0 from pack file/mr-guns_plus_plus.zip
- java.util.concurrent.CompletionException x1: net.minecraft.ReportedException: Registry Loading
- net.minecraft.ReportedException x1: Registry Loading

## exceptions mcfc
- java.lang.IllegalStateException x89: Failed to parse thepa:gun_0 from pack file/mr-guns_plus_plus.zip
- java.util.concurrent.CompletionException x1: net.minecraft.ReportedException: Registry Loading
- net.minecraft.ReportedException x1: Registry Loading

## exceptions perfbase
- java.lang.IllegalStateException x89: Failed to parse thepa:gun_0 from pack file/mr-guns_plus_plus.zip
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://sessionserver.mojang.com/session/minecraft/profile/c8b0ccc87cd3306481b83317aa73ed1a?unsigned
- java.net.SocketTimeoutException x1: Read timed out
- java.util.concurrent.CompletionException x1: net.minecraft.ReportedException: Registry Loading
- net.minecraft.ReportedException x1: Registry Loading

## Investigate
- Pack cannot load in vanilla 26.3: `Failed to get element thepa:left_click_detect from registry minecraft:enchantment` in 65 recipes `data/thepa/recipe/gun_*.json` (`result.components.enchantments`) -> registry loading fails, `/reload` never reports "Loaded N advancements", all variants (base1/base2/mcfc) fail identically. Not an mcfc issue.
- Workaround run without recipes: see `mr-guns_plus_plus-norecipe.md` (ad-hoc zip `cache\packs\mr-guns_plus_plus-norecipe.zip`, 65 recipe files removed).
