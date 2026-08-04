# Combat foundation gap analysis

監査対象: `io.github.gyai.projects.combat.damage`、`combat.stat`、および接続済み戦闘経路

## 結論

当初のPhase 1は一部が既に実装済みである。`DamageCalculator`と`StatCalculator`はBukkit非依存の決定的な純粋計算、`DamageService`はBukkit adapter兼application serviceとして再利用すべきであり、別のDamageServiceや並行する計算器を作成してはならない。

一方、設計上の攻撃タグ、火・氷・雷の独立属性値、複数属性、属性反映率は未実装である。既存APIへ実ゲーム接続するにはsnapshot境界と計算順序の設計が必要なため、今回は純粋なmetadata型とテストだけを安全な追加候補とし、DamageServiceへの接続は次Phaseへ送る。

## 既存クラスの設計適合分類

| クラス | 分類 | 根拠・扱い |
| --- | --- | --- |
| `DamageService` | `PARTIAL_MATCH` | 共通計算入口、Bukkit stat収集、適用、再入防止を持つ正式候補。純粋層ではなく、Scout/Boss等は未接続。全面書き直し不可。 |
| `DamageCalculator` | `PARTIAL_MATCH` | Bukkit非依存、同一入力で決定的、防御・critical・reduction・shieldを計算。属性、タグ、明示的step/breakdown snapshotがない。 |
| `DamageRequest` | `UNSAFE_TO_MIGRATE` | 名前は正式候補だが`Player`/`LivingEntity`を直接保持し、cast ID既定値がrandom。既存call siteが多いため削除・置換せずadapter境界に残す。 |
| `DamageResult` | `PARTIAL_MATCH` | 数値内訳は多いが、compact validation、不変step collection、属性別内訳、型付きcritical/penetration情報がない。 |
| `DamageOffenseSnapshot` | `PARTIAL_MATCH` | 不変・validation済みで遅延/splash再利用に有効。ただし攻撃者Stat全体や属性貢献snapshotではない。 |
| `DamageApplicationResult` | `UNRELATED` | Bukkit適用結果として必要。純粋計算resultとは役割が異なり、残す。 |
| `DamageEventApplicationPolicy` | `UNRELATED` | Bukkit event adapterの方針を純粋関数で表現。設計上の戦闘metadataではないが回帰保護に必要。 |
| `DamageKind` | `PARTIAL_MATCH` | NORMAL_ATTACK、DIRECT_SKILL、DoT、REFLECTEDを表現。SECONDARY相当なし。PERCENT_HEALTHは設計外。 |
| `DamageMode` | `CONFLICTS_WITH_DESIGN` | PVE critical 1.75は設計の基本1.50と矛盾。reduction capも設計書根拠がない。今回は変更しない。 |
| `DamageType` | `PARTIAL_MATCH` | PHYSICAL/MAGICALは一致。TRUEはBeta設計の二系統から外れる。既存互換のため削除しない。 |
| `CriticalHitResolver` | `PARTIAL_MATCH` | cast単位でrollを固定するがstatefulかつ外部random依存。計算自体へrollを渡す境界はテスト可能。 |
| `StatCalculator` | `PARTIAL_MATCH` | 純粋・saturation・NaN正規化を持つ。flat penetrationと複数の未設計hard-coded capが含まれる。 |
| `Stats` | `MATCHES_DESIGN` | 有限値validation、EnumMap、immutable snapshotを持つ汎用runtime stat container。永続化と集約境界は別途必要。 |
| `StatType` | `CONFLICTS_WITH_DESIGN` | 物理/魔法、critical、速度等は合うが、設計で不採用のflat penetrationを含み、属性値・タグ別damage statがない。 |
| `Telegraph` pure classes | `MATCHES_DESIGN` | 高品質モブの予兆基盤として独立・validation・テスト済み。今回変更不要。 |
| `Mob Editor` model/repository | `PARTIAL_MATCH` | 基本情報、Stats、AI、外見、保存、test spawnは実装。Skill/Drop、攻撃タグ/属性編集は未実装。今回変更不要。 |

## 概念別カバレッジ

### 攻撃系統

- 表現可能: `PHYSICAL`、`MAGICAL`、`TRUE`。
- 物理・魔法は対応防御値を選ぶ。
- 属性が付いても元系統の防御を使うという設計は、属性自体がないため未実装。
- TRUEは全防御を無視する既存挙動で、Beta設計との扱いを決めるまで変更しない。

### 攻撃区分

- `NORMAL_ATTACK`
- `DIRECT_SKILL`
- `DAMAGE_OVER_TIME`
- `REFLECTED`
- `PERCENT_HEALTH`

設計上必要な通常攻撃、skill、DoT、reflectedは概ね表現できる。`SECONDARY`または自動追撃/連鎖を統一して示す区分はない。`DamageKind`はcritical/lifesteal policyも内包するため、未決定のSECONDARYを推測追加しない。

### 攻撃タグ

既存DamageRequestにはタグ集合がない。MobDefinitionには自由文字列tagsがあるが、攻撃タグではなくvalidationも別であり再利用不可。設計例の`MELEE`、`PROJECTILE`、`MAGIC`、`PHYSICAL`、`NORMAL_ATTACK`、`SKILL`、`SHATTER`、`FIRE`、`ICE`、`LIGHTNING`は未表現。

### 属性値と属性反映率

- 火・氷・雷の独立値: 未実装。
- 複数属性保持: 未実装。
- 属性反映率: 未実装。
- 弱点特効: 未実装。
- 属性は元の物理/魔法防御を参照: 未実装。
- 光・闇: 実装なし（設計どおりBeta対象外）。

