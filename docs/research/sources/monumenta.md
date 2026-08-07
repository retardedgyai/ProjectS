# Monumenta GitHub Harvest

- Source: Team Monumenta GitHub organization
- URL: https://github.com/TeamMonumenta
- Date inspected: 2026-08-07
- Status: `HARVESTED`
- Primary repositories inspected:
  - `TeamMonumenta/monumenta-plugins-public`
  - `TeamMonumenta/scripted-quests`
  - `TeamMonumenta/library-of-souls`
- Secondary repositories identified for later inspection:
  - `TeamMonumenta/monumenta-server-management`
  - `TeamMonumenta/NBTEditor`
  - `TeamMonumenta/monumenta-velocity`
  - archived network / Redis / Paper forks

## Executive summary

MonumentaからProjectSへ持ち込みたい最大の価値は、個別スキルやボスそのものではなく、長期間MMOコンテンツを増築し続けるための基盤分離とコンテンツ制作パイプライン。

特に強い候補は以下。

1. Ability definition / runtime / trigger / managerの分離
2. GameplayとSkill VFX / cosmeticの分離
3. Circle / Bezier / Parametric等の再利用可能なParticle primitive
4. Shape-based Hitbox抽象化
5. Staged Damage pipeline
6. Event-driven ItemStat / MOD拡張モデル
7. Boss Spell + SpellManager + Phase orchestration
8. Dirty-render型GUI基盤
9. Marketのatomic transaction / lock / audit思想
10. Item schema migration / auto-updater
11. Condition + Action型のScriptedQuests思想とEditor
12. Mob definition library / pool / bestiary管理
13. Optional client modへのAbility / Effect同期
14. Roguelite / difficulty modifierをデータ・部品として積む構造

ProjectSへ第三者コードをそのままコピーすることは前提にしない。確認した `monumenta-plugins-public`、`scripted-quests`、`library-of-souls` はAGPLv3であり、直接コードを取り込む場合はライセンス義務を別途精査する。研究庫では構造・アルゴリズム・運用パターンを出典付きで保存し、ProjectS用に独立実装する前提とする。

---

## Candidates

### `MONU-001` — Ability Definition / Runtime / Manager Separation

- Status: `HARVESTED`
- Priority: `S`
- Category: `architecture/combat`
- Source-derived:
  - Monumentaは `Ability` をプレイヤーごとのruntime処理、`AbilityInfo` を名前・説明・cooldown・display item・使用条件・priority等のstatic definition、`AbilityManager` を登録・player ability collection・event dispatch側として分けている。
  - `AbilityInfo` はbuilder形式で定義され、多数のAbilityを同じ型で扱える。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/abilities/Ability.java`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/abilities/AbilityInfo.java`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/abilities/AbilityManager.java`
- Why it matters for ProjectS:
  - Warrior / Templar以降、クラス・スキル・passive・resource interactionが増えると個別executor中心の設計は肥大化しやすい。
  - 定義情報とruntime stateを分けることでUI、client sync、説明文、cooldown、test harnessが共通化できる。
- ProjectS adaptation:
  - `SkillDefinition`: stable ID、display data、tags、cooldown、resource cost、trigger metadata等。
  - `SkillRuntime`: player-specific state、charges、mode、duration、execution lifecycle。
  - `SkillRegistry`: definition登録。
  - `PlayerSkillCollection`: playerが現在持つruntime群。
  - 既存 `SkillManager` / class registryはこの方向へ段階的に寄せる。
- Dependencies / prerequisites:
  - Skill ID / class IDのstable naming。
  - runtime stateとdefinition stateを混ぜない規約。
- Risks / tradeoffs:
  - Monumentaの巨大 `AbilityManager` 自体は密結合が多いため丸ごと模倣しない。
  - ProjectSではevent bus / service分割をもう一段明確にした方がよい可能性がある。
- License status: `RESTRICTIVE` — repository is AGPLv3; concept-only independent reimplementation preferred.
- Suggested timing: `NOW`
- Compare against:
  - Wynncraft `WYNN-001` Registry思想。
  - Minestom / ECS系 ability architecture候補。
- Adoption decision: `PENDING`

### `MONU-002` — Declarative / Customizable Skill Trigger Layer

- Status: `HARVESTED`
- Priority: `S`
- Category: `combat/ui/architecture`
- Source-derived:
  - `AbilityTriggerInfo` はtrigger action、default key、装備restriction、prerequisiteを分離して持つ。
  - left click / right click / swap / drop、sneak、sprint、ground state等の条件を組み合わせられる。
  - playerごとのcustom triggerへ差し替える仕組みもある。
  - trigger条件から自然言語説明を生成する機能がある。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/abilities/AbilityTriggerInfo.java`
  - Related: `AbilityTrigger.java`, `AbilityTriggersGui.java`
- Why it matters for ProjectS:
  - クラス数が増えるほど入力競合、武器条件、sneak modifier、client keybind差分が複雑になる。
  - TriggerをSkill本体から分離するとrebindやclient UIへの表示が容易。
