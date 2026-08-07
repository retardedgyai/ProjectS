# Lepinoid GitHub Harvest

- Source: Lepinoid GitHub organization
- URL: https://github.com/Lepinoid
- Date inspected: 2026-08-07
- Status: `HARVESTED`
- Repositories inspected:
  - `Lepinoid/bb-data-structure`
  - `Lepinoid/cmdlib`
  - `Lepinoid/WorkflowCollections`
  - `Lepinoid/renovate`
  - `Lepinoid/infra`
  - `Lepinoid/maven-repo`
  - `Lepinoid/yarn`
  - `Lepinoid/uuid-serializer`
  - `Lepinoid/time-supporter-bot`

## Executive summary

LepinoidからProjectSへ持ち込みたい最大の価値は、Kotlinへ全面移行することではなく、**Minecraft/Blockbench等の外部形式を純データSchemaへ落とし、authoring/tooling/runtimeを分離する設計思想**。

特に強い候補は以下。

1. Blockbench `.bbmodel` をtyped pure dataとして扱うSchema
2. Blockbench Locatorを武器/VFX/弱点等のSocketとして使う設計
3. `.bbmodel`をruntimeで直接読むのではなくoffline importer/compilerでProjectS形式へ変換する構造
4. Kotlin DSL風の「人間が読みやすいauthoring API」から純Definitionを生成する方式
5. 再利用可能GitHub Actions workflow
6. Renovate設定の中央集約
7. Server / Client / Tools間のstable schema artifact分離
8. Minecraft mapping/NMS依存をadapter境界へ閉じ込める思想
9. GitOps / DB / Secret / Metricsをゲーム本体から分離した運用基盤
10. 標準機能で代替できる自作utilityは廃止する保守方針

直接コードを利用する場合、`bb-data-structure` / `cmdlib` / `uuid-serializer` はMIT、`yarn` forkはCC0、`time-supporter-bot` はApache-2.0を確認済み。`infra` / `WorkflowCollections` / `renovate` / `maven-repo` は今回確認範囲でLICENSEを確定できていないため、研究庫では設計思想として扱う。

---

## Candidates

### `LEPI-001` — Typed Blockbench Pure Data Schema

- Status: `HARVESTED`
- Priority: `S`
- Category: `content-pipeline/architecture`
- Source-derived:
  - `bb-data-structure` はBlockbenchのModel / Cube / Group / Texture / Animation / Animator / Keyframe / Locator等をKotlinのtyped dataとして表現し、serialization可能にしている。
  - Blockbench raw JSONをゲームロジックそのものへ直接混ぜない構造になっている。
- Source locations:
  - Repository: `Lepinoid/bb-data-structure`
  - `src/commonMain/kotlin/DataStructure.kt`
  - serializer package
- Why it matters for ProjectS:
  - ProjectS-Clientで3D Mob/Boss/Animationを扱う際、Blockbench format依存をrenderer全体へ漏らさずに済む。
- ProjectS adaptation:
  - `ProjectSModelData` / `ModelDefinition` を純データとして定義。
  - geometry / bones / textures / animations / locators / schemaVersionを保持。
- Risks / tradeoffs:
  - `bb-data-structure`自体はArchivedであり、現行Blockbench formatとの差分検証が必須。
- License status: `PERMISSIVE` — MIT.
- Suggested timing: `NOW / BEFORE_3D_PIPELINE`
- Adoption decision: `PENDING`

### `LEPI-002` — Blockbench Locator → ProjectS Socket / Attachment Point

- Status: `HARVESTED`
- Priority: `S`
- Category: `content-pipeline/combat/ui`
- Source-derived:
  - `bb-data-structure` はBlockbench Locatorを独立要素として読み取れる。
- Why it matters for ProjectS:
  - 3Dデザイナーが武器位置、魔法発生点、弱点等の座標をJavaへ手入力する必要を減らせる。
- ProjectS adaptation:
  - naming convention例: `weapon_main`, `vfx_cast`, `vfx_mouth`, `weakpoint_core`, 将来 `hitbox_*`。
  - Client animationと一緒にsocket transformを追従させる。
- Dependencies / prerequisites:
  - `LEPI-001`。
- Risks / tradeoffs:
  - gameplay authoritative hitboxへ利用する場合はServer/Client同期設計が別途必要。
- License status: `N/A` for concept.
- Suggested timing: `WITH_MODEL_PIPELINE`
- Adoption decision: `PENDING`

### `LEPI-003` — Offline Blockbench Importer / Model Compiler

