# 1.7.23 修复说明

适用版本：**1.21.1 NeoForge** 与 **1.20.1 Forge**（移植版）同步修复。

本版四项修复彼此独立，均来自实机反馈。前两项是「抖动/跳变」，第三项是「姿态没有被接管」，
第四项是新增配置项。

- 最新包（1.21.1）：`build/libs/battlefield-movement-1.7.23-neoforge-1.21.1.jar`
- 最新包（1.20.1）：`battlefield_movement-1.7.23-forge1201.jar`

---

## 滑铲镜头平滑

### 症状

滑铲进入时第一人称镜头有顿挫感。

### 原因

`SlideClient.cameraDrop` 直接按 `partial` 在「上一刻眼高」和「当前眼高」之间线性插值，而滑铲的
目标眼高本身是一条跨度很大的曲线（默认下沉 0.55 格）。曲线与线性插值叠加，进入瞬间斜率不连续。

### 修改

给滑铲眼高加了独立的时间轴，与姿态动画解耦：

- 进入 `SLIDE_ENTER_TICKS = 6` 刻
- 退出 `EXIT_TICKS = 4` 刻
- 插值曲线由 `StanceTransition.smooth(x)` 提供（五次平滑 `x*x*x*(x*(x*6-15)+10)`），两端一阶、二阶导均为 0

`SlideClient` 新增 `fromEye` / `started` / `ended` / `exitFrom` 状态，并提供：

- `slideEye(partial)` — 滑铲期间的眼高
- `settling()` — 退出过渡是否仍在进行
- `exitEye(partial)` — 退出过渡的眼高

`cameraDrop` 改为：

```java
active ? 1.62F - slideEye(partial) : 0.0F
```

`SlideClient.stop()` 会先采样 `exitFrom = slideEye(0.0F)` 再置 `active = false`，因此中断滑铲时
镜头从实际位置平滑抬升，而不是跳回。

### 为什么是 6 刻

与 `DiveClient` 的 6 刻进入一致，保持两个动作手感统一。需求原文是「稍微平滑一点点」，不是大幅改动，
所以没有拉长到 8~10 刻。

### 验证

`tools/SlideCurveSim.java` 为无头（不进游戏）曲线检查器，覆盖进入/退出曲线、单帧最大下沉量、
眼高与瞄准射线一致性等，**270 项检查通过**。

反向验证过该检查器确实能发现回归：把 `SLIDE_ENTER_TICKS` 强制改为 1 后，
检查器报 `FAILED: largest single-frame drop 0.3191358 stays under a quarter of 0.54999995`。

---

## 贴方块位置跳变

### 症状

贴着方块移动时（滑铲、飞扑等状态），**画面连续但位置突然跳一下**。
帧率和刻时间都正常，不表现为卡顿。

### 原因

`ProneGeometry.coverSafeEye` 用来防止渲染插值让眼睛「穿到遮挡物另一侧」，调用形式是：

```java
coverSafeEye(player, interpolated, player.getEyePosition(), partial)
```

注意参数顺序：`interpolated` 是 `getEyePosition(partial)`，位于**上一刻与当前刻之间**；
`current` 是最新刻的位置。时间上是 `interpolated … current`。

因此**向前移动**时，插值位置在 `current` 之后，离前方障碍**更远**，`reach` 反而**更大**。
原代码却把「更远」当成危险信号：

```java
return reach(player, interpolated, ray) > currentReach + .16 ? current : interpolated;
```

只要障碍在 4 格内，这个差值约等于 `(1-partial) × 每刻位移`，在每刻的大部分时间里都超过 0.16，
于是大部分帧返回 `current`——整个刻反复使用同一个位置。眼睛每刻才前进一次，帧间不插值，
就是「画面连续但位置突然跳」。

这也解释了为什么只在贴近方块时出现：`currentReach >= 4` 会提前返回，先跳过这段逻辑。

### 修改

比较方向纠正为「只有插值眼**看得更近**时才回退」：

```java
double interpolatedReach = reach(player, interpolated, ray);
return interpolatedReach < currentReach - .16 ? current : interpolated;
```

看得更远只是正常的运动滞后，不需要拦；看得更近才是插值眼可能戳进遮挡物、需要保护的方向。

### 验证

`tools/CoverSmoothSim.java` 复现该场景，**52 项检查通过**：

| | 每帧最大跳变 |
|---|---|
| 平滑应有 | 0.117 格 |
| 修复前（贴方块） | **0.467 格**（24 帧里 12 帧被拉回刻位置）|
| 修复后 | **0.117 格** |

同时验证了真正需要保护的情况仍然生效：插值眼比刻位置**更靠近**遮挡物时照样回退。

### 一个被撤销的错误改法

排查过程中曾给 `ActionEyePositionMixin` 加过「飞扑时跳过 `coverSafeEye`」的特判。那是基于错误假设的
掩盖而非修复，**已撤销**。现在没有任何按姿态开的分支：根因修好后不需要，留着只会掩盖同类问题。

---

## 持枪摆臂

### 症状

手持 TaCZ 枪械滑铲时，双臂仍在摆动。

### 原因

两条路径叠加导致的空缺：