- ProjectS adaptation:
  - `SkillTriggerDefinition` と `TriggerCondition` を用意。
  - server vanilla-input fallbackとProjectS-Client custom keybindを同一trigger IDへ束ねる。
  - 使用条件をUI説明へ再利用する。
- Dependencies / prerequisites:
  - `MONU-001`相当のSkillDefinition。
- Risks / tradeoffs:
  - 入力条件DSLを過度に複雑にしない。
- License status: `RESTRICTIVE`
- Suggested timing: `NOW / BEFORE_MORE_CLASSES`
- Compare against:
  - ProjectS-Client keybind protocol。
- Adoption decision: `PENDING`

### `MONU-003` — Gameplay / Skill Visual Separation (`CosmeticSkill` Pattern)

- Status: `HARVESTED`
- Priority: `S`
- Category: `combat/content-pipeline`
- Source-derived:
  - Monumentaではskill gameplay classから見た目を `CosmeticSkill` 実装へ分離している。
  - 例として `FrostNova.java` はdamage / slow / freeze / cooldown / hitboxを担当し、`FrostNovaCS.java` がparticle / sound / animationを担当する。
  - 同一Abilityに対してvisual variantを差し替えられる構造がある。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/cosmetics/skills/CosmeticSkill.java`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/abilities/mage/FrostNova.java`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/cosmetics/skills/mage/FrostNovaCS.java`
- Why it matters for ProjectS:
  - Skill balance調整とVFX反復を独立させられる。
  - AI/Codexへ「見た目だけ変更」を安全に依頼しやすくなる。
  - 将来skill skin / cosmetic monetizationを追加する場合にも相性が良い。
- ProjectS adaptation:
  - `SkillVisual` interface。
  - `DefaultSkillVisual` + optional visual variants。
  - gameplayはvisual hook (`onCast`, `onHit`, `onExpire`等)だけ呼ぶ。
  - 既存 `SkillEffectRenderer` はprimitive / orchestration寄りへ再編する候補。
- Dependencies / prerequisites:
  - Skill stable ID。
  - VFX primitive layer。
- Risks / tradeoffs:
  - gameplay informationをvisual側へ漏らし過ぎないcontext設計が必要。
- License status: `RESTRICTIVE`
- Suggested timing: `NOW`
- Compare against:
  - 他サーバーのcosmetic skill system。
- Adoption decision: `PENDING`

### `MONU-004` — Reusable Particle / VFX Primitive Library

- Status: `HARVESTED`
- Priority: `S`
- Category: `content-pipeline/performance`
- Source-derived:
  - Monumentaはparticle spawnをskillごとのfor-loopへ閉じず、`PartialParticle` / `PPCircle` / `PPBezier` / `PPParametric` / `PPPeriodic` 等の再利用可能な描画primitiveとして持つ。
  - player-visible spawn等の共通処理もprimitive側に寄せている。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/particle/PartialParticle.java`
  - `.../particle/PPCircle.java`
  - `.../particle/PPBezier.java`
  - `.../particle/PPParametric.java`
  - `.../particle/PPPeriodic.java`
- Why it matters for ProjectS:
  - Skill VFX制作速度と品質の両方へ直接効く。
  - Codex/AIに「arc / ring / spiral / cone」等の高レベル指示を出せる。
  - particle budget / distance cullingを一か所で最適化しやすい。
- ProjectS adaptation:
  - `VfxEmitter` + `VfxShape`。
  - `Circle`, `Arc`, `Line`, `Bezier`, `Spiral`, `Cone`, `Wave`, `Sphere`, `Burst` 等。
  - viewer filtering / density / LOD / max-particle-budgetを共通化。
- Dependencies / prerequisites:
  - `MONU-003`と高相性。
- Risks / tradeoffs:
  - primitive APIが自由すぎると最適化が難しくなる。
- License status: `RESTRICTIVE`
- Suggested timing: `NOW`
- Compare against:
  - ParticleLib等の既存ライブラリ、client-side rendering案。
- Adoption decision: `PENDING`

### `MONU-005` — Shape-based Hitbox Abstraction

