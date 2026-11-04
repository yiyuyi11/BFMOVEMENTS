# 战地机动 · Battlefield Movement

> Minecraft **1.21.1** ｜ NeoForge **21.1.233+** ｜ 版本 **1.7.8**
> `mod id: battlefield_movement` ｜ `package: cn.bfm`
> 配套：BattlefieldOps **0.8.30-handoff**、TaCZ 1.1.8（可选）、PlayerAnimator 2.0.4（可选）

**趴地动作直接移植自 `Tacz-Animation-Additions-1.2.12.jar`**：使用它的
`prone_tactical_animations.json`（7 帧进入、7 帧退出、37 个每 10° 的角度姿态）以及同一套
PlayerModel 姿态算法，而不是另画一套 PlayerAnimator 趴地动画。滑铲和飞扑是在此基础上新增的功能。

### 1.5.6 动作期间瞄准与发射点

补接此前未注册的 TaCZ 发射点补丁，统一服务端阶段时间与眼高设置，动作有效期改按游戏刻，移除飞扑时改变准星方向的纯视觉俯仰偏移。[说明](FIX-1.5.6.md)。本版变更网络协议，**多人服两端必须同步更新**。最新包：`build/libs/battlefield-movement-1.5.6-neoforge-1.21.1.jar`。

### 1.5.5 起身结束镜头交接

在相机定位和 F5 墙壁避让之前更新动作眼高，两种视角共用同一支点；起身结束时同步眼高缓存后交回原版，避免结束后向下抖动。[说明](FIX-1.5.5.md)。最新包：`build/libs/battlefield-movement-1.5.5-neoforge-1.21.1.jar`。

### 1.5.4 F5 重复起身修复

抑制趴地 SWIMMING 碰撞姿态引出的原版游泳动画权重，避免自定义起身结束后又播放一次原版水平身体到站立的过渡。同时恢复垂直镜头帧间插值，普通状态交回原版相机。[修复说明](FIX-1.5.4.md)。最新包：`build/libs/battlefield-movement-1.5.4-neoforge-1.21.1.jar`。

### 1.5.3 起身卡顿优化

减少动画入口重复清理和反射、复用姿态快照与退出采样，预加载动作数据，并改进起身末尾的正常持物姿态衔接。此次 JVM 原生崩溃分析和规避说明见 [1.5.3 修复说明](FIX-1.5.3.md)。最新包为 `build/libs/battlefield-movement-1.5.3-neoforge-1.21.1.jar`。

### 1.5.2 起身过渡修复

重复阶段同步不再重置起身进度；起身从上一帧实际骨骼姿态平滑退出，镜头和模型共用帧内进度。趴姿期间从 TaCZ 动画总入口让出控制权，避免残留身体动画叠加。客户端完成已有退出过渡后再交回普通站姿。详见 [本版修复说明](FIX-1.5.2.md)。

新 JAR 位于 `build/libs/battlefield-movement-1.5.2-neoforge-1.21.1.jar`，退出游戏后手动替换旧动作模组。本次交付不使用仍绑定旧文件名的 `install.ps1`。

### 1.4.9 修复

