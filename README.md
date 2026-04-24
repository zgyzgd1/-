# 课程表助手 (Timetable Assistant)

一个基于 Kotlin、Jetpack Compose 和 Material 3 的 Android 原生课表应用，强调本地离线、轻量界面和可维护的代码结构。

---

## 全球项目审查报告 (2026-04-24)

### 1. 架构评估 (Architecture Assessment)
项目采用现代 Android 开发的最佳实践：
- **MVVM 架构**: `ScheduleViewModel` 作为核心，通过 `StateFlow` 驱动 UI，实现了严格的单向数据流 (UDF)。
- **Repository 模式**: `TimetableRepository` 封装了 Room 数据库操作，为 UI 层提供简洁、线程安全的数据访问接口。
- **模块化 UI**: UI 层按功能拆分为 `DayScheduleList`、`WeekScheduleBoard` 等独立组件，有效解决了单文件膨胀问题（如 `ScheduleScreen` 的重构）。
- **解耦逻辑**: ICS 解析、冲突检测 (`TimetableConflicts`) 和提醒调度 (`CourseReminderScheduler`) 均抽离为独立逻辑模块，易于测试和维护。

### 2. 技术亮点 (Technical Highlights)
- **持久化方案**: 全面迁移至 **Room**，相比早期 JSON 存储，提供了更好的事务支持和查询性能。
- **后台任务**: 实现了“接力式”提醒机制，结合 `AlarmManager` 的准时性和 `WorkManager` 的兜底补偿，平衡了功耗与提醒准确度。
- **高度定制化**:
    - 支持自定义背景图（含透明度、遮罩、裁剪位移调节）。
    - 支持动态色相偏移，实现个性化主题色切换。
- **数据兼容性**: 完善的 `.ics` 导入导出能力，支持自定义周次解析和重复规则。

### 3. 工程质量与稳定性
- **签名统一 (Milestone)**: 已完成对全版本（从 v0.8 到 v1.27）的签名统一。所有历史 APK 现均由正式 Release 密钥重签，解决了用户升级时的签名冲突痛点。
- **自动化测试**: 核心业务逻辑（ICS 解析、日期计算、闹钟触发算法、数据快照）均由单元测试覆盖，确保了逻辑回归的安全性。
- **发布流程**: 建立了标准化的 PowerShell 发布流，集成了版本自动提升、构建校验、GitHub Release 上传及 APK 自动化归档。

### 4. 近期里程碑 (v1.27)
- **统一本地化**: 修复了提醒格式化和状态文本中的中英文混杂问题，实现了全界面本地化对齐。
- **归档一致性**: `apk-archive-repo` 现已同步所有正式签名的历史版本。
- **UI 性能优化**: 优化了复杂周视图下的渲染逻辑，减少了不必要的 Recomposition。

### 5. 未来演进建议 (Roadmap)
1. **云同步**: 引入 Firebase 或 WebDAV 实现跨设备数据备份。
2. **无障碍增强**: 进一步完善 Compose 的 `semantics` 语义，提升 TalkBack 使用体验。
3. **自动化 UI 测试**: 补充 `ComposeTestRule` 覆盖关键点击路径。
4. **数据库演进**: 随着功能增加，需设计更精细的 Room 自动迁移 (Migration) 策略。

---

## 技术栈
- **语言**: Kotlin (Coroutines, Flow)
- **界面**: Jetpack Compose, Material 3
- **存储**: Room (SQLite)
- **工具**: WorkManager, AlarmManager
- **构建**: Gradle Kotlin DSL, KSP

## 快速开始
```powershell
# 运行单元测试
.\gradlew.bat testDebugUnitTest

# 构建调试包
.\gradlew.bat assembleDebug

# 执行一键发布流 (含备份、推送、构建、发布、归档)
.\scripts\push-github.ps1 -Message "Your update message"
```

## 许可证
本项目采用 MIT 许可证。