- Status: `HARVESTED`
- Priority: `S`
- Category: `combat/architecture`
- Source-derived:
  - Monumentaの `Hitbox` はAABB、Sphere、upright cylinder、cone近似、cylinder segment、free-form等を統一interfaceで扱う。
  - entity bounding boxとのintersectionをshape側へ閉じ込めている。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/utils/Hitbox.java`
- Why it matters for ProjectS:
  - `TargetingService` がskillごとの距離/角度ロジックの寄せ集めになるのを防げる。
  - VFX shapeとdamage hitboxを同じparameter sourceから作れば見た目と判定を合わせやすい。
- ProjectS adaptation:
  - `CombatShape` / `TargetShape` abstraction。
  - `SphereShape`, `ConeShape`, `BoxShape`, `CylinderShape`, `LineShape`, `RingShape`。
  - query broad-phaseとexact intersectionを分離する。
- Dependencies / prerequisites:
  - Entity query service。
- Risks / tradeoffs:
  - free-form sampling系はaccuracyとCPUのtradeoffがある。
- License status: `RESTRICTIVE`
- Suggested timing: `NOW / BEFORE_SKILL_SCALE_UP`
- Compare against:
  - Minestom entity query / spatial indexing候補。
- Adoption decision: `PENDING`

### `MONU-006` — Staged Damage Modifier Pipeline

- Status: `HARVESTED`
- Priority: `S`
- Category: `combat/architecture`
- Source-derived:
  - Monumentaは独自 `DamageEvent` を持ち、original damage、source、ability metadata、item-stat snapshot、boss spell metadata等を保持する。
  - damage modifierを `BASE`, `GEAR`, `EFFECT_POSITIVE`, `EFFECT_NEGATIVE`, `CRITICAL`, `FINAL` 等のstageへ積み、再計算する。
  - damage typeごとに適用対象を制御できる。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/events/DamageEvent.java`
  - Related: `listeners/DamageListener.java`, `utils/DamageUtils.java`
- Why it matters for ProjectS:
  - PoE風MOD、buff/debuff、skill tags、critical、防御、boss modifier等が増えると「どの順序で計算したか」がバランスとbugの中心になる。
- ProjectS adaptation:
  - Monumentaの単一 `DamageType` enumはコピーしない。
  - ProjectS既存方針の複数Attack Tags (`MELEE / PROJECTILE / MAGIC / FIRE / SKILL ...`) を維持。
  - `DamageContext` + ordered `DamageStage` + immutable modifier recordsを作る。
  - calculation traceをdebug時に出力可能にする。
- Dependencies / prerequisites:
  - Attack tag model。
  - deterministic modifier order。
- Risks / tradeoffs:
  - stage数を増やし過ぎると理解コストが上がる。
- License status: `RESTRICTIVE`
- Suggested timing: `NOW / BEFORE_COMPLEX_MODS`
- Compare against:
  - Wynncraft formula registry (`WYNN-001`)。
- Adoption decision: `PENDING`

### `MONU-007` — Event-driven ItemStat / MOD Components

- Status: `HARVESTED`
- Priority: `S`
- Category: `items/combat/architecture`
- Source-derived:
  - Monumentaの `ItemStat` は単なる数値interfaceではなく、`onDamage`, `onHurt`, `onKill`, `onProjectileLaunch`, `onProjectileHit`, `onBlockBreak`, `onInteract`, `tick`, `onEquipmentUpdate` 等のevent hookを持つ。
  - Attribute / Enchantment / Infusion等が共通のstat processing systemへ乗る。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/itemstats/ItemStat.java`
  - `.../itemstats/ItemStatManager.java`
  - `.../itemstats/Attribute.java`
  - `.../itemstats/Enchantment.java`
  - `.../itemstats/Infusion.java`
- Why it matters for ProjectS:
  - PoE風MODを数百種類まで増やす場合、中央Listenerにif/switchを足す方式は破綻しやすい。
  - MOD自身が必要eventだけ実装するcomponent modelが拡張性に強い。
- ProjectS adaptation:
  - `ItemModifier` interface + narrow capability interfaces (`OnDamageModifier`, `OnKillModifier`, `OnSkillCastModifier` 等)を検討。
  - Registryから装備中modifierだけdispatch。
  - static statとreactive procを分離できるようにする。
- Dependencies / prerequisites:
  - `MONU-006` DamageContext。
  - ItemDefinition / ItemInstance separation候補 (`WYNN-005`)。
- Risks / tradeoffs:
  - すべてのevent methodを1巨大interfaceへ入れるよりProjectSではcapability interface分割が良い可能性。
- License status: `RESTRICTIVE`
- Suggested timing: `BEFORE_COMPLEX_ITEM_MODS`
- Compare against:
  - Wynncraft immutable equipment snapshot (`WYNN-005`)。
- Adoption decision: `PENDING`

### `MONU-008` — Aggregated Player Equipment Stat Snapshot

- Status: `HARVESTED`
- Priority: `A`
- Category: `items/performance`
- Source-derived:
  - `ItemStatManager.PlayerItemStats` はarmor add/multiply、mainhand stats、combined stats等を集約して保持する。
  - 装備更新時に再計算し、combat eventごとに全装備NBTを毎回走査する構造を避ける。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/itemstats/ItemStatManager.java`
- Why it matters for ProjectS:
  - MOD数・装備slot・buffが増えるほど毎hitのItemStack解析は高コスト。
- ProjectS adaptation:
  - `EquipmentSnapshot` / `PlayerCombatSnapshot`。
  - equipment mutation時だけdirty化し、必要時にrebuild。
  - projectile launch時にはlaunch snapshotを保持しimpact時も同じstatsを使う。
- Dependencies / prerequisites:
  - Item data model。
- Risks / tradeoffs:
  - invalidation漏れが最重要bug sourceになる。
- License status: `RESTRICTIVE`
- Suggested timing: `BEFORE_LARGE_SCALE_COMBAT`
- Compare against:
  - `WYNN-005`, `WYNN-006`。
