# Battlefield Movement

Minecraft 1.21.1 / NeoForge 21.1，当前版本 1.5.7。

滑铲、飞扑、战术趴下与起身。默认 Z 趴下／起身，冲刺加 Z 飞扑，左 Alt 或冲刺加潜行滑铲。滑铲可直接接趴下，趴下与起身中途可反向衔接。

详见 [1.5.7 更新说明](FIX-1.5.7.md)。客户端和服务端必须同时使用同版本；当前网络协议为 3。

## 构建

使用 Java 21。将 NeoForge 1.21.1 的 TaCZ 和 Player Animation Library 依赖分别放到 `libs/tacz.jar` 与 `libs/playeranimator.jar`，执行 `./gradlew build`。这些依赖仅用于编译，不在仓库中分发。

`build-local.py` 是本地整合包环境的快速构建入口，依赖该环境的 classpath；常规开发使用 Gradle。

动画来源和许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
