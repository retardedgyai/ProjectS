# ProjectS Economy / Equipment v1 現行仕様

最終更新: 2026-08-07

この文書は、ProjectS の装備・強化・生産・Gold 経済について、現時点で会話上合意した内容をまとめた正本候補である。

- **構造決定**: ゲーム設計として採用する方針。
- **仮値**: v1 実装・試遊用の初期値。必ず config / data 側から後で変更できるようにする。
- 数値バランスは実際に遊んだ後で調整する。現段階の優先は「構造を作り、実際に触れること」。

---

## 1. 装備成長の基本軸

### 縦方向

- Tier / ILv
- Enhancement `+0 ～ +30`

### 横方向

- Rarity
- MOD
- Skill / Element / AttackTag / Crit / Cooldown などのビルド構成

### Quality

**Quality v1 は廃止する。**

Quality と Enhancement がどちらも装備 Base Stat を伸ばす役割になり、縦成長が二重化するため。
既存コード上の予約フィールドは即時削除を必須とせず、Production gameplay では使用しない。

---

## 2. Tier / ILv

Beta の基本帯:

- T1: ILv 1–15
- T2: ILv 16–30
- T3: ILv 31–45

Tier / ILv が装備の世代・基礎性能を決定する。

### Tier Promotion

**構造決定**

- `T1 → T2 → T3`
- 飛び級なし
- 成功率 100%
- 必要物: 現在装備 + 次 Tier 用昇格素材 + Gold
- UUID 維持
- Rarity 維持
- MOD の種類 / Rank / Roll 維持
- Crafter 維持
- Custom Name 維持
- Enhancement 値は維持する方向
- 基礎性能と ILv は次 Tier 側へ更新
- MOD Rank は自動昇格しない

### 強化値持ち越しの経済対策

低 Tier で安く `+25` 等まで強化して上位 Tier に持ち込む抜け道を作らない。
Tier Promotion 時に、保持する Enhancement 値に応じた **継承差額** を請求する。

- Gold 差額
- Target Tier の Enhancement Stone 等
- 高 Rarity を持ち越す場合も Tier Promotion 費を増加させる

正確な式 / 倍率は仮値として後で調整する。

---

## 3. Rarity

Rarity は Base Stat を直接強化しない。
**役割は MOD slot capacity のみ。**

| Rarity | MOD Slot |
|---|---:|
| Common | 1 |
| Uncommon | 2 |
| Rare | 3 |
| Epic | 4 |

### Rarity Promotion

**構造決定**

- `Common → Uncommon → Rare → Epic`
- 一段階ずつ。飛び級なし
- 成功率 100%
- Gold + Rarity Catalyst を消費
- 昇格すると MOD slot が 1 つ増える
- 新しい slot は EMPTY。MOD は自動付与しない
- Quality は廃止済みのため対象外
- UUID / Crafter / 既存 MOD / Enhancement / Broken state 等は保持
- Quality のような素体値によって費用を変えない
- Gold はシステムへ消滅する

高 Rarity を低 Tier で安く作って Tier Promotion する経済 bypass を防ぐため、Tier Promotion cost 側を carried Rarity に応じて増やす。

例示用の仮倍率:

- Common ×1.0
- Uncommon ×1.25 前後
- Rare ×1.6 前後
- Epic ×2.2 前後

これは **仮値**。

---

## 4. MOD v1

### Capacity / Rank

- Rarity の MOD capacity は 1 / 2 / 3 / 4
- MOD Rank は `R1 / R2 / R3`
- T1 / ILv 1–15: 最大 R1
- T2 / ILv 16–30: 最大 R2
- T3 / ILv 31–45: 最大 R3

高 Tier でも低 Rank MOD material を使用可能。

- T2: R1 / R2 を受け入れる
- T3: R1 / R2 / R3 を受け入れる
- Material Rank が実際に付く MOD Rank を決める

これにより低 Rank MOD material が高 Tier 時代にも市場価値を持てる。

### Craft / MOD 挿入