- Status: `HARVESTED`
- Priority: `S`
- Category: `content-pipeline/tooling`
- Source-derived:
  - LepinoidはBlockbench data structureをruntime logicとは別ライブラリとして扱っている。
- Why it matters for ProjectS:
  - ゲーム中に巨大な`.bbmodel`を直接解析せず、format changeの影響をImporterに閉じ込められる。
- ProjectS adaptation:
  - `.bbmodel -> validator -> ProjectSModelData/Manifest -> Client assets`。
  - CIでmissing animation/socket/invalid hierarchyをfail closed。
- Dependencies / prerequisites:
  - `LEPI-001`, `LEPI-002`。
- Risks / tradeoffs:
  - compiler/schema version migrationが必要。
- License status: `N/A` for architecture concept.
- Suggested timing: `NOW / P0-P1`
- Adoption decision: `PENDING`

### `LEPI-004` — Kotlin DSL-style Authoring → Pure Definitions

- Status: `HARVESTED`
- Priority: `S`
- Category: `architecture/content-pipeline`
- Source-derived:
  - `cmdlib` はBrigadierを `register { literal { integer { executes {} } } }` のようなKotlin DSLで組み立てる。
  - CommandBuilder内ではliteral/argument/actionを安定した部品として構成する。
- Why it matters for ProjectS:
  - Ability / Quest / Mob / Recipe定義をJavaベタ書きや巨大YAML programmingへ寄せず、人間が読みやすいauthoring layerを作れる。
- ProjectS adaptation:
  - Kotlin DSLはruntime scriptとして実行せず、`AbilityDefinition` / `ContentDefinition`等の純データを生成するauthoring手段として評価。
  - Editorも同じDefinition schemaを生成する。
- Risks / tradeoffs:
  - Kotlin全面移行やcustom scripting runtimeとは分離する。
- License status: `PERMISSIVE` — `cmdlib` MIT.
- Suggested timing: `AFTER_SHARED_ABILITY_SCHEMA`
- Adoption decision: `PENDING`

### `LEPI-005` — Reusable CI Workflow Collection

- Status: `HARVESTED`
- Priority: `A`
- Category: `testing/operations`
- Source-derived:
  - `WorkflowCollections` はbuild / publish / PR validation等をreusable workflowとして中央化し、各repoから`uses:`で呼び出せる。
  - Java version、Gradle args、artifact upload等をinput化している。
- Why it matters for ProjectS:
  - ProjectS / ProjectS-Client / 将来Content ToolsでCI設定の重複と差異を減らせる。
- ProjectS adaptation:
  - ProjectS専用shared workflowを作り、Serverでは必ず`-PskipAutoStart`を維持。
- Risks / tradeoffs:
  - 外部repoのworkflowを直接依存せずProjectS側で管理する方が安全。
- License status: `UNCLEAR` for direct copying; concept use.
- Suggested timing: `SOON`
- Adoption decision: `PENDING`

### `LEPI-006` — Central Renovate Dependency Policy

- Status: `HARVESTED`
- Priority: `A`
- Category: `operations/tooling`
- Source-derived:
  - Lepinoidは各repoから共通Renovate設定をextendし、patch updateのみautomergeする等のpolicyを中央管理する。
- Why it matters for ProjectS:
  - Paper/Fabric/Gradle/utility dependenciesの更新漏れを減らせる。
- ProjectS adaptation:
  - patch系のみCI通過後の自動化候補。
  - Minecraft/Paper/Fabric/Protocolに影響するupdateはmanual review固定。
- Risks / tradeoffs:
  - Minecraft ecosystemではminorでも破壊的差異があり得るため保守的policyが必要。
- License status: `UNCLEAR` for direct config copying; concept use.
- Suggested timing: `SOON`
- Adoption decision: `PENDING`

### `LEPI-007` — Shared Schema / Artifact Boundary

- Status: `HARVESTED`
- Priority: `A`
- Category: `architecture/networking/content-pipeline`
- Source-derived:
  - Lepinoidは`bb-data-structure`, `model-meta`, `cmdlib`, `uuid-serializer`等を独立artifactとしてMaven repoから配布している。
- Why it matters for ProjectS:
  - Server / Client / Content Toolsでpacket ID、schema version、Ability/Model definition等を二重定義するとdriftしやすい。
- ProjectS adaptation:
  - 将来 `ProjectS-Protocol` / `ProjectS-Content-Schema` / `ProjectS-Model-Schema` のいずれかをshared module/artifactとして検討。
  - 既存golden-vector protocol testは維持。
- Risks / tradeoffs:
  - 小規模段階でrepo/moduleを増やし過ぎると開発速度が落ちる。