### クリティカル

- critical可否、chance、multiplier、cast単位roll固定を実装。
- DoT/反射/percent health/TRUEのlifesteal抑止を実装。
- `DamageResult`はcritical boolとmultiplierを保持する。
- 基本倍率がPvE 175%で設計150%と矛盾する。
- 氷砕き追加damageを非criticalにし、元直撃のcritical前基準値を使う機能は未実装。

### 貫通

- 物理・魔法の割合貫通を実装。
- 防御低下 → 割合貫通 → flat貫通の順で計算する。
- fixed/flat penetrationは設計で不採用のため矛盾。
- 型付きの貫通結果はなく、`DamageResult`のdefense before/effective値から読み取る形。

### ダメージ内訳・ログ

`DamageResult`はbase、offense resolved、defense、reduction、shield、health、lifesteal等の数値を持ち、最低限の内訳は存在する。属性別内訳、計算step名、適用元MOD、weakness、critical/penetration専用recordはない。ログcollectionもない。

### 不変性・validation

- `DamageCalculator.Input`/`OffenseInput`はarrayをcloneし、accessorもcloneする。
- `DamageOffenseSnapshot`はfinite/non-negativeを拒否する。
- `DamageRequest`はdouble入力のNaN/Infinityを拒否し、reduction arrayをcloneする。
- `StatCalculator`は純粋計算内のNaNを0、Infinityをsaturated finite値へ正規化し、負の攻撃・防御値を0へ寄せる。
- `DamageResult`と`DamageApplicationResult`自体にはconstructor validationがない。
- `DamageRequest`のnull/default policyはあるがBukkit依存であり、純粋モデルではない。

### Bukkit依存境界

純粋:

- `DamageCalculator`
- `DamageOffenseSnapshot`
- `DamageResult`
- `DamageKind`、`DamageMode`、`DamageType`
- `DamageEventApplicationPolicy`（文字列/数値のみ）
- `StatCalculator`

Bukkit/stateful adapter:

- `DamageRequest`（Bukkit entityを保持）
- `DamageService`
- `CriticalHitResolver`（UUID cacheを保持するがBukkit API自体は不使用）

### 既存スキルの接続状況

- Warrior: 通常攻撃とskillの主要経路が接続済み。
- Painter: 通常攻撃、skill、DoT、passive bonusが接続済み。
- Scout: Arrow vanilla damageとevent加算を使用し、未接続。
- Editor Mob: basic attackが接続済み。
- Harbor Devourer: 固有攻撃の一部が直接Bukkit damageで未接続。

## 移行時に壊れる可能性がある箇所

1. event modifier除去を変えると二重防御または吸収量のずれが発生する。
2. Warrior闘気bonusはevent listenerとpre-scaled depthの両方に依存し、移行で二重適用しやすい。
3. cast UUIDを変えると多段skillのcritical固定が崩れる。
4. splashの`DamageOffenseSnapshot`を再計算するとcritical/汎用MODが二重適用される。
5. Training Dummyはevent final damageとskill marker順序に依存する。
6. Scoutを即時移行するとArrow固有knockback、projectile source、passive、dummy集計が変わり得る。
7. TRUE/flat penetrationを削除すると既存Mob Editor YAMLまたはruntime Statとの互換性を壊す。

## 再利用すべきクラス

- 計算中心: `DamageCalculator`、`StatCalculator`
- 既存入口/adapter: `DamageService`、`DamageRequest`
- 遅延・派生damage snapshot: `DamageOffenseSnapshot`
- 結果: `DamageResult`、`DamageApplicationResult`
- 区分: `DamageType`、`DamageKind`、`DamageMode`
- critical: `CriticalHitResolver`
- event互換: `DamageEventApplicationPolicy`
- Stat container: `Stats`、`StatType`

## 記録のみの削除・廃止候補

今回は削除しない。

- Beta正式式から外れる可能性がある`PHYSICAL_PENETRATION_FLAT`、`MAGICAL_PENETRATION_FLAT`。
- Beta正式系統外となる可能性がある`DamageType.TRUE`。
- `PERCENT_HEALTH`が攻撃区分とdamage source policyを兼ねる構造。
- `BalanceMath.skillDamage`と`StatCalculator.baseDamage`の重複式。
- `EnhancementManager.applyingSkillDamage`と`DamageService.applying`の重複する再入防止責務。

いずれも使用箇所と保存互換を確認する移行Phaseまで残す。

## 今回安全に閉じられるgap

- 設計で確定済みの攻撃タグenum。
- Beta三属性enum。
- 複数属性値と属性反映率を保持するBukkit非依存・immutable・validated metadata。
- 既存DamageType/Kind/Calculatorのphysical/magical/TRUE、determinism、array防御コピー、NaN/Infinity/負数/defaultを明示するJavaExec test。

これらはDamageServiceへ接続せず、実ゲームの出力を変えない。

## 次Phaseへ送るgap

- Bukkit entityを含まない完全なattacker/target snapshot。
- 既存`DamageRequest`から純粋snapshot requestへのadapter。
- 属性直撃と元攻撃系統による防御計算。
- 型付きdamage breakdown、critical/penetration情報、計算step log。
- attack tagを既存skill/Mob Editor定義へ保存・通信するschema migration。
- Scout/Boss経路の個別互換移行。
- 火・氷・雷の実ゲーム状態、party共有貢献、weakness処理。
