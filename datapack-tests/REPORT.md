# Datapack-Testbericht: Function Compiler (mcfc) auf Minecraft 26.3

Stand: 2026-10-27. Getesteter Build: `mcfc-0.4.0-alpha.2+26.3-fabric.jar`, Commit `1a611fce` (eingefroren als `mcfc-1a611fce.jar`, sha256 `0bb5961a…`), fünf Läufe nach Bug 7 mit Commit `bfaa7efd`, 14 Läufe nach Fix 13 mit dem Code von Commit `2dc36058` (vor dem Commit gebaut und eingefroren als `mcfc-retfix.jar`, sha256 `5f2c1a7e…`) und Start-Tick-Angleichung im Harness, Fabric Loader 0.19.5, Carpet für Fake-Player, keine Fabric API auf dem Server.

## Zusammenfassung

- 26 Datapacks (BACAP, 12 Vanilla Tweaks, 13 Modrinth-Packs inkl. 6 Worldgen-Packs) und 6 Kombinationen wurden differenziell getestet: zwei Vanilla-Baselines gegen mcfc, gleiche Welt, gleicher Seed, eingefrorene Ticks, Fake-Player, aktiv ausgelöste Funktionen, Zustandsvergleich per Dump.
- Im finalen Lauf erreicht mcfc in allen 32 Läufen `READY_ON`, keine kompilierte Funktion wirft zur Laufzeit, kein mcfc-Fehler im Log. In den 26 Einzelpacks sind alle 8698 Funktionen kompiliert (vorher 8682; die letzten 16 liefen wegen möglichem `return` in nicht parsebaren Macro-Zeilen ganz über Vanilla, Fix 13), 821 einzelne Befehle innerhalb kompilierter Funktionen laufen weiter über Vanilla.
- Gefunden und behoben wurden 7 Verhaltens-, Lade- bzw. Stabilitäts-Bugs (u. a. ganzer Ladevorgang abgebrochen bei großen Packs, falsche Ergebnisse bei `UUID[i]` und `scoreboard players reset <targets>`, falsche Reihenfolge von Advancement-Rewards, Server-Hänger bei endloser Rekursion) sowie 6 Ursachen für unnötige Vanilla-Fallbacks. Jeder Fix hat einen Regressionstest; die Testsuite wuchs von 60 auf 75 Gametests.
- Alle verbleibenden Abweichungen im finalen Lauf sind als Rauschen (Physik, Worldgen, zufällige Mob-Ausrüstung) oder dokumentiertes Verhalten (`maxCommandChainLength`) eingeordnet. Die früher offene Team-Abweichung in `worldgen-stack` und die `gameTime`-Abweichungen kamen von ungleich vielen Start-Ticks; seit der Harness alle Varianten auf dieselbe Spielzeit bringt, treten sie nicht mehr auf. Die Mehrkosten von `mr-functions-all` sind in einer A/B-Messung nicht reproduzierbar (siehe Performance).
- Die automatische Datenerfassung und der Upload (Reporter) wurden vollständig entfernt.

## Testaufbau (kurz)

Details: `datapack-tests/README.md`, Architektur: `datapack-tests/ARCHITECTURE_NOTES.md`.

- Pro Pack drei bis vier Dedicated Server: `base1` und `base2` (Vanilla, parallel, für Rauschen), `mcfc` (allein, mit Performance-Messung), `perfbase` (Vanilla allein, Performance und zusätzliche Rauschbasis). Bei Befunden automatisch ein zweiter Durchgang; nur Befunde aus beiden Durchgängen gelten als bestätigt.
- Ablauf: Welt mit festem Seed (Funktionspacks: flach, ohne Strukturen; Worldgen: normal), `tick freeze`, `tick step` bis Spielzeit 20 in allen Varianten (seit den mit ⁴ markierten Läufen), Gamerules (`max_command_sequence_length` = 2147483647 in allen Varianten, Mob-Spawning und Wetter aus, `random_tick_speed` 0), zwei Carpet-Fake-Player, Pack laden, bei mcfc auf `READY_ON` warten, `#minecraft:load`, Szenario je Pack (`datapack-tests/scenarios/`), Exerciser ruft alle nicht-Macro-Funktionen einmal auf, 200 Ticks per `tick step`, Dump.
- Dump: eigener Server-Mod `dpdump` (`datapack-tests/dumper/`): Scoreboards, Teams, Storage, Bossbars, Gamerules, Spieler inkl. Inventar und Advancements, Entities (normalisierte UUIDs), Block-Hashes je Chunk-Spalte, Funktionen, Tags, geplante Funktionen.
- Worldgen-Packs: zusätzlicher Dump direkt nach der Generierung (`gen`), Vergleich der Block-Hashes in festen Boxen der drei Dimensionen.
- Performance: `tick sprint 1200` nach Aufwärmen, Median aus drei Messungen, nur ohne parallel laufenden Server.

