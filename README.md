# JoyMart

[![中文](https://img.shields.io/badge/%E8%AF%AD%E8%A8%80-%E4%B8%AD%E6%96%87-red?style=for-the-badge)](./README.md)
[![English](https://img.shields.io/badge/Lang-English-blue?style=for-the-badge)](./README_EN.md)
[![Spigot](https://img.shields.io/badge/Paper-1.20.1%2B-orange?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-17%2B-blue?style=for-the-badge)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-informational?style=for-the-badge)](./LICENSE)

一个为 **Spigot / Paper 1.20.1+** 打造的全 GUI 我的世界小游戏大厅插件。15 款游戏全部内置在主 jar 中，无需额外模块加载，丢进 `plugins/` 即开箱即玩。

> 主类：`me.nikl.gamebox.GameBox` · 版本：`1.0` · 构建：Maven shade（依赖重定位至 `me.nikl.gamebox.common.*`）

---

## 灵感来源与致谢

JoyMart 的设计与实现由作者**完全独立自研**，代码、游戏逻辑、GUI 交互、数据存储均为本仓库原创实现。

在产品形态、玩法思路与用户体验方面，本项目参考并致敬了以下两款优秀的 Spigot 社区作品：

- [JukeBox - Music Plugin](https://www.spigotmc.org/resources/jukebox-music-plugin.40580/) — 提供了"音乐播放器 + NBS 乐谱"的灵感来源，本项目的内置 `MusicPlayer`（NBS 乐谱解析 + 音符盒序列播放）正是在此启发下重新自研实现。
- [GameBox - Inventory Games Collection](https://www.spigotmc.org/resources/gamebox-inventory-games-collection.37273/) — 提供了"通过箱子 GUI 聚合多款小游戏"的整体产品形态灵感，本项目的 15 款游戏、代币系统、高分榜、双人邀请、AI 对战等内容均为独立重写，未引用其代码。

> 上述链接为 SpigotMC 资源页，可点击跳转查看原作品。感谢原作者们为社区带来的优秀作品。

---

## 特性一览

| 类别 | 说明 |
|---|---|
| **纯 GUI 操作** | 通过箱子界面选择游戏，玩家无需记忆任何指令。 |
| **15 款内置游戏** | 单人：2048、扫雷、打地鼠、宝石迷阵、彩票、抽奖机、迷宫、贪吃蛇、小恐龙跑酷；双人：战舰、四子棋、井字棋、石头剪刀布、国际象棋；多人：大富翁（2–3 人，含 AI 与下注模式）。 |
| **代币系统** | 自带 JoyMart 代币，可选挂钩 Vault 经济（金币）。 |
| **高分榜** | 自动记录每款游戏的最高分，支持跨服 MySQL 同步。 |
| **双人对战 + AI** | 双人游戏支持邀请好友或直接对战 AI（无需等待对手）。 |
| **可配置奖项池** | 彩票 / 抽奖机共享奖项池，管理员可在游戏内 GUI 增删奖项、调整权重与代币/金币/命令奖励。 |
| **大厅模式** | 在指定世界自动发放快捷物品，点击直接打开主菜单。 |
| **双存储** | YAML 文件（默认）或 MySQL（HikariCP 连接池，支持 BungeeCord 跨服）。 |
| **自定义事件** | 进入 / 离开 JoyMart 时可执行配置的命令。 |
| **公开 API** | 通过 `GameBoxAPI` 或 Bukkit ServicesManager 供其他插件调用。 |
| **挂钩支持** | PlaceholderAPI、bStats、CalendarEvents（自动检测）。 |
| **多语言** | 内置英 / 中 / 德 / 西 四种语言。 |
| **Scoreboard** | 游戏进行中在侧边栏显示当前游戏、分数、最高分、代币。 |

---

## 安装与构建

### 方式 A：自行构建（推荐）

需要 **JDK 17+** 和 **Maven 3.6+**：

```bash
git clone https://github.com/myzer121/JoyMart.git
cd JoyMart
mvn clean package
```

构建产物：`target/JoyMart-1.0.jar`

### 方式 B：使用预编译 jar

如果你拿到了发布版的 `JoyMart-1.0.jar`，直接放到服务器的 `plugins/` 目录即可。

### 服务器要求

- **Paper / Spigot 1.20.1+**（Paper 推荐）
- **Java 17+**
- 可选：[Vault](https://www.spigotmc.org/resources/vault.34315/) + 任意经济插件
- 可选：[PlaceholderAPI](https://github.com/HelpChat/PlaceholderAPI)
- 可选：[CalendarEvents](https://github.com/niklasstern/CalendarEvents)（节日彩蛋）

---

## 首次使用

1. 把 `JoyMart-1.0.jar` 放进 `plugins/`，启动一次服务器，让插件生成默认配置。
2. 编辑 `plugins/JoyMart/config.yml` 按需调整（经济、大厅、邀请、音效、Scoreboard 等）。
3. 编辑 `plugins/JoyMart/games.yml` 开关每款游戏、配置子命令。
4. 编辑 `plugins/JoyMart/tokenShop.yml` 增删商店商品。
5. 执行 `/gba reload` 重载，或重启服务器。
6. 玩家执行 `/gamebox` 即可打开主菜单。

---

## 命令

### 玩家命令（默认 `/gamebox`，简写 `/gb`）

| 命令 | 权限 | 说明 |
|---|---|---|
| `/gamebox` | `gamebox.use` | 打开主菜单 |
| `/gamebox <子命令>` | `gamebox.play.<id>` | 直接进入指定游戏（子命令见 `games.yml`） |
| `/gamebox token` | `gamebox.use` | 查看自己代币余额 |
| `/gamebox info` | `gamebox.use` | 查看插件信息 |
| `/gamebox help` | `gamebox.use` | 查看帮助 |
| `/gamebox accept [玩家]` | `gamebox.use` | 接受对战邀请 |
| `/gamebox decline [玩家]` | `gamebox.use` | 拒绝对战邀请 |

### 管理命令（默认 `/gameboxadmin`，简写 `/gba`）

| 命令 | 权限 | 说明 |
|---|---|---|
| `/gba reload` | `gamebox.admin.reload` | 重载所有配置 |
| `/gba tokens <玩家> get` | `gamebox.admin.tokens` | 查看玩家代币 |
| `/gba tokens <玩家> set <数量>` | `gamebox.admin.tokens` | 设置玩家代币 |
| `/gba tokens <玩家> give <数量>` | `gamebox.admin.tokens` | 给予玩家代币 |
| `/gba tokens <玩家> take <数量>` | `gamebox.admin.tokens` | 扣除玩家代币 |
| `/gba games <游戏> <enable\|disable>` | `gamebox.admin.games` | 开关某款游戏 |
| `/gba games` | `gamebox.admin.games` | 打开游戏管理 GUI（开关游戏、编辑彩票/抽奖机奖项池） |
| `/gba language` | `gamebox.admin.language` | 检查语言文件完整性 |
| `/gba migrate <file\|mysql>` | `gamebox.admin.migrate` | 在 YAML/MySQL 之间迁移数据 |
| `/gba reset <游戏>` | `gamebox.admin.reset` | 重置某游戏的高分榜 |
| `/gba shop` | `gamebox.admin.shop` | 打开商店管理 GUI（上架/下架物品） |

---

## 权限

| 权限节点 | 默认 | 说明 |
|---|---|---|
| `gamebox.use` | true | 使用 `/gamebox` 主命令 |
| `gamebox.play.*` | true | 游玩所有游戏 |
| `gamebox.play.<id>` | true | 游玩指定游戏（如 `gamebox.play.2048`） |
| `gamebox.gamegui.<id>` | true | 在主菜单看到指定游戏的图标 |
| `gamebox.admin.*` | op | 所有管理权限 |
| `gamebox.admin.shop` | op | 通过 GUI 上架/下架商店物品 |
| `gamebox.shop.<custom>` | false | 商店商品的购买权限（在 `tokenShop.yml` 中按商品定义） |

每个游戏在加载时会自动注册对应的 `gamebox.play.<id>` 和 `gamebox.gamegui.<id>` 权限（默认 true）。商店商品的 `buyPermission` 不会自动注册，请用你的权限插件（如 LuckPerms）分配。

---

## 配置文件说明

### `config.yml` — 全局配置

关键节点（与 `plugins/JoyMart/config.yml` 完全对应）：

```yaml
# ---------------- Economy ----------------
economy:
  tokens: true              # 是否启用 JoyMart 代币（内部货币，按玩家存储）
  vault: true               # 是否挂钩 Vault（金币奖励/消耗，可选）

# ---------------- Storage ----------------
storage:
  type: yaml                # yaml = 本地文件，mysql = 数据库（HikariCP 连接池）
  autoSaveInterval: 120     # 自动保存间隔（秒），0 = 关闭（仅退出时保存）
  mysql:                    # storage.type=mysql 时生效
    host: localhost
    port: 3306
    database: gamebox
    user: root
    password: ''
    poolSize: 10            # HikariCP 连接池大小
    bungee: false           # true = BungeeCord 子服共享高分榜

# ---------------- Language ----------------
language:
  default: en               # en / zh / de / es（缺失时回退到 en）

# ---------------- Lobby ----------------
lobby:
  enabled: false            # 大厅模式
  worlds: [world]           # 在这些世界发放快捷物品
  slot: 4                   # 快捷物品所在槽位 (0-8)
  material: NETHER_STAR
  name: '&6&lJoyMart'
  lockItem: true            # 是否禁止玩家丢弃/移动快捷物品

# ---------------- Invitations ----------------
invitations:
  expiry: 30                # 邀请有效时长（秒）
  style: json               # json（聊天可点击）/ actionbar / title

# ---------------- Navigation ----------------
navigation:
  mainMenuSize: 45          # 主菜单大小（9 的倍数，最大 54）
  backButtonSlot: 40        # 游戏 GUI 中返回按钮槽位（-1 = 无）
  closeButtonSlot: 44       # 关闭按钮槽位（-1 = 无）

# ---------------- Sounds ----------------
sounds:
  enabled: true
  click: UI_BUTTON_CLICK
  win: ENTITY_PLAYER_LEVELUP
  lose: BLOCK_ANVIL_LAND
  token: ENTITY_EXPERIENCE_ORB_PICKUP

# ---------------- Scoreboard ----------------
scoreboard:
  enabled: true             # 游戏进行中侧边栏计分板（显示游戏/分数/最高分/代币）

# ---------------- Custom commands ----------------
commands:
  onEnter: []               # 玩家进入 JoyMart 时以控制台执行
  onLeave: []               # 玩家离开 JoyMart 时以控制台执行

# ---------------- Shop ----------------
shop:
  enabled: true             # 代币商店总开关

# ---------------- bStats ----------------
bstats: true
```

### `games.yml` — 每款游戏配置

```yaml
games:
  2048:
    enabled: true
    subCommands: [2048, twentyfortyeight]
  minesweeper:
    enabled: true
    subCommands: [ms, minesweeper]
  # ... 其他游戏同理
```

每款游戏的详细参数（分数、奖励、费用、连击阈值等）在 `plugins/JoyMart/games/<id>/config.yml`，首次启动时从 jar 内自动复制。

### 彩票 / 抽奖机的奖项池配置

彩票（`lottery`）和抽奖机（`slotmachine`）共享同一种奖项池格式，可在游戏内 GUI 编辑（推荐），也可直接编辑 `plugins/JoyMart/games/<id>/config.yml`：

```yaml
# 一次抽奖 / 开奖消耗的代币（0 = 免费）
cost: 20

# 奖项池：按权重加权随机抽取一个
prizes:
  - weight: 50            # 权重（越大越易抽中，必须 >= 0）
    tokens: 10            # 奖励代币数（可省略）
    money: 0.0            # 奖励金币（需 Vault，可省略）
    commands: []          # 控制台命令（%player% 替换为玩家名，可省略）
    displayName: '&e小奖 +10 代币'
  - weight: 5
    tokens: 200
    money: 100.0
    commands:
      - 'say %player% 中了头奖！'
    displayName: '&6&l头奖 +200 代币 + 100 金币'
  - weight: 45
    tokens: 0
    displayName: '&7未中奖'
```

> 在 GUI 中编辑时，所有改动会即时回写到这个文件，无需手改。

### `tokenShop.yml` — 代币商店

支持分类（categories）+ 商品（items）。每个商品字段：

```yaml
items:
  my_item:
    material: DIAMOND
    name: '&bDiamond'
    lore:
      - '&7Cost: &e50 tokens'
    cost: 50
    permission: ''            # 看到该商品所需权限（空 = 所有人可见）
    buyPermission: ''         # 购买所需权限（空 = 所有人可买）
    commands:                 # 购买时以控制台身份执行
      - 'give %player% diamond 1'
    closeAfter: true          # 购买后关闭 GUI（默认 true）
    confirm: false            # 是否需要二次点击确认（默认 false）
```

`%player%` 会被替换为购买者名字。

### 商店管理 GUI（分类管理 / 上架 / 下架）

无需手改 `tokenShop.yml`，有权限的管理员可直接在游戏内用 GUI 管理：

**两种入口：**
1. `/gba shop`（需要 `gamebox.admin.shop` 权限）
2. 打开 `/gb` 主菜单 → 进入代币商店 → 右下角"管理商店"按钮（仅有权限者可见）

**操作：**
- **添加分类**：在商店管理主界面点击左下角的"+ 添加分类"按钮 → 在聊天中输入分类键名（小写字母/数字/下划线）→ 再输入显示名称 → 新分类即创建，自动保存到 `tokenShop.yml`。
- **重命名分类**：进入某个分类的管理页 → 点击底部"重命名分类"按钮 → 在聊天中输入新名称 → 自动保存。
- **删除分类**：进入某个分类的管理页 → 点击底部"删除分类"按钮 → 再次点击确认 → 该分类及其所有商品一并删除并保存。
- **上架**：进入某个分类的管理页 → 把要上架的物品**拿在鼠标光标上** → 点击底部的"+ 上架物品"槽位。物品会以默认价格 10 代币加入该分类，自动保存到 `tokenShop.yml`。
- **下架**：在分类管理页点击任意商品 → 第一次点击提示确认 → 第二次点击即删除并保存。
- **改价 / 改名**：在分类管理页点击商品进入编辑页 → 可用 +/- 按钮调价，或点击改名按钮在聊天中输入新名称。
- 上架时商品名/lore 会沿用光标物品的显示名与 lore；价格默认 10，也可在编辑页调整。

> **上架 / 下架修复说明**：旧版本在事件回调内同步修改光标物品，会出现"物品从光标上消失但未真正上架/下架"的同步问题。本版本已将所有光标操作与界面刷新统一通过 `Bukkit.getScheduler().runTask()` 延迟到下一 tick 执行，避免客户端 / 服务端状态错位。同时所有管理界面文本已完全中文化（沿用 `language_zh.yml` 中的 `gui.shop*` 键）。

> 权限 `gamebox.admin.shop` 默认仅 OP 拥有，是 `gamebox.admin.*` 的子权限。普通玩家看不到管理按钮，也无法执行 `/gba shop`。

---

## 游戏模式

### 单人游戏（2048 / 扫雷 / 打地鼠 / 宝石迷阵）

打开 `/gb` → 选择游戏 → 点击 **开始游戏** 即可。分数自动记录到高分榜。

### 彩票（Lottery）

经典数字型彩票，玩法完全在 GUI 中进行：

1. 打开 `/gb` → 选择 **彩票** → 选择 4 个数字（每位的范围 1–9）。
2. 点击 **开奖** 按钮即可从奖项池中加权随机抽奖一次。
3. 奖项池支持代币、金币（需 Vault）和控制台命令三种奖励，可同时下发。

**配置奖项（管理员 GUI）：**

- 入口：游戏内执行 `/gba games`，进入 **彩票** 管理页 → 点击 **奖项池** 按钮；或在 `lottery` 的管理 GUI 中点击奖项池图标。
- 在奖项池编辑界面可：增加新奖项、删除已有奖项、调整每个奖项的**权重**（越大越易抽中）、**代币奖励**、**金币奖励**与**命令奖励**。
- 改完点 **保存** 即写入 `plugins/JoyMart/games/lottery/config.yml`。

### 抽奖机（SlotMachine）

经典拉霸机，3 格滚轮，点击拉杆后停在随机图案上：

- 三个图案全相同 → 触发头奖（从对应奖项池加权抽取）
- 两个图案相同 → 触发小奖（按预设代币返还）
- 全不同 → 未中奖
- 奖项池配置方式与彩票完全一致（管理员 GUI 入口同理）。

### 迷宫（Maze）

每局随机生成一个 9×9 的递归回溯法迷宫，玩家点击相邻格子即可向上下左右移动一格，目标是到达右下角终点：

- 走过的路径会变绿色，方便回看。
- 计时器记录通关用时；用时越短，通关奖励代币越多。
- 中途关闭 GUI 视为放弃，无奖励。
- 通关后自动记录最佳用时到高分榜。

### 贪吃蛇（Snake）

经典贪吃蛇，在 9×5 的双箱界面中自动移动：

- 点击方向按钮（上/下/左/右）控制蛇的移动方向，不能直接反向。
- 吃到食物（红苹果）蛇身变长并 +10 分，自动刷新新食物。
- 撞墙或咬到自身即游戏结束；填满整个棋盘视为通关。
- 支持暂停/继续按钮；分数越高代币奖励越多。

### 小恐龙跑酷（DinoRun）

灵感来自 Chrome 离线小恐龙，侧向滚动跑酷：

- 恐龙自动向前奔跑，仙人掌从右侧滚入。
- 点击跳跃按钮（3 格宽，方便点击）跳过仙人掌。
- 每存活 1 tick +1 分；分数越高障碍物间隔越短（难度递增）。
- 撞到仙人掌即游戏结束；分数即存活时长。

### 国际象棋（Chess）

6×6 洛斯阿拉莫斯变体（无象），支持双人对战和 AI 对战：

- 白方先行；玩家 1 执白（底部），玩家 2 / AI 执黑（顶部）。
- 棋子：车、马、后、王、马、车 + 6 个兵（无象）。
- 点击己方棋子选中（绿色高亮可走点），再点击目标格落子。
- 兵到达底线自动升变为后；支持将军/将杀/逼和判定。
- 认输按钮可随时结束；将杀对方即获胜。
- **双人对战**：邀请好友共享同一棋盘，轮流走棋。
- **AI 对战**：两步 minimax AI，会优先吃子、阻挡威胁、控制中心。

### 大富翁（Monopoly）

经典桌游大富翁的 GUI 复刻，支持 **2–3 人** 同场对战，也支持 **1 人 + AI** 模式：

- 26 格环形棋盘渲染在 6×9（54 格）箱子的外圈，中心为骰子、玩家信息与操作按钮。
- 格子类型：起点（GO，经过领 200 元）、监狱、免费停车、入狱；机会格与命运格随机抽卡；17 块可购买地产（老街、市政厅、火车站、机场、摩天大楼、金矿 等）。
- 每回合：当前玩家点击 **掷骰** → 单颗骰子前进相应步数 → 落在空地可购买、落在他人地盘自动扣租金、落在机会/命运格抽卡 → 点击 **结束回合**。
- 余额归零即破产淘汰，最后一名未破产的玩家获胜。
- **3 人模式独有下注**：开局前每个玩家可押注一名玩家，押中赢家可获额外奖励。
- **AI 对手**：单人进入即自动补一名 AI 玩家，AI 会自动购买地产、掷骰、结束回合。
- 奖励：胜 +10 代币、平 +3 代币、负 +2 代币。

### 双人游戏（井字棋 / 四子棋 / 石头剪刀布 / 战舰 / 国际象棋）

双人游戏提供两种对战方式：

1. **邀请好友** — 点击 **邀请对战**，在聊天中输入对方玩家名，对方会收到可点击的邀请消息（接受/拒绝）。接受后双方共享同一游戏界面，轮流操作。

2. **对战 AI** — 点击 **对战 AI**，无需等待对手，立即开始。AI 会自动在 1 秒后出招：
   - **井字棋 AI**：策略型 — 先找自己能赢的位置 → 再封堵对手 → 优先占中心 → 占角 → 随机。
   - **四子棋 AI**：策略型 — 先找自己能赢的列 → 再封堵对手 → 偏好中间列 → 随机。
   - **石头剪刀布 AI**：随机出招。
   - **战舰 AI**：随机射击未攻击过的格子。
   - **国际象棋 AI**：两步 minimax — 评估每步走子后对手最佳回复，优先吃子、控制中心、阻挡威胁。

> 对战 AI 的结果同样计入高分榜和代币奖励。

---

## 游戏计分与特殊事件

每款游戏在游戏进行中会实时通过 `onScoreChange` 更新侧边栏计分板，并在达成里程碑时触发 `onGameEvent` 给予即时奖励：

| 游戏 | 计分规则 | 特殊事件奖励 |
|---|---|---|
| **2048** | 每次合并 = 合并方块值之和 | 达成 2048 +50 代币 |
| **扫雷** | 每揭开 1 安全格 +10 分 | 触雷时播放受伤音效 |
| **打地鼠** | 击中 +1 分，3 连击额外 +2 分 | 击中黄金地鼠 +5 代币 |
| **宝石迷阵** | 消除 N 个宝石 N×10×连锁倍数 | 4+ 连锁每次 +N 代币 |
| **彩票** | 每次开奖消耗 1 张彩票（或代币） | 奖项池随机奖励（代币/金币/命令） |
| **抽奖机** | 每次抽奖消耗代币 | 三连相同 → 头奖奖项池；两连相同 → 固定代币 |
| **迷宫** | 通关用时（秒）越短分越高 | 通关基础 30 代币，每多 10 秒 -2 代币（最低 5） |
| **战舰** | 击中 +100，击沉 +500 | 击沉奖励 = 船大小×5 代币 |
| **四子棋** | 每落子 +1，胜利时 4 分 | 形成 3 连威胁 +2 代币 |
| **井字棋** | 胜 +3，平 +1 | 形成 2 连威胁 +1 代币 |
| **石头剪刀布** | 胜 +2，平 +1 | 2 连胜 +2，3 连胜 +3 代币 |
| **贪吃蛇** | 每吃一个食物 +10 分 | 每 50 分里程碑 +1 代币 |
| **小恐龙跑酷** | 每存活 1 tick +1 分 | 每 50 分里程碑 +1 代币 |
| **国际象棋** | 胜 200 + 吃子价值×10，平 100 + 吃子价值×5 | — |
| **大富翁** | 胜 / 平 / 负 按结果结算 | 胜 +10、平 +3、负 +2 代币（3 人下注命中额外奖励） |

---

## 数据存储

### YAML（默认，单服）

- 玩家数据：`plugins/JoyMart/data/players/<uuid>.yml`
- 高分榜：`plugins/JoyMart/data/scores/<gameId>.yml`

### MySQL（跨服推荐）

在 `config.yml` 中开启 `mysql.enabled: true` 并填好连接信息。JoyMart 会自动用 HikariCP 连接池管理连接，所有 BungeeCord 子服共享同一份高分榜与代币数据。

**迁移**：`/gba migrate mysql` 把现有 YAML 数据导入 MySQL；`/gba migrate file` 反向。

---

## PlaceholderAPI 占位符

挂钩 PlaceholderAPI 后可用：

| 占位符 | 说明 |
|---|---|
| `%gamebox_tokens%` | 当前玩家代币余额 |
| `%gamebox_highscore_<id>%` | 当前玩家在某游戏的最高分（如 `%gamebox_highscore_2048%`） |
| `%gamebox_ingame%` | 当前玩家是否在游戏中（true/false） |

---

## 公开 API（供其他插件调用）

JoyMart 在启动时会向 Bukkit ServicesManager 注册 `GameBoxAPI`，其他插件可通过三种方式获取：

```java
// 方式 1：静态单例（最快）
GameBoxAPI api = GameBoxAPI.getInstance();

// 方式 2：ServicesManager（推荐，解耦）
GameBoxAPI api = Bukkit.getServicesManager().load(GameBoxAPI.class);

// 方式 3：直接拿主类
GameBox plugin = GameBoxAPI.getPlugin();
GameBoxAPI api = plugin.getApi();
```

常用方法：

```java
// 代币
int balance = api.getTokens(uuid);
api.addTokens(uuid, 100);
api.removeTokens(uuid, 50);
boolean ok = api.payIfCanAfford(uuid, 30);

// 高分
long hs = api.getHighScore(uuid, "2048");
int rank = api.submitScore("2048", uuid, "Steve", 1234L);
List<TopList.Entry> top = api.getTopList("2048", 10);
api.resetHighScores("2048");

// 状态
boolean inMenu = api.isInGameBox(uuid);
boolean inGame = api.isInGame(uuid);
boolean in2048 = api.isInGame(uuid, "2048");
Set<String> games = api.getEnabledGameIds();

// 操作
api.openMainMenu(player);
api.openTopList(player, "2048");
api.enterGameBox(player);
api.leaveGameBox(player);
api.saveAll();
api.reload();
```

完整方法签名见 [GameBoxAPI.java](src/main/java/me/nikl/gamebox/GameBoxAPI.java)。

---

## 自定义事件

JoyMart 触发两个 Bukkit 事件，其他插件可监听：

```java
@EventHandler
public void onEnter(EnterGameBoxEvent e) {
    Player p = e.getPlayer();
    // ...
}

@EventHandler
public void onLeave(LeftGameBoxEvent e) {
    Player p = e.getPlayer();
    // ...
}
```

事件类位于 `me.nikl.gamebox.events` 包。

---

## 缓存与性能

- **GBPlayer** 在玩家加入时一次性加载到内存，之后所有读写都走缓存，只在 `autoSaveInterval`（默认 300 秒）或玩家退出时落库，避免每次操作查 DB。
- **GBPlayer.isDirty()** 标记机制：只有真正变化过的玩家才会被写入。
- **TopListPage** 高分榜 GUI 带 30 秒 TTL 缓存，新纪录产生时自动失效，避免重复查全表。
- **MySQL 模式** 使用 HikariCP 连接池（默认 10 连接），跨服安全。

---

## 常见问题

**Q: 玩家进游戏后背包被清空了？**  
A: 这是设计行为——进入 JoyMart 时会保存并清空背包，离开时自动还原。如果服务器异常崩溃导致背包没还原，重启后玩家重新进一次 JoyMart 再离开即可恢复。

**Q: 双人邀请发出去对方没收到？**  
A: 检查 `config.yml` 的 `invitations.style` 设置（`json` / `actionbar` / `title`），以及对方是否在同一个服务器（跨服邀请需要 MySQL 模式 + BungeeCord 事件转发，本版本暂不内置跨服邀请转发）。

**Q: 商店商品点击没反应？**  
A: 检查 `tokenShop.yml` 缩进是否正确，以及商品的 `buyPermission` 是否被权限插件正确分配。

**Q: 上架 / 下架时光标物品消失了？**  
A: 早期版本在 `InventoryClickEvent` 内同步修改光标，会和客户端预测冲突导致物品"卡没"。本版本已将所有光标修改和 `build()` 调用包裹在 `Bukkit.getScheduler().runTask()` 中延迟到下一 tick 执行，彻底解决该问题。如果你仍遇到，请确认 jar 文件已替换为最新构建版本。

**Q: 彩票 / 抽奖机的奖项怎么改？**  
A: 管理员执行 `/gba games` → 选择对应游戏 → 点击 **奖项池** 按钮，在 GUI 中可视化增删奖项、调整权重和奖励即可，保存后自动写入对应游戏 `config.yml`，无需手改文件。

**Q: 迷宫太简单 / 太难？**  
A: 迷宫大小写死为 9×9（递归回溯法保证唯一通路）。如需调整，可修改 `MazeSession.java` 中的 `n` 常量并重新构建。

**Q: 如何添加自己的语言？**  
A: 复制 `plugins/JoyMart/language/language_en.yml` 为 `language_xx.yml`，翻译所有键值，然后在 `config.yml` 设置 `language.default: xx`。游戏专用语言文件位于 `language/game_<游戏名>/language_xx.yml`，同样复制翻译即可。

**Q: 设置了中文但游戏内还是英文？**  
A: 确保 `config.yml` 中 `language.default: zh`，然后执行 `/gba reload`。插件会自动将 `language_zh.yml` 和各游戏的 `language_zh.yml` 复制到 `plugins/JoyMart/language/` 目录。

---

## 构建依赖

| 类型 | 依赖 |
|---|---|
| provided | PaperAPI、Vault、PlaceholderAPI |
| 运行时软依赖（仅检测存在性，无需编译期） | CalendarEvents |
| compile (shade, 重定位到 `me.nikl.gamebox.common.*`) | bstats-bukkit、HikariCP、slf4j-nop、ACF (Paper)、ExpiringMap、jsr305 |

完整依赖见 [pom.xml](pom.xml)。

---

## License

见 [LICENSE](LICENSE)。
