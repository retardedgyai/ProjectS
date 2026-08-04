# ProjectS current code audit

監査日: 2026-08-04
監査基準: `agent/boss-telegraphs` の `799cffc9c4f709eeb5e6ad989eef8017685916f6` と、`origin/main` の `4b8801d` をマージした状態

## 監査範囲と前提

- `docs/design/` の正本8ファイルを全て確認した。
- 既存の戦闘経路は変更せず、コードと設計の差を記録する。
- `ProjectS-Client` は今回変更していない。通信チャンネル名はサーバー側の現状だけを監査した。
- `DataManager`、`DungeonManager`、`QuestManager`、`WeaponData`、`ArmorData` は現状空のプレースホルダーである。

## 現在存在する主要システム

| システム | 主なクラス | 現状 |
| --- | --- | --- |
| 起動・配線 | `ProjectSPlugin` | manager、listener、channel、tick taskを一か所で組み立てる。機能数の増加により変更衝突リスクが高い。 |
| 共通ダメージ | `DamageService`、`DamageCalculator`、`DamageRequest`、`DamageResult` | 物理・魔法・TRUE、通常攻撃・直接スキル・DoT等を扱う。WarriorとPainterの主要経路、Mob Editorの通常攻撃が接続済み。 |
| Stat | `StatCalculator`、`Stats`、`StatType` | 純粋計算と実行時Statコンテナを提供。攻撃、防御、速度、回復、リソースを含む。 |
| クラス | `ClassRegistry`、`ClassManager`、各Controller | 手持ち武器IDからWarrior、Scout、Painter Mageを選択する。永続化はない。 |
| スキル | `SkillManager`、Warrior skill群、Painter executor | UUID別cooldownをメモリ保持。Warriorは共通DamageService、Painterはラッパー経由で接続。 |
| リソース | `ResourceManager`、`ResourceDefinition` | 闘気とManaを実装。Mana等はメモリのみ。 |
| 強化・破損・修理 | `EnhancementManager`、`EnhancementListener` | 武器強化+0〜+30、成功・破損・修理、PDC保存、攻撃速度Attribute反映を実装。 |
| バランス調整 | `BalanceMath`、`BalanceTuningManager` | 武器とスキルの一部数値をYAML overrideとクライアントUIで調整する。 |
| CC・状態異常 | `CrowdControlManager`、`StatusEffectManager` | hard CCと汎用SLOW等を実装。設計書の共有燃焼・冷気・凍結・氷砕きではない。 |
| モブ・ボス | `MonsterManager`、`CustomMonster`、`HarborDevourerBoss` | カスタムモブ、ボスAI、UI、CC耐性、予兆、Editor Mobを管理。 |
| Mob Editor | `monster.editor`、`MobEditorChannel` | YAML定義、validation、revision競合、atomic save、外見・装備、test spawn、通信を実装。Skill/Drop編集は未実装。 |
| Telegraph | `combat.telegraph`、`TelegraphManager` | circle/donut/line、追跡・lock・detonate・cancel、client packetとfallbackを実装。 |
| Training Dummy | `TrainingDummyManager`、`TrainingDummyListener` | DPS session、skill別集計、PDC識別を実装。 |
| HUD・入力・通信 | `CombatHudManager`、`ClientInputListener`、`network` | skill input、HUD、loadout、balance、monster UI、Mob Editor、telegraph通信を実装。 |
| プレイヤーデータ | `PlayerManager`、`PlayerData`、`Stats` | UUID別メモリ状態のみ。戦闘レベル、闘気、Statの永続化はない。 |

## 主要な依存関係

`ProjectSPlugin`が全サービスを生成し、`DamageService`へ`PlayerManager`、`ItemManager`、`EnhancementManager`、`TrainingDummyManager`を注入する。`MonsterManager`はMob EditorのStat解決を`DamageService`へ渡す。Warrior skill群とPainterの`SkillDamageService`は`DamageRequest`を構築して`DamageService.apply`を呼ぶ。

`DamageService`内部ではBukkitオブジェクトから装備、HP、吸収、Player Stat、Mob Editor Statを読み、純粋な`DamageCalculator`へ数値を渡す。計算後は`LivingEntity.damage`を呼び、`EntityDamageByEntityEvent`上でバニラ軽減modifierを除去する。したがって、純粋計算境界は存在するが、要求モデルとStat snapshot生成はBukkit adapterから分離されていない。

## 現在のダメージ経路