## Ergebnisse je Pack (finaler Lauf, Jar `mcfc-1a611fce.jar`)

Spalten: Funktionen gefunden/kompiliert, Befehle mit Vanilla-Fallback innerhalb kompilierter Funktionen, ganze Funktionen über Vanilla; Befunde bestätigt/flaky/niedrige Konfidenz (Items, XP-Orbs, fallende Blöcke); MSPT Vanilla → mcfc (ms/Tick, Median).

| Pack | Art | Status | Funktionen | Fallback-Befehle | ganze Fkt. Vanilla | Befunde | MSPT | Bewertung |
|---|---|---|---|---|---|---|---|---|
| bacap ³ | Funktion | OK | 5436/5436 | 98 | 0 | 0/0/37 | 32.97 → 33.15 | ok |
| mr-veinminer ⁴ | Funktion | OK | 33/33 | 36 | 0 | 0/0/0 | 0.34 → 0.29 | ok (vorher 6 Funktionen über Vanilla) |
| mr-tree-vein-miner ⁴ | Funktion | OK | 70/70 | 6 | 0 | 0/0/4 | 0.39 → 0.35 | ok (vorher 2 Funktionen über Vanilla) |
| mr-timber-strike ⁴ | Funktion | OK | 51/51 | 83 | 0 | 0/0/31 | 0.38 → 0.30 | ok (vorher 1 Funktion über Vanilla) |
| mr-gardeners-dream | Funktion | OK | 54/54 | 23 | 0 | 0/0/0 | 0.23 → 0.24 | ok |
| mr-spawn-animations | Funktion | OK | 87/87 | 8 | 0 | 0/0/0 | 0.43 → 0.40 | ok |
| mr-orbital-strike-cannon ¹ | Funktion | DIFF | 88/88 | 1 | 0 | 1067/385/209 | 8.94 → 8.58 | Rauschen: TNT-Physik (Positionen, `fuse`) |
| mr-armor-stand-arms ⁴ | Funktion | OK | 20/20 | 7 | 0 | 0/0/0 | 0.33 → 0.42 | ok (vorher 1 Funktion über Vanilla) |
| mr-terralith | Worldgen | DIFF | 3/3 | 1 | 0 | 10/16/173 | 1.36 → 1.10 | Rauschen: Worldgen-Spalten, Minecart aus Mineshaft |
| mr-tectonic | Worldgen | OK | 0/0 | 0 | 0 | 0/1/1 | 0.52 → 0.43 | ok |
| mr-incendium ⁴ | gemischt | DIFF | 2314/2314 | 255 | 0 | 1/42/4 | 1.17 → 1.35 | Rauschen: Tag-Liste eines nach Position sortierten Piglin Brute (Anzahl `in.netherite` in Vanilla-Spanne), Worldgen-Spalten |
| mr-nullscape ⁴ | Worldgen | DIFF | 9/9 | 17 | 0 | 1/15/7 | 1.10 → 1.43 | Rauschen: Worldgen-Spalte (`gen`-Dump) |
| mr-structory | Worldgen | DIFF | 1/1 | 0 | 0 | 3/3/1 | 0.62 → 0.69 | Rauschen: Worldgen-Spalten |
| mr-dungeons-and-taverns ⁴ | gemischt | DIFF | 65/65 | 213 | 0 | 5/27/7 | 0.68 → 0.69 | Rauschen: Worldgen-Spalten (`gen`-Dump); Bienen (mcfc 16 bzw. 13, Vanilla 14) stammen aus Nestern in Chunk (1,-4) bzw. (1,-8), deren Blöcke schon im `gen`-Dump abweichen; die Pack-Funktionen ändern keine Blöcke |
| vt-multiplayer-sleep | Funktion | OK | 33/33 | 17 | 0 | 0/0/0 | 0.29 → 0.26 | ok |
| vt-cauldron-concrete | Funktion | OK | 2/2 | 0 | 0 | 0/0/0 | 0.24 → 0.25 | ok |
| vt-afk-display | Funktion | OK | 5/5 | 0 | 0 | 0/0/0 | 0.24 → 0.18 | ok |
| vt-coordinates-hud | Funktion | OK | 9/9 | 0 | 0 | 0/0/0 | 0.37 → 0.27 | ok |
| vt-durability-ping | Funktion | OK | 106/106 | 0 | 0 | 0/0/0 | 0.23 → 0.28 | ok |
| vt-track-statistics | Funktion | OK | 4/4 | 0 | 0 | 0/0/0 | 0.26 → 0.22 | ok |
| vt-track-raw-statistics | Funktion | OK | 1/1 | 0 | 0 | 0/0/0 | 0.20 → 0.21 | ok |
| vt-armor-statues | Funktion | OK | 72/72 | 1 | 0 | 0/0/0 | 0.25 → 0.29 | ok (vorher DIFF, Bug 2) |
| vt-fast-leaf-decay | Funktion | OK | 23/23 | 0 | 0 | 0/0/0 | 0.27 → 0.23 | ok |
| vt-graves ⁴ | Funktion | OK | 139/139 | 51 | 0 | 0/0/0 | 0.26 → 0.31 | ok (vorher 3 Funktionen über Vanilla) |
| vt-homes ³ | Funktion | OK | 54/54 | 4 | 0 | 0/0/0 | 0.30 → 0.26 | ok (vorher 4 Funktionen über Vanilla) |
| vt-tpa ⁴ | Funktion | OK | 19/19 | 0 | 0 | 0/0/0 | 0.19 → 0.23 | ok (vorher DIFF: `gameTime` ±4 durch Start-Ticks) |
| **Kombinationen** | | | | | | | | |
| bacap-vt4 ³ | Kombi | OK | 5537/5537 | 119 | 0 | 0/0/122 | 19.75 → 20.43 | ok (mit 1a611fce: Server-Hänger, Bug 7) |
| vt-all ⁴ | Kombi | OK | 467/467 | 73 | 0 | 0/0/0 | 0.45 → 0.49 | ok |
| guns-terralith ² ⁴ | Kombi | DIFF | 54/54 | 84 | 0 | 3/8/9 | 2.22 → 1.95 | Rauschen: Worldgen-Spalten (`gen`-Dump) |
| mr-functions-all ⁴ | Kombi | OK | 5839/5839 | 262 | 0 | 0/0/11 | 23.88 → 21.07 | ok (MSPT siehe Performance) |
| worldgen-stack ⁴ | Kombi | DIFF | 2327/2327 | 273 | 0 | 3/6/11 | 1.99 → 1.83 | Rauschen: Worldgen-Spalten (`gen`-Dump); Team-Abweichung geklärt (Start-Ticks) |
| kitchen-sink ⁴ | Kombi | OK | 6060/6060 | 297 | 0 | 0/7/75 | 36.01 → 30.26 | ok |

