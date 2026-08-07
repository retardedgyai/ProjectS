# Wynncraft GitHub Harvest

- Source: Wynncraft GitHub organization
- URL: https://github.com/wynncraft
- Date inspected: 2026-08-07
- Status: `HARVESTED`

## Executive summary

Wynncraftの公開GitHubは現行ゲーム本体のソース公開ではなく、旧周辺基盤・API・ツールと、現在公開されている `SP-Algorithm-Bounty` が中心。

ProjectSに持ち込みたいのはコードそのものではなく、以下の設計・検証思想。

1. Formula / Algorithmを差し替え可能にするRegistry構造
2. 正しさテストとJMH性能試験を分ける検証基盤
3. 実サーバーに近い混合ワークロードBenchmark
4. Item Definition / Instance / calculation snapshotの分離思想
5. Recipe / Ingredientのデータ駆動設計
6. 軽い処理を先に確定し、重い探索対象を減らす枝刈り思想
7. 将来の複数サーバー運用でControl Plane / Node / DB / Admin UIを分離する思想

第三者コードの直接コピーは行わない。ライセンス未確認のため、基本は出典を参照してProjectS用に独立実装する。

---

## Candidates

### `WYNN-001` — Versioned Formula / Algorithm Registry

- Status: `HARVESTED`
- Priority: `S`
- Category: `architecture`
- Source-derived:
  - `SP-Algorithm-Bounty` は `IAlgorithm` と `AlgorithmRegistry` を使い、複数アルゴリズムを同一インターフェースで登録・比較可能にしている。
  - 各アルゴリズムは名前・version・authorメタデータを持つ。
- Source locations:
  - Repository: `Wynncraft/SP-Algorithm-Bounty`
  - File: `src/main/java/com/wynncraft/AlgorithmRegistry.java`
  - Related: `src/main/java/com/wynncraft/core/interfaces/IAlgorithm.java`
  - URL: https://github.com/Wynncraft/SP-Algorithm-Bounty
- Why it matters for ProjectS:
  - ProjectSは強化成功率、破損率、製造品質、MOD抽選、リファイン効率などの数式が今後何度も変わる。
  - 数式を直接サービスへ埋め込むとA/B比較とロールバックが難しい。
- ProjectS adaptation:
  - `EnhancementFormula`
  - `CraftQualityFormula`
  - `ModRollFormula`
  - `RefineFormula`
  - `DamageFormula`
  - 各Formulaにversionを持たせ、Registryから選択可能にする。
- Dependencies / prerequisites:
  - 既存システムの計算ロジックをServiceから分離する設計ルール。
- Risks / tradeoffs:
  - 小規模段階では抽象化が増える。
  - Registry乱用は避け、調整数式やアルゴリズムの比較価値が高い箇所に限定する。
- License status: `UNCLEAR`
- Suggested timing: `NOW`
- Compare against:
  - Monumenta等のStrategy/Registry実装があれば比較。
- Adoption decision: `PENDING`

### `WYNN-002` — Correctness Tests + JMH Benchmarks

- Status: `HARVESTED`
- Priority: `S`
- Category: `testing`
- Source-derived:
  - `SP-Algorithm-Bounty` はJUnitの正当性検証とJMHの性能測定を分離している。
  - テストケースを hand-written / curated synthetic / generated に分類できる。
  - Gradle propertyでテスト対象アルゴリズム・case group・benchmarkを絞れる。
- Source locations:
  - Repository: `Wynncraft/SP-Algorithm-Bounty`
  - File: `README.md`
  - File: `build.gradle`
  - Path: `src/jmh/java/com/wynncraft/`
- Why it matters for ProjectS:
  - 装備MOD、タグ、Buff、強化、製造などが増えると「正しいが遅い」「速いがedge caseで壊れる」が起こりやすい。
- ProjectS adaptation:
  - `CombatCalculationTests`
  - `ItemStatTests`
  - `EnhancementSimulationTests`
  - `CraftingSimulationTests`
  - `CombatBenchmark`
  - `ItemStatBenchmark`
  - `EconomySimulationBenchmark`
  - GradleからカテゴリとFormula versionを選べるようにする。
- Dependencies / prerequisites:
  - pure calculation layerをPaper APIから切り離す。
- Risks / tradeoffs:
  - 最初から全システムへJMHを入れる必要はない。
  - hot pathと大規模simulationを優先。
- License status: `UNCLEAR`
- Suggested timing: `NOW`
- Compare against:
  - 他サーバーのbenchmark基盤。