| 経路 | 計算・適用 | 再入防止 | 監査結果 |
| --- | --- | --- | --- |
| starter sword通常攻撃 | `CombatListener` → `DamageService` | `DamageService.applying`、`EnhancementManager.applyingSkillDamage` | 共通経路へ接続済み。 |
| Warrior skill | `WarriorSkillSupport` → `DamageService` | 上記に加え`SkillHitSession`とdepth | 共通経路へ接続済み。イベント後段で闘気bonusやhit確定処理がある。 |
| Painter通常攻撃 | `PainterCombatListener` → `DamageService` | `DamageService.isApplying` | 共通経路へ接続済み。 |
| Painter skill/DoT/passive | `SkillDamageService` → `DamageService` | cast UUID cacheと`isApplying` | 魔法系統として接続済み。攻撃タグ・属性は未伝達。 |
| Scout通常射撃・Volley | `RangedWeaponListener`/`ScoutController` → ArrowのBukkit damage | 専用PDCのみ | 共通DamageServiceを迂回する。passiveはeventへ最大HP10%を直接加算する。移行リスクが高い。 |
| Editor Mob通常攻撃 | `EditorCustomMonster`/`DamageService.applyMob` | `DamageService.applying` | 共通計算へ接続済み。 |
| Harbor Devourer攻撃 | `HarborDevourerBoss` → `Player.damage` | ボス固有state | 共通DamageServiceを迂回する箇所がある。 |
| Warrior遅延・splash等 | `WarriorEffectManager` → `DamageService`または`Player.damage` | 複数のUUID map/task | 大部分は共通化済みだが、遅延自己ダメージ等に直接適用が残る。 |

`DamageService.applying`はattacker/target UUIDの組をキーにしたstackで、共通適用中のBukkit eventを識別する。`finally`で解除するため通常例外には耐えるが、同じtickのlistener順序とdeprecated `DamageModifier` APIに強く依存する。

## データ保存経路

- `PlayerManager`、`PlayerData`、`Stats`、class、resource、cooldown、Warrior/Scout/Painter状態はメモリのみで、再起動時に失われる。
- `DataManager`は空であり、プレイヤーlevel/experience/statの永続化境界は未実装。
- Item PDC:
  - `projects:item_id`
  - `projects:enhancement_level`
  - `projects:weapon_broken`
  - `projects:weapon_attack_power_bonus`
  - `projects:weapon_attack_speed_bonus`
- Entity/projectile/tool PDC:
  - `projects:training_dummy`
  - `projects:custom_monster_id`
  - `projects:mob_editor_test`
  - `projects:mob_editor_test_owner`
  - `projects:scout_arrow`
  - `projects:dev_cc_mode`
  - `projects:dev_cc_duration_ticks`
- Attribute modifier key:
  - `projects:enhancement_attack_speed`
  - `projects:warrior_indomitable_attack_speed`
  - `projects:warrior_blood_battle_attack_speed`
  - `projects:hard_cc_movement`
  - `projects:status_slow`
- Mob Editor定義とhead定義はplugin data folder配下のYAMLへatomic saveされる。
- バランスoverrideもYAMLへ非同期保存され、main threadで再適用される。

これらのID、PDC key、channel名は移行時の互換契約として固定する。

## Plugin channel

- `projects:skill_input`
- `projects:hud_state_v2`
- `projects:loadout_req_v1`
- `projects:loadout_sel_v1`
- `projects:loadout_state_v1`
- `projects:balance_req_v1`
- `projects:balance_upd_v1`
- `projects:balance_act_v1`
- `projects:balance_state_v1`
- `projects:monster_ui_v1`
- `projects:mob_editor_req_v1`
- `projects:mob_editor_state_v1`
- `projects:telegraph_v1`
- `projects:telegraph_hello_v1`

## Tick処理と状態保持

- 1 tick: ranged auto-fire、Painterの一部skill task、Telegraph、Monster/Boss AIなど。
- 10 tick: Training Dummy session expiry等。
- 20 tick: Warrior闘気減衰、HUD/resource更新等。
- UUID keyの`HashMap`/`HashSet`が多く、quit/reset/disable時のclearに依存する。Player entityを直接保持するCC/status stateもentity再生成・world変更をtickで検証する。
- `ProjectSPlugin.onDisable`には主要managerのstop/clearがあるが、新規状態を追加する際は同じ終了境界へ接続しないとリークする。

## 既存テスト

Gradle `check`へ以下のassertベースJavaExecが接続されている。

- `BalanceMathTest` (`balanceUnitTest`)
- `CcFoundationTest` (`ccFoundationTest`)
- `TelegraphFoundationTest` (`telegraphFoundationTest`)
- `StatFoundationTest` (`statFoundationTest`)
- `MobEditorFoundationTest` (`mobEditorFoundationTest`)

`test`タスク自体にはJUnit test discoveryがなく、`failOnNoDiscoveredTests=false`である。Paper serverを起動せずに純粋部分、packet、YAML repositoryを検証する方式である。

## クールダウン回復速度0スタート