¹ `pack_format` auf 26.3 (121.0) angepasst, Inhalt unverändert.
² Guns++ lädt auf 26.3 auch in Vanilla nicht und wurde in dieser Kombination durch Timber Strike ersetzt (Name der Kombination beibehalten).
³ Mit `mcfc-bfaa7efd.jar` und dem endgültigen Harness wiederholt (Kombinationen übernehmen die `exerciserSkip`-Listen ihrer Packs; vorher liefen in mr-functions-all die Orbital-Nukes im Exerciser). Der Unterschied zu `1a611fce` betrifft nur den Abbruch am Befehlslimit; in allen übrigen Läufen des finalen Durchgangs trat das Limit in keiner Variante auf. Im bacap-vt4-Lauf meldet mcfc das Limit zweimal (innerer und äußerer Kontext), Vanilla einmal.
⁴ Mit `mcfc-retfix.jar` (Fix 13) und dem Harness mit Start-Tick-Angleichung (`--start-ticks 20`) wiederholt: alle Packs, deren Funktionen Fix 13 betrifft, die Kombinationen mit ihnen sowie vt-tpa und Dungeons and Taverns (vorher `gameTime`-Abweichungen).

Nicht lauffähig und übersprungen (nur `pack_format`-Anpassung erlaubt, das reicht nicht):
- **Guns++ 5.9.6**: alle 65 Rezepte verweisen auf das nicht existierende Enchantment `thepa:left_click_detect`, 85 Give-Funktionen laden nicht.
- **Vanilla Refresh 1.4.31** (nur für 26.2 deklariert): 201 Advancements, 138 Predicates und 10 Item-Modifier scheitern am Registry-Laden.
- Inhaltlich veränderte Kopien beider Packs (`-norecipe`, `-sanitized`) wurden nur zur Fehlersuche genutzt (Guns++ führte zu Bug 3) und stehen nicht in der Tabelle.

