# mr-vanilla-refresh: CRASH

- kind: function, packs: mr-vanilla-refresh
- mcfc final state: None (after load: None)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:45:08)
- compile: None/None functions compiled, partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- decisive mcfc error: java.lang.IllegalStateException: Failed to parse vanilla_refresh:biome/badlands from pack file/mr-vanilla-refresh.zip
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 0; not re-run (no findings)
- exceptions (non-ignorable lines): base1=702, base2=702, mcfc=702
- note: mr-vanilla-refresh: pack_format patched (manifest): pack_format 71 -> 121; min_format 71 -> 121; max_format 9999 -> 121; supported_formats {'min_inclusive': 71, 'max_inclusive': 9999} -> {'min_inclusive': 121, 'max_inclusive': 121}
- note: base1 run failed: TimeoutError: log pattern not seen: Loaded \d+ advancements
- note: base2 run failed: TimeoutError: log pattern not seen: Loaded \d+ advancements
- note: mcfc run failed: TimeoutError: log pattern not seen: Loaded \d+ advancements

## exceptions base1
- java.lang.IllegalStateException x699: Failed to parse vanilla_refresh:biome/badlands from pack file/mr-vanilla-refresh.zip
- com.google.gson.JsonParseException x1: Pack key supported_formats is deprecated starting from pack format 82. Remove supported_formats from your pack.mcmeta.
- java.util.concurrent.CompletionException x1: net.minecraft.ReportedException: Registry Loading
- net.minecraft.ReportedException x1: Registry Loading

## exceptions base2
- java.lang.IllegalStateException x699: Failed to parse vanilla_refresh:biome/badlands from pack file/mr-vanilla-refresh.zip
- com.google.gson.JsonParseException x1: Pack key supported_formats is deprecated starting from pack format 82. Remove supported_formats from your pack.mcmeta.
- java.util.concurrent.CompletionException x1: net.minecraft.ReportedException: Registry Loading
- net.minecraft.ReportedException x1: Registry Loading

## exceptions mcfc
- java.lang.IllegalStateException x699: Failed to parse vanilla_refresh:biome/badlands from pack file/mr-vanilla-refresh.zip
- com.google.gson.JsonParseException x1: Pack key supported_formats is deprecated starting from pack format 82. Remove supported_formats from your pack.mcmeta.
- java.util.concurrent.CompletionException x1: net.minecraft.ReportedException: Registry Loading
- net.minecraft.ReportedException x1: Registry Loading

## exceptions perfbase
- java.lang.IllegalStateException x699: Failed to parse vanilla_refresh:biome/badlands from pack file/mr-vanilla-refresh.zip
- com.google.gson.JsonParseException x1: Pack key supported_formats is deprecated starting from pack format 82. Remove supported_formats from your pack.mcmeta.
- java.util.concurrent.CompletionException x1: net.minecraft.ReportedException: Registry Loading
- net.minecraft.ReportedException x1: Registry Loading

## Investigate
- Pack (v1.4.31, declared 26.2) cannot load in vanilla 26.3: registry loading errors for 201 advancements, 138 predicates, 10 item_modifiers, e.g. `Not a JSON object: [{"condition":"minecraft:reference","name":"vanilla_refresh:setting/biome"}...` (advancement `player` conditions as list), `No key recipes in MapLike[{"recipe_id":"anvil"}]` (recipe_crafted), `Failed to get element minecraft:is_explosion from registry minecraft:damage_type`, overlay `26.1` predicates `time_check` without `type`. All variants time out waiting for "Loaded N advancements". Not an mcfc issue.
- Workaround run with the failing registry elements deleted (also in overlay `26.1`): `mr-vanilla-refresh-sanitized.md` (ad-hoc zip `cache\packs\mr-vanilla-refresh-sanitized.zip`; many functions then fail to parse in vanilla because they reference deleted predicates, 832 of 859 exerciser calls return errors, so coverage is low).
