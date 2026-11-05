# 1.7.0

## 总开关

- 新增公共配置 `enabled`（`battlefield-movement-common.toml` 顶层），关闭后滑铲、飞扑、趴地、镜头下沉、眼高偏移全部停用。
- 新增按键 **Toggle Battlefield Movement**（默认未绑定，按键设置 → 战场机动）：只对本人生效，不写配置文件。
- 新增 `/bfm toggle`（需要权限 2）：按世界切换并写入配置，`/bfm` 查询当前状态。
- 关闭时会立刻把已有姿态重置为站立，并跳过姿势渲染与相机 roll，不会残留歪头或半蹲相机。
