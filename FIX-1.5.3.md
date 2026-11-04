# Battlefield Movement 1.5.3：起身卡顿优化与崩溃分析

本版针对 1.5.2 的动画开销与起身末尾顿挫调整：

- 移除 TaCZ 动画入口每次调用时重复执行的反射查找与动画清理，保留阶段切换时的一次清理和动画入口互斥。
- 缓存 TaCZ 桥接方法，在客户端初始化时预先解析桥接及动画数据，减少首次动作时的同步加载。
- 骨骼快照复用固定数组，移除逐帧的 Stream、快照数组及六个 PartPose 对象创建。
- 退出阶段复用基础采样，根旋转只按已有平滑曲线推进。
- 起身末尾的旋转衔接当前正常持物与走路姿态，位置回归模型初始偏移，避免先到中立站姿再跳一次持物动作。
- 将阶段诊断输出降为 debug。

安装：退出游戏，只替换旧 battlefield-movement JAR 为 1.5.3；保留原 BattlefieldOps 配套包，多人服建议两端同步更新。没有自动安装或修改启动器设置，不使用项目旧 install.ps1。

## 本次崩溃证据

读取用户提供的 minecraft-exported-crash-info-2026-11-03-13-07-18.zip 和 hs_err_pid51044.log：

- 最终退出为 Java 25.0.4.1 HotSpot 的 EXCEPTION_ACCESS_VIOLATION，发生在渲染线程；不是 Java 层可捕获的普通模组异常。
- 问题帧是 C2 编译后的 PlayerRenderer.getArmPose。原生错误报告涉及 Create 的 CustomArmPoseItem 类型检查，当时物品对象为 LRTactical MeleeItem。这只能定位出错现场，不能据此认定 Create、LRTactical 或动作模组单独造成了 JVM 错误。
- 在该次起身前还出现独立的 C2ME ClassCastException：ChunkMap 无法转为 IChunkSystemAccess；报错链为 c2me_notickvd 的 PlayerNoTickLoader。该异常与区块系统模块配置/兼容有关，本版未修改 C2ME，不能宣称消除了它。

动作 JAR 无法承诺修复 JVM 原生崩溃。可优先在启动器中改用本机已有 Java 21：C:\Program Files\Java\jdk-21\bin\javaw.exe。若暂时保留 Java 25，可使用附带文本中的单方法编译排除参数进行临时规避；该方法仍需实际运行确认。

验证范围：完成 Java 21 编译、包内版本和 Mixin 清单静态核对。遵循此前要求，没有启动游戏或运行测试，没有实际帧耗时测量，因此不承诺具体帧率提升或已验证崩溃消失。