- **滑铲/飞扑镜头与弹道仍差一截**：`SlideClient.cameraDrop` / `DiveClient.cameraDrop` 之前返回 `lerp(previous, current, partial)`，而服务端用的是完整固定下沉量；现在动作期间客户端直接返回配置下沉量，和 `MovementEyeHeight` 的服务端值完全一致；
- **镜头仍晚一 tick**：`CameraSmoother.eyeHeight(partial)` 之前仍返回 `lerp(previous, current, partial)`，而实体眼高/子弹起点已经是 `current`；现在直接返回 `current`，相机 Y 也用当前实体 Y，不再慢一拍；
- **TaCZ lie 动画集**：之前把 `isPlayerLie` 强制成 `false`，TaCZ 会选站立 `walk_lower` / `hold_upper` / `reload_upper`；现在趴地时让它返回 `true`，使用 TaCZ 自己的 `lie` / `lie_aim` / `lie_reload`，下体站立动画自然不再播放，枪和手臂也按趴姿动画对齐；
- **起身失败不再躺回去**：`EXITING` 时如果 `canStand` 仍为 false，保持退出阶段等待空间，不再重置成 `PRONE` 导致第二次起身；
- **客户端起身补站姿**：收到 `STANDING` 同步时显式 `setPose(STANDING)`，避免实体姿态停在 SWIMMING 再被原版补一次；
- **TaCZ 下体站立动画（第二次起身）**：`AnimationManager.playLowerAnimation` 在 `isPlayerLie=false` 时仍会播 `walk_lower`/`hold_lower`；现在趴地状态取消该层，并在进入趴地时停掉 TaCZ 的 PlayerAnimator 层，避免“根节点起身 + 腿又起一次”；
- **TaCZ 子弹半 tick 起点**：`EntityKineticBullet` 原本用 `xOld + (x-xOld)/2` 生成子弹，滑铲时比相机落后约 0.2 格；现在玩家射出的子弹直接用当前 `getEyePosition()`，和镜头同 tick；
- **TaCZ 每 tick 清掉趴地姿态（根因）**：解包 TaCZ 后确认 `LocalPlayerCrawl.tickCrawl` / `LivingEntityCrawl.tickCrawling` 在自身 `isCrawling=false` 时会无条件 `setForcedPose(null)`，与我们的 SWIMMING 姿态互相覆盖。现在两端都加了守卫，BattlefieldOps 的对应 mixin 也同步让位，趴地/起身不再来回闪、射击眼高也不再跳；
- **实体层兜底**：新增 `ForcedPoseMixin`，趴地状态激活时任何模组对玩家 `setForcedPose(null)` 都会被取消，不再依赖对方是否装了对的 BF 补丁；
- **镜头不再二次滞后**：去掉 `CameraSmoother` 额外的 lerp，镜头直接等于“阶段进度/下沉量”算出的眼高，和 `getEyeHeight()`、TaCZ 子弹起点在同一 tick 上完全一致；
- **滑铲结束蹲姿**：滑铲结束时若潜行键仍按住，取消滑铲期间的下蹲抑制，自然进入蹲下；若已松开则直接站起（左 Alt 触发的滑铲同样适用）；
- **镜头抽搐/钻地**：镜头目标改成单一来源（趴地阶段进度或站立眼高），不再读取会来回跳的 `player.getEyeHeight()`，也不在 `Camera.setup` 结果上重复叠加偏移；
- **趴地肢体没入地面**：root 下压后补回约 0.98 格，抵消 addon 姿态把模型压到地下的位移；
- **飞扑第三人称不像飞扑**：飞扑不再依赖 PlayerAnimator 片段，改成专用的 `DivePoseRenderer`：root 旋转 -72°、身体水平、手臂前伸、腿后摆；
- **换弹/开枪错位**：保留 TaCZ 手臂动画后，再用 addon 的 `retargetReload` 四元数基变换把手臂放回趴地坐标系；
- **卓越前线兼容**：检测到主手是 `superbwarfare` 枪械时，保留它的手臂动画，只应用趴地身体/头/腿，避免两个模组抢骨骼；
- **第一人称弹道起点**：`getEyeHeight()` / `getEyeY()` 同步滑铲/飞扑时的下沉镜头，TaCZ 的 `EntityKineticBullet` 和服务端弹道都从新眼高生成，不再从站立眼高飞出；
- **滑铲镜头下移**：第一人称目标眼高改为从 `EntityDimensions.eyeHeight` 计算并显式减去滑铲/飞扑下沉量，不再依赖 `getEyeHeight()` 是否被其它模组改写；
- **起身不再“卡两次”**：起身从当前俯卧姿态直接连续混合到站姿，不再播放 addon 里身体前倾 55° 再回正的 exit 曲线；服务端也不会因为趴下第一帧 `onGround` 抖动而 enter→exit→enter 来回切。

---

## 1. 键位（独立、可改）

`选项 → 控制 → 按键绑定 → 战地机动`：

| 键位 | 默认 | 行为 |
|---|---|---|
| `趴下 / 起身（冲刺时飞扑）` | **Z** | 站着按 → 趴下；趴着按 → 起身；**冲刺时按 → 飞扑** |
| `滑铲` | **左 Alt** | 直接滑铲 |
| 潜行键 | 左 Shift | **冲刺时按下 → 滑铲**（可配置关闭）；平时仍是蹲下 |

- 滑铲和趴下不再共用 Z；
- 飞扑是“冲刺 + Z”，普通趴下是“站着 Z”；
- TaCZ 原趴下键默认也是 Z，控制界面可能标红冲突：这是预期的，TaCZ 的原趴下处理已被取消。