Ergebnisdateien: `datapack-tests/results/<id>.md|json`, Gesamttabelle `results/_summary.md`, Läufe zur Fehlersuche mit älteren Builds in `results/repro/`. Die Diagnose-Dateien je Pack (`<id>.diagnostics.tsv`, mit Befehlstexten aus den Packs) bleiben aus Lizenzgründen außerhalb des Repos; im Repo liegt nur die Auswertung `results/_unsupported.md|json`.

## Gefundene und behobene Bugs

| # | Bug | gefunden mit | Fix (Commit) | Regressionstest |
|---|---|---|---|---|
| 1 | Packs mit rund 20 000 verschiedenen Konstanten (NBT, Texte, IDs) überliefen den Constant Pool der einen generierten Datenklasse (`IllegalArgumentException: 65636 is not a valid index`). Der ganze Ladevorgang brach ab, alle Funktionen liefen über Vanilla. | Orbital Strike Cannon | Konstanten auf Shard-Klassen (max. 4096 Felder je Art) und Loader-Klassen verteilt, Cache-Format v2.8 (`d0d3612a`) | `ManyConstantsTest` (24 000 Compounds) |
| 2 | `path[i]` auf Byte-/Int-/Long-Arrays (z. B. `data get entity @p UUID[0]`) schlug im kompilierten Code fehl, `execute store result` speicherte 0. | VT Armor Statues | Indexzugriff über `CollectionTag` wie Vanillas `IndexedElementNode` (`bcf1b7f1`) | `tests/data/get/entity_uuid_index.mctest` |
| 3 | `scoreboard players reset <targets>` ohne Objective tat nichts und scheiterte (Parser las ein leeres Objective). | Guns++ (Tick-Funktion) | Score-Accessor verlangt ein Objective (`5892d290`) | `tests/scoreboard/players/reset_all_objectives.mctest` |
| 4 | Eine kompilierte Funktion, die während eines laufenden Vanilla-Befehls gestartet wird (Advancement-Reward bei `advancement grant`, Chat/RCON/Commandblock), lief sofort statt danach. BACAP: Advancements blieben gewährt, `bac_pigling` wurde doppelt gezählt. | BACAP | Aufruf wird wie in Vanilla eingereiht (`a105aac0`) | `RewardOrderTests` |
| 5 | Kompiliertes `advancement grant` führte Rewards zwischen zwei Kriterien aus; ein Advancement mit alternativen Kriterien, dessen Reward es zurücknimmt, wurde zweimal belohnt. | Analyse zu Bug 4 | Award-Schleife in eigenem Ausführungskontext (`f713cacf`) | `RewardOrderTests` (zweiter Fall) |
| 6 | Ein Macro-Argument, das als Text und als Zahl genutzt wird, ließ die Funktion an der Bytecode-Verifikation scheitern (beide Typen teilten ein Register). | Armor Stand Arms | Macro-Parameter nach Name und Typ getrennt (`e45665e4`) | `tests/function/macro_same_argument_two_types.mctest` |
| 7 | Endlose Rekursion (Funktion ruft sich zweimal selbst auf) ließ den Server hängen: Vanilla stoppt am Befehlslimit die ganze Kette, kompilierter Code verlor nur den verschachtelten Vanilla-Aufruf und startete den nächsten. | BACAP + VT Homes (homes:list_home → homes:bubble) | Abbruch wickelt kompilierte Frames ab und stoppt den äußeren Vanilla-Kontext (faa7efd) | ChainLimitTests |

