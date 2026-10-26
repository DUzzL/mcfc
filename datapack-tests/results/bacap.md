# bacap: DIFF

- kind: function, packs: bacap
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:35:24)
- compile: 5435/5436 functions compiled (100.0%), partial fallback commands: 98
- whole-function fallbacks: 0, compile failures: 1, runtime disables: 0
- findings: confirmed 51, flaky 1, low-confidence 0, noise paths 3627
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 34.52 ms (min 29.88, max 36.18); mcfc median 31.54 ms (min 30.01, max 34.53)
- mcfc debug: 5423 compiled | 1 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.8293
- exerciser (mcfc): ran 5418 of 5436 candidate functions (macro skipped 8, name skipped 10, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Missing space after argument @ function <functions:function>: 73
- Missing space after argument @ advancement grant <targets:entity_selector[players]> only <advancement:resource_location>: 17
- Failed to parse any options @ scoreboard objectives: 8

## confirmed findings (first 20)
- [final] `entities.minecraft:overworld.list[minecraft:item#836].pos[0]`: base=0.54 | mcfc=0.53
- [final] `entities.minecraft:overworld.list[minecraft:item#837].pos[0]`: base=0.54 | mcfc=0.53
- [final] `entities.minecraft:overworld.list[minecraft:item#838].pos[0]`: base=0.54 | mcfc=0.53
- [final] `entities.minecraft:overworld.list[minecraft:item#839].pos[0]`: base=0.54 | mcfc=0.53
- [final] `entities.minecraft:overworld.list[minecraft:item#840].pos[0]`: base=0.54 | mcfc=0.53
- [final] `entities.minecraft:overworld.list[minecraft:item#841].pos[0]`: base=0.54 | mcfc=0.53
- [final] `entities.minecraft:overworld.list[minecraft:item#842].pos[0]`: base=0.54 | mcfc=0.53
- [final] `players.DPTester1.inventory[21].components`: base="{}" | mcfc="{\"minecraft:custom_data\":{Trophy:1b},\"minecraft:custom_model_data\":{floats:[131.0f]},\"minecraft:custom_name\":{bold:1b,color:\"#B98E5B\",italic:
- [final] `players.DPTester1.inventory[21].count`: base=16 | mcfc=4
- [final] `players.DPTester1.inventory[21].id`: base="minecraft:blue_egg" | mcfc="minecraft:clock"
- [final] `players.DPTester1.inventory[22].count`: base=16 | mcfc=60
- [final] `players.DPTester1.inventory[22].id`: base="minecraft:brown_egg" | mcfc="minecraft:amethyst_shard"
- [final] `players.DPTester1.inventory[23].components`: base="{\"minecraft:custom_data\":{Trophy:1b},\"minecraft:custom_model_data\":{floats:[131.0f]},\"minecraft:custom_name\":{bold:1b,color:\"#B98E5B\",italic: | mcfc="{\"minecraft:custom_data\":{Trophy:1b},\"minecraft:custom_model_data\":{floats:[131.0f]},\"minecraft:custom_name\":{bold:1b,color:\"#E48360\",italic:
- [final] `players.DPTester1.inventory[23].count`: base=4 | mcfc=1
- [final] `players.DPTester1.inventory[23].id`: base="minecraft:clock" | mcfc="minecraft:crossbow"
- [final] `players.DPTester1.inventory[24].components`: base="{}" | mcfc="{\"minecraft:custom_data\":{Trophy:1b},\"minecraft:custom_model_data\":{floats:[131.0f]},\"minecraft:custom_name\":{bold:1b,color:\"#E48360\",italic:
- [final] `players.DPTester1.inventory[24].count`: base=60 | mcfc=1
- [final] `players.DPTester1.inventory[24].id`: base="minecraft:amethyst_shard" | mcfc="minecraft:crossbow"
- [final] `players.DPTester1.inventory[25].components`: base="{\"minecraft:custom_data\":{Trophy:1b},\"minecraft:custom_model_data\":{floats:[131.0f]},\"minecraft:custom_name\":{bold:1b,color:\"#E48360\",italic: | mcfc="{\"minecraft:custom_data\":{Trophy:1b},\"minecraft:custom_model_data\":{floats:[132.0f]},\"minecraft:custom_name\":{bold:1b,color:\"#68FFFF\",italic:
- [final] `players.DPTester1.inventory[25].count`: base=1 | mcfc=4

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `entities.minecraft:overworld.list[minecraft:item#464].pos[0]`

## Investigate
- Not physics noise: `scoreboard.scores.DPTester1.bac_pigling` base=2 mcfc=3, `...bac_vault_hunter_count` base=2 mcfc=3 (confirmed in attempt2); inventory[21..35] shifted (mcfc misses blue_egg/brown_egg x16, has cod x48/echo_shard x64 instead) because reward order/side effects differ.
- Minimal repro: scenario `bacap-grantall` (only `advancement grant DPTester1 everything`, exerciser skipped), run `python harness\run_pack.py bacap-grantall --zip ...\cache\packs\bacap.zip --no-perf --no-confirm`: bac_pigling base=1 mcfc=2, bac_vault_hunter_count base=1 mcfc=2, `players.DPTester1.advancementsDone` base=1295 mcfc=1296 (extra: `blazeandcave:technical/consume_food_null`).
- Mechanism guess: reward function of a technical advancement revokes itself (`advancement/count/give_piglin_gold.mcfunction`: `advancement revoke @s only blazeandcave:technical/give_piglin_gold`, same for `count/vault_hunter`); with grant-everything the advancement stays granted in mcfc, so `one_second_timer.mcfunction:158` (`execute as @a[advancements={blazeandcave:technical/give_piglin_gold=true}] ... run function ...count/give_piglin_gold`) fires a second time.
- Item-entity `pos[0]` diffs 0.54 vs 0.53 etc.: physics noise, not counted.
- Compile failure: `bacap_rewards:advancement_made_macro` ("`return` inside an unparsed command would be swallowed").
- Partial fallback "Missing space after argument @ function <functions:function>" (73 cmds, all bacap_rewards), likely `function bacap_rewards:advancement_made_macro {adv_id:"...",reward_id:"...",tier:"goal"}` (e.g. `bacap_rewards:enchanting/fiery` line 2; counts only in log).
