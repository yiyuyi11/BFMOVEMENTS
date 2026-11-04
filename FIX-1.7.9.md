# 1.7.9

配置文件为 `config/battlefield-movement-common.toml`。多人游戏修改服务端配置后重启，客户端会在登录时取得动作参数。

- `[slide]` 的 `enabled`、`speed`、`distance` 控制滑铲，`crouch_style = true` 使用冲刺接蹲滑姿态。
- `[dive]` 的 `enabled`、`speed`、`distance` 控制飞扑，`up` 控制起跳高度。
- `[prone]` 的 `enabled` 和 `speed_penalty` 控制趴姿和匍匐速度。匍匐可持续，飞扑关闭趴姿后仍可触发并正常落地。
- 缩窄趴姿和滑铲碰撞箱。镜头、准星射线与 TaCZ 共享趴姿眼位，并兼容使用 ShootParameters 的卓越前线版本。
- 网络协议升级到 4，联机请同时更新客户端和服务端。