- Adoption decision: `PENDING`

### `MONU-009` — Boss Spell Lifecycle Abstraction

- Status: `HARVESTED`
- Priority: `S`
- Category: `combat/content-pipeline`
- Source-derived:
  - Boss技は `Spell` abstractionとして `run`, `canRun`, `cooldownTicks`, `castTicks`, `cancel`, `persistOnPhaseChange` 等を持つ。
  - spell自身がactive runnable/taskを追跡し、phase changeやboss unload時にまとめてcancel可能。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/bosses/spells/Spell.java`
- Why it matters for ProjectS:
  - 大型bossではdelay task / projectile / telegraph / phase transitionが大量に発生する。
  - lifecycleを標準化しないとphase跨ぎの残留task・ghost damageが起きやすい。
- ProjectS adaptation:
  - `BossSpell` + scoped task/effect handle。
  - `cancel(reason)` を明示。
  - encounter结束時に全child task/resourceを必ず破棄する。
- Dependencies / prerequisites:
  - Boss encounter lifecycle。
- Risks / tradeoffs:
  - BukkitTaskだけでなくdisplay entity / temporary block / client packet等も同じscopeで管理したい。
- License status: `RESTRICTIVE`
- Suggested timing: `BEFORE_FIRST_LARGE_BOSS`
- Compare against:
  - MythicMobs / boss DSL / Minestom scheduler patterns。
- Adoption decision: `PENDING`

### `MONU-010` — Boss Spell Selection / Anti-Repetition Manager

- Status: `HARVESTED`
- Priority: `A`
- Category: `combat/ai`
- Source-derived:
  - `SpellManager` はavailable spellをshuffleし `canRun()` を満たす技を選択する。
  - 最近使ったspellをqueueへ入れ、spell数に応じて再選択までの間隔を作る。
  - force-cast APIも持つ。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/bosses/SpellManager.java`
- Why it matters for ProjectS:
  - boss AIが単純randomだと同じ技連打や不自然なpatternになりやすい。
- ProjectS adaptation:
  - `BossActionSelector`。
  - cooldown、recent-history、weight、distance、phase、player count、anti-repeatをselector policyとして分離。
  - force castをdev editor/testから利用。
- Dependencies / prerequisites:
  - `MONU-009`。
- Risks / tradeoffs:
  - Monumentaのclass-based spell keyはProjectSではstable spell IDの方が扱いやすい。
- License status: `RESTRICTIVE`
- Suggested timing: `BEFORE_FIRST_LARGE_BOSS`
- Compare against:
  - behavior tree / utility AI候補。
- Adoption decision: `PENDING`

### `MONU-011` — Boss Phase Orchestration + Encounter Suspension

- Status: `HARVESTED`
- Priority: `S`
- Category: `combat/performance`
- Source-derived:
  - `BossAbilityGroup` がactive spells、passive spells、boss bar、detection range、phase transitionを統括する。
  - phase変更時にactive spell cancellationとspell set差し替えができる。
  - playerがdetection rangeにいない時はboss処理を進めず、missing bossのfallback unloadも持つ。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/bosses/bosses/BossAbilityGroup.java`
- Why it matters for ProjectS:
  - 大型boss、world boss、dungeon bossを増やす際の共通encounter controllerになる。
  - player不在時のtick停止はserver負荷にも効く。
- ProjectS adaptation:
  - `BossEncounter` / `BossPhase` / `BossPhaseController`。
  - encounter activation radius / suspend / resume / reset policy。
  - HP threshold / timer / mechanic completion trigger。
- Dependencies / prerequisites:
  - `MONU-009`, `MONU-010`。
- Risks / tradeoffs:
  - open-world bossとinstanced bossでreset policyを分離する必要。
- License status: `RESTRICTIVE`
- Suggested timing: `BEFORE_FIRST_LARGE_BOSS`
- Compare against:
  - ProjectSゲーム内統合Mob/Boss editor Epic。
- Adoption decision: `PENDING`

### `MONU-012` — Deterministic Event Priority for Abilities / Item Mods

- Status: `HARVESTED`
- Priority: `A`
- Category: `combat/architecture`
- Source-derived:
  - `AbilityInfo` と `ItemStat` はevent handling priority amountを持ち、最終damage参照・lifeline等の処理順を明示している。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `abilities/AbilityInfo.java`
  - `itemstats/ItemStat.java`
- Why it matters for ProjectS:
  - proc、damage modifier、shield、resurrection、execute等が増えた時、Listener registration orderに依存すると再現不能bugが起きる。
- ProjectS adaptation:
  - `CombatHookPriority` / named phaseを定義。
  - numeric magic numberよりenum stage + sub-priorityを優先。
  - debug traceで処理順を表示。
- Dependencies / prerequisites:
  - `MONU-006`, `MONU-007`。
- Risks / tradeoffs:
  - priority ruleをdocumentation化しないと逆に複雑になる。
- License status: `RESTRICTIVE`
- Suggested timing: `BEFORE_COMPLEX_ITEM_MODS`
- Adoption decision: `PENDING`

### `MONU-013` — Dirty-render GUI Base + GuiItem Routing

- Status: `HARVESTED`
- Priority: `A`
- Category: `ui/tooling`
- Source-derived:
  - Monumentaの新しいGUI libは `Gui` がInventory lifecycle、dirty flag、rerender、title/size recreation、main-thread guardを共通化する。
  - `render()` 内だけ `setItem()` を許可し、interactionは `GuiItem`へroutingできる。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/guis/lib/Gui.java`
  - Related: `GuiItem.java`, `GuiListener.java`
