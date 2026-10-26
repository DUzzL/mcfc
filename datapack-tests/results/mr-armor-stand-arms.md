# mr-armor-stand-arms: FALLBACK-ISSUE

- kind: function, packs: mr-armor-stand-arms
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:45:08)
- compile: 18/20 functions compiled (90.0%), partial fallback commands: 7
- whole-function fallbacks: 1, compile failures: 1, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 2; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.32 ms (min 0.26, max 0.51); mcfc median 0.3 ms (min 0.19, max 0.47)
- mcfc debug: 13 compiled | 1 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.9895
- exerciser (mcfc): ran 13 of 20 candidate functions (macro skipped 5, name skipped 2, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 5
- Unknown command: dialog: 1
- Unknown command: say: 1

## whole-function fallbacks (first 15)
- armor_stand_arms:config/save_config: verification failed: Bad type on operand stack in mcfc/batch/Batch$0::fn_armor_stand_arms_config_save_config_5262b0c7__instanced(CommandSourceStack,String,int) @21 (java/lang/String is not assignable from integer_type)

## Investigate
- Compiler bug: `armor_stand_arms:config/save_config` falls back as a whole function: "verification failed: Bad type on operand stack in mcfc/batch/Batch$0::fn_armor_stand_arms_config_save_config_5262b0c7__instanced(CommandSourceStack,String,int) @21 (java/lang/String is not assignable from integer_type)". Pack lines (macro argument `dialog_right_click_arms` used as raw value, as score value and quoted): `$data modify storage armor_stand_arms:root data.right_click_arms set value $(dialog_right_click_arms)` / `$scoreboard players set &right_click_arms armor_stand_arms.config $(dialog_right_click_arms)` / `data modify storage armor_stand_arms:root data.dialog_right_click_arms set value "$(dialog_right_click_arms)"`.
- Compile failure: `armor_stand_arms:app/arms/right_click/change_arms` ("`return` inside an unparsed command would be swallowed").
