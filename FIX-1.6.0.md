# 1.6.0

## 趴地/翻滚后头部永久歪斜

- 根因：原版 `HumanoidModel.setupAnim` 每帧只重写 `xRot` / `yRot`，**从不重置 `zRot`**。
  趴地姿势把侧倾写进 head / body / 四肢的 `zRot`，姿势结束后没人清，于是头部一直保持最后的侧倾角。
- 修复：新增 `PronePoseRenderer.clearRoll(PlayerModel)`，在既无趴地也无翻滚姿势时从 `PlayerModelMixin`
  调用，只把各部件 `zRot` 归零（包括帽子/外套/袖子/裤子的复制层），不触碰行走与瞄准姿势写入的 x/y 旋转。

## 诊断保留

- `[BFM] camera roll=...` 诊断行仍保留，用于后续排查相机侧倾。
