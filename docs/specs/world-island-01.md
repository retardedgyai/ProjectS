# ProjectS 第一島 / 港町 v0.1 現行仕様

最終更新: 2026-08-07

この文書は、ProjectS 第一島・港町・外世界の地形 / 導線 / NPC / POI 構成について、現時点で決定した内容をまとめる。

細かい敵 Lv、Gold、drop rate、採取量などは後から調整する。現段階の優先は **世界構造を先に作り、実際に歩いて触ること**。

---

## 1. 第一島コンセプト

南の交易港から上陸し、島の奥へ進むほど自然が荒れ、古代遺跡と巨大山岳へ到達する「開拓島」。

- 南: 港湾 / Spawn / 都市機能
- 中央: T1 平原 / 初心者 PvE / Gathering
- 西: 深い森林 / 木材 / Beast / 探索
- 東: 岩山 / 鉱山 / Ore Gathering / Cave
- 北中央: 中央渓谷 / Dungeon / Elite
- その奥: 古代遺跡盆地 / T2 後半
- 最北: 巨大山岳 / T3 / Boss / 最終 Dungeon 候補

島のシルエットは左右対称にしない。

- 南に大きな天然湾
- 西は比較的丸く穏やかな海岸線
- 東は崖 / 岩壁が多い
- 北だけ巨大な山塊が突出

Spawn / 港町から北を見ると、島最奥の巨大山が遠景ランドマークとして見えることを狙う。

---

## 2. サイズ

WorldPainter 作業領域:

- `4096 × 4096`
- Sea Level: `Y62 前後`

実陸地の目安:

- 東西: 約 1,800 blocks
- 南北: 約 2,100 blocks

港町:

- 約 `150 × 130 blocks`

港町から島最奥:

- 約 1,500 blocks 前後を目安

数値は地形 walk test 後に調整可。

---

## 3. 高さ構成

大地形の初期目安:

| Region | Height |
|---|---:|
| Sea | Y62 |
| Harbor / Town | Y64–72 |
| T1 Plain | Y70–90 |
| Western Forest Hills | Y80–120 |
| Eastern Rocky Area | Y90–145 |
| Ruin / Valley Area | Y85–140 |
| Northern Mountains | Y120–220 |

最初から細かい erosion / noise を作り込まず、大きな massing を優先する。

---

## 4. 地域構成

### 4.1 港湾 / 港町

島南部の天然湾の奥。

役割:

- Spawn
- Market
- Storage / Bank
- Craft
- Refine
- Enhancement
- Repair
- MOD 関連
- Dynamic NPC Procurement
- Quest / Bounty
- Dungeon / Party / Guild 導線
- Harbor / Travel

### 4.2 T1 中央平原

港町から北 100–500 blocks 程度。

地形:

- 緩い丘
- 小林
- 川
- 岩
- 農地跡

用途:

- 初心者 Gathering
- Normal Mob
- 最初の Elite
- Small POI
- Main road introduction

### 4.3 西部森林

おおよそ 600 × 650 blocks 級の森林圏。

地形は全面を木で埋めず、

- Dense forest
- Clearing
- Valley
- Stream

を混ぜて視界変化を作る。

用途:

- Wood Gathering
- Herb
- Beast Mob
- Hunter POI
- Elite
- Secret Cave

ランドマーク候補:

- 巨大古木
- 古い祠

### 4.4 東部岩山 / 鉱山

開けた視界と崖を中心に、草原から採石場、岩山、大型鉱山へ変化。

用途:

- Ore Gathering
- Cave Mob
- Elite
- Mining Event
- Underground Dungeon 候補

大型 POI:

- Abandoned Quarry
- Large Mine Entrance

### 4.5 中央渓谷

北 500–950 blocks 程度から危険度を明確に上げる。

特徴:

- 両側が崖の谷
- 古い巨大石橋
- 第一 Dungeon 入口

港町から 600–700 blocks 程度で Dungeon gameplay に触れられる配置を目安とする。

### 4.6 古代遺跡盆地

島中盤以降。

山に囲まれた開けた basin に、崩壊した古代都市を点在させる。

構成:

- 壊れた塔
- 壁
- 神殿
- 地下入口
- 石像
- 巨大門

用途:

- Elite
- Rare Mob
- MOD material
- T2 Gathering
- Dungeon
- Lore
- Field Event

### 4.7 北部山岳

第一島最奥。

- 島の広い範囲から見える一つの巨大山塊
- 最高地点 Y200–220 程度
- 山腹に崩れた道 / 洞窟 / 廃砦 / 小遺跡 / Elite camp / 高 Tier Gathering
- 山頂付近に Boss / 最終 Dungeon 用の 100 × 100 程度の空間を確保

---

## 5. 河川

主要河川を一本作る。

流れ:

`Northern Mountain → Ancient Ruins → Central Valley → T1 Plain → Town east side → Sea`

町の中央を貫通させず、町東側から港湾へ合流する。

途中に:

- Small waterfall
- Stone bridge
- Wooden bridge
- Shallow crossing
- Cliff section

等を作れる余白を残す。

河川は自然景観だけでなく navigation landmark として利用する。

---

## 6. 港町レイアウト

高密度・コンパクトな港町。
徒歩 30 秒～1 分程度で主要機能へアクセスできることを優先する。

概念配置:

`Harbor / Spawn → Production / Procurement → Central Market → Adventurer Guild → North Gate → Field`

主要区画:

- Harbor / Arrival
- Central Market
- Production District
- Procurement Warehouse
- Adventurer Guild
- Guard / Administration
- Inn / Residential
- North Gate

### 主要建築

| Building | Size guide | Main use / NPC |
|---|---:|---|
| Market Hall | 22×28 | Market |
| Storage / Bank | 15×20 | Storage |
| Procurement Warehouse | 20×30 | Dynamic NPC Procurement |
| Forge | 24×30 | Craft / Enhance / Repair |
| Refinery | 18×24 | Refine |
| MOD Atelier | 15×20 | MOD |
| Adventurer Guild | 24×28 | Quest / Bounty / Dungeon |
| Inn | 18×22 | Innkeeper |
| Guardhouse | 18×20 | Guard Captain |
| Harbor | ~40×100 | Harbor Master / Travel |

主要 gameplay NPC は最初 10–12 人程度でよい。
Atmosphere NPC は後から追加。

---

## 7. 港町の建築テーマ

テーマ:

**海洋交易都市 + 開拓拠点**

主素材:

- Weathered Oak 系
- 淡い灰色 Stone
- Dark / Blue 系屋根
- Iron
- Oxidized Copper
- 濃紺系の都市旗

港側は木造比率を高く、北門側へ行くほど石造りを増やす。
巨大な城ではなく、狭い路地と 2–3 階建て建物が密集した町を目指す。

### Lighthouse

町東側の小崖に Lighthouse を置く。

目的:

- 港側ランドマーク
- 南 / 海方向の方向感覚

北の巨大山と Lighthouse の 2 点で島全体の navigation landmark を作る。

---

## 8. 町外導線

北門から 50–80 blocks 程度進んだ地点に大きな十字路を作る。

- West road → Forest / Wood / Hunting
- East road → Quarry / Mine / Ore
- North road → Normal PvE / Elite / Dungeon / Higher Tier

町を出てすぐ「今日は何をするか」を選べる構造にする。

Road width guide:

- Main road: 7–9 blocks
- Secondary road: 4–5 blocks
- Trail / mountain path: 2–3 blocks

文明圏から奥地へ行くほど road quality を落とす。

- Town: Stone road
- T1: Dirt road
- T2: Damaged road
- T3: Mountain trail

---

## 9. 主要ランドマーク / POI

大型候補:

1. Harbor Town
2. Lighthouse
3. Broken Watchtower in T1
4. Giant Ancient Tree / Shrine in west forest
5. Abandoned Quarry / Large Mine in east
6. Ancient Stone Bridge / First Dungeon in central valley
7. Ruined Ancient City
8. Northern Mountain / Boss-Dungeon area

第一島全体の POI 密度目標:

- 超大型 landmark: 5–8
- 中型 POI: 12–15
- 小型 POI: 25–35

小型 POI 例:

- Broken wagon
- Small camp
- Abandoned house
- Small cave
- Lumberjack hut
- Shrine
- Bandit camp
- Broken tower
- Mining remains

原則として 200 blocks 以上「何もない」区間をできるだけ避ける。

---

## 10. WorldPainter 制作順

1. 4096×4096 / Sea Level 62 前後で作成
2. 約 1800×2100 の非対称な island silhouette を作る
3. South natural bay を作る
4. Northern giant mountain mass を作る
5. Harbor town 用 150×130 程度の buildable terrain を確保
6. Main river を通す
7. Western Forest / Eastern Rock / Central Valley / Ruin Basin を大地形として形成
8. Road route を annotation / temp layer でマーク
9. Major POI 予定地を確保
10. Tree / building / fine rock detail 前に一度 Minecraft へ Export
11. Harbor → North Mountain を walk test
12. 距離 / slope / landmark visibility を調整
13. その後に biome detail / vegetation / rocks / architecture を追加

最重要確認:

- 港予定地から北の山が魅力的に見えるか
- 最初の gameplay が町から近いか
- 走るだけの空白が長すぎないか
- 地域ごとの silhouette が分かるか

---

## 11. 世界とゲームシステムの対応

地形を見た目だけで作らず、各 region に gameplay role を持たせる。

例:

- Forest → Wood / Herb / Beast
- Rocky Area → Ore / Cave / Mining
- Ruins → Elite / MOD / Lore / Dungeon
- Northern Mountain → T3 / Boss / High-tier Gathering
- Town Procurement Warehouse → Dynamic NPC Procurement
- Forge / Refinery → Craft / Enhancement / Repair / Stone economy

今後新しい system を追加する際は、「それは町のどこに存在するか」「外世界のどこで入手 / 使用するか」をセットで設計する。

---

## 12. 今後の進め方

ユーザーの主な担当:

- 景色として好きかどうかの最終判断
- Minecraft 内で実際に歩いた game feel
- WorldPainter / build tool での最終手触り調整

ChatGPT 側の担当:

- Island layout
- Height / biome / road / POI design
- NPC / facility composition
- Gameplay placement
- Economy-to-world mapping
- Build style / landmark design
- Screenshot review and redline feedback
- 必要なら Heightmap / mask / reference image の生成

細かい数値よりも、まず playable world structure を完成させ、その後の playtest で数値を調整する。