Weitere Fehlerquellen, die nur zu unnötigen Vanilla-Fallbacks führten (Verhalten war korrekt, aber langsamer):

| # | Ursache | Effekt vorher | Fix (Commit) | Test |
|---|---|---|---|---|
| 8 | NBT-Listen mit gemischten Elementtypen (seit 1.21.5 erlaubt) wurden abgelehnt. | 709 von 717 Fallbacks in Orbital Strike Cannon | `63284804` | `tests/parsers/nbt_mixed_list.mctest` |
| 9 | Unter `execute store`/`return run` hatten Fallback-Befehle und `kill`, `tellraw`, `schedule function` kein Ergebnis: ganze Funktion über Vanilla. | 15 Funktionen in VT Graves und Homes | `d4d9b55f` | `tests/execute/store/fallback_results.mctest` |
| 10 | Ein nicht parsebarer Befehl hinter `execute … run`/`return run` machte die ganze Zeile (mit `return`: die ganze Funktion) zum Fallback. | 52 Funktionen ganz über Vanilla (u. a. `return run rotate`); mit Fix 12 sank die Zahl in den Einzelpacks auf 16 | Nur der innere Befehl fällt zurück (`74c8a07d`) | `tests/return/run_unparsed_command.mctest` |
| 11 | Inline-Predicates `execute if predicate {…}` nicht unterstützt. | 226 Befehle in Dungeons and Taverns | `0abdd68c` | `tests/execute/conditions/inline_predicate.mctest` |
| 12 | Macro-Platzhalter im Block-Predicate von `execute if|unless block`. | 75 Befehle (Vein Miner, Timber Strike) | `1a611fce` | `tests/execute/conditions/if_block_macro.mctest` |
| 13 | Eine nicht parsebare Zeile, die `return` enthalten kann (wörtlich oder über einen Macro-Platzhalter vor dem `run`, z. B. `$execute unless data storage veinminer:data category.$(category) run return run …`, `$execute positioned $(x) $(y) $(z) run function …`), schickte die ganze Funktion zu Vanilla, weil ein `return` im einzeln ausgeführten Fallback verschluckt worden wäre. Die Zeile läuft jetzt allein über Vanilla in einem Frame, dessen `return` beobachtet wird; die kompilierte Funktion gibt denselben Wert bzw. Fehlschlag zurück. Unter `execute store`/`return run` bleibt es beim Fallback der ganzen Funktion. | die letzten 16 Funktionen der Einzelpacks ganz über Vanilla (Vein Miner 6, Tree Vein Miner 2, Graves 3, Incendium 2, Timber Strike, Armor Stand Arms, Nullscape je 1) | `2dc36058` | `tests/return/macro_fallback_line.mctest` (Macro-Zeilen), `tests/return/fallback_line_static_and_forked.mctest` (ohne Macro, im kompilierten Fork); beide scheitern ohne den Fix an „loaded function count != total function count“ |

Verbesserungen an der Testsuite: `no_fallback` (Test schlägt fehl, wenn ein Befehl über Vanilla läuft), Laufzeitfehler kompilierter Funktionen lassen Tests jetzt fehlschlagen (vorher verdeckt, weil Vanilla übernimmt; Opt-out `allow_runtime_error`), Fehlermeldungen zeigen beide Scoreboard-Zustände, `generated/diagnostics.tsv` listet alle Fallbacks je Funktion mit Befehlstext. Format: `docs/tests.md`.

