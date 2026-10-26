# perf A/B: mr-functions-all

Solo runs (one server at a time), same seed and scenario, warm-up `tick sprint 200`, then 3 x `tick sprint 1200`; ms per tick as reported by the server. Exerciser on. Order: vanilla old new new old vanilla.

## per run

| # | config | median | min | max | sprints (ms/tick) |
|---|---|---|---|---|---|
| 1 | vanilla | 21.13 | 20.69 | 21.65 | 21.65, 21.13, 20.69 |
| 2 | old | 19.55 | 19.32 | 19.75 | 19.75, 19.32, 19.55 |
| 3 | new | 19.7 | 18.81 | 19.77 | 19.7, 19.77, 18.81 |
| 4 | new | 19.07 | 18.71 | 19.18 | 19.18, 18.71, 19.07 |
| 5 | old | 19.5 | 18.9 | 21.61 | 19.5, 18.9, 21.61 |
| 6 | vanilla | 18.07 | 17.3 | 18.42 | 18.42, 18.07, 17.3 |

## per configuration (both runs pooled)

| config | median of run medians | median of all sprints (min/max) | median of sprints 1-4 (min/max) | jar sha256 |
|---|---|---|---|---|
| vanilla | 19.6 | 19.555 (17.3/21.65) | - | - |
| old | 19.525 | 19.525 (18.9/21.61) | - | fca3ca87409825f9 |
| new | 19.385 | 19.125 (18.71/19.77) | - | 5f2c1a7e1971f44c |

ratio to vanilla (median of run medians):
- old: 0.996x
- new: 0.989x
