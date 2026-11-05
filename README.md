# Battlefield Movement — Forge 1.20.1

**Minecraft 1.20.1 ｜ Forge 47.4.16+ ｜ 版本 1.7.14-forge1201 ｜ MIT**

滑铲 / 飞扑 / 趴地 / 起身衔接 / 镜头平滑 / 自定义趴地姿态。1.21.1 版本在 `main` 分支。

## 构建状态

`clean build` 通过（ForgeGradle 6.0.54 / Java 17 / Gradle 8.8），产物：

```
build/libs/battlefield_movement-1.7.14-forge1201.jar
```

- MixinGradle 生成 SRG refmap 并打包；
- 依赖：可选 `playeranimator`（1.20.1 为 **1.0.2-rc1**，范围 `[1.0.0,2.0.0)`）、可选 TaCZ。

## 构建

```bash
gradle build     # JDK 17 + Gradle 8.8
```

## 1.20.1 适配要点

- PlayerAnimator 1.0.x 与 2.x API 不同：注册表返回 `KeyframeAnimation`，需要 `KeyframeAnimationPlayer` 包装，
  且没有 `setFirstPersonMode`；动画资源目录是 `assets/<ns>/animations/`（2.x 是 `player_animations/`）；
- 七种网络消息改为 Forge `SimpleChannel`；
- 属性修饰符用 UUID 构造；
- 趴地/翻滚姿势结束后会清零模型 `zRot`，避免原版 `setupAnim` 不重置侧倾导致头部永久歪斜。

## 已知限制

- 尚未完成全部实机检查（单人加载、多人姿态同步、TaCZ 视线、第三人称与 PlayerAnimator 组合）。
