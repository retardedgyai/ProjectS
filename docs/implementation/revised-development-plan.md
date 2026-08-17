# ProjectS Revised Development Plan

更新: 2026-08-18

## 方針

ProjectSはVFX Editor中心ではなく、プレイヤーがLv1から遊び始め、装備を更新し、戦闘・採取・製造・パーティー・クエストを経て最初の島のエンドゲームへ進めるMMOとして開発する。

VFX EditorとWorld Builderは凍結する。既存の試作コードと文書は履歴・将来候補として残すが、本線の開発対象にしない。必要なVFXは本線のAbility/Presentation Runtimeの受け手として後から追加する。

## 完成目標

新規プレイヤーが次のループを壊れずに体験できること。

```text
開始
-> 武器を試す
-> Mobを倒す
-> XP・素材・報酬を得る
-> 装備を更新する
-> より強い敵・ボスへ進む
-> 採取・製造・MOD・パーティーでビルドを伸ばす
-> 最初の島の最終ボスを倒す
-> Lv45エンドゲームを解放する
```

## 現状認識

### 既に使える基盤

- 共通DamageService、DamageCalculator、StatCalculator
- Warrior/Painterの既存戦闘経路
- SpinSlashのDamage metadata shadow
- Telegraphのサーバー権威処理とClient表示
- Mob Editorの定義、検証、revision、atomic save、test spawn
- Enhancement +0〜+30、破損、修理、PDC
- Balance UIとYAML override
- Ability Runtime v0.1のwait、telegraph、damage
- ClientのHUD、通信、Mob Editor、Balance UI

### 大きな未完了

- PlayerData、level、XP、resource、class、statの永続化
- T1〜T3、ILv、rarity、8装備枠、equipment metadata
- MOD定義、装備への付与、再抽選、削除、transaction
- Ability RuntimeとPlayer Skill/Mob/Bossの実ゲーム接続
- CombatShapeとProjectile/Targetingの共通化
- 火・氷・雷の正式なDamage/状態異常接続
- Gathering、Refining、Crafting
- Tier promotion、Enhancement v2、Repair v2の統合
- Party、Quest、Participation、Reward claim
- Mob EditorのSkill/Drop/属性編集
- Capability UIの本番producer接続
- 最初の港町、序盤導線、最終ボス、エンドゲーム報酬ループ

## 凍結対象

- 旧Skill Editorの機能追加
- VFX World Builderの機能追加
- VFX専用の大画面Studio改修
- VFX Builderをスキル保存形式へ接続する作業

既存の未コミット差分は勝手に削除しない。本線作業ではSkill Editor/VFX Builderのファイルを変更しない。

## 新しい実装順

### Milestone 0: Baselineと決定事項の固定

目的は、既存挙動を壊さずに今後の作業境界を固定すること。

- Server/Clientの`check`と`build`を記録
- Acceptance Matrixの未達項目を実装済み/未実装/blockedに分類
- 既存PDC、channel、Mob schema v1、item/skill/class IDを固定
- VFX作業を凍結対象として記録
- 未決定のXP curve、MOD weight、drop、party XP、marketを実装しない

完了条件: baseline test成功、破壊的変更なし、次Milestoneのowned filesが明確。

### Milestone 1: Player Progression and Persistence

最初に、再起動してもプレイヤーの進行が失われない境界を作る。

- versioned Player aggregate
- level、XP、combat resource、class unlockの保存対象を明示
- immutable load snapshot
- atomic save、backup、unknown version isolation
- join、quit、disconnect、server stop、reconnect round trip
- temporary buff、cooldown、cast、target、UI stateは保存しない

完了条件: 再起動後に進行が戻り、破損・未知version・中断書き込みでデータを失わない。

### Milestone 2: Equipment Foundation

- 8装備枠
- T1〜T3
- ILv 1〜45
- rarityとMOD slot数
- base statsとcrafter name
- legacy PDC reader/writer
- 旧武器のread-only legacy view
- equip validationとlevel restriction

強化とMOD付与は、metadata read/writeとvalidationが安定してから接続する。

