# Battlefield Movement 1.5.4：F5 重复起身与垂直镜头跳动

本版针对已确认使用 1.5.3 后仍出现的 F5 重复起身。

## 代码根因

模组用 Pose.SWIMMING 缩小趴姿碰撞箱。LivingEntity.updateSwimAmount() 根据 isVisuallySwimming() 每刻累计 0.09 的原版游泳动画权重，即使人在普通地面趴着也会累积到 1。

模组起身期间替换了 PlayerRenderer.setupRotations，但结束后交回原版；原版 PlayerRenderer.setupRotations 又读取尚未降到零的 getSwimAmount，将模型旋转回水平，再随权重降低抬起。HumanoidModel 也使用同一个权重混合四肢。因此只清 TaCZ、改自定义退出曲线或延长退出阶段都没有处理这个独立动画源。

修正：客户端在本模组拥有趴姿时，将 swimAmount / swimAmountO 清零并阻止累积；渲染读取返回零。交接结束保留 14 刻短保护，等待服务器姿态元数据到达，避免重新累积。水中游泳、实际游泳标志或鞘翅飞行直接让回原版。碰撞姿态和服务端弹道眼高不由这个补丁改动。

另修正垂直画面：原 CameraMixin 无条件把第一人称相机 Y 改为 entity.getY()，导致模拟位置按 20 Hz 阶梯更新。本版动作期间恢复脚底位置的帧内插值；普通站立、蹲伏、跳跃不再由该补丁覆盖，交回原版相机。渲染插值不会拿来改写服务端物理或弹道。

## 安装与验证

退出游戏，将旧 battlefield-movement JAR 替换为 1.5.4，勿并存两个版本。此次两项修复在客户端生效，多人服务器可同步更新。保留当前 BattlefieldOps 配套包，不运行项目旧 install.ps1。本次没有改动实际游戏目录。

完成编译、Mixin 目标签名和包内清单静态核查；遵循此前要求，不启动游戏、不运行测试。实际 F5 连贯性仍需游戏内确认。本版不声称修复此前 Java 25 的原生崩溃或 C2ME 区块异常。