`COOLDOWN_RECOVERY_PERCENT`は装備、MOD、パッシブ等から得る追加Statであり、新規`PlayerData`と`Stats.reset()`後の値は0とする。旧コードにあった全プレイヤー共通の隠し30%短縮は廃止し、互換用の基礎回復速度も持たせない。計算式は`baseCooldown / (1 + recoveryPercent)`であるため、0スタートではbase cooldownがそのまま実効値になる。既存スキルの操作感が変わることは意図した仕様変更として受け入れ、base cooldownの再調整は別のバランス作業とする。

| skill ID | 現在のbase CD | 旧30%短縮時 | 新仕様0%時 | 差分 |
|---|---:|---:|---:|---:|
| `spin_slash` | 8.0秒 | 5.6秒 | 8.0秒 | +2.4秒 |
| `armor_break` | 6.0秒 | 4.2秒 | 6.0秒 | +1.8秒 |
| `warrior_charge` | 10.0秒 | 7.0秒 | 10.0秒 | +3.0秒 |
| `indomitable_spirit` | 20.0秒 | 14.0秒 | 20.0秒 | +6.0秒 |
| `end_war_strike` | 45.0秒 | 31.5秒 | 45.0秒 | +13.5秒 |

旧名`PlayerData.getCooldownReduction()`は外部バイナリ／ソース互換のためだけに残す。返す値は短縮率ではなく回復速度Statであり、内部コードは`getCooldownRecoveryPercent()`だけを使用する。

## 壊れやすい箇所

1. `ProjectSPlugin`の手動配線・listener/channel登録順。
2. `DamageService.removeBukkitMitigation`のdeprecated Bukkit `DamageModifier`依存。
3. 共通DamageService経路とScout/Boss直接damage経路の併存。
4. Warriorのevent listener、skill context、pre-scaled depth、遅延damageの相互作用。
5. UUID mapのcleanup漏れとscheduler taskのキャンセル漏れ。
6. Item PDCとAttribute modifier key。名前変更は既存アイテム互換性を壊す。
7. Mob Editorのschema version、packet v1、revision check、atomic save。
8. `DamageMode`、defense constant、reduction cap、lifesteal等のコード固定数値。
9. `Stats`は可変でsnapshot取得時点が統一されておらず、将来のDoT/共有属性状態では発生時Statと現在Statの混同が起こり得る。

## 互換層が必要な箇所

- 既存Bukkit依存`DamageRequest`は削除せず、将来の純粋request/snapshotへ変換するadapterとして段階移行する。
- `DamageService`は既存skill APIとevent再入防止を維持したまま、内部計算入力の組み立てだけを交換する。
- Scout arrow、Boss固有攻撃、Warrior event bonusは個別adapterと回帰テストなしに一括移行しない。
- `StatType`の既存flat penetrationを削除せず、Beta正式計算から無効化する場合も読み取り互換を残す。
- Enhancement PDCは装備メタデータ導入後も旧アイテムを読み込めるmigration readerが必要。
- Mob Editor schemaとnetwork protocolはversioned migrationを使い、既存v1を上書きしない。

## 設計書との主な矛盾

- 設計は攻撃系統を物理・魔法の二つとするが、コードは`DamageType.TRUE`を正式経路で扱う。
- 設計の基本クリティカル倍率は150%だが、`DamageMode.PVE`とMob既定値は175%。PVPのみ150%。
- 設計は固定値貫通を不採用とするが、`StatType`、`DamageCalculator`、`DamageService`に物理・魔法のflat penetrationがある。
- 設計上の攻撃タグ、火・氷・雷属性値、属性反映率、複数属性は既存DamageRequest/Calculatorに存在しない。
- `status-effects.md`後半の氷仕様は一部を未決定としているが、より専門的な`ice-system.md`では8%増幅、氷砕き125%、氷核50%、残冷気40%、再凍結耐性等が決定済みである。正本間の優先順位を明記する必要がある。
- 設計ではBeta上限Lv45だが、`PlayerData.setCombatLevel`は1〜999の開発用範囲で、経験値・到達制御はない。
- 設計の装備Tier/ILv/rarity/MOD slotと、既存の+0〜+30強化は統合されていない。

## 現時点で不明・設計確認が必要な点

- TRUE damageをBeta正式仕様に残すか、既存互換専用にするか。
- 既存flat penetration値をどう移行・表示するか。
- `status-effects.md`と`ice-system.md`が食い違う項目の正本優先順位。
- 既存PvE 175% criticalを150%へいつ、どの互換・バランス手順で移すか。
- `DamageKind.SECONDARY`に相当する攻撃のcritical、lifesteal、凍結増幅の既定可否。
- 火の共有貢献を古いstackから消費するか比例消費するか。
- 雷属性の詳細、MOD数値幅、passive tree構造、経済詳細は設計未完了。