## Offene Probleme

1. **BACAP: im mcfc-Lauf 10–13 Item-Entities weniger** als die Spanne der Vanilla-Läufe (1210/1213 gegen 1223–1236), Spieler, Scores und Advancements gleich. Als Physik-/Loot-Rauschen eingestuft, Ursache nicht belegt.
2. **Mod-interne Ausführung kompilierter Funktionen ohne Vanilla-Kontext**: Rewards, die durch andere native Befehle als `advancement grant` ausgelöst werden (z. B. `give` → `inventory_changed`), laufen weiterhin sofort statt nach dem Befehl. Bei Vanilla-Fallback-Befehlen ist die Reihenfolge korrekt.
3. **`maxCommandChainLength`**: wie dokumentiert ignoriert. Bei unendlicher Rekursion (z. B. `homes:bubble`, wenn einzeln aufgerufen) zählt kompilierter Code keine Befehle; die Kette endet erst, wenn die Rekursion an Vanilla übergeben wird und dort das Limit erreicht. Seit Bug 7 endet dann die ganze Kette wie in Vanilla, die Zahl der bis dahin ausgeführten Befehle (und damit z. B. Zählerstände) unterscheidet sich aber.
4. **Vanilla-gleiche Exceptions deaktivieren kompilierte Funktionen**: Wirft eine Operation, die auch in Vanilla wirft (z. B. `distance=..30000000` in `tests/selectors/huge_distance_no_hang.mctest`), bricht die Funktion wie in Vanilla ab, mcfc schaltet sie danach aber dauerhaft auf Vanilla um und meldet „Please report this bug“.
5. **Testumgebung**: Zwischen Serverstart und `tick freeze` laufen 1–10 Ticks (RCON-Verbindung, abhängig von der Startdauer). Seit den mit ⁴ markierten Läufen steppt der Harness danach jede Variante auf Spielzeit 20, `gameTime` und tick-gezählte Zustände sind gleich. Was in den ungefrorenen Ticks passiert (z. B. welche Chunks schon geladen sind), hängt weiter von der Echtzeit ab. Worldgen ist zwischen Läufen nicht vollständig deterministisch (auch Vanilla gegen Vanilla), Worldgen-Befunde sind deshalb nur bei Packs mit Funktionen relevant und dort ebenfalls auf den `gen`-Dump vor jeder Funktion zurückgeführt. Entities werden nach Typ und Position sortiert verglichen; bewegen sich Mobs mit zufälliger Ausrüstung (Incendium), entstehen Befunde an einzelnen Indizes, obwohl die Anzahlen in der Vanilla-Spanne liegen. Bienen aus Nestern in abweichend generierten Chunks (Dungeons and Taverns) ändern die Entity-Anzahl.

Geklärt seit dem vorigen Stand:

- **`return` in nicht parsebaren Macro-Zeilen** (16 Funktionen ganz über Vanilla): behoben, Fix 13.
- **`worldgen-stack`: Team-Mitglieder und `$toggle`**: Der Wiederholungslauf zeigte statt `in.castle` Abweichungen bei `in.noname` (mcfc 5 Mitglieder, Vanilla 1–2) und `$toggle in.constants` (mcfc 0, Vanilla 1), beide in zwei Durchgängen. Ursache waren ungleich viele Ticks vor `tick freeze`: mcfc startet langsamer, beide mcfc-Läufe hatten 8 Start-Ticks, die Vanilla-Läufe 1 bzw. 7. `$toggle` wechselt jeden Tick (`incendium:technical/main`), seine Parität folgte der Spielzeit; Incendium-Mobs treten bei ihrer Initialisierung im Tick `in.noname` bei. Mit gleicher Spielzeit (Harness `--start-ticks`) haben alle sieben Läufe dieselbe Spielzeit (20 bei `gen`, 267 bei `final`) und denselben `$toggle`-Wert, `in.noname` streut auch zwischen Vanilla-Läufen (0–5) und ist kein Befund mehr. Kein mcfc-Fehler.
- **Performance von `mr-functions-all`** (vorher +10 % bzw. +13 % mit mcfc): in einer A/B-Messung nicht reproduzierbar, Funktionen machen höchstens 4,2 % der Tick-Zeit aus; siehe Performance.