按住模式：`prone.hold=true` 时 Z 变为按住趴下、松开起身（冲刺 + Z 仍是飞扑）。

---

## 2. 趴地：直接使用 Addon 的动作数据

`assets/battlefield_movement/prone_tactical_animations.json` 来自
**Tacz Animation Additions 1.2.12**（Locknar，MIT），包含：

- `proneEnter`：7 帧、0.85 秒，从站立到趴下（含手撑地、重心下沉）；
- `proneExit`：7 帧、0.75 秒，从趴下到起身；
- `anglePoses`：37 个姿态，覆盖 -180°～+180°，每 10° 一个，包含身体侧翻、腿部支撑、
  头部和手臂的朝向。

运行逻辑：

1. 服务端进入/退出阶段分别为 **17 / 15 刻**，与 0.85 s / 0.75 s 对齐；
2. 客户端在 `PlayerRenderer.setupRotations` 里取消原版旋转，按 addon 的 root 变换把身体放倒；
3. 在 `PlayerModel.setupAnim(TAIL)` 里按当前视角角度采样 37 个姿态之一，应用腰、头、双臂、双腿；
4. 移动时叠加 addon 的爬行步态（前后/左右、腿的摆动、手臂支撑）；
5. **趴着转动视角**：视角相对身体转到侧面/后面时，姿态数据会让身体侧翻，转到 ±180° 附近自然变成仰躺——
   这就是战地 5 的“转身仰躺”，不是另外用俯仰角硬切；
6. 装了 TaCZ 时，`AnimationManager.isPlayerLie` 返回 false，让 TaCZ 的上半身参考保持在正常骨架，
   避免和我们的趴地姿态抢手臂。

> 这一段是对 1.20.1 addon 的 **1.21.1 重实现**，不是把 1.20.1 的 jar 塞进 1.21.1。
> 由于原 addon 的枪械重定向/换弹手部逻辑依赖 1.20.1 TaCZ，本模组只移植了姿态、根变换、
> 步态和角度采样；持枪时手臂沿用 addon 姿态数据里的持枪姿势，不做逐枪重定向。

### 趴地参数

| 项 | 默认 |
|---|---|
| 进入 / 退出 | 17 / 15 刻（0.85 / 0.75 s） |
| 姿态 | 强制 `Pose.SWIMMING`，0.6 格碰撞箱 |
| 移速 | -35% |
| 跳跃 | 锁定，只能按 Z 起身 |
| 起身空间 | 1.8 格净空，不足时提示 |

---

## 3. 滑铲

| 项 | 默认 | 说明 |
|---|---|---|
| 初速 | 0.46 格/刻 | 约 9 m/s，3 刻加速斜坡 |
| 摩擦 | 0.92 / 刻 | 约 **4 格** |
| 最长 | 16 刻 | 到点结束 |
| 冷却 | 24 刻 | 结束后计时 |
| 镜头 | 下沉 0.55 格 | 第一人称逐帧插值 |
| 碰撞箱 | 不变 | 不能钻 1 格缝 |

滑铲是 PlayerAnimator 动画（`slide_start → slide_loop`），所有切换使用 INOUTSINE 交叉淡入淡出。

## 4. 飞扑

冲刺 + Z：

- 向前上方**真实跃出**（水平 0.38、垂直 0.42），不再每刻重置水平速度，所以是跳跃弧线而不是贴地滑；
- 空中不可转向，落地**本地预测立即趴下**，不再等服务端回包时先站一帧；
- 落地后 24 刻冷却；
- 第一人称：镜头下沉 0.45 格、俯仰 +16°、少量侧滚，FOV +12，落地后平滑收回；
- 动画 `dive`（0.6 s）。

---

## 5. 动画平滑

- 趴地：进入/退出用 addon 的 7 帧插值（Hermite），不是两帧线性；角度姿态每 10° 一个，转身连续；
- 滑铲/飞扑：PlayerAnimator 的 `replaceAnimationWithFade`（INOUTSINE）交叉淡化，不再硬切；
- 镜头：用**绝对眼高**平滑，而不是在 `Camera.setup` 的结果上反复加减偏移，低帧率/第一帧也不会钻到地板下；
- 滑铲速度前 3 刻从 55% 升到 100%，避免起步弹一下；
- 趴地爬行步态按实际位移驱动，不是固定循环。