- Craft 時に 0 ～ capacity 個の MOD Core / Material を挿入できる方向
- 1 MOD material = 1 slot
- v1 では post-craft reroll を前提にしない
- MOD material は exact MOD ではなく **MOD Family** を指定する
- 同一 exact MOD は同じ装備に重複不可
- 同じ Family の別 MOD は複数共存可能
- 同 Family material を複数投入した場合、候補から no-repeat 選択
- Prefix / Suffix は v1 では使用しない

### Crafting Proficiency と MOD

Crafting Proficiency は v1 で MOD の type / rank / numeric roll に影響させない。
熟練度はレシピ解禁・高 Tier 生産・効率など、生産職の進行度として扱う。

### MOD Roll

- Family 内 weight: v1 は均等
- Numeric roll: 0.1 刻みで uniform
- Rare Boss / Special catalyst / MOD material は素材還元対象外

### AttackTag

既存 AttackTag を使用する。
複数の direct-damage modifier が同一攻撃に該当した場合、同じ `INCREASED_DIRECT_DAMAGE` layer 内で加算し、互いに乗算しない。

### Element interaction

- Fire direct damage MOD が Detonation へ二重適用されない
- Detonation は Detonation-specific MOD のみで追加強化
- Ice direct damage MOD が Shatter extra damage へ二重適用されない
- Shatter-specific MOD は Shatter extra damage のみを強化

### MOD Family / 初期カタログ

以下は **v1 仮値**。構造を優先し、実プレイ後に調整する。

#### 戦技

1. Melee Damage
   - R1: +3.0–5.0%
   - R2: +6.0–9.0%
   - R3: +10.0–14.0%
2. Projectile Damage: 同レンジ
3. Magic Damage: 同レンジ
4. Skill Damage
   - R1: +3.0–5.0%
   - R2: +6.0–8.0%
   - R3: +9.0–12.0%
5. Normal Attack Damage
   - R1: +4.0–6.0%
   - R2: +7.0–10.0%
   - R3: +11.0–15.0%

#### 精密

6. Critical Chance
   - R1: +1.0–1.5 pt
   - R2: +1.6–2.5 pt
   - R3: +2.6–4.0 pt
7. Critical Damage
   - R1: +5–8 pt
   - R2: +9–13 pt
   - R3: +14–20 pt
   - base critical 175% へ加算
8. Attack Speed
   - R1: +2–4%
   - R2: +4.5–7%
   - R3: +7.5–10%

#### 炎

9. Fire Damage: +3–5% / +6–9% / +10–14%
10. Fire Accumulation: +10–15% / +16–25% / +26–40%
11. Fire Detonation Damage: +8–12% / +13–20% / +21–30%

#### 氷

12. Ice Damage: +3–5% / +6–9% / +10–14%
13. Cold Accumulation: +10–15% / +16–25% / +26–40%
14. Shatter Damage: +8–12% / +13–20% / +21–30%

#### 循環

15. Cooldown Recovery: +3–5% / +6–8% / +9–12%
16. Lifesteal: +0.3–0.5 pt / +0.6–0.8 pt / +0.9–1.2 pt

Generic な「status effect +%」は v1 では使わず、Fire / Ice 等に分離する。

---

## 5. Enhancement v1

### 基本思想

Enhancement は最終ダメージへ後付けする単純倍率ではなく、**装備の Enhancement 対象 Base Stat を直接強化する縦成長軸**。

計算概念:

`Tier / ILv Base → Enhancement → Effective Base → Skill coefficient → MOD / AttackTag / Crit / Element / Build`

Enhancement が直接伸ばすものの例:

- Base Physical Attack
- Base Magical Attack
- Base Armor / Defense
- Base Resistance

Enhancement が直接伸ばさないものの例:

- MOD Crit Chance
- MOD Crit Damage
- Attack Speed
- Cooldown Recovery
- Lifesteal
- Fire / Cold Accumulation
- Detonation / Shatter 固有倍率

Detonation / Shatter 等が Weapon Base を式の一部で参照する場合、その Base 部分を通じた間接強化はあり得る。