### Milestone 3: Combat and Shared Ability Runtime

- AbilityDefinitionとAbilityRuntime v0.1の既存契約を維持
- Player/Mob/Bossが同じdefinitionを使える入口を作る
- Projectile action、travel、collision、impactを追加
- CombatShape: sphere、cone、line、ringを追加
- DamageServiceを唯一のDamage適用境界として使う
- Skill 1つをCast → Travel → Hit → Damage → Impactまで完成
- VFXはserver authorityのpresentation cueとして送る

最初の縦切りは火球ではなく、既存仕様に影響が少ない独立Skill IDで作る。既存SpinSlashを置換しない。

### Milestone 4: Combat Metadata and Elements

- attack tags
- immutable attacker/target snapshots
- typed damage breakdown
- physical/magical系統と火/氷/雷属性値
- 火の燃焼/爆燃を既存Damageと分離したpure stateとして接続
- 氷の冷気/凍結/氷砕き
- 雷の仕様が決定してから実装

既存critical 175%、TRUE damage、flat penetrationはcharacterization testなしに変更しない。

### Milestone 5: Equipment MOD and Enhancement Integration

- MOD definition schema、rank、tag、slot rule
- immutable equipment/combat snapshot
- deterministic modifier order
- apply/remove/reroll transaction
- Tier promotion
- enhancement、break、repairの新metadata adapter
- feature flagはfalseが既定

### Milestone 6: Gathering, Refining, Crafting

- resource definition
- gathering MVP
- refining recipes
- equipment base crafting
- inventory full、logout、duplicate request、server stop rollback
- craft結果にTier、ILv、rarity、crafter nameを付与

### Milestone 7: Party, Quest, and Rewards

- invite、join、leave、leader、reconnect
- party HUD/chat
- participation attribution
- quest trigger/condition/action
- idempotent reward claim
- 二重報酬、退出直前、距離、world境界テスト

### Milestone 8: Mob Content and Client UI

- Mob schema v2を追加しv1 readerを維持
- Skill/Drop/attribute/weakness編集
- Ability ID assignment
- force cast、cast context inspect、validation/reload
- capability handshakeとold-client fallback

### Milestone 9: First Island Vertical Content

- 第一港町の短い導線
- 武器3種を試せる序盤
- 通常Mob、elite、training dummy
- 採取地点、refine、craft導線
- クエストと報酬
- 最終ボスとLv45 gate
- クリア後のrepeatable endgame loop

## 作業ルール

- 1つのMilestoneをさらにbounded taskへ分割する
- pure model/testを先に作り、Bukkit接続はadapterとして後から追加する
- 新機能はdisabled flagを既定にする
- 既存経路を一括移行しない
- 新しいUUID mapやscheduler taskには必ずcleanupを持たせる
- persistence、transaction、permission、rollbackを機能と同時に作る
- VFXはGameplay authorityにならない
- 外部サーバーの実装をコピーせず、概念だけProjectSへ適用する

## 直近の次タスク

次に実装するのはMilestone 1の最初のbounded task。

**Player Progression Persistence Contract**

- 保存対象のimmutable recordを定義
- schema versionを定義
- repositoryのload/save契約を定義
- atomic writeとunknown version test
- 既存PlayerDataのruntime値はまだGameplayへ切り替えない

このタスクが終わるまで、Ability RuntimeのProjectile拡張、VFX Editor、MOD適用、属性実ゲーム接続は着手しない。

## 完了判定

Beta完成は、クラスやUIの数ではなく、次を満たすこと。

- Lv1からLv45まで進行可能
- 再接続・再起動後も進行とアイテムが保持される
- 戦闘、採取、精製、製造、装備、MOD、強化、修理が繋がる
- パーティー、クエスト、報酬が二重付与なく動く
- 最終ボス撃破でエンドゲームが解放される
- 旧PDC、旧player data、Mob schema v1、既存channelにfallbackがある
- feature flagを無効化して旧挙動へrollbackできる
- Unit、Integration、Paper manual、Multiplayer、Persistence、Rollback、Securityの該当セルが合格する
