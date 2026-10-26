# perf A/B: bacap

Solo runs (one server at a time), same seed and scenario, warm-up `tick sprint 200`, then 5 x `tick sprint 1200`; ms per tick as reported by the server. Exerciser off. Order: vanilla pre-shard shard shard pre-shard vanilla.

## per run

| # | config | median | min | max | sprints (ms/tick) |
|---|---|---|---|---|---|
| 1 | vanilla | 8.49 | 3.33 | 9.56 | 8.49, 7.98, 9.56, 8.52, 3.33 |
| 2 | pre-shard | 6.35 | 2.33 | 6.44 | 6.35, 6.44, 6.3, 6.36, 2.33 |
| 3 | shard | 5.96 | 2.45 | 6.24 | 6.24, 5.96, 5.89, 5.99, 2.45 |
| 4 | shard | 6.01 | 2.53 | 6.14 | 6.11, 5.99, 6.01, 6.14, 2.53 |
| 5 | pre-shard | 6.11 | 2.51 | 6.19 | 6.19, 6.11, 6.13, 6.03, 2.51 |
| 6 | vanilla | 6.08 | 2.72 | 6.2 | 6.2, 6.05, 6.08, 6.15, 2.72 |

## per configuration (both runs pooled)

| config | median of run medians | median of all sprints (min/max) | median of sprints 1-4 (min/max) | jar sha256 |
|---|---|---|---|---|
| vanilla | 7.285 | 6.175 (2.72/9.56) | 7.09 (6.05/9.56) | - |
| pre-shard | 6.23 | 6.16 (2.33/6.44) | 6.245 (6.03/6.44) | 279c02180056ae0e |
| shard | 5.985 | 5.99 (2.45/6.24) | 6.0 (5.89/6.24) | a722b3bad7e3e863 |

ratio to vanilla (median of sprints 1-4):
- pre-shard: 0.881x
- shard: 0.846x

Notes: the last sprint of every run is about 2.5x cheaper than the first four (same in vanilla, so not an mcfc effect). The first vanilla run is slower than the second one (cold start), which an A B C C B A order only partly cancels.