- Adoption decision: `PENDING`

### `WYNN-003` — Server-like Mixed Workload Benchmark

- Status: `HARVESTED`
- Priority: `S`
- Category: `testing`
- Source-derived:
  - `ServerSimBenchmark` は単一関数のmicrobenchmarkだけではなく、装備順変更、skill point変更、大量のweapon swapを混ぜた実利用に近い負荷を再現する。
  - seeded RNGを使った再現性のあるworkloadが想定されている。
- Source locations:
  - Repository: `Wynncraft/SP-Algorithm-Bounty`
  - README section: `Benchmark structure`
  - Path: `src/jmh/java/com/wynncraft/benchmarks/ServerSimBenchmark`
- Why it matters for ProjectS:
  - Paper上で実際に重くなるのは単一のdamage計算ではなく、装備変更、buff更新、skill発動、mob処理が混ざった状況。
- ProjectS adaptation:
  - 50/100/200 player equivalent simulation。
  - weapon swap / equipment update / buff add-remove / skill calculation / mob tickを一定比率で混ぜる。
  - RNG seed固定。
- Dependencies / prerequisites:
  - Paper API無しでも回せる計算層。
- Risks / tradeoffs:
  - 模擬負荷が実サーバーと乖離しないよう定期更新が必要。
- License status: `UNCLEAR`
- Suggested timing: `BEFORE_LARGE_SCALE_COMBAT`
- Adoption decision: `PENDING`

### `WYNN-004` — Deterministic Simulation / Seeded Random Source

- Status: `HARVESTED`
- Priority: `S`
- Category: `architecture/testing`
- Source-derived:
  - Wynncraft側benchmarkは再現性のあるseeded workloadを重視している。
- Source locations:
  - Repository: `Wynncraft/SP-Algorithm-Bounty`
  - README benchmark notes
- Why it matters for ProjectS:
  - ProjectSでは製造品質、MOD抽選、強化成功/失敗/破損など乱数依存システムが多い。
- ProjectS adaptation:
  - `RandomSource` interfaceを用意。
  - productionは通常乱数、test/simulationはseed指定。
  - bug reportにseedを残せるようにする。
  - 10k〜1M試行の経済simulationを再現可能にする。
- Risks / tradeoffs:
  - 乱数ソースを直接`ThreadLocalRandom`等へ散らさない規約が必要。
- License status: `N/A`
- Suggested timing: `NOW`
- Adoption decision: `PENDING`

### `WYNN-005` — Immutable Equipment Input for Calculators

- Status: `HARVESTED`
- Priority: `A`
- Category: `items/architecture`
- Source-derived:
  - `IEquipment` はtype、requirements、bonuses等の読み取り中心の小さいinterface。
  - Bounty READMEではalgorithmが `IEquipment` instanceを変更してはいけないルールを明示している。
- Source locations:
  - Repository: `Wynncraft/SP-Algorithm-Bounty`
  - File: `src/main/java/com/wynncraft/core/interfaces/IEquipment.java`
  - README rule: never modify an `IEquipment` instance
- Why it matters for ProjectS:
  - 装備計算中に永続Item状態を直接書き換えると、MOD・Buff・強化値・snapshot cacheが増えた時に副作用bugを生みやすい。
- ProjectS adaptation:
  - `ItemDefinition`: マスターデータ
  - `ItemInstance`: UUID、quality、rolled mods、enhancement、durability、crafter等の所有個体
  - `EquipmentSnapshot`: 戦闘計算用immutable view
  - calculatorはSnapshotのみ読む。
- Risks / tradeoffs:
  - Snapshot生成コストとcache invalidation設計が必要。
- License status: `N/A` for concept
- Suggested timing: `BEFORE_COMPLEX_ITEM_MODS`
- Adoption decision: `PENDING`

### `WYNN-006` — Primitive / Compact Data in Hot Paths

- Status: `HARVESTED`
- Priority: `A`
- Category: `performance`
- Source-derived:
  - WynnPlayerのskill point計算はenumを毎回Map lookupせず、ordinalに対応する`int[]`を使う。
  - combination cacheにもprimitive collectionが採用されている。
- Source locations:
  - Repository: `Wynncraft/SP-Algorithm-Bounty`
  - File: `src/main/java/com/wynncraft/core/WynnPlayer.java`
  - File: `src/main/java/com/wynncraft/core/NegativeMaskCache.java`
