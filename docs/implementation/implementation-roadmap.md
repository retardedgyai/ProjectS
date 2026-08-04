# ProjectS incremental implementation roadmap

## 原則

- 各Phaseは単独でbuild、test、commit、rollbackできる差分にする。
- 既存経路を新基盤へ一括移行しない。1経路ずつadapterとgolden testを追加する。
- PDC key、item/skill/class ID、channel名、Mob Editor schema v1を変更しない。
- 未決定数値は設定境界へ隔離するか`BLOCKED / DESIGN REQUIRED`として止める。
- client変更が必要なPhaseはserver protocol versionと旧client fallbackを先に定義する。

## Phase 0: 現状監査とbaseline

成果:

- 最新設計書の取り込み。
- current code audit、coverage matrix、roadmap、combat gap analysis。
- `clean check -PskipAutoStart` baseline。

完了条件: 文書と既存testが成功し、gameplay codeを変更しない。今回実施。

## Phase 1: 既存戦闘基盤の純粋metadata gap

小分け:

1. 既存`DamageType`/`DamageKind`/`DamageCalculator`の回帰testを独立JavaExecへ追加。
2. 確定済みattack tag、Beta属性、immutable属性値/反映率metadataのみ追加。
3. DamageServiceやskillへ接続しない。

完了条件: 既存挙動ゼロ変更、finite/negative/immutability/determinism test成功。今回のコード上限。

## Phase 2: 既存damage経路への互換adapter

進捗（2026-08-05）:

- starter sword通常攻撃は限定authoritative切替まで完了。
- Warrior `spin_slash`だけは`SKILL / MELEE / PHYSICAL` metadataと、legacyを
  一回だけ適用する非authoritative shadow検証経路まで追加。
- 他Warrior skillは`AttackMetadata.EMPTY`のまま。SpinSlash authoritative化は
  実サーバー比較の合格後に別Phaseで判断する。

前提となる設計判断:

- TRUE damageの正式扱い。
- flat penetrationのlegacy扱い。
- critical 175%→150%の移行方法。
- SECONDARYのcritical/lifesteal/freeze増幅policy。

小分け:

1. Bukkit非依存attacker/target snapshotと型付きbreakdownを追加。
2. 現在の`DamageRequest`からpure inputへ変換するadapterを`DamageService`内部へ追加。
3. sword通常攻撃だけgolden test付きで新内部経路へ切替（完了）。
4. Warrior skill、Painterを一系統ずつ移行（SpinSlash shadowまで進行）。
5. ScoutとBossは別commitで最後に移行。projectile/event semanticsを保持する。

rollback: 各経路のfeature switchまたは旧adapter呼び出しへ戻せる構造にする。

## Phase 3: Player Stat集計と永続化境界

1. Stat source（base/class/equipment/MOD/temporary）の順序を決定。
2. immutable aggregated snapshotを作成。
3. versioned player data repositoryを追加し、level/resource/statのうち保存対象を明示。
4. quit/disable/reconnect round-trip test。

最大リスク: 現在メモリだけの状態を誤って永続化し、temporary buffを恒久化すること。

## Phase 4: 装備metadata・Tier・ILv・rarity

1. 既存`item_id`とenhancement PDCを包むversioned metadata reader/writer。
2. Tier/ILv/rarity/MOD slotのpure validation。
3. 旧itemへ安全defaultを与えるmigration view。
4. equip level制限。ring x2/neck等のUI・slotは別commit。

`BLOCKED / DESIGN REQUIRED`: 防具重量境界、旧武器のTier/ILv default。

## Phase 5: データ駆動MOD基盤

1. MOD definition schema、rank、部位、tag、calculation layer。
2. loader/validatorと未知IDの隔離。
3. item slot保存とread-only表示。
4. 付与・再抽選・削除transaction。

加工素材・費用・抽選weightが未決定なら4で停止する。

## Phase 6: 属性共通基盤

1. Phase 1 metadataをDamage snapshotへ接続。
2. 複数属性直撃を元の物理/魔法系統で計算。
3. PvE weakness一回だけ、PvP weaknessなし。
4. Mob Editorへversioned属性/weakness編集を追加。

属性状態異常はまだ接続しない。

## Phase 7: 火属性

1. pureなburn contribution/stack stateと設定validation。
2. 通常mob/ボス閾値、減衰、10→爆燃→3のtest。
3. 物理/魔法/Player貢献分割。
4. scheduler/runtime adapter、UI、balance editor。

`BLOCKED / DESIGN REQUIRED`: stack貢献消費順、tick係数、1攻撃爆燃上限、PvP補正。

## Phase 8: 氷属性

1. 設計正本優先順位を確定。
2. pure shared chill contribution、freeze、ice core、re-freeze resistance。
3. direct damage 8%、SHATTER一回、成立hit除外、single-target、残冷気40%をtest。
4. CC/status adapterとMob category設定。

Boss freeze挙動とchill減衰が未決定ならruntime接続前に停止する。

## Phase 9: Level・experience・passive基盤

1. Lv1〜45 curveとsource/level差減衰をデータ化。
2. versioned XP保存とparty非依存の単独報酬。
3. passive node schema/validator、point grant/refund。
4. aggregated Statへ接続。

passive tree規模・構造が未決定ならnode runtimeは実装しない。

## Phase 10: Party・共有貢献

1. invite/join/leave/leader/reconnect。
2. HP HUDとparty chat。
3. 距離付きXP、quest/boss participation。
4. 火/氷共有貢献のdamage/reward attribution。

最大リスク: 二重報酬、退出直前の貢献消失、別world/距離境界。

## Phase 11: 採取・refine・製造

1. resource/recipe/item transactionのpure model。
2. 採取MVP。
3. refine品質と素材変換。
4. 装備base製造（Tier/ILv/rarity/crafter name）。

各生活機能を独立commitとし、marketを前提にしない。

## Phase 12: Tier昇格・強化・修理統合

1. 既存Enhancement PDCのcharacterization test。
2. 新装備metadataとのread/write adapter。
3. Tier昇格を設計確定後に追加。
4. 強化・破損・修理のUIとbalance dataを統合。

既存+0〜30 itemを破損させないmigration fixtureが必須。

## Phase 13: Mob Editor・balance管理拡張

1. Skill/Drop schemaをv2として追加しv1 reader維持。
2. 攻撃tag/属性/weakness編集。
3. fire/ice category defaultと個別override。
4. validation、preview、rollback、history。

server/client protocolはversionを上げ、v1 channelを即時破棄しない。

## Phase 14: Content統合とendgame

1. Lv45 + final boss kill gate。
2. dungeon/elite/boss reward loop。
3. economy/market transaction。
4. Beta endgame unlockと繰り返しcontent。

経済仕様が未決定の間は、取引可能itemやmarketを推測実装しない。

## Post-Beta候補

- 光・闇属性。
- 世界横断の本格魔術研究・設備・都市連携。
- T4以降、Lv80〜120。
- 大規模guild/territory等。

## 次に着手する安全な移行

Phase 1完了後の最初の作業は、`DamageService.calculate`が現在生成している数値をそのまま保持するBukkit非依存snapshotとcharacterization testである。計算式や実適用は変えず、sword通常攻撃の現行入力・出力を固定してからadapterを導入する。Scout、Boss、火、氷への接続は行わない。