1. 滑铲期间 TaCZ 动画被主动取消——`TaczAnimationOwnerMixin` 取消 `PlayerAnimatorCompat.playAnimation`
   （`AnimController.ownsSlide(player)` 为真时），`TaczProneAnimatorMixin` 另外取消
   `playLowerAnimation` / `playRotationAnimation`。
2. 而 `SlidePoseRenderer.steadyArms` 的第一行是「手持枪械就直接返回」：

```java
if (TaczProneCompat.isFirearm(main) || TaczProneCompat.isFirearm(off)) return;
```

这一行本意是「别跟 TaCZ 抢手臂」。但滑铲时 TaCZ 已经不驱动手臂了，于是**既没有 TaCZ 也没有我们**，
手臂落回原版 `setupAnim` 依 `limbSwing` 算出的行走摆动。袖子在第 82-83 行 `copyFrom` 手臂，
所以看起来是完整两条手臂在摆。

### 修改

枪械分支不再返回，改为调用新增的 `holdArms`：

```java
if (TaczProneCompat.isFirearm(main) || TaczProneCompat.isFirearm(off)) {
    holdArms(model, amount);
    return;
}
```

```java
private static void holdArms(PlayerModel<?> model, float amount) {
    applyTuck(model.rightArm, amount, -1.15f, -0.34f,  0.16f);
    applyTuck(model.leftArm,  amount, -1.28f,  0.44f, -0.20f);
}
```

空手分支同时补了 `tuckArms`（-0.55 俯仰，手臂垂在身侧），因为同一处 `if (!braceGround || support == null) return;`
在蹲姿滑铲（`crouch_style = true`，默认）下也会让空手落回走路摆动。两者都用
`Mth.lerp(amount, ...)` 从当前姿势过渡，进出平滑。

### 未实机验证

**持枪手势的这三个角度是按「双手前收在身前持护」估算的，没有进游戏看过。** 若姿态不自然，
调 `holdArms` 的两个 `applyTuck` 调用即可，参数依次为俯仰、偏航、翻滚，不必改结构。

---

## 趴下速度与步频

### 需求

趴下时腿动得太快；并且需要一个「和站立比的减速幅度」配置。

### 修改

**步频**：`PronePoseRenderer` 里原本写死 `motion.phase += distance * 7.5;`，`7.5` 是固定步频系数。
现在改为：

```java
motion.phase += distance * 7.5 * cn.bfm.BfmConfig.PRONE_STRIDE_SPEED.get();
```

新配置 `[prone] stride_speed`，默认 **1.0**（即原始速度，外观与旧版一致），范围 0.05~2.0。

**减速幅度**：旧配置 `speed_penalty` 是「惩罚比例」（0.35 = -35%，还剩 65%），语义绕。
改为直观的倍率：

```java
double scale = clamp(BfmConfig.PRONE_SPEED_SCALE.get(), 0.0, 1.0);
double penalty = prone ? 1.0 - scale : 0.0;
```

新配置 `[prone] speed_scale`，默认 **0.65**（站立速度的 65%，与旧值 0.35 等价），范围 0.0~1.0。
`0.0` 表示趴下无法移动，`1.0` 表示不减速。

### 配置迁移

**旧键 `speed_penalty` 不会被自动迁移**，新代码不再读取它。装上后配置会自动补上
`speed_scale = 0.65` 与 `stride_speed = 1.0`，旧键留着无害但已失效，可手动删除。

若之前调过旧值，按 `speed_scale = 1 - 旧值` 换算。

---

## 构建与验证方式

两个分支的产物都做过结构校验（条目数、CRC 无损坏、`mods.toml` / `neoforge.mods.toml`、
mixin 配置、refmap 存在性），并用字节码扫描确认新增方法/字段确实进入了产物：

| 分支 | 产物 | 校验 |
|---|---|---|
| 1.21.1 NeoForge | `battlefield-movement-1.7.23-neoforge-1.21.1.jar` | 98 项，无损坏 |
| 1.20.1 Forge | `battlefield_movement-1.7.23-forge1201.jar` | 123 项，无损坏，refmap 存在 |

已确认在产物中的符号：`holdArms` / `tuckArms`（姿态）、`PRONE_SPEED_SCALE` / `PRONE_STRIDE_SPEED`
及配置键 `speed_scale` / `stride_speed`（配置）、`interpolatedReach`（`coverSafeEye` 修复）。
旧 `PRONE_SPEED_PENALTY` / `speed_penalty` 已确认不存在于产物。

### 未经实机验证的部分

本节所有曲线与判据来自无头数值模拟，**没有进游戏做画面级验收**。以下需要实机确认：

- 滑铲镜头的平滑程度是否合适（6 刻 / 4 刻）
- 持枪滑铲时双臂的姿态是否自然
- 趴下步频默认 1.0 是否仍有「腿太快」的观感（若仍有，调到 0.4~0.6）

---

## 1.20.1 移植版额外说明

移植版与本版同步修复，但**该实例装了 `Connector-1.0.0-beta.48+1.20.1.jar`**（Fabric 模组兼容层），
行为可比性低于纯 Forge 环境。若在 1.20.1 上仍遇到异常，请优先在无 Connector 的环境复现。

已知移植差异：1.20.1 的属性修饰符 API 为 `addTransientModifier` / `addOrUpdateTransientModifier`，
1.21.1 为对应新 API，两者语义等价但实现不同，`ProneManager.applySpeed` 分别维护。