- Why it matters for ProjectS:
  - combat tick中のstat/tag/mod処理は大量に呼ばれるため、boxing・Map・allocationの積み重ねが効く可能性がある。
- ProjectS adaptation:
  - 設計段階ではreadability優先。
  - profiler/JMHでhotと確認されたstat vectorsのみprimitive array / bitset / enum ordinalへ最適化。
- Risks / tradeoffs:
  - 可読性が下がるのでpremature optimization禁止。
- License status: `N/A` for concept
- Suggested timing: `LATER / AFTER_PROFILING`
- Adoption decision: `PENDING`

### `WYNN-007` — Cheap-first Evaluation + Search Pruning

- Status: `HARVESTED`
- Priority: `B`
- Category: `algorithms/performance`
- Source-derived:
  - `WynnSolverAlgorithm` は条件なし・負ボーナスなしの安全な装備を先に確定し、残りだけbacktrackingする。
  - 現在bestを超えられない枝をupper-boundで打ち切る。
- Source locations:
  - Repository: `Wynncraft/SP-Algorithm-Bounty`
  - File: `src/main/java/com/wynncraft/algorithms/WynnSolverAlgorithm.java`
- Why it matters for ProjectS:
  - 条件付きMOD、effect、target selector、loot conditionなどが複雑化した場合に応用可能。
- ProjectS adaptation:
  - simple unconditional rulesを先に評価。
  - expensive conditions / combinatorial rulesだけ第二段階へ送る。
  - condition graphやbuild optimizer実装時に枝刈りを導入。
- Risks / tradeoffs:
  - 現時点のProjectSでは不要な箇所も多い。
- License status: `UNCLEAR`
- Suggested timing: `LATER`
- Adoption decision: `PENDING`

### `WYNN-008` — Data-driven Recipe Definitions

- Status: `HARVESTED`
- Priority: `A`
- Category: `crafting/content-pipeline`
- Source-derived:
  - Wynncraft APIのRecipeはlevel range、type、skill、materials、health/damage range、durability range等をデータオブジェクトとして公開していた。
  - recipe id一覧を取得し、更新時にcacheする運用も推奨されていた。
- Source locations:
  - Repository: `Wynncraft/WynncraftAPI`
  - File: `Recipe-API/README.md`
- Why it matters for ProjectS:
  - 製造・リファイン・Tier昇格・攻城兵器等のレシピが増え、Javaコード直書きではコンテンツ制作速度が落ちる。
- ProjectS adaptation:
  - `RecipeDefinition`
  - stable string ID
  - inputs / outputs / category / station / tier / costs / conditions / time / proficiency scaling
  - JSON/YAML/DB等からロードし、将来のゲーム内統合コンテンツエディタと接続。
- Risks / tradeoffs:
  - schema versioning / validationが必要。
- License status: `N/A` for schema concept
- Suggested timing: `BEFORE_CRAFTING_SCALE_UP`
- Adoption decision: `PENDING`

### `WYNN-009` — Rich Ingredient Definition

- Status: `HARVESTED`
- Priority: `A`
- Category: `crafting/items`
- Source-derived:
  - Ingredient resourceはtier、level、craft skills、stat min/max、durability modifier、requirements、position modifier等をIngredient objectへ持たせていた。
- Source locations:
  - Repository: `Wynncraft/WynncraftAPI`
  - File: `Ingredient-API/README.md`
- Why it matters for ProjectS:
  - ProjectSには通常素材、mob素材、boss素材、触媒、MOD素材があり、後から用途や効果が増える。
- ProjectS adaptation:
  - `IngredientDefinition`をRecipeから独立。
  - tags / tier / rarity / source / allowed recipe categories / crafting effects / economy classificationを持たせる。
- Risks / tradeoffs:
  - Wynncraftのposition modifier自体をProjectSへ持ってくる必要はない。データ駆動の粒度だけ参考にする。
- License status: `N/A` for concept
- Suggested timing: `BEFORE_CRAFTING_SCALE_UP`
- Adoption decision: `PENDING`

### `WYNN-010` — List Endpoint + Cache Invalidation Pattern

- Status: `HARVESTED`
- Priority: `B`
- Category: `content-pipeline/cache`
- Source-derived:
  - Recipe / Ingredient APIは一覧取得endpointを持ち、全件詳細を毎回取り直さず更新単位でcacheすることを推奨していた。
- Source locations:
  - Repository: `Wynncraft/WynncraftAPI`
  - Files: `Recipe-API/README.md`, `Ingredient-API/README.md`