## Performance

MSPT (ms pro Tick, `tick sprint`, Median von drei Messungen, Server allein):

- Bei den großen Funktionspacks ist der Gewinn klein und liegt teils im Rauschen: BACAP 30.07 → 29.05 (−3 %) im ersten, 32.97 → 33.15 (+0,5 %) im Wiederholungslauf; Orbital Strike Cannon 8.94 → 8.58 (−4 %); kitchen-sink 24.57 → 22.71 (−8 %) bzw. 24.12 → 23.19 (−4 %); bacap-vt4 19.75 → 20.43 (+3 %). Ein großer Teil der Tick-Zeit dieser Packs entsteht außerhalb der Funktionen (Advancement-Trigger, Entities), den mcfc nicht beschleunigt.
- `mr-functions-all` (acht Modrinth-Funktionspacks inkl. BACAP) war in den zwei früheren Läufen mit mcfc langsamer (17.46 → 19.17 und 17.91 → 20.33 ms), im Wiederholungslauf schneller (23.88 → 21.07 ms). Eine A/B-Messung mit Exerciser und abwechselnder Reihenfolge (Vanilla, `bfaa7efd`, retfix, retfix, `bfaa7efd`, Vanilla; `results/_perf_ab_functions_all.md`) ergibt Vanilla 21.13 bzw. 18.07 ms, `bfaa7efd` 19.55 bzw. 19.50 ms, retfix 19.70 bzw. 19.07 ms: kein reproduzierbarer Unterschied, die beiden Vanilla-Läufe streuen allein um 17 %. JFR-Profile der gemessenen Sprints (`perf_ab.py --jfr`, Auswertung `harness/jfr_split.py`) zeigen 91–93 % der Server-Thread-Samples in Entity-Ticks (vor allem `Entity.canCollideWith`; die Szenarien hinterlassen rund 920 Item-Entities) und nur 2,4–4,2 % in Funktionen (Vanilla 3,6 % und 4,2 %, mcfc 2,4–2,9 %). Ein Unterschied von 10 % kann daher nicht aus der Funktionsausführung kommen; vermutlich streut die Kollisionslast der Items zwischen Läufen (nicht weiter belegt).
- Kleine Packs (0.15–0.4 ms/Tick) liegen im Messrauschen; die Spannweite zweier Vanilla-Läufe ist dort oft größer als der Unterschied.
- A/B-Messung der Datenklassen-Aufteilung (Bug 1, Felder nicht mehr `final`), BACAP, abwechselnd A B C C B A: vor dem Fix 6.25 ms, nach dem Fix 6.00 ms, Vanilla 6.08–8.49 ms (`results/_perf_ab.md`). Die nicht-finalen Konstantenfelder kosten messbar nichts.

## Nicht unterstützte Befehle nach Häufigkeit

Aus `generated/diagnostics.tsv` aller Einzelpacks im finalen Lauf (837 Diagnosen, Kombinationen nicht mitgezählt; vollständig in `results/_unsupported.md|json`). Diese Befehle laufen über Vanilla, die umgebende Funktion bleibt kompiliert.

