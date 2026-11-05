# Battlefield Movement 移植记录

已通过 Java 17 编译、ForgeGradle 构建及重新混淆。尚未在游戏内实测；这是测试移植版。

- 将七种网络消息改接 Forge SimpleChannel，保留方向限制、服务端校验和主线程处理。客户端与服务端必须使用相同移植版本。
- 保留滑铲、飞扑、趴下/起身衔接、镜头平滑及自定义趴地姿态。
- 适配 PlayerAnimator 1.0.2-rc1+1.20、属性 UUID、Forge 事件和玩家渲染参数。
- MixinGradle 生成并打包 SRG refmap；眼高注入使用精确描述符，Forge 新增 setForcedPose 不进行原版混淆映射。
- 静态核对 19 个原版/TaCZ 注入目标，并核对可选 TaCZ 注入回调参数。
- 所有 65 个类使用 Java 17 字节码，未残留 NeoForge 包引用。

仍需游戏内检查：单人世界加载、多人姿态同步、滑铲后趴下、快速起身、TaCZ 射击视线、第三人称与 PlayerAnimator 组合。没有进行这些实测，不能视为正式稳定版。