- Why it matters for ProjectS:
  - 将来、管理UI・Web/API・client modがmaster dataを参照する場合に使える。
- ProjectS adaptation:
  - content revision / schema version / data hashを持たせる。
  - client/admin toolはrevision変更時のみdefinition一覧を再取得。
- Suggested timing: `LATER`
- License status: `N/A`
- Adoption decision: `PENDING`

### `WYNN-011` — Separate Control Plane from Game Nodes

- Status: `HARVESTED`
- Priority: `B`
- Category: `operations/networking`
- Source-derived:
  - 旧Wynncraft公開repoでは、`Minestack`をMinecraft server network deployment/control、`Redstone`をnode control、`DoubleChest`をdatabase library、`CraftingTable`をweb applicationとして分離していた。
- Source locations:
  - `Wynncraft/Minestack`
  - `Wynncraft/Redstone`
  - `Wynncraft/DoubleChest`
  - `Wynncraft/CraftingTable`
- Why it matters for ProjectS:
  - 将来Lobby / island / dungeon / raid / war等へサーバー分割すると、ゲームロジックとdeployment/controlを一体化しない方が運用しやすい。
- ProjectS adaptation:
  - Game Node
  - shared persistence / messaging
  - deployment/control service
  - admin/content web UI
  - を別責務として設計。
  - 古いWynncraft実装自体は利用しない。
- Risks / tradeoffs:
  - 現在は単一Paper serverなので早すぎる。
- License status: `UNCLEAR`
- Suggested timing: `LATER / BEFORE_MULTI_SERVER`
- Adoption decision: `PENDING`

### `WYNN-012` — Algorithm Cache Must Self-invalidate

- Status: `HARVESTED`
- Priority: `A`
- Category: `cache/testing`
- Source-derived:
  - `SP-Algorithm-Bounty` のRegistryはalgorithm instanceを再利用し、cacheを持つ実装に対してinput変更時のself-invalidationを要求している。
  - cold benchmark用の`clearCache()` hookも設けている。
- Source locations:
  - Repository: `Wynncraft/SP-Algorithm-Bounty`
  - README sections: `IAlgorithm#clearCache`, `AlgorithmRegistry`
- Why it matters for ProjectS:
  - equipment snapshot / stat cache / damage modifier cacheを今後導入するとstale cache bugが危険。
- ProjectS adaptation:
  - cache keyにrevision/versionを含める。
  - explicit invalidation eventとself-validationの責務を決める。
  - benchmarkではcold/warmを分離して測る。
- Suggested timing: `BEFORE_STAT_CACHING`
- License status: `N/A` for concept
- Adoption decision: `PENDING`

---

## Things not worth bringing over directly

### Old PHP / mobile / forum integrations

`WynncraftAPI-PHP`, `WynncraftMobile`, XenForo関連等は歴史資料としては面白いが、ProjectSの現行Java/Paper/Fabric環境へ直接持ち込む価値は低い。

### Old Minestack implementation itself

Control Plane分離という思想は参考になるが、Archivedな旧deployment stackそのものは現在のProjectSへ移植しない。

### Premature primitive optimization

primitive array / bitmaskはhot pathで効くが、まだprofilerで問題が出ていない箇所まで全体適用しない。

### Wynncraft-specific crafting semantics

Ingredient position modifierやWynncraft固有skill point装備ルールをProjectSへそのままコピーしない。

---

## Recommended later cross-source comparison

他の調査結果が揃ったら以下を比較する。

1. `WYNN-001` Registry vs Monumenta等のability/effect registry
2. `WYNN-002/003` benchmark方式 vs Minestom/Monumentaのperformance test
3. `WYNN-005` Item model vs 他MMOサーバーのitem definition/instance architecture
4. `WYNN-008/009` crafting data model vs Albion/PoE的なProjectS経済要件
5. `WYNN-011` server control architecture vs modern Velocity/Kubernetes/Agones/Minestom network architecture

---

## Current best candidates before comparison

まだ採用決定ではないが、Wynncraft単体で見た暫定上位は以下。

- `WYNN-001` Formula / Algorithm Registry
- `WYNN-002` Correctness Tests + JMH
- `WYNN-003` Server-like Mixed Benchmark
- `WYNN-004` Deterministic Random Source
- `WYNN-005` Definition / Instance / Snapshot separation
- `WYNN-008` Data-driven Recipe Definition
- `WYNN-012` Cache invalidation discipline

最終判断は他ソースのHarvestを統合してから行う。