---

## 6. 与 BattlefieldOps 的 Z 键交接

1. 本模组取消 TaCZ 原生趴下键处理（`@Pseudo` mixin）；
2. 交付的 BattlefieldOps 增加 `cn.bfops.MovementHandoff`：检测到 `battlefield_movement` 后，
   BF 的 `TaczCrawlKeyMixin` 立即让出；
3. BF 操作设置页显示“由战地机动接管”；
4. 没有 BF 时本模组也能独立工作。

> 必须同时替换 BF jar，否则两个模组会同时抢 Z。

---

## 7. 安装

```text
battlefield-movement-1.4.9-neoforge-1.21.1.jar
battlefield-ops-0.8.30-handoff-neoforge-1.21.1.jar   ← 替换原 battlefield-ops-0.8.30
```

删除旧版 `battlefield-ops-0.8.30-neoforge-1.21.1.jar` 和旧版 `battlefield-movement-*.jar`。

本机路径：

```text
D:\USTBL\.minecraft\versions\Mechanomania-test-new\mods\
```

或运行 `install.ps1`（备份旧 BF jar，清理旧 movement jar）。

可选依赖：PlayerAnimator（滑铲/飞扑动画）、TaCZ（枪械联动）、BattlefieldOps（Z 键交接）。

---

## 8. 配置

`config/battlefield-movement-common.toml`

```toml
[slide]
enabled = true
sprint_sneak_starts_slide = true
speed = 0.46
friction = 0.92
max_ticks = 16
cooldown_ticks = 24
camera_drop = 0.55
particles = true
require_sprint = true

[dive]
enabled = true
speed = 0.38
up = 0.42
cooldown_ticks = 24
camera_drop = 0.45

[prone]
enabled = true
hold = false
speed_penalty = 0.35
ground_lift = 0.98      # 趴地姿态整体抬高；腿/手仍插地就调大，漂浮就调小
```

---

## 9. 构建

```powershell
cd BattlefieldMovement
# libs/：tacz-neoforge-1.21.1-1.1.8-hotfix-r1.jar
#        player-animation-lib-forge-2.0.4+1.21.1.jar
gradle build
```

本次交付为离线 `javac` 编译：

```text
build/libs/battlefield-movement-1.4.9-neoforge-1.21.1.jar
```

BattlefieldOps 补丁是在原版 0.8.30 安装 jar 上只替换 3 个 class，见 [BF-CHANGES.md](./BF-CHANGES.md)。

---

## 10. 已验证 / 未验证

已验证：
- 移植的 `prone_tactical_animations.json` 能被 Gson 解析：进入 0.85 s、退出 0.75 s、37 个角度姿态；
  采样结果：正面 `rootY=0`、背面 `rootY=-180`、左侧 `rootY≈73`，说明仰躺/侧翻数据通路正确；
- 1.4.9 全部类通过 Java 21 编译，mixin 配置已登记 `PlayerModelMixin` / `PlayerRendererMixin` /
  `TaczProneAnimatorMixin`；
- 滑铲/飞扑的 7 段动画 JSON 通过 PlayerAnimator codec 解析。

未验证（需要进游戏确认）：
- 真实模型上的关节角度、和 TaCZ 枪械的贴合；
- 与完整 280 模组整合包的其它 PlayerModel/Renderer mixin 冲突。

> 本地最小客户端启动器在 FML 初始化前停住（空 mods 也一样），是启动器/classpath 问题，不是模组报错，
> 所以还没有画面级验收。角度不对时可调 `prone_tactical_animations.json` 的 `anglePoses`/`proneEnter`，
> 或滑铲/飞扑的 `player_animations/*.json`，不必改 Java。

---

## 11. 许可与致谢

本模组代码 MIT。`prone_tactical_animations.json` 及趴地姿态算法移植自
**Tacz Animation Additions 1.2.12**（Locknar，MIT），文件内保留原数据结构。
滑铲/飞扑关键帧数值参考了 MIT 许可的 [Slide!](https://modrinth.com/mod/slide)
与 [TACtical Movement](https://modrinth.com/mod/tactical-movement)。

- **Locknar** — Tacz Animation Additions 的趴地/战术动作体系；
- **Thebugger4 / sheeta** — Slide! 的滑铲动画与动量原则；
- **corbinsmithtx21** — TACtical Movement 的短滑铲定位。
