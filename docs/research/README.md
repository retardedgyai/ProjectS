# ProjectS Research Harvest Vault

ProjectSで参考にした外部プロジェクト・MMO・Minecraftサーバー・OSSから、後で比較・採用判断できる形で「持ってきたい設計」を蓄積するための研究庫。

## 目的

- 調査ごとの知見を会話内だけで失わない。
- 複数ソースを後から横断比較できるようにする。
- 「面白そうだから即実装」ではなく、候補を一旦貯めてからProjectSの優先順位へ落とす。
- 元コードを無断コピーせず、設計・アルゴリズム・運用パターンをProjectS向けに再実装できるよう、出典を残す。

## 基本ルール

1. **原則として第三者コードそのものはこの研究庫へコピーしない。**
   - 元repo、対象ファイル、クラス、仕組み、参考箇所を記録する。
   - 実コードを直接利用する場合は、必ずその時点でライセンスを確認する。
2. **事実とProjectS向けの推論を分ける。**
   - `Source-derived`: 元ソースが実際に持っている仕組み。
   - `ProjectS adaptation`: ProjectSへどう落とすかという提案。
3. **採用判断は横断比較後。**
   - 他ソースにもっと良い実装・設計がある可能性を残す。
4. **候補にはIDを付ける。**
   - 例: `WYNN-001`, `MONU-001`, `LEPI-001`。
   - 後から統合表で重複・競合を処理しやすくする。

## ステータス

- `HARVESTED`: 調査済み。まだ採用判断しない。
- `COMPARE`: 他ソースとの比較対象。
- `CANDIDATE`: ProjectSへの採用候補。
- `ADOPT`: 採用決定。
- `DEFER`: 良いが今は早い。
- `REJECT`: ProjectSには採用しない。

## 優先度

- `S`: ProjectSの基盤として早期採用を強く検討。
- `A`: 効果が大きく、該当システム実装前に検討。
- `B`: 将来規模が大きくなった時に有効。
- `C`: 面白いが現状の優先度は低い。

## ディレクトリ

```text
docs/research/
├─ README.md
├─ harvest-template.md
└─ sources/
   ├─ wynncraft.md
   ├─ monumenta.md
   ├─ lepinoid.md
   └─ <other-source>.md
```

今後、候補数が増えたら以下を追加する。

```text
docs/research/
├─ adoption-matrix.md       # 全ソース横断比較
├─ implementation-plan.md  # 採用決定後のProjectS実装順
└─ rejected.md              # 不採用理由の記録
```

## 調査ソース一覧

| Source | Status | File | Notes |
|---|---|---|---|
| Wynncraft GitHub | HARVESTED | `sources/wynncraft.md` | アルゴリズム検証、JMH、データ駆動、旧ネットワーク管理基盤 |
| Team Monumenta GitHub | HARVESTED | `sources/monumenta.md` | Skill/VFX/Hitbox/Damage/ItemStat/Boss/Market/Item migration/Quest DSL/Mob library/Client sync |
| Lepinoid GitHub | HARVESTED | `sources/lepinoid.md` | Blockbench pure schema、DSL設計、Shared CI、Renovate、Model/Protocol共有、GitOps/運用基盤 |
| Other research runs | pending import | - | 他チャット・他プロンプトの結果を同形式で後から統合 |

## 後で統合するときの流れ

1. 各 `sources/*.md` を集める。
2. 候補IDをカテゴリ別に並べる。
3. 同じ問題を解いている候補をまとめる。
4. 以下で比較する。
   - ProjectSへの効果
   - 実装コスト
   - パフォーマンス
   - 保守性
   - コンテンツ制作速度
   - AI/Codexとの相性
   - 将来のMinestom/複数サーバー化との相性
   - ライセンス/依存リスク
5. `ADOPT / DEFER / REJECT` を決める。
6. `ADOPT` だけを実装タスクへ変換する。

## AIへ渡すとき

別の調査プロンプトには `harvest-template.md` と同じ形式で出力させる。

最終統合時には、AIへ次のように指示する。

> `docs/research/sources/` 以下をすべて読み、候補IDを重複排除・競合整理し、ProjectSの現行設計と照合して採用候補を優先順位付きでまとめる。まだ実装は行わず、ADOPT / DEFER / REJECTの提案と理由を出す。

