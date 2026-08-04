# ProjectS specification coverage matrix

状態定義:

- `IMPLEMENTED`: 決定済み仕様が主要経路で動作する。
- `PARTIAL`: 基盤または限定経路のみ存在する。
- `NOT_IMPLEMENTED`: 対応実装が存在しない。
- `BLOCKED_BY_DESIGN`: 実装に必要な決定が不足または正本間で矛盾する。
- `DEFERRED_POST_BETA`: 設計上Beta対象外。

この表は「似たコードがある」だけで`IMPLEMENTED`にしない。設計どおりのデータ、計算、保存、実ゲーム接続、回帰テストが揃うかで判定する。

| 領域・仕様 | 参照設計書 | 関連する既存クラス | 状態 | 推奨Phase | 破壊リスク | 必要テスト |
| --- | --- | --- | --- | --- | --- | --- |
| 物理・魔法の攻撃系統 | `mod-system.md`、`decision-status.md` | `DamageType`、`DamageCalculator`、`DamageService`、`Weapon`、`Armor` | `IMPLEMENTED` | 1-2 | 中: event防御との二重計算 | 物理/魔法が別防御を参照、属性追加後も系統維持 |
| TRUE damage | 設計に正式採用記述なし | `DamageType.TRUE`、`DamageCalculator` | `BLOCKED_BY_DESIGN` | 2 | 高: 既存Mob/API互換 | 既存TRUE回帰、正式対象外にする場合のadapter |
| 通常攻撃・skill・DoT・反射区分 | `mod-system.md`、`ice-system.md` | `DamageKind`、`SkillDamageService` | `PARTIAL` | 1-2 | 中: critical/lifesteal policy | 各kindのcritical、lifesteal、凍結増幅対象 |
| SECONDARY/自動発生区分 | `ice-system.md` | `DamageKind` | `BLOCKED_BY_DESIGN` | 2 | 中 | 追撃・連鎖・爆燃の区分と増幅除外 |
| 攻撃タグ | `mod-system.md`、`ice-system.md` | `AttackMetadata`、`WarriorAttackMetadata` | `PARTIAL` | 1-2 | 低（限定metadata）/高（接続時） | starter swordとSpinSlashのみ接続。他skillはEMPTY、完全一致と不変性を回帰test |
| 共通damage純粋計算 | `mod-system.md` | `DamageCalculator`、`DamageCalculationSnapshot`、`DamageShadowComparator` | `PARTIAL` | 1-2 | 中 | starter swordとSpinSlashのlegacy/pure parity、finite、epsilon、array defensive copy |
| attacker Stat snapshot | `status-effects.md`、`ice-system.md` | `DamageOffenseSnapshot`、`Stats.snapshot` | `PARTIAL` | 2-3 | 高: DoT/共有貢献の時点差 | 発生時Stat固定、遅延damage二重強化防止 |
| target defense snapshot | `mod-system.md` | `DamageService`内の一時計算のみ | `NOT_IMPLEMENTED` | 2-3 | 高 | Player/Mob別snapshot、物理/魔法防御 |
| damage breakdown/計算step | `ice-system.md` | `DamageResult`のflat field群 | `PARTIAL` | 2 | 中: 表示・集計互換 | 各計算層、属性別、critical前後、penetration前後 |
| critical率・倍率 | `mod-system.md` | `CriticalHitResolver`、`DamageMode`、`StatType` | `PARTIAL` | 2 | 高: PvE既定175%と設計150%の差 | cast単位固定、base+increase、移行前後golden test |
| 物理・魔法割合貫通 | `mod-system.md` | `StatType`、`StatCalculator`、`DamageService` | `IMPLEMENTED` | 2 | 中 | 対応系統だけに適用、属性でも元系統を使用 |
| 固定値貫通 | `mod-system.md`で不採用 | `PHYSICAL_PENETRATION_FLAT`等 | `BLOCKED_BY_DESIGN` | 2-3 | 高: 既存値の扱い | legacy値読み取り、正式式からの隔離 |
| Player Stat集計 | `mod-system.md`、`equipment-system.md` | `Stats`、`StatType`、`DamageService` | `PARTIAL` | 3 | 高 | 装備/MOD/class sourceの順序とsnapshot |
| Player Stat永続化 | `beta-system-decisions.md` | `PlayerData`、空の`DataManager` | `NOT_IMPLEMENTED` | 3 | 高: data loss/migration | round-trip、schema version、旧データdefault |
| レベル | `beta-system-decisions.md`、`game-loop.md` | `PlayerData.combatLevel`、dev command | `PARTIAL` | 9 | 中 | Lv1〜45境界、UI、再接続保存 |
| 経験値・レベル差減衰 | `beta-system-decisions.md` | 実装なし | `NOT_IMPLEMENTED` | 9 | 中 | XP curve、格下減衰、報酬source |
| パッシブポイント・大型tree | `beta-system-decisions.md` | Painter固有passiveは別物 | `NOT_IMPLEMENTED` | 9 | 高 | node validation、refund、保存、Stat反映 |
| 装備8枠 | `equipment-system.md` | Bukkit標準装備とcustom weaponのみ | `NOT_IMPLEMENTED` | 4 | 高 | 2 ring、neck、equip validation、再接続 |
| Tier T1〜T3 | `equipment-system.md` | 実装なし | `NOT_IMPLEMENTED` | 4 | 中 | level帯、tier migration、表示 |
| ILv=装備可能level | `equipment-system.md` | 実装なし | `NOT_IMPLEMENTED` | 4 | 高 | 装備拒否、level down、旧item default |
| rarity=MOD slot 1〜4 | `equipment-system.md`、`mod-system.md` | 実装なし | `NOT_IMPLEMENTED` | 4 | 中 | rarity/slot mapping、serialization |
| 軽・中・重装と重量 | `equipment-system.md` | `Armor`は防御値のみ | `BLOCKED_BY_DESIGN` | 4 | 中 | 重量境界が未決定、混在装備集計 |
| データ駆動MOD定義 | `mod-system.md` | 実装なし | `NOT_IMPLEMENTED` | 5 | 高 | schema、rank、部位、tag、weight validation |
| MOD付与・再抽選・削除 | `beta-system-decisions.md` | 実装なし | `BLOCKED_BY_DESIGN` | 5 | 高: item mutation/economy | 未決定素材/加工rule、transaction/rollback |
| 火属性値・直撃 | `mod-system.md`、`status-effects.md` | 実装なし | `NOT_IMPLEMENTED` | 6-7 | 高 | scaling、元系統防御、weakness、複数属性 |
| 燃焼値・共有stack・爆燃 | `status-effects.md` | 汎用`BURN` enumのみ | `NOT_IMPLEMENTED` | 7 | 高: party attribution/tick | stack変換、減衰、貢献分割、爆燃上限 |
| 氷属性値・冷気・凍結 | `ice-system.md` | 汎用SLOW/hard CCのみ | `NOT_IMPLEMENTED` | 6-8 | 高 | shared gauge、段階slow、freeze immunity |
| SHATTER・氷核・残冷気 | `ice-system.md` | 実装なし | `NOT_IMPLEMENTED` | 8 | 高 | 一凍結一回、成立hit除外、single target、40%比例縮小 |
| 氷の一部正本差異 | `status-effects.md`対`ice-system.md` | 実装なし | `BLOCKED_BY_DESIGN` | 8 | 中 | 正本優先順位決定後のacceptance test |
| 雷属性 | `mod-system.md`、`status-effects.md` | Painterの雷演出/skillは属性systemではない | `BLOCKED_BY_DESIGN` | 6以降 | 高 | 詳細未決定。属性metadata保持だけ先行可能 |
| 属性弱点（PvEのみ） | `mod-system.md` | 実装なし | `PARTIAL` | 6 | 高 | 複数弱点倍率非重複、PvP除外 |
| 光・闇属性 | `mod-system.md`、`magic-system.md` | 実装なし | `DEFERRED_POST_BETA` | Post-Beta | 低 | Beta enumへ混入しないこと |
| Party基本機能 | `game-loop.md`、`beta-system-decisions.md` | 実装なし | `NOT_IMPLEMENTED` | 10 | 高 | invite/leave/leader/reconnect/chat |
| Party XP・参加・共有貢献 | 同上、`status-effects.md`、`ice-system.md` | 実装なし | `PARTIAL` | 10 | 高 | 距離、重複報酬、燃焼/冷気attribution |
| 通常Mob基盤 | `beta-system-decisions.md` | `MonsterManager`、`CustomMonster`、Editor Mob | `PARTIAL` | 13 | 中 | spawn/reset/AI/damage/UI integration |
| Boss・固有AI・予兆 | `beta-system-decisions.md` | `HarborDevourerBoss`、`TelegraphManager` | `PARTIAL` | 13-14 | 高 | reset、phase、CC、common damage migration |
| Telegraph基盤 | `beta-system-decisions.md` | `combat.telegraph`、client channel | `IMPLEMENTED` | 0完了 | 中 | geometry、timeline、packet、fallback |
| Mob Editor MVP | `beta-system-decisions.md`、`decision-status.md` | `monster.editor`、`MobEditorChannel` | `PARTIAL` | 13 | 高: schema/network | atomic save、revision、validation、test spawnは既存test済み |
| Mob Editor Skill/Drop編集 | 同上 | 実装なし | `NOT_IMPLEMENTED` | 13 | 高 | schema migration、packet bounds、runtime apply |
| 採取 | `game-loop.md`、`magic-system.md` | 実装なし | `BLOCKED_BY_DESIGN` | 11 | 中 | resource/tool/proficiency詳細未決定 |
| リファイン | `beta-system-decisions.md` | 実装なし | `BLOCKED_BY_DESIGN` | 11 | 中 | recipe、quality、transaction詳細未決定 |
| 製造 | `equipment-system.md` | 実装なし | `PARTIAL` | 11 | 高 | ILv/rarity roll、crafter name、atomic inventory transaction |
| Tier昇格 | 総合設計で詳細なし | 実装なし | `BLOCKED_BY_DESIGN` | 12 | 高 | 材料、成功、item identityの決定が必要 |
| 武器強化 | 総合設計の未決定事項 | `EnhancementManager`、`EnhancementListener` | `PARTIAL` | 12 | 高: 既存PDCと数値 | +0〜30回帰、素材消費、成功/失敗 |
| 破損・修理 | `beta-system-decisions.md`で詳細未決定 | `EnhancementManager`、`EnhancementListener` | `PARTIAL` | 12 | 高 | PDC互換、破損中使用禁止、修理cost |
| 経済・market | `game-loop.md` | 実装なし | `BLOCKED_BY_DESIGN` | 14 | 高: duplication/transaction | currency、listing、tax、rollback、concurrency |
| Lv45 final boss gate | `game-loop.md`、`beta-system-decisions.md` | bossはあるがgate/endgame stateなし | `NOT_IMPLEMENTED` | 14 | 高 | Lv45 + boss kill、party credit、保存 |
| Beta endgame content | 同上 | 実装なし | `NOT_IMPLEMENTED` | 14 | 高 | unlock persistence、repeatable reward、economy |
| 世界横断の魔術 | `magic-system.md` | Painter Mageと属性演出のみ | `BLOCKED_BY_DESIGN` | Post-Beta/個別Phase | 高 | 採取・加工・戦闘を分離したprogression |

## 現在の優先判断

設計文書の開発優先順位はMob Editor UIを最優先としている一方、今回の作業は既存基盤の安全監査に限定されている。次の実装では、このmatrixをそのまま一括消化せず、各Phase開始時に対象仕様の`BLOCKED_BY_DESIGN`を解消してから進める。