- License status: `N/A` for architecture concept.
- Suggested timing: `WHEN_SERVER_CLIENT_SCHEMA_DUPLICATION_GROWS`
- Adoption decision: `PENDING`

### `LEPI-008` — Minecraft Internal API Adapter Boundary

- Status: `HARVESTED`
- Priority: `A`
- Category: `client/architecture`
- Source-derived:
  - LepinoidのYarn forkはMinecraft内部mappingを独自に調整しており、version変更に伴う名前/API差異をmapping layerで吸収している。
- Why it matters for ProjectS:
  - Client MODのrenderer/mixin/NMS相当の内部依存が増えた際、game/UI logicまでmapping変更を伝播させたくない。
- ProjectS adaptation:
  - `MinecraftClientAdapter`等のversion-specific boundaryへ内部API依存を閉じ込める。
- Risks / tradeoffs:
  - ProjectS独自Yarn forkは必要になるまで作らない。
- License status: `PERMISSIVE` — Yarn fork CC0.
- Suggested timing: `WITH_COMPLEX_CLIENT_RENDERING`
- Adoption decision: `PENDING`

### `LEPI-009` — Game Runtimeから分離したGitOps / DB / Observability

- Status: `HARVESTED`
- Priority: `B`
- Category: `operations/networking`
- Source-derived:
  - `infra` はKubernetes/Flux CD、PostgreSQL、persistent volume、health probe、SOPS+Age secret管理、Grafana/Prometheus等をゲームアプリから分離して管理している。
- Why it matters for ProjectS:
  - 将来の複数Game Node / Dungeon / Raid / DB運用時にdeploymentとgameplay codeを分離できる。
- ProjectS adaptation:
  - Beta初期はDocker + DB + metrics + backup程度から開始。
  - multi-server化した段階でGitOps/Kubernetes等を再評価。
- Risks / tradeoffs:
  - 現在導入すると過剰設計。
- License status: `UNCLEAR` for direct copying; architecture concept only.
- Suggested timing: `LATER / BEFORE_MULTI_SERVER_OPERATIONS`
- Adoption decision: `PENDING`

### `LEPI-010` — Delete Custom Utility When the Standard Platform Supersedes It

- Status: `HARVESTED`
- Priority: `B`
- Category: `maintenance`
- Source-derived:
  - `uuid-serializer` はKotlin標準/serialization側がUUIDを扱えるようになったためArchivedと明記している。
- Why it matters for ProjectS:
  - 一度作った自前基盤を永久維持せず、標準機能が十分になったら削除できる文化を持つ。
- ProjectS adaptation:
  - 自作utility/libraryへ「存在理由」をdocument化し、標準置換可能になったらmigration taskを作る。
- License status: `PERMISSIVE` — MIT.
- Suggested timing: `ONGOING`
- Adoption decision: `PENDING`

---

## Things not worth bringing over directly

### Archived `cmdlib` / `bb-data-structure`へのそのまま依存

- どちらも古いMinecraft/Blockbench世代を前提とする部分がある。
- ProjectSのPaper/Fabric 26.1.xと現行Blockbench fixtureで必要要件を再確認し、設計を参考に独立実装する。

### Kotlin全面移行

- Lepinoidの強みはKotlinそのものより、typed data / serialization / DSL / module separationにある。
- ProjectS既存Java資産を全面書き換える理由にはしない。

### ProjectS独自Yarn fork

- mapping overrideが本当に必要になるまで導入しない。

### Kubernetesを今すぐ導入

- 現在の単一/少数server開発では運用コストが先に増える。

### `time-supporter-bot`固有ロジック

- Coroutine/error handling等は一般的参考にはなるがProjectS coreへの直接候補は少ない。

---

## Integration notes

横断比較時は以下へまとめる。

### Model / 3D Content Kernel

- `LEPI-001`
- `LEPI-002`
- `LEPI-003`
- `LEPI-008`
- Monumenta Mob library / client sync候補と比較。

### Content Definition / Authoring Kernel

- `LEPI-004`
- `LEPI-007`
- Monumenta `MONU-001`, `MONU-016`, `MONU-017`
- Wynncraft `WYNN-008`, `WYNN-009`

### Development Operations

- `LEPI-005`
- `LEPI-006`
- `LEPI-010`

### Large-scale Operations

- `LEPI-009`
- Wynncraft `WYNN-011`

最終判断では、言語変更そのものではなく、**ProjectSのコンテンツ制作速度・Server/Client整合性・3D designer workflow・AI/Codexとの相性**を基準に採否を決める。