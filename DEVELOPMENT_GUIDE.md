# DataChart IntelliJ IDEA 插件开发规范

## 项目概述
IntelliJ IDEA 插件项目，支持自定义 `.datachart` 文件类型的图形查看与编辑。

## 核心架构

### 文件类型注册
- `plugin.xml` 中注册 `fileType` 和 `fileEditorProvider` 两个扩展点
- `DataToolsFileType` 定义文件类型，关联语言 `dataChart`
- `DataChartEditorProvider` 通过扩展名 `datachart` 匹配文件

### 编辑器生命周期
- `DataChartEditor` 实现 `FileEditor` 接口 + 继承 `UserDataHolderBase`
- 采用延迟初始化（双重检查锁），`getComponent()` 首次调用时才创建 UI
- 使用 `PropertyChangeSupport` 管理属性变更通知
- `dispose()` 时清理所有 UI 资源

### 修改状态管理
- `isModified()` 返回实际修改状态（不再硬编码 false）
- `setModified(boolean)` 设置修改状态并通过 `PropertyChangeSupport` 通知 IDE
- IDE 通过 `addPropertyChangeListener` 监听 `PROP_MODIFIED` 变化来更新保存按钮等 UI

### 文件有效性
- `isValid()` 应委托给 `file.isValid()`，而非硬编码 true
- 文件被外部删除后编译器能自动感知

## 注意事项 / 踩坑记录

### 1. FileEditorProvider 必须注册
`plugin.xml` 中必须同时注册 `fileType` 和 `fileEditorProvider` 扩展点，否则自定义编辑器不会被 IDE 使用。

### 2. accept() 应检查文件扩展名
`DataChartEditorProvider.accept()` 应通过 `file.getExtension()` 判断，不能使用 `instanceof DataChartVirtualFile`。IDE 打开文件时传递的是来自本地文件系统的普通 `VirtualFile`。

### 3. 禁止使用 testFramework 包
`LightVirtualFile` 等类位于 `com.intellij.testFramework`，仅供测试使用，不得在生产代码中引用。

### 4. UI 组件不应继承 DialogWrapper
非对话框场景不应继承 `DialogWrapper`，应使用 `JPanel` 或 `SimpleToolWindowPanel`。`DialogWrapper` 会创建不必要的对话框 UI 结构。

### 5. 图标加载优化
大量图标的静态初始化会在类加载时触发 I/O，建议使用 `IconLoader.getIcon()` 按需加载或改用懒加载模式。

### 6. 构建版本一致性
`sinceBuild`/`untilBuild` 需与本地 IDE 版本匹配：
- `ideaIC-2022.3` → `sinceBuild=223`
- `ideaIC-2023.2` → `sinceBuild=232`

### 7. Java 源码必须放在 src/main/java
`.java` 文件必须放在 `src/main/java/`，不能放在 `src/main/kotlin/`。Kotlin Gradle 插件只编译 `.kt` 文件，Java 插件只编译 `src/main/java` 下的 `.java` 文件。放错目录会导致 `ClassNotFoundException`（`build/classes` 为空）。

### 8. .form 文件支持的 border 类型有限
`.form` 文件（GUI Designer）不支持 `matte` border 类型，会报 `UnexpectedFormElementException: unknown type: matte`。支持的类型只有：`none` / `etched` / `bevel` / `line` / `titled` / `empty` / `compound`。
- **间距**：用 `.form` 的 `<margin>` 控制
- **分隔线**：在 Java 代码中 `setBorder(new MatteBorder(0,0,1,0, JBColor.border()))`
- **注意**：`.form` 的 margin 与 Java 的 `EmptyBorder` 会叠加成双重内边距，二者只能选其一
- **主题适配**：分隔线颜色用 `JBColor.border()`，自动适配深色/浅色主题

### 9. 扩展名统一管理
所有文件扩展名统一用 `DataToolsFileType.EXTENSION = "datachart"` 常量，避免硬编码导致大小写不一致（曾导致 `getDefaultExtension()` 返回 `"dataChart"` 与注册的 `"datachart"` 不一致，使右键 New 菜单不显示文件类型）。