- Why it matters for ProjectS:
  - DevMenu、class select、crafting、market、admin content editor等のinventory UIが増える。
  - 画面ごとにInventoryClickEventを手書きすると保守が崩れる。
- ProjectS adaptation:
  - `GuiScreen` state + declarative render。
  - reusable button / pagination / confirmation / numeric input component。
  - dirty updatesとfull reopenを分ける。
- Dependencies / prerequisites:
  - UI navigation convention。
- Risks / tradeoffs:
  - 将来client mod UIへ寄せる場合でもadmin/fallback UIとして価値あり。
- License status: `RESTRICTIVE`
- Suggested timing: `NOW / BEFORE_DEV_MENU_EXPANDS`
- Compare against:
  - Triumph GUI等の外部GUI framework。
- Adoption decision: `PENDING`

### `MONU-014` — Transaction-safe Player Market Patterns

- Status: `HARVESTED`
- Priority: `S`
- Category: `economy/architecture`
- Source-derived:
  - Monumenta Marketはlisting状態のatomic edit、lock/unlock、claimable balance、expire/unexpire、index更新、audit logを分けて扱う。
  - async storage操作とmain-thread item/currency deliveryを分離している。
  - sellability validationも中央化している。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/market/MarketManager.java`
  - Related: `MarketRedisManager`, `MarketListingIndex`, `MarketAudit`, `market/gui/*`
- Why it matters for ProjectS:
  - ProjectS経済では市場が低Tier素材需要、製造、修理用装備の循環中心になる。市場dupe / double claimは経済全体を破壊する。
  - ProjectSの採取・製造・破損修理経済は市場信頼性が前提。関連現行仕様は `ProjectS_採取_リファイン_製造_武具強化_仮仕様` に記録済み。
- ProjectS adaptation:
  - listing state machine。
  - atomic compare-and-swap or DB transaction。
  - idempotent buy/claim operations。
  - immutable audit records。
  - item escrow / currency escrowを明示。
  - market indexはsource-of-truth DBと分離。
- Dependencies / prerequisites:
  - currency / item identity / persistence layer。
- Risks / tradeoffs:
  - MonumentaのRedis実装そのものはProjectSの将来DB構成と比較して決める。
- License status: `RESTRICTIVE`
- Suggested timing: `BEFORE_PLAYER_MARKET`
- Compare against:
  - Hypixel / Albion market design、SQL transaction方式。
- Adoption decision: `PENDING`

### `MONU-015` — Item Schema Migration / Auto-update Pipeline

- Status: `HARVESTED`
- Priority: `S`
- Category: `items/operations`
- Source-derived:
  - `ItemUpdateManager` はplayer join、pickup、drop、item spawn、inventory open、entity load等のタイミングでdirty itemを検出して現行item schemaへ更新する。
  - nested item/containerの更新も考慮している。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/itemupdater/ItemUpdateManager.java`
  - Related: `ItemUpdateHelper.java`
- Why it matters for ProjectS:
  - beta後にMOD、quality、enhancement、DataComponent/PDC形式を変更しても既存itemを壊さず移行する必要がある。
- ProjectS adaptation:
  - 全ProjectS itemへ `schemaVersion`。
  - `ItemMigration vN -> vN+1` chain。
  - lazy migration on load/use + admin batch migration tool。
  - migration failureはitem UUID / owner / location付きでaudit。
- Dependencies / prerequisites:
  - stable item identity / serialization format。
- Risks / tradeoffs:
  - migrationは一方向だけでなくbackup / rollback strategyも検討。
- License status: `RESTRICTIVE`
- Suggested timing: `BEFORE_PUBLIC_BETA`
- Compare against:
  - DataFixerUpper的version migration、Wynncraft item definition model。
- Adoption decision: `PENDING`

### `MONU-016` — Condition + Action Content DSL (`ScriptedQuests` Pattern)

- Status: `HARVESTED`
- Priority: `S`
- Category: `content-pipeline/tooling`
- Source-derived:
  - `scripted-quests` はJSON駆動でquest componentにprerequisitesとactionsを持たせ、NPC interaction、death、login、race、trader、timer、interactable等をplugin code無しで構築する。
  - AND / OR / NOT系condition、score/tag/item/location等のprerequisite、dialog / command / function / loot等のactionを組み合わせる。
  - Web editorを主要なcontent authoring tool兼documentationとして持つ。
- Source locations:
  - Repository: `TeamMonumenta/scripted-quests`
  - `README.md`
  - `tools/gui_editor.html`
  - `tools/quest_editor.html`
  - zone / trader / race等各editor
- Why it matters for ProjectS:
  - ProjectSが目指しているゲーム内統合コンテンツエディタの基礎思想として非常に強い。
  - QuestだけでなくMob spawn、boss phase、event、drop、region interactionまで共通Condition/Actionへ寄せられる可能性。
- ProjectS adaptation:
  - `Trigger -> Conditions -> Actions` の汎用content graph。
  - Condition: player state / item / region / party / quest / time / mob state等。
  - Action: dialog / spawn / loot / effect / objective / teleport / schedule / boss phase等。
  - JSON schema + validation + game内editor。
  - runtime engineとeditor UIを分離。
- Dependencies / prerequisites:
  - stable action/condition IDs。
  - schema versioning。
- Risks / tradeoffs:
  - 万能DSL化し過ぎるとdebug困難。まずQuest/Eventから狭く始める。
- License status: `RESTRICTIVE` — AGPLv3.
- Suggested timing: `BEFORE_CONTENT_EDITOR`
- Compare against:
  - MythicMobs condition/mechanic model、Denizen、Skript、Minestom custom DSL。
- Adoption decision: `PENDING`

### `MONU-017` — Mob Definition Library / Pools / Bestiary (`Library of Souls` Pattern)

- Status: `HARVESTED`
- Priority: `A`
- Category: `content-pipeline/tooling`
- Source-derived:
  - `Library of Souls` はNBTEditorを拡張し、大量のMinecraft mob definitionをlibraryとして管理する。
  - `SoulEntry`, `SoulPoolEntry`, `SoulPartyEntry`, database/API、inventory UI、history、bestiary storage等を持つ。
- Source locations:
  - Repository: `TeamMonumenta/library-of-souls`
  - `README.md`
  - `src/main/java/com/playmonumenta/libraryofsouls/SoulEntry.java`
  - `SoulPoolEntry.java`
  - `SoulPartyEntry.java`
  - `SoulsDatabase.java`
  - `LibraryOfSoulsAPI.java`
  - `SoulsInventory.java`
  - `bestiary/*`
- Why it matters for ProjectS:
  - Mob/BossをJava classだけではなく再利用可能なcontent assetとして扱う必要がある。
  - spawn table / random pool / bestiary / editorと同じdefinitionを共有できる。
- ProjectS adaptation:
  - `MobDefinition` stable ID。
  - `MobPoolDefinition` weighted entries + conditions。
  - `MobVariant` / equipment / stats / model / AI / skills references。
  - editor history / duplicate / test spawn。
  - bestiaryは同じdefinition metadataを参照。
- Dependencies / prerequisites:
  - ProjectS Mob content schema。
  - 統合content editor。
- Risks / tradeoffs:
  - NBTそのものをmaster dataにせず、ProjectS domain schemaからMinecraft entityへcompileする方が将来性が高い可能性。
- License status: `RESTRICTIVE` — AGPLv3.
- Suggested timing: `BEFORE_MOB_LIBRARY_SCALE_UP`
- Compare against:
  - MythicMobs mob configs、ModelEngine integration、custom entity framework。
- Adoption decision: `PENDING`

### `MONU-018` — Optional Client Mod State Sync Protocol

- Status: `HARVESTED`
- Priority: `S`
- Category: `networking/ui`
- Source-derived:
  - `ClientModHandler` はoptional client modを検出し、plugin message channel経由でAbility cooldown / charges / mode / duration、class update、effect update、silence status、location等をJSON packetとして送る。
  - client mod無しでもserver gameplayが成立するoptional integration。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/network/ClientModHandler.java`
- Why it matters for ProjectS:
  - ProjectS-Clientが既に存在し、skill HUD / effect HUD / custom keybind / future UIをserver authorityのまま表示できる。
- ProjectS adaptation:
  - versioned protocol (`projects:client_vN`)。
  - typed packet IDs / schema。
  - server authoritative state only。
  - full snapshot + incremental update。
  - capability handshakeでclient version差を吸収。
- Dependencies / prerequisites:
  - ProjectS-Client protocol design。
  - Skill / Effect stable IDs。
- Risks / tradeoffs:
  - raw class/display nameをnetwork identityにせずstable IDを使う。
  - JSONで十分かbinary codecにするかは負荷測定後。
- License status: `RESTRICTIVE`
- Suggested timing: `NOW / BEFORE_CLIENT_UI_EXPANDS`
- Compare against:
  - Fabric custom payload API / PacketCodec。
- Adoption decision: `PENDING`

### `MONU-019` — Reusable Effect Engine + Display Metadata

- Status: `HARVESTED`
- Priority: `A`
- Category: `combat/ui`
- Source-derived:
  - Monumentaはvanilla PotionEffectだけでなく独自 `Effect` / `EffectManager` を持ち、Bleed、Frozen、Stasis、damage immunity等を独立effectとして管理する。
  - effectはduration / magnitude / display name / buff-debuff / display priority等の状態をclient modへ同期できる。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/effects/EffectManager.java`
  - examples: `Bleed.java`, `Frozen.java`, `Stasis.java`, `DamageImmunity.java`
  - related sync: `network/ClientModHandler.java`
- Why it matters for ProjectS:
  - Buff/debuff、DoT、CC、aura、item procをvanilla potionだけで表現するのは限界がある。
- ProjectS adaptation:
  - `StatusEffectDefinition` + `StatusEffectInstance`。
  - stacking rule / refresh rule / source ID / dispel category / tags / HUD metadata。
  - effect hooksはcombat pipelineへ接続。
- Dependencies / prerequisites:
  - `MONU-006`, `MONU-018`。
- Risks / tradeoffs:
  - ItemModifierとEffectの責務境界を明確化する。
- License status: `RESTRICTIVE`
- Suggested timing: `BEFORE_MORE_CLASSES_AND_BOSSES`
- Compare against:
  - ECS effect component、MMO buff stack systems。
- Adoption decision: `PENDING`

### `MONU-020` — Roguelite Ability / Room Choice Content Layer (`Depths`)

- Status: `HARVESTED`
- Priority: `B`
- Category: `content-pipeline/game-mode`
- Source-derived:
  - Monumenta `depths` packageは通常class abilityと別にroguelite ability pool、tree、rarity、room choice、upgrade GUI、summary/debug GUI、party state等を持つ。
  - AbilityInfo系を再利用しつつ別ゲームモードのprogressionを構築している。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/depths/DepthsManager.java`
  - `depths/abilities/DepthsAbilityInfo.java`
  - `depths/guis/DepthsRoomChoiceGUI.java`
  - `DepthsUpgradeGUI.java`
  - `DepthsSummaryGUI.java`
  - `DepthsParty.java`
- Why it matters for ProjectS:
  - 将来roguelite dungeon / seasonal mode / randomized build modeを追加する場合、core combat abilityを再利用しつつ別progressionを載せる参考になる。
- ProjectS adaptation:
  - core Skillとrun-specific acquisition/progressionを分離。
  - `RunModifier`, `RunSkillOffer`, `RoomDefinition`, `RunState`。
- Dependencies / prerequisites:
  - core skill/content definition基盤。
- Risks / tradeoffs:
  - ベータ優先度は低い。通常open-world MMO loop完成前には実装しない可能性が高い。
- License status: `RESTRICTIVE`
- Suggested timing: `LATER`
- Compare against:
  - Hades / PoE Sanctum / roguelite Minecraft servers。
- Adoption decision: `PENDING`

### `MONU-021` — Modular Difficulty Modifiers (`Delves` Pattern)

- Status: `HARVESTED`
- Priority: `A`
- Category: `combat/content-pipeline`
- Source-derived:
  - Monumenta `delves` はdifficulty modifierを独立定義し、preset、manager、custom inventory、boss scaling等と組み合わせる。
  - `DelvesModifier`, `DelvePreset`, individual abilities/modifiers、`DelveScalingBoss` が確認できる。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/delves/DelvesModifier.java`
  - `delves/DelvePreset.java`
  - `delves/DelvesManager.java`
  - `delves/DelveCustomInventory.java`
  - `bosses/bosses/DelveScalingBoss.java`
- Why it matters for ProjectS:
  - dungeon difficulty、endgame farm、challenge modifierを「HP+damage倍率」だけでなくルール追加として積める。
  - 同一コンテンツから難易度・報酬・build要求を増やせる。
- ProjectS adaptation:
  - `DifficultyModifier` stable ID。
  - modifier hooks: mob spawn / stat / AI / projectile / healing / environment / rewards。
  - preset / point budget / reward multiplierを別レイヤーへ。
- Dependencies / prerequisites:
  - encounter / mob spawn / reward pipeline。
- Risks / tradeoffs:
  - modifier combinationsの爆発をsimulation/testする必要。
- License status: `RESTRICTIVE`
- Suggested timing: `BEFORE_ENDGAME_DIFFICULTY_SYSTEM`
- Compare against:
  - Mythic+ affix、PoE map modifiers、Hypixel dungeon modifiers。
- Adoption decision: `PENDING`

### `MONU-022` — Developer Test Hooks for Content Iteration

- Status: `HARVESTED`
- Priority: `A`
- Category: `tooling/content-pipeline`
- Source-derived:
  - Monumentaにはboss spellのforce cast、particle test command、item stat commands、GUI debug、Depths debug GUI等、実コンテンツを即時検証するdev toolが複数存在する。
  - `BossAbilityGroup` / `SpellManager` のforce-cast APIはproduction runtimeとtest toolingで同じspell definitionを利用できる。
- Source locations:
  - Repository: `TeamMonumenta/monumenta-plugins-public`
  - `plugins/paper/src/main/java/com/playmonumenta/plugins/commands/ForceCastSpell.java`
  - `commands/PartialParticleCommand.java`
  - `commands/ItemStatCommands.java`
  - `depths/guis/DepthsDebugGUI.java`
  - `bosses/SpellManager.java`
- Why it matters for ProjectS:
  - ProjectSのゲーム内統合コンテンツエディタでは「保存→再起動」ではなく、作成したskill / boss / drop / VFXをその場でtestできることが制作速度を決める。
- ProjectS adaptation:
  - DevMenuから `force cast`, `spawn test mob`, `play VFX`, `simulate damage`, `roll item`, `reload definition`, `inspect context`。
  - runtime definitionとdev test pathを同じAPIへ通す。
- Dependencies / prerequisites:
  - Skill/Boss/Mob/VFX registries。
- Risks / tradeoffs:
  - admin permission / production shard guardを厳格化する。
- License status: `RESTRICTIVE`
- Suggested timing: `NOW / WITH_EACH_NEW_CONTENT_KERNEL`
- Compare against:
  - ProjectS既存DevMenu、MythicMobs test commands。
- Adoption decision: `PENDING`

---

## Things not worth bringing over directly

### Monumenta-specific monolith / scoreboard coupling

- `AbilityManager`, `Plugin` 等は長年の機能追加によりMonumenta固有systemとの結合が大きい。
- Scoreboard IDがclass / ability / quest progressionの重要stateとして広く使われている。
- ProjectSではstable domain ID + persistence modelを優先し、scoreboardをsource of truthへしない方がよい可能性が高い。

### MonumentaのDamageType enumそのもの

- Monumentaは `MELEE`, `MELEE_SKILL`, `PROJECTILE`, `PROJECTILE_SKILL`, `MAGIC` 等を単一typeとして分類する。
- ProjectSは既に複数attack tagを同時保持する方針なので、type列挙そのものは逆輸入しない。
- 参考にするのはmodifier stage / metadata / traceability。

### Archived Paper forks / old network stackの直接採用

- TeamMonumentaには過去Paper fork、Redis sync、network relay等のarchived repositoryが存在する。
- 現在のPaper 26.1 / Java 25 / modern Velocity・DB設計に直接適用する前提では見ない。
- 必要になった段階で「何を解決していたか」という設計上の要件のみ抽出する。

### NBT representationの直接移植

- Monumenta item stackはNBT APIへ強く依存している箇所が多い。
- ProjectSは現在のPaper / Minecraft data component / PDC環境に合わせ、domain item schemaをMinecraft serializationから分離した方がよい。

### AGPL codeの無計画なcopy/paste

- 確認した主要repoはAGPLv3。
- 本研究庫ではsource locationを残し、独立実装の設計資料として使う。
- 実コードを直接利用したくなった場合のみ、公開義務を含めライセンス判断を別タスク化する。

---

## Follow-up research

- `EffectManager` のstacking / source priority / serializationを深掘りし、ProjectS StatusEffect仕様と比較。
- `MarketRedisManager` / `MarketListingIndex` のatomic operationとfailure recoveryを詳細確認し、SQL transaction案と比較。
- `monumenta-server-management` の現行multi-server control plane、deploy、observabilityを調査。
- `monumenta-velocity` のcurrent routing / proxy拡張をProjectS将来構成と比較。
- `scripted-quests` のJSON schema / validator / web editor生成方式を詳細調査し、ProjectSゲーム内editor schemaへ応用可能か確認。
- `Library of Souls` のversion history / history tracking / pool semanticsをMob editor設計時に再調査。
- cosmetic skill repositoryをさらにsamplingし、良いVFX primitive / animation patternsをカタログ化。
- boss spellを複数実例で調査し、telegraph / cancel / target selection / phase cleanupの共通部品を抽出。
- guild / LuckPerms integrationはGvG着手時に再調査。

---

## Integration notes

横断比較時は個別候補をそのまま22個実装するのではなく、以下のKernel候補へまとめて比較する。

### Skill Kernel

- `MONU-001`
- `MONU-002`
- `MONU-012`
- `MONU-018`

### VFX / Targeting Kernel

- `MONU-003`
- `MONU-004`
- `MONU-005`

### Combat / Item Kernel

- `MONU-006`
- `MONU-007`
- `MONU-008`
- `MONU-019`
- Wynncraft `WYNN-005`, `WYNN-006` と比較

### Boss / Encounter Kernel

- `MONU-009`
- `MONU-010`
- `MONU-011`
- `MONU-021`
- `MONU-022`

### Content Authoring Kernel

- `MONU-013`
- `MONU-016`
- `MONU-017`
- `MONU-022`
- Wynncraft data-driven recipe / ingredient候補と共通schema思想を比較

### Economy / Persistence Kernel

- `MONU-014`
- `MONU-015`

### Later Game-mode Layer

- `MONU-020`
- `MONU-021`

最終判断時は `docs/research/sources/` 全体を読んで、同じ問題を解いている候補を統合し、ProjectS現行実装への変更量・AI/Codex制作速度・performance・保守性・ライセンス・将来Minestom/複数server化まで含めて `ADOPT / DEFER / REJECT` を決める。