| Befehl | Anzahl | Grund |
|---|---|---|
| `tell` | 204 | nicht in der Grammatik (Dungeons and Taverns) |
| Macros an nicht unterstützten Stellen | 140 | `execute if block ~ ~ ~ $(trunk)` als Teil eines Macro-Arguments mit weiteren Platzhaltern (59), Storage-Pfade wie `blocks.$(category)` (25), Macro-Argumente in `function … {…}` und `execute unless data … category.$(category)` (je 7) |
| `execute …`, Unterbefehl nicht unterstützt | 91 | v. a. `facing entity …` (`execute facing entity @p[…] eyes …`, auch mitten in Ketten), Incendium |
| `function <macro-id>` (`function bacap_rewards:msg/$(reward_id)`) | 73 | Macro im Funktionsnamen (BACAP) |
| `attribute` | 63 | nicht in der Grammatik |
| `rotate` | 61 | nicht in der Grammatik |
| `bossbar` | 23 | nicht in der Grammatik |
| `setblock … strict` und `setblock … replace` | 20 | Modus nicht in der Grammatik (Gardener's Dream) |
| `advancement grant … only <macro>` | 17 | Macro in Advancement-ID (BACAP) |
| `effect give … infinite …` und `…players[].graves[]` | 17 | `infinite` als Dauer bzw. `[]` im NBT-Pfad nicht unterstützt (Incendium, Graves) |
| `playsound` ohne Ziel | 17 | Form ohne Selector nicht in der Grammatik (Timber Strike) |
| `place`, `forceload`, `damage` | 13 / 12 / 12 | nicht in der Grammatik |

Aktualisierte Übersicht der unterstützten Formen: `docs/command_support.md`.

## Entscheidungen und Annahmen

- **Modul**: `mc-26-1-fabric` baut bereits gegen 26.3 (Loom 1.17.21, Loader 0.19.5, Java 25); Name und Struktur blieben unverändert.
- **Reporter-Entfernung**: auch die Konfigurationsoption `enable_reporting` entfällt; alte Konfigurationen werden als Version 3 neu geschrieben. Zahlen und Zeiten der Kompilierung stehen stattdessen lokal in einer `[Summary]`-Logzeile.
- **Baseline** ist Vanilla mit demselben Fabric Loader, Carpet und Dump-Mod, nur ohne mcfc. Fabric API fehlt in allen Varianten (mcfc braucht sie nicht).
- **Spieler**: Carpet-Fake-Player in allen Varianten, Offline-Modus für feste UUIDs.
- **Determinismus**: eingefrorene Ticks mit exakter Schrittzahl, Mob-Spawning/Wetter/Random-Ticks aus, flache Welt ohne Strukturen für Funktionspacks, NoAI für bestehende Mobs, Forceload der Dump-Boxen. Vanilla wird zweimal (plus `perfbase`) ausgeführt; als Befund zählt nur, was von allen Baselines abweicht und in zwei Durchgängen auftritt.
- **Rauschregeln**: Abweichungen bei Items, XP-Orbs und fallenden Blöcken gelten als niedrige Konfidenz; Worldgen-Spalten, die auch zwischen Vanilla-Läufen variieren oder schon im `gen`-Dump abweichen, als Worldgen-Rauschen.
- **Exerciser**: ruft alle Funktionen ohne Macro-Zeilen einmal auf, außer Namen wie `uninstall`/`reset`/`kill_all`; Funktionen, die in allen Varianten endlos laufen oder die Welt zerstören, stehen in `exerciserSkip` des jeweiligen Szenarios (z. B. `homes:bubble`, Orbital-Nukes). Zeitbudget 300 s.
- **`maxCommandChainLength`** (26.3: `max_command_sequence_length`) wurde in allen Varianten auf das Maximum gesetzt, damit diese dokumentierte Abweichung keine Befunde erzeugt.
- **Gametest-Server** des Gradle-Builds liefen teilweise parallel zu den zwei Test-Servern des Harness; sie zählen nicht als Datapack-Testserver.
- **Datapacks**: nur aus offiziellen Quellen (Modrinth, vanillatweaks.net), Prüfsummen im Manifest, keine ZIPs im Repo. `pack_format` nur bei Orbital Strike Cannon angepasst. Vanilla Tweaks erzeugt ZIPs auf Anfrage; abweichende Prüfsummen werden nur gewarnt.
- **Performance-Zahlen** stammen aus einzelnen Läufen ohne parallele Server; Unterschiede unter etwa 10 % bei Packs unter 1 ms/Tick sind nicht aussagekräftig.
- **Prism Launcher**: Instanz `mcfc-test-26.3` (Fabric Loader 0.19.5, Fabric API 0.165.0, mcfc) angelegt; für die Tests war kein Client nötig.
- **Structory-Spalte `-4,-6`**: frühere Vermutung einer mcfc-Abweichung widerlegt; die Spalte hat in Vanilla-Läufen dieselben zwei Varianten.
