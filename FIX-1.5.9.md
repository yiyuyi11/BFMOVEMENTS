# 1.5.9

## 诊断：趴地起身的歪头

- 相机侧倾（`ViewportEvent.ComputeCameraAngles` 里的 dive roll）现在会在非中性时每 0.5 秒打一行
  `[BFM] camera roll=... extra=... pronePhase=... proneAmount=... progress=...`，仅日志，不改变画面。
- 目的：区分歪头来自「相机 roll」「趴地退出过渡」还是「模型本身」。复现一次后把这几行发回来即可定位。