### Build 方針

すべての Build が同じ Enhancement 依存度を持たないようにする。

- **縦スケーリング型**: 総火力のうち Enhanced Base 依存部分が大きい
- **ハイブリッド型**: Base と Element / proc / special effect の両方
- **横スケーリング型**: Status accumulation / Cooldown / proc / special mechanics 等の比重が高い

`+15` 前後でも良い MOD / Skill synergy によりエンドゲームを攻略できる余地を残す。
一方 `+25` 等の高強化を持つプレイヤーは、Base 反映率の高い Build で強みを最大化できる。

**最高難度を +25 / +30 必須前提にはしない。**

### Enhancement の位置付け

- +0～+10: 通常成長
- +11～+14: 終盤
- **+15: エンドゲーム入口**
- +16～+20: 本格エンドゲーム
- +21～+24: ハードコア投資
- **+25: 廃人級**
- +26～+29: 超極端な縦成長
- **+30: ほぼ存在しない伝説級**

### Base Stat 性能カーブ

**仮決定済み v1 カーブ**

| Enhancement | 1 段階の上昇 | 累計補正 | Effective Base |
|---:|---:|---:|---:|
| +0 | — | 0% | 100% |
| +1～+5 | +0.5% | +2.5% at +5 | 102.5% |
| +6～+10 | +1.0% | +7.5% at +10 | 107.5% |
| +11～+15 | +1.5% | **+15% at +15** | **115%** |
| +16～+20 | +2.5% | **+27.5% at +20** | **127.5%** |
| +21～+25 | +3.5% | **+45% at +25** | **145%** |
| +26～+30 | +5.0% | **+70% at +30** | **170%** |

この数値は v1 の開始値。後から config / data で変更可能にする。

---

## 6. Enhancement 成功 / 失敗 / Broken

### 共通ルール

- 成功: Enhancement +1
- 通常失敗: Enhancement 維持
- 強化値低下は **なし**
- 装備本体の完全消滅は **なし**
- Broken: Enhancement 値、Rarity、MOD、UUID、Crafter 等を保持したまま使用不可 / 修理待ち
- 成功 / 通常失敗 / Broken のいずれでも、その挑戦で支払った Gold / Stone は消費
- v1 では pity / 天井ゲージを入れない
- 「失敗しても強化値が下がらない」ことを主な救済とする

### 確率カーブ

以下を **v1 初期値** とする。

| 到達値 | Success | Broken | Normal Fail |
|---:|---:|---:|---:|
| +1～+5 | 100% | 0% | 0% |
| +6 | 90% | 0% | 10% |
| +7 | 85% | 0% | 15% |
| +8 | 80% | 0% | 20% |
| +9 | 75% | 0% | 25% |
| +10 | 70% | 0% | 30% |
| +11 | 60% | 3% | 37% |
| +12 | 50% | 5% | 45% |
| +13 | 40% | 7% | 53% |
| +14 | 32% | 10% | 58% |
| **+15** | **25%** | **15%** | **60%** |
| +16 | 20% | 20% | 60% |
| +17 | 18% | 25% | 57% |
| +18 | 16% | 30% | 54% |
| +19 | 14% | 35% | 51% |
| **+20** | **12%** | **40%** | **48%** |
| +21 | 10% | 45% | 45% |
| +22 | 8% | 50% | 42% |
| +23 | 6% | 55% | 39% |
| +24 | 5% | 60% | 35% |
| **+25** | **4%** | **60%** | **36%** |
| +26 | 3% | 60% | 37% |
| +27 | 2.5% | 60% | 37.5% |
| +28 | 2% | 60% | 38% |
| +29 | 1.5% | 60% | 38.5% |
| **+30** | **1%** | **60%** | **39%** |

狙いは、Broken を大きな **Item Sink trigger** にすること。

---

## 7. Repair / Item Sink

### Repair 基本ルール

Broken 本体は消滅させない。
修理時に量産装備を donor として完全消滅させる。

Donor 条件:

- 同 Tier
- 同 Item Family
- +0
- Unbroken
- Rarity / MOD は原則問わない

Repair success は 100%。

修理後に保持:

- Enhancement
- Rarity
- MOD
- UUID
- Crafter
- Custom Name
- その他 target identity / extension data

Broken のみ `false` に戻す。

### Donor 数

| Broken 装備の Enhancement | Donor 消費 |
|---:|---:|
| +11～+20 | 1 |
| +21～+24 | 2 |
| +25～+27 | 3 |
| +28～+29 | 4 |
| +30 | 5 |

高強化プレイヤーが Common 等の量産装備を大量消費することで、Crafting のハズレ / 安価品にも継続需要を作る。

---

## 8. Enhancement Core / Stone

### 供給構造

**構造決定**

`PvE → Enhancement Core`

`Gathering → 対応 Tier 素材`

`Refining → Core + Tier 素材 + 加工費 → Enhancement Stone`

`Player → Gold + Enhancement Stone → Enhancement`

### Tier

- T1 Core → T1 Stone → T1 equipment
- T2 Core → T2 Stone → T2 equipment
- T3 Core → T3 Stone → T3 equipment

Core / Stone の Tier conversion は v1 では行わない。
高 Tier Stone recipe は Refining proficiency により解禁する。

### Refining Proficiency

基本思想:

- 熟練度が上がると高 Tier recipe を作れるようになる
- 高 Tier Enhancement Stone もその一部
- 熟練度を戦闘性能の RNG 補正には使わない
- 将来的に efficiency / fee / throughput 等の副次的恩恵を追加可能

### Core PvE source

- Normal Mob: Fragment を低確率
- Elite: Fragment 確定 + Core chance
- Dungeon: 安定した Core source
- Boss: 完成 Core 確定 / 高効率
- Raid / World Boss: 将来の大量供給 / 特殊素材枠

初期案:

- `10 Fragment = 1 Enhancement Core`
- Normal Mob: 約 8% で Fragment ×1
- Elite: Fragment ×2–4 guaranteed + Core 約20%
- Dungeon: 1 run で概ね 2–2.5 Core 相当を目安
- Boss: Core ×1 以上 + Fragment

これらは **仮値**。

Fragment / Core / Stone は tradeable とする方向。

### Stone recipe

初期レシピ **仮値**:

`同 Tier Enhancement Core ×1 + 同 Tier Refined Mineral ×5 + Refining fee → Enhancement Stone ×10`

### Stone 消費量 / 1 attempt

| 到達値 | Stone |
|---:|---:|
| +1～+5 | 1 |
| +6～+10 | 2 |
| +11～+15 | 3 |
| +16～+20 | 5 |
| +21～+24 | 8 |
| +25 | 12 |
| +26 | 15 |
| +27 | 20 |
| +28 | 30 |
| +29 | 40 |
| +30 | 60 |

Success / Fail / Broken の全結果で消費。

---

## 9. Enhancement Gold Cost

実額はまだ固定しない。
各 Tier で「普通のプレイヤーが 1 時間で稼ぐ純 Gold」を `1H` として相対値で設計する。

### Enhancement fee / attempt

| 到達値 | Gold |
|---:|---:|
| +1～+5 | 0.005H |
| +6～+10 | 0.01H |
| +11～+15 | 0.03H |
| +16～+20 | 0.06H |
| +21～+24 | 0.12H |
| +25 | 0.25H |
| +26 | 0.40H |
| +27 | 0.60H |
| +28 | 0.90H |
| +29 | 1.40H |
| +30 | 2.50H |

### Repair fee

| Broken 帯 | Gold |
|---:|---:|
| +11～+15 | 0.02H |
| +16～+20 | 0.04H |
| +21～+24 | 0.08H |
| +25～+27 | 0.15H |
| +28～+29 | 0.30H |
| +30 | 0.50H |

これらは **仮値**。実 Gold 量は実際の Gold Faucet / 時給を計測後に設定する。

---

## 10. Gold Sink

主要 Sink:

- Market Tax
- Refine fee
- Craft fee
- Rarity Promotion
- Tier Promotion
- Enhancement
- Repair

Enhancement は Gold だけでなく、Stone / Core / Gathering material / donor equipment を同時に消費する大型 Sink とする。

---

## 11. Gold Faucet v1

Gold の新規生成源の **目標比率**:

| Source | Target |
|---|---:|
| Dungeon / Boss / PvE reward | 35% |
| Dynamic NPC Procurement | 30% |
| Quest / Bounty | 20% |
| Normal Mob / NPC vendor loot | 10% |
| World Event etc. | 5% |

これは固定配分ではない。
7 日等の期間で実際の Gold creation を観測し、目標レンジとして扱う。

Market player-to-player trade は Gold を生成しない。Market tax によりむしろ Sink になる。

---

## 12. Dynamic NPC Procurement

市場で供給過多になった一般品を、世界内 NPC / 都市が一時的に買い取る自動経済安定装置。

### 目的

- 余剰 item を完全消滅させる
- Gold Faucet と Item Sink を同時に発生させる
- 低 Tier material / mass-produced equipment の価値を維持する
- プレイヤー自身が市場調整へ参加できるようにする

### 対象

例:

- Gathering material
- Refined material
- 一般製造装備
- 一般消耗品
- 一部 Enhancement 関連一般素材

非対象:

- Rare Boss Drop
- MOD rare material
- Unique
- Event limited
- 超希少品

### Trigger

システムは少なくとも以下を監視する。

- 実際に成立した transaction price
- 24h / 7d 等の実売中央値
- Trade volume
- Market inventory
- Moving reference price

**出品価格だけでは判定しない。**
Market manipulation 対策のため、成立した取引を主データにする。

v1 trigger 例 **仮値**:

- current realized price < 7d reference × 70%
- AND market inventory > normal inventory × 250%

### Procurement order

例:

- 市場基準 100G
- 現在実売 55G
- NPC procurement 65G

市場で 55G で買ったプレイヤーが NPC に 65G で納品してもよい。
その行為自体が余剰 inventory を吸収する gameplay になる。

### Safety

- Order quantity cap
- Expiry
- Per-item Gold budget
- Server-wide procurement Gold budget
- 長期間低価格が続く場合、reference price 自体を徐々に下げる

**短期暴落は支えるが、永久価格保証はしない。**

### World presentation

単なる backend UI にせず、港町の Procurement Warehouse / Quartermaster / public board 等として世界内に表現する。

---

## 13. 経済ループ

基本循環:

`PvE → Core / MOD material / Gold source`

`Gathering → material`

`Refining → processed material / Enhancement Stone`

`Crafting → equipment`

`Market → player distribution`

`Enhancement → Gold + Stone sink`

`Broken → Repair → donor equipment + Gold sink`

`Dynamic NPC Procurement → surplus item sink + controlled Gold faucet`

上級者の高強化ほど低～中価格の製造品を大量に Repair donor として消費し、Crafting demand を作る。

---

## 14. 実装原則

1. 数値は可能な限り config / data driven とする。
2. 成功率、Broken 率、Enhancement bonus、Stone consumption、Gold fee、Procurement trigger / budget をコード定数へ固定しない。
3. v1 の数値は試遊開始用の baseline と考える。
4. 最初に playable structure を作り、実測から調整する。
5. 変更後も装備 identity / transaction safety / replay safety を崩さない。
6. MOD combat application は既存 Damage / AttackTag / Element rules と整合させる。

---

## 15. 現在の実装状態メモ

PR #31 staging slice では以下の player-visible flow が live Paper smoke PASS 済み:

`ore acquisition → Refine ×3 → T1 Craft → staging MOD → Inspect`

確認済み:

- ore 4 / ingot 0 / weapon 1
- UUID present
- T1 / ILv1
- MOD R1 present
- Enhancement +0
- Broken false

この staging behavior は Production balance の最終仕様ではない。
この文書の v1 design を今後 Production implementation へ接続していく。
