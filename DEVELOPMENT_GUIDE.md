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

### 10. Database 插件集成（可选依赖 + 反射）
- Database 插件仅 Ultimate 版提供，集成时必须用 `<depends optional="true" config-file="databasePlugin.xml">com.intellij.database</depends>`
  - `config-file` 属性必须声明（插件校验器要求），指向同目录 `META-INF/databasePlugin.xml`（Database 插件存在时才加载的扩展声明文件）
  - 本插件对 Database 的访问全走反射 + 运行时检查，`databasePlugin.xml` 目前仅含 `<idea-plugin>` 头占位，不迁移任何扩展点；若后续引入直接引用 Database API 的扩展点，必须放进该文件而非 plugin.xml
- 反射访问关键类（避免 ClassNotFoundException）：
  - `com.intellij.database.psi.DbPsiFacade` - 入口
  - `com.intellij.database.psi.DbDataSource` - 数据源
  - `com.intellij.database.util.DasUtil` - 工具方法（getTables/getColumns）
  - `com.intellij.database.model.DasTable` / `DasColumn` - DAS 模型
- 运行时检查：`PluginManagerCore.isPluginEnabled(PluginId.getId("com.intellij.database"))`
- **API 语义注意**：`DasColumn.isNotNull()` 是"非空"，与 SQL `NOT NULL` 一致，但与 Java 语义相反，使用时要取反
- 数据库类型字符串可能带反引号或双引号（`bigint` / `"varchar"`），需要清洗

### 11. KanbanCard 双模式渲染
- 图表模式：title + type + description
- 表格模式（`KanbanCard.forTable(...)`）：header 显示"表名 / * 注释 *"，右上角显示 schema；body 显示列定义（PK 金色方块、索引灰色圆点、列名、类型、注释）
- 通过 `tableInfo != null` 判断当前模式（`isTableMode()`）

### 12. Database 拖拽接入（IntelliJ 自定义 DnD）
- Database 工具窗口拖拽用的是 **IntelliJ 自定义 DnD**（`com.intellij.ide.dnd`，平台核心模块，可直接编译期引用），**不是** Swing DnD（TransferHandler/Transferable）
- 关键接口：
  - `DnDManager.getInstance().registerTarget(DnDTarget, JComponent)` 注册目标
  - `DnDTarget`：`update(DnDEvent)` + `drop(DnDEvent)` + `cleanUpOnLeave()`
  - `DnDEvent.getAttachedObject()` 获取拖拽对象
- 拖拽对象判定：Database 表是 `DbTable`（实现 `DbElement`/`DbNamedElement`），用 `instanceof` 反射判断
- **纯反射访问 Database**（`DbElement`/`DbNamedElement`），避免 ClassNotFoundException；`com.intellij.ide.dnd` 则直接编译期依赖
- 反编译确认的 Database 元信息 API：
  - 主键/索引：`DasTable.getColumnAttrs(DasColumn)` 返回 `Set<Attribute>`，用 `PRIMARY_KEY`/`INDEX` 枚举判断（**不要**用 `col.isPrimary()`，DasColumn 没有该方法）
  - 类型：`getDataType()` 返回 `DataType` 对象，用 `getSpecification()` 取类型名，清洗反引号/引号
  - 可空性：`isNotNull()` 是"非空"，取反
  - 表/字段注释：`getComment()`

### 13. .datachart JSON 图数据模型（com.wd.model）
- `ChartData`：根模型（version + name + tables + relations），对应 .datachart JSON
- `TableCardModel`：画布上的表卡片（id/datasource/schema/tableName/comment + 位置尺寸）
- `ChartRelation`：表连接（from/to 卡片 ID + 字段 + relationType）
- `RelationType`：ONE_TO_ONE/ONE_TO_MANY/MANY_TO_ONE/MANY_TO_MANY/UNKNOWN
- 计划用 fastjson（已在依赖中）序列化/反序列化
- **连线列定位（2026-08-03）**：`fromColumn`/`toColumn` 存列 **index**（字符串数字，删除列后会错位）；新增 `fromColumnName`/`toColumnName` 存**列名**
  - 保存：`BoardPersistence.toChartData` 通过 `resolveColumnName` 从行 index 取列名一并写入
  - 加载：`loadFromChartData` 用 `resolveRowIndex` **优先按列名定位**真实 index，找不到（旧文件无列名）才回退旧 index → 向后兼容旧 .datachart
  - 新保存的文件即使之后增删列，只要列名还在，连线仍准确落在该列
- **AI 使用说明 `_aiGuide`（2026-08-03）**：`.datachart` JSON 头部含 `_aiGuide` 字段，给 AI 读文件时的使用指引
  - `ChartData` 有 `aiGuide` 字段（声明在 `version` 前，序列化时靠前输出）
  - 新建文件由模板 `fileTemplates/DataChart.datachart.ft` 写入默认说明；fastjson 默认不序列化 null，旧文件无该字段不影响
  - **传递链**：`DataChartView` 加载时 `data.getAiGuide()` 暂存到 `DataChartView.aiGuide` 字段，保存时 `serializeToJson` 用 `data.setAiGuide(aiGuide)` 写回——避免因 `KanbanBoard.toChartData()` 重建 ChartData 而丢失
  - 注意：JSON 不支持原生注释，故用 JSON 字段承载说明，不影响 fastjson 解析

### 14. ER 图交互规范（ER-style interaction）

#### 连线绘制
- 默认线宽 `Connection.DEFAULT_STROKE_WIDTH = 2.4f`（从 1.6 加粗，便于辨识）
- 选中连线时 `KanbanBoard.paintComponent` 临时把 `strokeWidth` 改成 2.5f 强调
- **端点形状（2026-08-02 起简化）**：两端都画**开口椭圆**（不再区分 "1"/"多"），长轴竖直、短轴水平
- `RelationType` 字段保留（持久化到 JSON），但目前不再影响渲染
- 形状大小随线宽缩放（`Math.max(8.0, strokeWidth * 4.5)`），描边略细于线本身

#### 水平引出线 + 贝塞尔曲线 + 端点关系类型渲染（需求 19、20、21、23、27）
- **问题（需求 19）**：之前直接从 `sourcePoint` 到 `targetPoint` 走 CubicCurve，当源/终点 Y 不对齐时，曲线进入/离开卡片处是斜的，看起来别扭
- **问题（需求 20）**：需求 19 修复后，连线方向是斜的，整体仍显歪斜
- **问题（需求 21）**：形状有一半被卡片遮住，不够醒目
- **问题（需求 27）**：端点形状需根据 `RelationType` 动态绘制（“1”的一端为直线，“多”的一端为三叉分叉线）：
  - **ONE_TO_ONE**：起点(1) 直线，终点(1) 直线
  - **ONE_TO_MANY**：起点(1) 直线，终点(多) 三叉/鸟爪分叉
  - **MANY_TO_ONE**：起点(多) 三叉/鸟爪分叉，终点(1) 直线
  - **MANY_TO_MANY / UNKNOWN**：起点(多) 三叉/鸟爪分叉，终点(多) 三叉/鸟爪分叉
- **方案**：
  1. 在源/终点各加一段水平直线（"引出线"），中间用 CubicCurve 连接
     - 引出线长度 `leadLen = clamp(|dx|/3, 24, 60)` 画板坐标
     - `dirSign = sign(dx)` 支持右→左连线
  2. 根据 `RelationType` 分别对源/目标端判定是否为“多”端：
     - 若为“多”端：从卡片边缘 `(ex, ey)` 绘制 3 根线分支（向上 `ey - spread`、中 `ey`、下 `ey + spread`），汇聚合并于主连线点
     - 若为“1”端：保持引出线直线连接到卡片边缘，不画三叉
- **常量**：`Connection.LEAD_MIN = 24.0`、`LEAD_MAX = 60.0`

#### 起点背景色同步整条连线（需求 1）
- `Connection.resolveLineColor()` 优先用 `source.getHighlightedColorForRow(sourceRow)`，没有再用 palette 颜色
- 这样当起点行被高亮（用户选中 / 关联列 / 连线占用），整条线统一为该色

### 28. 同步表结构增量对比提示（需求 28）
- **新增列**：
  - 背景色设为浅绿色（深色主题 `#2E4A32`，浅色主题 `#D4EDDA`）
  - 触发 300ms 从左侧向右平滑划入显示背景的动画 (`addedSlideProgress`)
  - 该高亮为一次性纯内存状态，关闭编辑器/文件重新打开后恢复正常背景
- **删除列**：
  - 在卡片底部保留展示被删除的列，背景设为浅红色（深色主题 `#4A2E2E`，浅色主题 `#F8D7DA`）
  - 绘制贯穿一整行的删除线（中划线）
  - `deletedColumns` 为内存临时列表，不参与 `.datachart` JSON 序列化，重新打开文件后被删除列即消失

#### 关联列高亮（需求 2）
- `KanbanBoard.activeHighlightCard / activeHighlightRow`：当前用户左键选中的列
- `KanbanBoard.refreshRelatedRows()` 在每次重绘前重新计算 `relatedRowKeys`（cardId#row 形式）
- 关联列高亮色 `RELATED_ROW_COLOR = #FD9933`，与用户选中色 `#FE9933` 略区分
- 切换到其他列时，`toggleRowSelection` 会清空旧选中 → 自动恢复

#### 行高亮临时通道（让 Connection 拿到激活色）
- `KanbanCard.activeRowColors`：每帧重绘前由 `KanbanBoard` 写入
- `getHighlightedColorForRow()` 优先级：用户选中 > 临时激活色 > 连线占用色
- 用途：让连线的 `source.getHighlightedColorForRow()` 能感知"激活列"颜色

#### 行高亮背景下的文字色自适应（需求 22）
- **问题**：深色主题下，行高亮背景色是**亮色**（连线占用的浅蓝/粉色 `#A8C5E7`，搜索命中的淡黄 `#FFF3B0`、深黄 `#FFD24A`，用户选中的橙色 `#FE9933`），但文字仍用主题默认色（深色主题 = 白色），导致**白字浅背景看不清**
- **方案**：`KanbanCard.drawTableCard` 在行循环里用 `isLightColor(highlightColor)` 判断背景亮度：
  - 亮背景（YIQ ≥ 128）→ 文字用深色：`#222222`（列名）/ `#0E5A8E`（类型）/ `#666666`（冒号）/ `#555555`（注释）
  - 暗背景/无高亮 → 维持主题默认色（深色主题白字，浅色主题黑字）
- **新增 API**：`KanbanCard.isLightColor(Color)`（静态，YIQ 公式，与 `BoardExportUtil.isDarkTheme` 反义）
- **影响范围**：仅表格卡片 `drawTableCard` 的列名/类型/冒号/注释；图标颜色不变；表头/卡片标题/边框不受影响

#### 复制到剪贴板（需求 3、4）
- 使用 `java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()` + `StringSelection`
- 不使用 IntelliJ 的 `CopyPasteManager`，因为本组件不依赖 IDE 编辑器上下文
- 表头右键统一弹"复制表名 / 复制注释 / 同步表结构 / 删除表"四个菜单项（不再分左右半）
  - "同步表结构"调 `TableMetadataService.getFetcher().fetchTableInfo` 重新拉取，成功后 `KanbanCard.setTableInfo` 替换 + 重绘 + 通知内容变更
  - "删除表"回调 `KanbanBoard.deleteCard(card)`：效果等同 Command+Del——有连线先弹二次确认，无连线直接删除并清理关联连线/搜索项
  - "删除表"文字颜色用红色 `DELETE_FOREGROUND`（JBColor 双态：浅色深红 `#C62828` / 深色亮红 `#FF6B6B`），`MenuItem.selectionForeground` 也设红，hover 保持红色，与其他蓝色菜单项区分（危险操作视觉标识）
- 列行右键分左右半：
  - 左半（列名+类型，hit test 用 `KanbanCard.getColumnNameRightX()`）→ 弹"复制列名/复制注释"菜单
  - 右半（注释区域）→ 走连线模式
- **菜单 hover 颜色修复**：Swing L&F 在 IntelliJ 主题下默认是白字 hover → 看不见
  - 解决：所有菜单项 `putClientProperty("MenuItem.selectionForeground", MENU_HOVER_FOREGROUND)` 固定为蓝色 `#2470B0`（与列类型文字色一致）
  - 背景保持 `JBColor.background()`，两种主题下 hover 都清晰
  - 参考 `KanbanBoard.buildStyledPopupMenu()` / `buildStyledMenuItem()` / 常量 `MENU_HOVER_FOREGROUND`

#### 拖拽磁吸 + 对齐辅助线（需求 6）
- 阈值常量 `SNAP_THRESHOLD = 8px` / `ALIGN_THRESHOLD = 10px`（画板坐标）
- 候选对齐点：其它卡片的左/中/右（X 轴）、上/中/下（Y 轴）
- 距离 < SNAP 时直接吸附；< ALIGN 时显示虚线辅助线（`ALIGN_GUIDE_COLOR_LIGHT/DARK`）
- 辅助线在 `paintComponent` 末尾绘制，独立于 `transform`，覆盖在所有元素之上
- 屏幕坐标转换：`screenX = bestSnapX * zoomFactor + transform.getTranslateX()`

#### 连线右键菜单（需求 7）
- 子菜单 "关系类型"：一对一 / 一对多 / 多对一 / 多对多，使用 `JCheckBoxMenuItem` 标记当前选中
- 选中后 `Connection.setRelationType()` → `repaint()`
- 关系类型通过 `ChartRelation.relationType` 持久化到 JSON

### 15. 主题适配
- 所有新增颜色都按 `isDarkTheme()` 区分深色 / 浅色变体：
  - `ALIGN_GUIDE_COLOR_LIGHT = #FE9933`（浅色）
  - `ALIGN_GUIDE_COLOR_DARK = #FFB266`（深色）
- 关联列高亮 `RELATED_ROW_COLOR = #FD9933` 在两套主题下都能看清，不需要切换
- 复制菜单的 `setEnabled` 处理空注释情况，避免用户点了无效果

### 16. 视图工具栏（Focus / FullScreen / Zoom 显示）

#### Focus 按钮（focusButton）
- 位置：工具栏第 2 列（紧跟搜索框），图标用 `PluginIcons.reset`（"复位/居中"视觉语义）
- 点击 → `KanbanBoard.focusView()`：**保留当前 zoom**，平移 transform 让内容对齐到视口
- 对齐策略（2026-08-04 自适应）：
  - **能完整展示**（内容宽 ≤ 视口宽 且 内容高 ≤ 视口高，含 `FOCUS_PADDING(20)` 基础留白）：**上下左右居中**
  - **展示不完**（宽或高超限）：**左对齐 + 上下居中**，内容 `minX` 落到 `FOCUS_PADDING_LEFT(80)`（更大留白），保证最左卡片完整露出不贴边；右侧/下方超出由用户滚动查看
- 与 `resetView()` 的区别：reset 是 zoom=100% + transform 清零（完全重置）；focus 是"画板内容回到视口合适位置"，zoom 不变
- 无卡片时不改变视图

#### OneOne 按钮（oneOneButton，缩放 1:1）
- 位置：工具栏，文案 "1:1"，图标 `PluginIcons.oneOne`
- 点击 → `KanbanBoard.setZoomTo1()` + `focusView()`：
  - `setZoomTo1()`：调 `viewport.setZoomFactor(viewCenterX, viewCenterY)`，直接设缩放=1.0（复用 `zoom()`，scaleFactor = `1/当前zoom`，以视口中心为锚点）
  - `focusView()`：自适应对齐（能展示完居中，否则最左对齐）
- `BoardViewport.setZoomFactor`：直接设缩放因子，保持屏幕中心锚定内容不变

#### FullScreen 按钮（fullScreamButton）
- 位置：工具栏第 3 列（在 Focus 之后）
- 状态切换（`toggleFullScreen()`）：
  - **进入全屏**：隐藏 `searchTextField / focusButton / exportPDFButton / exportPictureButton / zoomPercentLabel`，只保留 fullScreamButton
  - 按钮 icon 从 `PluginIcons.fullScream` 换成 `PluginIcons.exit_fullScream`，文字 "FullScream" → "ExitFullScream"，tooltip 翻转
  - **退出全屏**：恢复所有被隐藏组件，按钮 icon/text/tooltip 恢复初始
- 用 `hiddenOnFullScreen: List<Component>` 记录被隐藏的组件，退出时批量恢复
- 调用 `rootPanel.revalidate()` + `repaint()` 触发重排

#### Zoom 百分比显示
- `zoomPercentLabel` 始终从 `KanbanBoard.getZoomFactor()` 实时读取，`(int) Math.round(zoomFactor * 100) + "%"`
- 引入 `KanbanBoard.viewChangeListener: Runnable`，在以下时机触发：
  - `zoom()` 缩放
  - `panByWheel()` 滚轮平移
  - `mouseDragged` 画板拖拽平移
  - `resetView()` 完全复位
  - `focusView()` 聚焦
  - `scrollToFocusResult()` 搜索结果滚动（已存在）
- DataChartView 注册 `viewChangeListener → updateSearchStatusLabel`，每次触发都重算 zoom 文本

#### 设计原则
- **focusView 不改 zoom**：只调 transform，缩放倍率是用户工作状态，不应该被聚焦动作破坏
- **fullscreen 用 setVisible(false)**：比 CardLayout / 换 rootPanel 简单，不影响数据模型
- **zoom 实时刷新**：viewChangeListener 统一驱动，避免在每个 zoom 入口散落更新调用

### 17. 搜索功能（DataChartView + KanbanBoard）
- **入口**：`DataChartView` 顶部工具栏的 `SearchTextField`，回车触发 `doSearch()` → `kanbanBoard.search(keyword)`
- **搜索范围**：遍历 `cards`，对每张卡片检查 表名、表注释（= `description`）、列名、列注释，**不区分大小写**子串匹配
- **结果表示**：`KanbanBoard.SearchResult(cardId, rowIndex)` 列表；`rowIndex = -1` 表示表头命中，否则是列索引
- **高亮颜色**：
  - 普通命中行 `SEARCH_HIGHLIGHT_COLOR = #FFF3B0`（淡黄，柔和不刺眼）
  - 焦点行（键盘上下键导航到的）`SEARCH_HIGHLIGHT_FOCUS_COLOR = #FFD24A`（深黄，显著）
  - 焦点卡片（表头命中时）把卡片边框换成深黄，整张卡片作为视觉锚点
- **行高亮优先级**（drawTableCard 中）：用户选中（橙）> 搜索焦点行（深黄）> 搜索命中行（淡黄）> 连线占用（线色）
- **上下键导航**：`DataChartView.setupSearchField` 给 `searchTextField.getTextEditor()` 加 `KeyListener`，`VK_DOWN` → `focusNextSearchResult()`，`VK_UP` → `focusPrevSearchResult()`，循环切换
- **滚动到焦点**：`scrollToFocusResult()` 根据 `transform` 反推 translate，让焦点行中心落到视口中心（屏幕坐标）
- **状态显示**：复用 `zoomPercentLabel` 区域右侧显示 "100% | 3/12"（当前/总数），无结果时恢复纯百分比
- **ESC 键**：清空搜索文本 + 调用 `kanbanBoard.clearSearch()`，重置状态显示
- **删除卡片同步**：`KanbanBoard.removeSearchResultsForCard(cardId)` 同步清理搜索结果中该卡的所有项，并修正焦点下标
- **加载文件清空搜索**：`DataChartView.loadFromJson` 加载新文件后清空搜索框和搜索结果，避免旧结果干扰
- **行高常量**：`KanbanCard.ROW_HEIGHT = 18` 提为 public 常量，所有 `getRowRight/getRowLeft/getRowTop/getRowIndexAt/drawTableCard` 统一引用，避免硬编码散落
- **设计原则**：搜索不影响用户选中的橙色高亮，不影响连线占用色，搜索状态独立成一套"黄色系"高亮

### 18. 导出 PDF / 图片（DataChartView + KanbanBoard）
- **入口**：工具栏 `exportPDFButton` / `exportPictureButton`，点击弹 `JFileChooser`（`showSaveDialog`）
- **默认文件名**：`{baseFileName}_{yyyyMMdd_HHmmss}.{ext}`
  - `baseFileName` 来自 `DataChartEditor.resolveBaseFileName()`（`file.getNameWithoutExtension()`），注入到 `DataChartView.setBaseFileName(name)`
  - 未注入时 fallback 为 "datachart"
- **核心 API**（KanbanBoard）：
  - `calculateTotalBounds()`：所有 cards 合并包围盒 + 4px padding（**2026-08-01 从 40 减小到 4**，原 40 padding 让导出图四周留白过大；4 像素够容纳阴影偏移 +2 且不裁切）；无 cards 时返回画板大小
  - `paintForExport(Graphics2D, Rectangle2D, boolean dark)`：导出共用的绘制方法，画背景 + 网格 + 连线 + 卡片；不画屏幕坐标的对齐辅助线 / tooltip / 鼠标连线预览
  - `exportToPdf(File)`：用 iText 5.5.13 + iText Asian，中文用 `STSong-Light (UniGB-UCS2-H)`，回退到 Windows `simsun.ttc`；PDF 页面大小 = exportArea 的 width/height
  - `exportToImage(File, String format, double scale)`：JPG 用 `JPEGImageWriteParam` 高质量压缩 0.95f；PNG 用 `TYPE_INT_ARGB`；超内存自动降级 scale（参考 DataHelper 内存管理）
  - **对称 padding 围绕内容中心**：`calculateTotalBounds` 用 `(centerX - halfW, centerY - halfH, 2*halfW, 2*halfH)`，让 exportArea 中心 = 卡片合并中心，padding 四面对称（各 4 像素），不再受 cards 位置不对称影响
- **通知**：成功后用 `Notifications.Bus.notify(Notification("DataChart", ...))` 弹系统通知，失败给 Error 通知
- **空画板**：cards.isEmpty() 时直接给 "画板为空，无内容可导出" 通知，不弹文件框
- **iText 字体映射器**：`DefaultFontMapper` 的 `awtToPdf` 自定义返回 `BaseFont`（中文 STSong → simsun → 默认）
- **画板坐标变换**：`paintForExport` 中 `g2.translate(-minX, -minY)` + `g2.scale(s, s)`，transform 链 = `scale ∘ translate`，最终 `T(P) = (P - (minX, minY)) * s`
- **导出背景色**：2026-08-01 修复"两种背景色"问题。原 `backgroundColor = Gray._240 (#F0F0F0)` 与卡片 `BG_LIGHT (#FFFFFF)` 不一致。导出时硬编码用 `KanbanCard.getCardBackgroundColor(dark)`（与卡片同色），让画板 = 卡片，导出图只有一个背景色。IDE 内画板仍保持 `Gray._240`，不影响交互体验
- **依赖**（`build.gradle.kts` 已配）：`com.itextpdf:itextpdf:5.5.13` + `com.itextpdf:itext-asian:5.2.0` + `com.twelvemonkeys.imageio:*:3.10.1`
- **设计原则**：
  - 复用 `paintForExport` 共享 PDF / Image 绘制逻辑，避免两份 paintComponent 走偏
  - 默认文件名用 `getNameWithoutExtension()` 而非 `getName()`，避免 `.datachart` 出现在 `xxx.datachart_20260801_xxx.pdf` 这种叠加后缀
  - 后缀兼容：用户没写 .pdf / .jpg 时自动补，避免保存成无后缀文件
  - **导出行为 vs IDE 行为分叉**：导出时的视觉策略（背景色、字体等）可以与 IDE 内不同，硬编码导出相关参数比修改全局字段更安全

#### 导出图片已知问题修复（2026-08-01）
- **清晰度问题**：原默认 scale=1.0 导出 11pt 字体渲染到 11px 像素，字小且模糊
  - **修复**：默认 scale 改为 2.0（2x 高 DPI），字号 / stroke 自动放大；`KEY_FRACTIONALMETRICS_ON` 启用子像素精度
  - `paintForExport` 增加 `scale` 参数：先 `translate(-minX, -minY)` 再 `scale(s, s)`，transform 链 = `scale ∘ translate`，最终 `T(P) = (P - (minX, minY)) * s`
- **黑色背景问题**（填 rect 起点错误）：
  - **根因**：g2d transform 链 = `scale ∘ translate(-minX, -minY)`，所以 `T((0, 0)) = (-minX*s, -minY*s)` 落在 BufferedImage 外，`fillRect(0, 0, w, h)` 不会覆盖完整 BufferedImage
  - **修复**：`fillRect((int) minX, (int) minY, w, h)` 用 exportArea 起点（=卡片合并 - 40 padding）作为用户坐标起点，transform 后正好落在设备 (0, 0)
- **居中问题**（calculateTotalBounds padding 不对称）：
  - **根因**：原 `Rectangle(minX-40, minY-40, w+80, h+80)` 上下对称但**左右不对称**（如果 minX 不在 0）
  - **修复**：以"卡片合并中心"为锚点，加对称 padding：`center ± (extent/2 + padding)`
  - 实际画板坐标值与旧 `minX-40` 相同，但语义清晰：四面对称 padding
- **API 重载**：`paintForExport(g2d, area, dark)` 重载为 `paintForExport(g2d, area, dark, scale, deviceW, deviceH)`，scale=1.0 保持原行为
- **PDF 不传 scale**：PDF 是矢量，scale 始终 1.0，由 `document` 的 `Rectangle(width, height)` 决定尺寸
- **导出时先清屏再 transform**（2026-08-01 重构）：fillRect/clearRect 双保险用设备坐标 (0, 0) 完整覆盖整个目标区域，再 apply transform。避免之前"transform 链 + fillRect"数学上对但实际有黑色透出的问题
- **Debug log**：`exportToImage` / `exportToPdf` 用 `LOG.warn("[DataChart export] ...")` 输出 exportArea 实际值 + cards bounds + scale，方便用户重启 IDE 后看 log 诊断问题

### 19. 工具类抽取（2026-08-01 重构 KanbanBoard）
为降低 `KanbanBoard`（原 ~2368 行）的复杂度，把职责单一的方法抽取为独立工具类：

#### BoardExportUtil（导出工具类，`com.wd.ui.BoardExportUtil`）
- **静态方法**（无需实例化，私有构造）：
  - `calculateTotalBounds(List<KanbanCard>)`：包围盒计算（原来依赖 `cards`，抽成静态后传 `cards` 即可）
  - `isDarkTheme(Color background)`：YIQ 亮度判断（原为 KanbanBoard 私有方法）
  - `exportToPdf(KanbanBoard, File)` / `exportToImage(KanbanBoard, File, format, scale)`：文件 I/O + 尺寸计算 + 内存管理 + 图像编码
- **绘制入口留在 KanbanBoard**：`paintForExport(...)` 因依赖大量内部状态（`computeLinkedRows` / `refreshRelatedRows` / `activeHighlightCard` / `relatedRowKeys` / `parseRelatedKeyById` / `drawGrid`），改为**包级可见**（去掉 `public`），由工具类调用。
- **调用方式**：`BoardExportUtil.exportToPdf(kanbanBoard, file)`，不再是 `kanbanBoard.exportToPdf(file)`。
- **抽取原则**：把"纯 I/O + 计算"（与看板状态无关的部分）尽量静态化到工具类；把"强耦合看板状态"的绘制逻辑留在原类但暴露包级入口，避免为了抽取而破坏封装。

#### NotificationUtil（提醒工具类，`com.wd.ui.NotificationUtil`）
- **静态方法**（私有构造）：
  - `info(title, content)` / `error(title, content)`：封装 `Notifications.Bus`（从 DataChartView 抽取，通知组 ID 统一 `"DataChart"`）
  - `confirmYesNo(project, title, message, yesText, noText)`：封装 `Messages.showYesNoDialog`（从 KanbanBoard 的删除确认抽取）
- **设计原则**：所有提醒入口统一走工具类，避免 `Notification`/`Messages` 调用散落在各处；后续要改通知样式只需改工具类一处。

#### 注意事项
- `KanbanBoard.isDarkTheme()` 仍保留为私有，但内部委托给 `BoardExportUtil.isDarkTheme(getBackground())`，避免亮度判断逻辑重复。
- `calculateTotalBounds()` 在 KanbanBoard 保留薄壳（委托给 util），兼容内部 `focusView` 等调用，避免破坏现有逻辑。

### 20. KanbanBoard 二次拆分（2026-08-01，文件过大 → 按职责拆分）
第 19 节已抽出导出/提醒工具类，但 `KanbanBoard` 仍约 2150 行。再次按**逻辑内聚 + 低耦合**拆出 5 个类（KanbanBoard 降到约 1400 行）：

#### BoardViewport（视口几何，`com.wd.ui.BoardViewport`，实例类）
- **持有**：`transform`（AffineTransform）+ `zoomFactor`（double）
- **方法**：`reset()` / `zoom(p, scale)` / `panByWheel(e, rot)` / `pan(dx, dy)` / `focusOn(cards, w, h)` / `centerOn(bx, by, w, h)` / `transformPoint(p)` / `getInverse()` / `getTransform()` / `getZoomFactor()`
- **坐标约定**：`screen = board * zoomFactor + translate`；`transformPoint` 用逆矩阵屏幕→画板
- **边界**：`MIN_ZOOM=0.1` / `MAX_ZOOM=10`；`focusOn`/`centerOn` 保留 zoom 只改 translate
- **KanbanBoard 职责**：只做 `viewport.xxx()` + `notifyViewChanged()` + `repaint()` 的编排

#### BoardSnapHelper（磁吸对齐，`com.wd.ui.BoardSnapHelper`，静态工具）
- **纯计算**：`compute(moving, all, zoomFactor, translateX, translateY, panelW, panelH)` → `SnapResult`
- **直接改 `moving.getBounds()`**（吸附成功时）；返回屏幕坐标辅助线（`guideVX` / `guideHY`，NaN 表示无）
- **阈值**：`SNAP_THRESHOLD=8`（吸附）/ `ALIGN_THRESHOLD=10`（只显示辅助线）
- **吸附规则**：当前卡片 左/中/右 ↔ 其它卡片 左/中/右；上/中/下 ↔ 上/中/下
- **SnapResult**：`hasVertical()` / `hasHorizontal()`；KanbanBoard 据此构造 `activeSnapGuideV/H`（Line2D）再绘制

#### BoardSearchModel（搜索模型，`com.wd.ui.BoardSearchModel`，实例类）
- **持有**：`searchResults` + `searchFocusIndex`
- **方法**：`search(cards, keyword)` / `clearSearchState(cards)` / `focusNext()` / `focusPrev()` / `applyFocus(cards, FindCard)` / `scrollToFocus(cards, FindCard, viewport, w, h)` / `removeResultsForCard(cardId)` / `getResultCount()` / `getFocusIndex()`
- **`SearchResult(cardId, rowIndex)`**：`rowIndex=-1` 表头命中；`rowIndex>=0` 列索引
- **解耦**：通过函数式接口 `FindCard { KanbanCard find(String id) }` 回调查找卡片，不直接持有 KanbanBoard
- **KanbanBoard 桥接**：`search`/`clearSearch`/`focusNextSearchResult`/`focusPrevSearchResult` 调用后 `applySearchFocus()` + `repaint()`；`scrollToFocusResult` 用 `getVisibleRect()`

#### BoardContextMenu（右键菜单，`com.wd.ui.BoardContextMenu`，静态工具）
- **方法**：`buildConnectionMenu(conn, onRepaint, onNotifyChanged, onRemove)` / `buildHeaderMenu(info, onSyncStructure, onDeleteTable)` / `buildColumnMenu(col)` / `copyToClipboard(text)`
- **回调注入**：连线菜单通过 `Runnable onRepaint / onNotifyChanged / onRemove` 解耦，不直接调用 KanbanBoard
- **主题适配**：`MENU_HOVER_FOREGROUND=#2470B0`（hover 蓝字）+ `patchMenuUiDefaults()`（`UIDefaults` 全局覆盖，`menuUiPatched` 标志防重复）
- **KanbanBoard 桥接**：`showConnectionContextMenu` / `showHeaderContextMenu` / `showColumnContextMenu` 组装回调后调用工具类

#### BoardPersistence（持久化，`com.wd.ui.BoardPersistence`，静态工具）
- **方法**：`toChartData(cards, connections)` / `loadFromChartData(data, FindCard, AddConnection)` / `resolveTableInfo(model, project)` / `parseRowIndex(String)`
- **解耦**：`FindCard { KanbanCard find(String) }` + `AddConnection { void add(src, srcRow, tgt, tgtRow, type) }` 回调
- **要点**：`resolveTableInfo` 优先用 JSON 保存的 columns（离线可用），否则重新查元信息；`loadFromChartData` 只恢复连线，卡片由调用方先行构建

#### 拆分原则（重要）
- **纯计算 / 无副作用 → 静态工具类**：SnapHelper、ContextMenu、Persistence、ExportUtil、NotificationUtil
- **有状态 → 实例类**：Viewport（transform/zoom）、SearchModel（results/index）
- **强耦合渲染状态留在 KanbanBoard**：`paintComponent` / `paintForExport` / `drawGrid` / `drawCards` / 关联列高亮（`activeHighlightCard` / `relatedRowKeys` / `refreshRelatedRows` / `parseRelatedKeyById`）仍留在原类
- **解耦用函数式接口回调**：FindCard / AddConnection / Runnable，模块之间不互相 import，只依赖 KanbanCard / Connection 等模型类
- **KanbanBoard 变成"编排层"**：持有 viewport/searchModel，事件→调用模块方法→`notifyViewChanged()` + `repaint()`

### 21. 表格卡片宽度自适应（2026-08-01 修复"导出图片右边没显示完"）
根因：表格卡片宽度固定 280px，sys_job（19 字段，含 `invoke_target : varchar(500) /* 调用目标字符串 */`）字段名+类型+注释总长超过 280，draw 时写到 `bounds` 外面被 BufferedImage 裁剪。

#### 修复
- **`KanbanCard.computeRequiredWidth(TableInfo)`**（静态方法）：
  - 离屏 1×1 BufferedImage 拿到真实 FontMetrics（不靠字符数估算）
  - 遍历所有列 + header 行：`padding + 图标宽 + 列名 + " : " + 类型 + 6 + "/* 注释 */" + padding`
  - 与 `MIN_TABLE_CARD_WIDTH=280` 取最大
- **`KanbanCard.forTableAutoWidth(id, info, x, y, explicitWidth, h)`**（新静态工厂）：
  - `width = max(computeRequiredWidth, explicitWidth)`，兼容旧持久化尺寸
- **`KanbanBoard.addTableCard`** 调用 `KanbanCard.computeRequiredWidth(info)` 算实际宽度
- **`KanbanBoard.loadFromChartData`** 用 `forTableAutoWidth` 代替 `forTable`，即使旧 JSON 里的 width=280 偏小也能自动加宽

#### 注意事项
- 必须在 `BufferedImage` 拿 FontMetrics（创建 Graphics2D 立即 dispose），不要在 paint 阶段才计算
- 中英文混排：FontMetrics.stringWidth 真实测量比 `char.length() * 7` 估算精确
- 高 DPI 场景下需要留意缩放（Mac Retina），但 FontMetrics 已感知系统 DPI
- 用户拖动手动改的 width > computed 会被保留（适配"用户故意加宽"场景）

### 22. 卡片宽度改回固定值 + 注释截断（2026-08-01 用户反馈改回）

#### 需求变更
- 用户反馈："每个表的宽度应该是固定的，一样的，注释太长截取一部分，后面用省略号"
- 之前的 21 节按需加宽让不同表宽度不一致（sys_job 460, gen_field_config 可能 280），不满足"统一"要求

#### 方案
- 改回固定宽度：`KanbanBoard.TABLE_CARD_WIDTH = 280`（所有表都用这个）
- 注释过长时由 `KanbanCard.truncateByWidth(text, maxWidth, fm)` 截断 + 省略号（已存在的工具方法）
- `addTableCard` 改回用 `TABLE_CARD_WIDTH`
- `loadFromChartData` 改回用 `forTable`（保留用户保存的 width，不强制加宽）
- 保留 `computeRequiredWidth` / `forTableAutoWidth` 工具方法供未来使用

#### 截断机制（drawTableCard 注释）
```java
int maxCmtW = (int) (bounds.getX() + bounds.getWidth() - padding - cmtX);
if (maxCmtW > 10) {
    g2d.drawString(truncateByWidth(commentText, maxCmtW, italicFm), cmtX, textY);
}
```
- `truncateByWidth` 按字符增量检查 `fm.stringWidth(sb + "...") > maxWidth` 触发截断
- 注释前缀 `/* ` 和后缀 ` */` 一起参与截断，截断后省略号

#### 已知边界
- 列名 / 类型未截断（如 `invoke_target : varchar(500)` 长度 ~155px，远小于 280，无问题）
- 极端长列名（>30 字符）会溢出到卡外，暂未处理（实际场景少见）

### 23. 导出边距 20px（2026-08-01）
#### 需求
- "图片，pdf 都给个 20px 的边距"

#### 实现
- `BoardExportUtil.EXPORT_MARGIN = 20`（画板坐标像素）
- `calculateTotalBounds` 的对称 padding 从 `4` 改为 `EXPORT_MARGIN`
- 图片 + PDF 共用 `calculateTotalBounds`，因此边距对两者同时生效
- `paintForExport` 的 `fillRect(0,0,deviceW,deviceH)` 用设备坐标填充整个图像（含 20px 边距区域），边距显示为背景色，正确

#### 说明
- 20px > 卡片阴影偏移（约 2px），阴影不会被裁
- 对称 padding 保证四边留白均匀（内容居中）

### 24. 修复"导出图片左边留白太多"（2026-08-01）
#### 根因
- `addTableCard` 无 dropPoint 时调 `addCard(card)`，`addCard` 把 table card 位置覆盖为 (50, 50)，宽度变成 `DEFAULT_CARD_WIDTH=200`（而非 280）
- cards 起点固定 (50, 50) → `calculateTotalBounds` 算的 minX=50，exportArea.x=30
- 画板 x=0~50 范围（50px）被画到设备 x=-30~20，但 x<0 部分被 clip → 视觉上"左边留白 20px 看着很多"

#### 修复
- **`addCard` 首张起点从 (50, 50) 改为 (0, 0)**，保留 card 原宽度/高度（不强制 DEFAULT_CARD_WIDTH）
- **`addTableCard` 不再调 `addCard`**，自己管理布局（首张 (0, 0)，后续自动平铺）
- **`drawGrid` 增加 rangeOverride 参数**，导出时传入 exportArea 范围，只在 exportArea 内画网格

#### 设计取舍
- 不归一化 `loadFromChartData` 的 cards 位置（保留用户拖动意图）
- 用户重新拖入 cards 即可生效（新建场景从 (0, 0) 开始）
- 已存在的 .datachart 文件需要用户重新调整位置

### 25. 二次修复"导出图片错位"（2026-08-01 继续迭代）
#### 用户反馈
- 24 节修复后仍然不对，截图显示：左边大块空白 + 右边 sys_job 被裁
- "还是不对，仔细检查，修复"

#### 根因
- **旧 .datachart 文件**保存的 cards 位置/尺寸是 chart 模式默认：
  - x=50, y=50, w=200, h=130（被旧 `addCard` 强制写入）
- `loadFromChartData` 用 `model.getWidth()` (=200) 还原 card → table card 渲染宽度=200
- 多个 cards 实际宽度比预期 280 窄 → exportArea 算得偏小 → 右边内容溢出被裁

#### 修复
- **`loadFromChartData` 强制归一化**：
  - table card 宽度 = `TABLE_CARD_WIDTH` (280)
  - 高度按字段数计算（base + rows * 18，上限 400）
  - 位置按"4 张/行"自动平铺，起点 (0, 0)
- **`addTableCard` 无 dropPoint 分支**重写：用"最右卡片"逻辑判断是否换行
  - 找 cards 中 rightmost，按"同 Y 行"分组
  - 同行 < 4 张：在 rightmost 右边 +30 spacing 放
  - 同行 = 4 张：换行到 (0, rowBottom + 30)
- **`addCard` 换行时 `nextX = 0`** 而非 50

#### 编译
- `./gradlew compileJava --rerun-tasks` BUILD SUCCESSFUL
- 0 lint 错误
- 笔记：DEVELOPMENT_GUIDE.md 第 25 节

### 26. loadFromChartData 改回保留位置（2026-08-01 用户反馈"位置变了"）
#### 用户反馈
- "关闭重新打开，就变成...这样了，为什么位置变了，应该保留之前的位置"
- 25 节强制归一化位置到 (0, 0)+4 张/行平铺 → 关闭重开后用户拖动过的位置丢失

#### 修复
- **`loadFromChartData` 改回保留位置**：
  - 保留 `model.getX()`, `model.getY()`（用户拖动过的位置）
  - **只修正尺寸**：width = TABLE_CARD_WIDTH (280)，height 按字段数计算
- 新 cards（`addTableCard`）起点 (0, 0)，**老 cards 保留位置**

#### 设计原则（重要）
- **位置属于用户意图**：必须保留，不能因修复其他问题破坏
- **尺寸属于代码约束**：可以强制统一为合理值（TABLE_CARD_WIDTH）
- **新数据用代码规则，老数据保留用户位置**——两者并存
- exportArea 用 card.getBounds() 算，用户位置不变 → 导出图也跟着用户位置走

### 27. 性能/稳定性审查与修复（2026-08-03）

对全项目做了 线程安全/内存泄漏/异步回调/执行性能/响应速度/运行效率/稳定性 七维审查，已修复项 + 待优化建议：

#### 已修复
- **`KanbanCard.getColumnNameRightX` 每次新建离屏 BufferedImage** → 复用懒加载 `getColumnFontMetricsCache()`（与 `getHeaderFontMetricsCache` 同模式），消除 hit-test 热路径反复创建/释放 Graphics 资源
- **`DatabaseTableMetadataFetcher` 反射 Method 查找无缓存** → 新增 `METHOD_CACHE`（ConcurrentHashMap，key=`类名#方法名`）+ `findMethodByNameUncached`，`findMethodByName` 先查缓存
- **`DataChartView.loadFromJson` 解析失败静默丢数据** → catch 里记录日志 + `NotificationUtil.error` 提示用户

#### 待优化项（2026-08-03 已按报告顺序修复）
- **`DataChartView extends DialogWrapper` 用错基类**：经评估，`.form` 字段绑定依赖 `DialogWrapper.init()` 运行时加载，且构建未配置 GUI Designer 编译插件，彻底改继承会破坏字段注入 → **保留继承**，但补真实泄漏修复：`KanbanBoard` 新增 `dispose()` 注销 DnD target（`dropHandler.unregisterFrom`），由 `DataChartView.dispose()` 调用，避免编辑器关闭后 DnD 目标仍指向已释放组件
- **`PluginIcons` 60+ 图标全部加载** → 删除 50+ 从未引用的图标字段，只保留实际使用（表格列图标 + 工具栏按钮）；另注 `IconLoader.getIcon()` 本身是延迟加载（首次 paint 才解析 SVG），类加载不触发立即 I/O
- **`KanbanBoard.paintComponent` 全量重绘** → `computeLinkedRows()` 结果缓存到 `linkedRowsCache` 字段（改非 final，初始 null），仅在 `addConnection`/`removeConnection`/`deleteCard`/`loadFromChartData.clear` 时置 null 失效；`refreshRelatedRows` 依赖交互高亮，保持每次重算
- **`DatabaseTableMetadataFetcher.findDataSource/findTable` 全量遍历** → 数据源对象运行时可变，引入缓存有失效风险，**不做数据缓存**；改为给 `findMethod`（`getMethod` 精确签名）也加 METHOD_CACHE（key 前缀 `F#.` 区分），消除热路径反射查找

#### 遗留说明
- `DataChartView extends DialogWrapper` 架构不纯问题仍在：彻底改继承需引入 IntelliJ GUI Designer 表单编译插件（如 `org.jetbrains.intellij` form 编译）或手写 UI，且对话框从未 `show()`，实际泄漏风险低，建议后续专项处理

### 29. 关系类型默认值 + 勾选框自绘（2026-08-03）

#### 连线默认关系类型改为"一对一"
- **背景**：新画连线默认 `RelationType.UNKNOWN`（渲染时两端三叉 = 视觉等同多对多），用户反馈希望默认一对一。
- **改动点**（`UNKNOWN` → `ONE_TO_ONE`）：
  - `Connection.relationType` 字段默认值、无参构造、6 参构造的 null fallback、`setRelationType` 的 null fallback
  - `KanbanBoard.addConnection(src,row,tgt,row)` 无参重载传入的默认类型
  - `BoardPersistence.loadFromChartData`：加载时若 `getRelationType()` 为 null 或 `UNKNOWN`，统一回退 `ONE_TO_ONE`（旧文件 / 缺失字段向后兼容）
- **说明**：`RelationType.UNKNOWN` 在 `Connection.paint` 中被当作"多对多"渲染（两端三叉），故新默认与加载回退都指向一对一，避免旧文件连线意外变回多对多视觉。

#### 勾选框浅色主题对比度优化（FlatCheckBoxMenuItem）
- **问题**：关系类型子菜单用 `JCheckBoxMenuItem`，IntelliJ 浅色主题下 Swing L&F 默认勾选框是 `Gray._40` 深灰填充，与白底菜单对比度低、看不出勾选状态。
- **方案**：新增 `BoardContextMenu.FlatCheckBoxMenuItem`（继承 `JCheckBoxMenuItem`），重写 `paintComponent` 完整自绘 cell：
  - 整行背景：`isArmed/isSelected ? MenuItem.selectionBackground : getBackground()`
  - 勾选框 14×14 圆角矩形，垂直居中于行高：
    - 选中：蓝底（浅色 `#2470B0` / 深色 `#4A90E2`，`JBColor` 双态）+ 白色对勾（`drawPolyline` 折线）
    - 未选中：填充 `#FFFFFF`（浅色）/`#3C3F41`（深色）+ 1px 灰边（`#B0B0B0` / `#6B6B6B`）
  - 文字：`getForeground()`（`JBColor.foreground()`）从 `18 + 14 + 6` 处开始 `drawString`，基线用 FontMetrics 垂直居中
- **关键点**：
  - `setOpaque(true)` + `setBorderPainted(false)`，绕开 `BasicMenuItemUI` 缓存的 `checkIcon` 渲染
  - 保留 `isSelected/setSelected` 语义，原有 ActionListener 业务不变，仅 `addRelationTypeItem` 改用它
  - 颜色一律 `JBColor(light, dark)` 双态，自动适配深浅主题
- **踩坑**：Swing L&F 的 `CheckBoxMenuItem.checkIcon` 从 UIDefaults 加载后被 `BasicMenuItemUI` 缓存，直接改 UIDefaults 不生效，必须自定义组件 paint。
- **踩坑（2026-08-04 Win 菜单宽度不够）**：自绘 `paintComponent` 接管渲染后，Swing 默认的 `BasicMenuItemUI.getPreferredSize()` 依赖 `checkIcon` 计算宽度，而本组件已经把默认 icon 关掉，导致子菜单宽度按 0 icon 宽算，**Win 系统下 "一对多/多对多" 被截断**。必须重写 `getPreferredSize()`：手动算 `BOX_LEFT_PADDING(18) + BOX_SIZE(14) + 6 + 文字宽度 + 右边距(12)`，高度 `max(BOX_SIZE+8, fm.getHeight()+6)`。父类 L&F 算 preferredSize 不可信是自绘菜单项的通用坑。

### 30. 列行左键起手连线（2026-08-04 修复 Win 系统不能拖线）
#### 问题
Win 系统下从列行按下左键拖拽鼠标到终点，**松手后连线不会自动建立**，必须先右键起手才能连线。用户期望"鼠标移动终点，松开按钮自动连上"。

#### 根因
- 原 `mousePressed` 列行分支只在**右键** (`isPopupTrigger` || BUTTON3) 才设置 `isConnecting=true`
- 左键点击列只调用 `toggleRowSelection(card, rowIndex)`（列高亮），不进入连线模式
- 用户实际想要"在列上按下拖动"，但 Mac 习惯（Win 上大多数人也是）用左键拖拽，左键被高亮逻辑"拦截"了

#### 方案：待连线状态 + 移动阈值升级
不能简单地把"左键列行 = 立即连线"，否则会破坏"快速点击列 = 选中高亮"的原有交互。用阈值延迟升级：

- **新增字段**（`KanbanBoard`）：
  - `pendingConnectionSource / pendingConnectionSourceRow / pendingConnectionPressPoint`：左键按下列行时记录"待连线"三件套
  - `CONNECTION_DRAG_THRESHOLD = 4`（屏幕像素）
- **mousePressed 左键列行分支**：不再立即 toggleRowSelection，而是设置 pendingConnection*；不入 `isConnecting=true`，不画预览线
- **mouseDragged 新增分支**：`pendingConnectionSource != null` 时计算 `dx² + dy² >= 16` 就升级为 `isConnecting=true` + 设光标 + repaint + 清空 pending
- **mouseReleased**：先检查 `pendingConnectionSource`，未升级就 `toggleRowSelection(pendingCard, pendingRow)`，保持原"快速点击列 = 高亮"行为；再走原有的 `isConnecting` 分支查找 targetCard 并 `addConnection`

#### 行为
- 快速左键点击列（不移动） → 触发列高亮（原行为不变）
- 左键按下列后拖动 ≥4px → 进入连线预览；移动到目标列松手 → 自动 addConnection
- 右键行为完全不变（保持弹菜单/连线的原有逻辑）

#### 设计原则
- **拖拽判定用阈值而非时间**：避免长按造成点击/拖拽歧义
- **左键列行 = 拖拽优先**：Mac/Win 现代 GUI 默认（图标/列表项拖动都是左键），不再依赖右键起手
- **不破坏现有列高亮交互**：未移动就松手仍触发 toggleRowSelection，与之前等价

### 31. 连线松手点飘走不丢失连接（2026-08-04 优化）
#### 问题
"鼠标移到 leader 行没点击，点一下别处连接就没了"——拖拽过程中预览高亮正确显示了 leader 行，但松手时鼠标飘到空白处（甚至隔壁行），原 `mouseReleased` 用松手点找 `targetCard`，找不到就不 `addConnection`，预览高亮消失，用户视觉上"连接没了"。

#### 修复：记录最后一次 hover 的目标
- **新增字段**：`lastHoverTargetCard / lastHoverTargetRow`（KanbanBoard 字段）
- **`mouseDragged` isConnecting 分支**：命中目标行时同步写入 lastHoverTarget（仅在 `targetCard != connectionSource` 时）
- **`mouseReleased` isConnecting 分支**：
  1. 优先用 `lastHoverTargetCard / lastHoverTargetRow` 建线
  2. 没有 hover 过有效目标（null 或 == connectionSource）才回退到松手点找 targetCard
  3. mouseReleased 末尾清空 lastHoverTarget
- **`mousePressed` 进入连线时清空 lastHoverTarget**：防止上次拖拽残留（两个入口：右键列行 + 升级瞬间）

#### 行为
- 拖到目标列（leader）→ 飘到空白松手 → **仍按 leader 建线**（用户期望）
- 拖到目标列（leader）→ 在 leader 上松手 → 按 leader 建线（原有行为不变）
- 没有 hover 过任何有效目标 → 回退到松手点位置判断（兼容快速点击场景）
- 松手点 = source card 上 → targetCard == connectionSource 不会建线（自连禁止）

#### 设计原则
- **以"用户最终选择的目标"为准**：松手点是次要的，hover 状态更准确反映用户意图
- **兼容快速点击场景**：没 hover 过任何有效目标（按一下就松）才用松手点判断
- **避免自连**：判断 `targetCard != connectionSource` 双保险（hover 阶段和松手阶段都校验）

### 32. PDF 导出中文字体回退（2026-08-04 整理抽取）
`BoardExportUtil.createCjkFont(font)` 私有静态方法，按优先级回退（供 `exportToPdf` 的 `DefaultFontMapper.awtToPdf` 调用）：

1. **iText Asian 中文字体（首选）**：`STSong-Light`，编码 `UniGB-UCS2-H`，`NOT_EMBEDDED`
   - 跨平台、内置，不依赖系统字体
2. **Windows 系统字体（兜底）**：`C:/Windows/Fonts/simsun.ttc,0`（宋体），编码 `IDENTITY_H`，`NOT_EMBEDDED`
   - 仅在 iText Asian 缺失或加载失败时使用
3. **默认处理（都失败）**：`new DefaultFontMapper().awtToPdf(font)` 回退到 AWT 默认字体

- **iText Asian 字体 key**：`STSong-Light`（配合编码 `UniGB-UCS2-H`）是 `itext-asian` 依赖提供的 CID 字体
- **系统宋体 key**：`simsun.ttc,0`（`/ttc` 字体集合取第 0 个 face）需配合 `IDENTITY_H`（Unicode 编码）使用
- **嵌入级别统一 `NOT_EMBEDDED`**：STSong-Light 是 CID 字体通常不嵌入；simsun 不嵌入则依赖查看方机器字体，如担心跨机显示可改 `EMBEDDED`（会增加文件体积）

### 33. 连线建线后源行用户选中残留（2026-08-04 修复）
#### 问题
用户选中 `config_type` 行（橙色 #FE9933）→ 拖到 `invoke_target` 松手建线 → 整条连线变橙色（取源行高亮色）；点击别处后 `invoke_target` 变淡黄（搜索命中），**但 `config_type` 仍是橙色**——用户期望：建线后源行的橙色高亮应该被清空（与连线的"占用色"语义一致，避免视觉混淆）。

#### 根因
- `KanbanBoard` 列行左键按下 → 进 `pendingConnectionSource` 状态 → 拖动升级为 `isConnecting=true`
- 升级瞬间**没有调** `toggleRowSelection` 取消源行用户选中
- 用户列高亮（`highlightedRows` 集合 + `activeHighlightCard/Row`）一直保留，连线建好后整条线仍是橙色

#### 修复
- **新增私有方法** `clearUserRowSelection(KanbanCard card, int rowIndex)`：从 `card.getHighlightedRows()` 移除该行；若是当前 activeHighlight 则同步 `clearActiveHighlight()`（连带 `relatedRowKeys.clear()`）
- **`mouseDragged` 升级分支**：升级为 `isConnecting=true` 时调 `clearUserRowSelection(upgradeSource, upgradeRow)`
- **`mousePressed` 右键列行起手分支**：同样调 `clearUserRowSelection(card, rowIndex)`
- 不在 `pendingConnectionSource` 未升级（快速点击）路径上调，避免破坏原有"快速点击切换选中"交互（`mouseReleased` 的 `toggleRowSelection` 仍负责）

#### 设计原则
- **连线建好后源行不应是"用户激活列"**：橙色应该是连线的占用色，不是用户的选中意图
- **快速点击 vs 拖拽建线 行为分叉**：
  - 快速点击（没移动）→ `toggleRowSelection`（toggle 语义，可能选中/取消）
  - 拖拽建线 → `clearUserRowSelection`（强制清掉，避免遗留）
- **不入 `repaint`**：`clearUserRowSelection` 不主动重绘，由调用方（升级分支/起手分支）按需刷新，避免和现有 `repaint()` 逻辑重复或乱序

### 34. 连线建线后用 palette 分配颜色（2026-08-04 撤销"沿用预览色"误改）
#### 历史与撤销
第一次修复（2026-08-04 上午）误以为"建线后整条线突变成 palette 颜色是 bug"，尝试让 `addConnection` 接收 `overrideColor` 并把拖拽预览的粉色作为新连线 color——结果用户反馈"怎么能一直粉色呢？连一次换一个颜色啊"。

**撤销内容**：
- 删掉 `addConnection(source, sourceRow, target, targetRow, relationType, Color overrideColor)` 6 参重载
- 删掉私有方法 `capturePreviewColor(card, rowIndex)`
- `mouseReleased` 建线分支恢复为 `addConnection(source, sourceRow, target, targetRow)`（走 palette）

#### 设计原则（正确版）
- **预览色只是拖拽过程临时色**，建线成功后必须用 palette 分配一个新颜色，让多条连线**视觉可区分**——这是 ER 图工具的基本要求
- **palette 循环分配**（`connectionColorIndex++ % palette.length`）保证每条新连线颜色不同
- **加载文件恢复连线** / **编程方式建线** 也走 palette，保持视觉一致
- 第 33 节修复（清源行用户选中）依然必要，但**清掉的应该是"激活高亮"而不是"连线本身的颜色"**——连线仍应保留 palette 区分
- **预览色由粉色改为深灰（2026-08-04 用户反馈）**：`CONNECTION_PREVIEW_COLOR` 改为 `JBColor(浅色 #757575, 深色 #AAAAAA)`，预览连线绘制处（原 `Color.PINK`）也统一用它。避免粉色与 palette 中的粉紫/粉红系颜色混淆

### 35. 卡片 ID 用 UUID，兼容旧 schema.table id（2026-08-07）
#### 问题
用户拖入两张相同的表（如 `sys_user`），连线时一切正常，但**关闭重新打开后连线全乱**——因为：
- `DatabaseTableMetadataFetcher` 默认把卡片 id 设为 `schema + "." + tableName`（如 `public.sys_user`）
- 两张 `sys_user` 的 id 完全相同
- `BoardPersistence.loadFromChartData` 用 `findCardById` 按 id 查卡时，cards 列表里第一张 `public.sys_user` 先匹配上，所有指向 `public.sys_user` 的连线都打到第一张卡上
- 视觉表现：原本从下方新加的 `sys_user.user_id` 连到 `sys_user_social.user_id` 的线，重新打开后变成从**第一张** `sys_user.user_id` 连出去 → 线错乱

#### 修复
- **`TableInfo.id`** 从 `final` 改为可写，新增 `setId(String)` 方法（拖入时分配 UUID 覆盖默认 id）
- **`KanbanBoard.addTableCard`**：在 `KanbanCard.forTable` 之前 `info.setId(UUID.randomUUID().toString())`，保证画板上的每张卡 id 全局唯一
- **`KanbanBoard.loadFromChartData`**：遍历 `data.getTables()` 时检测重复 id（`isCardIdExists` 私有辅助方法），发现重复或为 null 就给 model 补 UUID，并同步 `model.setId(newId)` 让持久化时也写入新 id
- **新文件 vs 旧文件**：
  - 新文件：id 是 UUID，唯一性天然满足，无重复
  - 旧文件加载：cards 列表顺序扫描，第一张 `public.sys_user` 保持原 id，后续重复的补 UUID；第一次保存后 id 全部唯一，问题根治
  - 旧文件里**已经丢失了**的连线（id 重复时本来就没正确关联）无法挽救，但用户**重新保存一次**后所有 id 都规范化
- **未改动**：`Connection` / `ChartRelation` / `BoardPersistence.findCardById` 逻辑完全不动——`ChartRelation.fromCardId` 存的就是卡片 id，本身就是 string 类型，向后兼容

#### 设计原则
- **数据标识用 UUID 而非业务字段**：`schema.table` 不适合做 id（用户可能拖同一张表两次；同 schema 下不同库的同名表会冲突）
- **拖入时分配，不靠加载时补救**：从源头避免 id 冲突
- **加载时检测 + 补 UUID**：保证旧文件首次打开也不会让用户重画所有连线
- **持久化 in-memory model**：补 UUID 后立即 `model.setId(newId)`，下次保存就把规范化结果写回 JSON，**用户感知到的恢复是「保存一次就好」**

### 36. 同步表结构后连线列重定位（2026-08-07）
#### 问题
`KanbanBoard.syncTableStructure`（表头右键"同步表结构"）用 `card.setTableInfoWithDiff(fresh, ...)` 替换 TableInfo，但 `Connection.sourceRow/targetRow` 存的是**列 index**。同步后列顺序可能变化（增删列），沿用旧 index 会让连线指向错误列——与第 13 节"删除列后 index 错位"同源。
- 持久化（load/save）路径已用 `fromColumnName/toColumnName` 列名解决（13 节）
- 但 **syncTableStructure 运行态路径没有列名重定位**，是遗漏

#### 修复
- **`Connection.sourceRow/targetRow`** 从 `final` 放开为可变，新增 `setSourceRow(int)` / `setTargetRow(int)`
- **`KanbanBoard.syncTableStructure`** 替换表结构前遍历所有连线，记录「涉及该卡片的连线 → 旧列 index」；替换后用 `findColumnIndex(fresh, 旧列名)` 重新定位：
  - 列仍存在 → `conn.setSourceRow/setTargetRow(newRow)`
  - 列被删除 → 移除该连线（并清空 `selectedConnection`、失效 `linkedRowsCache`）
- 辅助方法：`oldRowToColumnName(TableInfo)`（行 index→列名）、`findColumnIndex(TableInfo, colName)`
- 通知文案附带"已移除 N 条失效连线"

#### 设计原则
- **所有引用列 index 的地方，在表结构变化后都要用列名重定位**（持久化 13 节 + 运行态 36 节两条路径都已覆盖）
- 列被删除的连线直接丢弃（列都不存在了，连线语义失效），不要静默保留指向错误列

### 37. 重新打开 .datachart 视口位置错位（2026-08-07）
#### 现象
IDE 退出前打开某 .datachart 文件，下次启动自动重开时，卡片"都跑到画板顶部/角落去了"，用户必须点 Focus 按钮才能恢复。

#### 根因
- `DataChartEditor.ensureInitialized` 在 `getComponent` 同步链路中触发 `loadFromFile → dataView.loadFromJson`
- `loadFromJson` 用 `SwingUtilities.invokeLater` 延迟一帧后调 `focusView`，但此时**面板还没真正嵌入 IDE 编辑区**，Swing 布局 pass 还没完成
- `KanbanBoard.getVisibleRect()` 返回 width=0/height=0
- `BoardViewport.focusOn` 进入"情况 A"分支（`fits = true` 因为 `viewWidth=0`），把内容中心对齐到 (0, 0) → `transform.translate` 把视口推到负方向
- 等 panel 真正显示时，画面跑到顶/左外

#### 修复
- **`KanbanBoard.focusView`** 在 `viewWidth <= 0 || viewHeight <= 0` 时直接 return（防御性护栏，所有路径都受益）
- **`DataChartView.loadFromJson`** 改用 `ComponentAdapter` 监听 kanbanBoard 首次 `componentResized` 拿到有效尺寸后再 `focusView`，触发后立即注销
- 新增 `scheduleFocusWhenReady()` 方法 + `focusWhenReadyListener` 字段
  - 已有有效尺寸 → 直接 focus
  - 否则注册 listener（多次 loadFromJson 时先移除旧 listener 避免累积）
  - `dispose()` 中也清理 listener，避免内存泄漏

#### 设计原则
- **不要用 `invokeLater` 一帧延迟代替"等组件布局完成"**：在 `getComponent`/`ensureInitialized` 同步链路中调用的代码，一帧延迟后 panel 仍未真正布局完成
- **focusOn 的入参 viewWidth/viewHeight 必须有非零校验**：否则会把内容中心对齐到 (0, 0) 产生灾难性偏移
- **监听器注册后必须管理生命周期**：跨多次 `loadFromJson` 累积、dispose 时清理，是 Swing 组件的标准做法

### 38.1 工具栏 1:1 按钮改为 100%（2026-08-07）
- 原 `1:1` 按钮 = 缩放 100% + focus 居中（组合操作），职责不纯，易误导
- 改为：文字 `100%`（去掉图标），行为**只缩放到 100%，不动位置**（以视口中心为锚，保持视野中心稳定）
- 与 `Focus` 按钮职责正交：`100%` = 改缩放，`Focus` = 改位置
- tooltip 同步为 "缩放到 100%（保持当前画板位置不动）"

### 38.2 新增 Fit 按钮（缩放 + 居中显示完，2026-08-07）
- 用户需求："缩放 + 居中显示完"（Fit to Window）按钮
- 新增 `BoardViewport.fit(List<KanbanCard>, int viewWidth, int viewHeight)`：
  - 计算所有卡片合并包围盒（画板坐标）
  - 按 `min(availW/contentW, availH/contentH)` 求目标缩放（限制在 [MIN_ZOOM, MAX_ZOOM]）
  - 以视口中心为锚缩放，再居中（内容中心落到视口中心）
- 新增 `KanbanBoard.fitView()`：调 `viewport.fit` + notifyViewChanged + repaint（含 viewWidth 零值防御）
- 新增 `DataChartView.fitButton`（Fit 按钮，form 布局 column 3，column-count 8→9，原列号整体+1）
- 三个按钮职责正交：
  - `100%` = 只改缩放
  - `Recenter` = 只改位置（保留缩放）【原 Focus，2026-08-07 改名，语义"保持缩放只居中"】
  - `Fit` = 改缩放到适配 + 居中（组合）

### 38. 选中行后所有连通图行变橙（2026-08-07）
- 原 `1:1` 按钮 = 缩放 100% + focus 居中（组合操作），职责不纯，易误导
- 改为：文字 `100%`（去掉图标），行为**只缩放到 100%，不动位置**（以视口中心为锚，保持视野中心稳定）
- 与 `Focus` 按钮职责正交：`100%` = 改缩放，`Focus` = 改位置
- tooltip 同步为 "缩放到 100%（保持当前画板位置不动）"

### 38. 选中行后所有连通图行变橙（2026-08-07）
#### 现象
左键选中某行（如 `sys_user.id`），希望所有"有连接关系"的行都变成选中色 #FD9933（橙），但 `sys_menu.parent_id` 这类"连通图内但不是直接邻居"的行仍显示为连线占用色（紫色）。

#### 原因
`refreshRelatedRows` 只遍历**直接邻居**（一阶 BFS），不沿连线图递归。所以 sys_menu.parent_id 这样的间接连通行留在 linkedRowsCache 渲染的紫色（连线自身颜色）。

#### 修复
`refreshRelatedRows` 改为**完整 BFS 沿 `connections` 双向遍历**：
- 构造 `Map<endpointKey, List<RelatedRowPos>>` 邻接表
- 从 `(activeHighlightCard, activeHighlightRow)` 出发 BFS，所有可达的 `(card, row)` 加入 `relatedRowKeys`
- 渲染时 `relatedRowKeys` 已统一用 `RELATED_ROW_COLOR`（橙色），覆盖默认的连线占用色
- 复用现有 `RelatedRowPos` + `makeRelatedKey`，不引入新数据结构

#### 设计原则
- **"选中一个，所有连通行都高亮"是图遍历语义**：一阶邻居不够，必须 BFS/DFS 整个连通子图
- **复用现有数据结构**：BFS 状态直接用 `RelatedRowPos`，key 用 `makeRelatedKey`，与渲染路径同源，避免新旧 key 不匹配
- **连通子图边界**：不属于该连通子图的孤立连线行不受影响（用户只关注选中的那张网络）

### 41. 空白拖拽改框选 + 多选 + Command+Del 批量删除（2026-08-20）
#### 需求
1. 左键表头按住移动 = 移动表（原有逻辑，未动）
2. 左键空白按住移动 = 画选区，选区内的表全部选中（多选）
3. Command+Del 删除所有选中的表
#### 实现
- **字段**：`selectedCards`（`LinkedHashSet<KanbanCard>` 多选集合）、`isSelecting`、`selectionStartBoard`（画板坐标）、`selectionRect`（画板坐标）、`SELECTION_DRAG_THRESHOLD=4`
  - `selectedCard` 保留为"主选中卡"（多选时是 Z 序最上层的一张），兼容大量单卡逻辑
- **mousePressed 空白处**：从 `isDraggingBoard=true`（平移画板）改为 `isSelecting=true` + 记录起点 + `CROSSHAIR_CURSOR`
  - 平移画板仍可用滚轮 pan；`isDraggingBoard` 分支保留但不再触发
- **mouseDragged**：`isSelecting` 分支实时更新 `selectionRect`（min/max 归一化）→ `updateSelectionFromRect()`
  - 判定规则：`selectionRect.intersects(card.bounds)`，相交即选中（部分重叠也算）
- **mouseReleased**：`isSelecting=false`；选区宽高都 < 4 视为单击空白 → `clearSelectedCards()`
- **paintComponent**：在画板坐标、卡片之上画选区：`fill`（composite 0.18 半透明蓝 #4A90E2）+ `draw` 边框
  - 坑：`new Color(int, true)` 把值当 ARGB（alpha=0x00 → 完全透明），必须用 `new Color(int)` 配 composite
- **drawCards**：`isSelected = (card == selectedCard || selectedCards.contains(card))`
- **选中入口统一**：`selectOnly(card)`（点击卡片时清空集合 + 只选一张）；`clearSelectedCards()` 清空
- **删除**：`deleteSelectedCard()` 改为删除 `selectedCards ∪ selectedCard`：
  - 先统一检查是否含连线 → 弹一次确认
  - `deleteCard(card, false)` 逐张删除（`confirmRelations=false` 避免重复弹窗）
  - `deleteCard` 拆出带参重载，右键菜单仍走 `deleteCard(card)`（带确认）
- **同步清理**：`removeCard` / `clearCards` / `loadFromChartData` / `deleteCard` 都要 `selectedCards.remove/clear`
#### 设计原则
- **单卡选中字段与多选集合并存时，所有入口必须走统一方法**（selectOnly / clearSelectedCards），禁止散落直接赋值
- **框选矩形用画板坐标**（随 zoom/pan 跟随画布），判定相交用卡片真实 bounds
- **批量删除的连线确认只弹一次**：确认逻辑上提到批量层，逐卡删除关闭 confirm
- **任何清空卡片的路径都要同步清多选集合**：removeCard / clearCards / loadFromChartData / deleteCard 已全覆盖

### 40. 拖放表格卡片定位契约：鼠标位置 = 卡片左上角（2026-08-20）
#### 现象
从 Database 工具窗口把表拖入画板，表格出现位置与鼠标松手位置有明显偏差（数百到上千像素）。
#### 根因（两层叠加）
**第一层 — 坐标系不一致**：
- `DnDEvent.getPointOn(null)` 给的是 **IDE 屏幕绝对坐标**（含 IDE frame 与多屏偏移）
- `mouseDragged` 的 `MouseEvent.getPoint()` 给的是 **JPanel 局部坐标**（panel 内 0,0）
- 两条链路坐标系不同，原代码直接把 IDE 屏幕坐标喂给 `viewport.transformPoint`（按 component-local 设计）→ 反算出来的"画板坐标"实际是含 IDE 偏移的错位值
**第二层 — 对齐方式**：
原"鼠标 = 卡片中心"对超高卡片（如 21 字段 sys_menu ≈ 416px 高）用户感受是"卡片漂移"。
#### 修复
- 在 `addTableCard` 里把 IDE 屏幕坐标先减去 `KanbanBoard.getLocationOnScreen()` 转成 JPanel 局部坐标，再走 `viewport.transformPoint`：
```java
java.awt.Point panelLocationOnScreen = getLocationOnScreen();
localPoint = new java.awt.Point(screenPoint.x - panelLocationOnScreen.x,
                                  screenPoint.y - panelLocationOnScreen.y);
Point2D boardPoint = viewport.transformPoint(localPoint);
x = boardPoint.getX();  // 鼠标位置 = 卡片左上角
y = boardPoint.getY();
```
- 同时把契约改为"鼠标 = 卡片左上角"（draw.io / Freeform 习惯），避免超高卡让用户感觉"漂移"
#### 设计原则
- **坐标链路先对齐，再谈对齐方式**：IDE DnD 屏幕坐标必须先转 JPanel 局部坐标，否则 transform 反算全错
- **拖放落点契约要明确**：中心 vs 左上角，二选一并注释写明；列多的表建议左上角

### 42. 全项目性能/线程安全修复批（2026-08-27）
按审查报告优先级修复了 8 项，涉及 6 个文件：
#### 已修复
1. **dispose 不再无条件自动保存**（`DataChartEditor`）
   - 新增 `lastSavedStamp`（保存时记录文件修改戳）；dispose 时若磁盘文件被外部工具改过则放弃自动落盘（避免覆盖外部改动），否则照常保存
   - 新增 `NotificationUtil.error` + `LOG`：保存失败不再静默吞掉（P2-8）
   - `getModifiedPropertyName()` 反射结果静态缓存（volatile，P3-18）
2. **拖表/同步表结构后台化**（`TableDropHandler.processTable` / `KanbanBoard.syncTableStructure`）
   - 反射查库移 `executeOnPooledThread` + `ReadAction`（PSI/DAS 必须 read action），结果 `SwingUtilities.invokeLater` 回 EDT
   - 回 EDT 校验：project.isDisposed()（handler）、cards.contains(card)（sync 期间卡可能被删）
3. **每帧全量重绘裁剪**（`KanbanBoard`）
   - `drawCards(g2d, dark, Rectangle2D visibleArea)`：与视口不相交的卡片跳过绘制（selected 状态仍更新保持一致）
   - `computeVisibleBoardArea()`：JPanel bounds 经 `viewport.getInverse()` 反算画板坐标可见区；宽高<=0 或逆变换异常返回 null 退回不裁剪
   - 导出路径传 null 不裁剪（需全量）
4. **导出后台化**（`DataChartView.exportAsPdf/exportAsImage`）
   - `ProgressManager.run(Task.Modal)`：PDF 编码/大图创建+JPEG 编码移后台；模态进度框阻塞 EDT 交互 → board 状态无竞态；结果 `invokeLater` 回 EDT 弹通知
5. **打开文件 IO 后台化**（`DataChartEditor.loadFromFile`）
   - `file.contentsToByteArray()` 移后台（ReadAction），JSON 解析回 EDT
6. **dispose 完整清理**（`KanbanBoard.dispose` / `KanbanCard.disposeTimers`）
   - 停止所有卡片 `slideAnimTimer` + 清空 cards/connections/选中集合，防止 Swing Timer 持引用泄漏
7. **裸 printStackTrace → LOG.warn**（KanbanBoard 3 处）
8. **反射热路径日志降级**（`DatabaseTableMetadataFetcher`）
   - `resolveColumnType`/`hasColumnAttribute`/`invokeWithArg`/`invokeNoArgs`/`invokeMethod` 失败改 `LOG.debug`（大表每列失败会刷屏）；整表失败由 `fetchTableInfo*` 的 warn 汇总
#### 未修复（风险高，留待单独批）
- **P2-16 `DataChartView extends DialogWrapper` 反模式**：改为普通 JPanel 涉及 GUI Designer `.form` 绑定改造，风险高收益中等，需单独评估
#### 设计原则
- **后台线程回 EDT 回调三要素**：project 存活校验 + 目标对象（卡片/组件）仍存在校验 + Swing 更新必须 EDT
- **PSI/DAS 对象访问必须在 ReadAction 中**（拖表后台化后尤其关键）
- **Task.Modal 顺带解决竞态**：模态进度框阻塞 EDT，后台线程读 board 状态安全
- **定时器/监听器必须随 dispose 停止**：Swing Timer 会持有卡片引用
- **热路径日志降级原则**：每对象×每字段级失败用 debug，整体失败用 warn 汇总

### 43. 异步化引入的回归修复（2026-08-27 复查批）
上一批后台化改造引入了 3 个新问题，复查时发现并修复：
1. **异步加载回调悬空（P0）**：`loadFromFile` 后台读取的 `invokeLater` 回调在 `dispose()` 后仍会执行，
   重新填充已清理的看板 → 新增 `disposed` 标志：`dispose()` 最先置位，回调内 `if (disposed) return;`
   **教训：任何后台任务 + 生命周期（dispose/关闭）组合，必须加 disposed 守卫，且置位必须在清理最前面**
2. **保存竞态（P1）**：异步加载未完成时 Ctrl+S，`serializeToJson()` 序列化空看板覆盖磁盘原文件
   → `saveDocument()` 开头 `if (loading)` 拒绝保存 + 提示
   **教训：loading 标志不再只是"屏蔽修改状态"，还承担"数据未就绪"语义，所有读数据源的操作都要检查**
3. **裁剪区域坐标系（P1）**：`computeVisibleBoardArea()` 用 `getBounds()`（父容器坐标，可能非 0 起点）
   → 改用 `getWidth()/getHeight()` 构造 `(0,0,w,h)` 局部坐标
   **教训：组件自身范围的裁剪计算用 getWidth/getHeight 而非 getBounds**
#### 复查确认
- `drawGrid` 的 null 分支本就用逆变换做了可见裁剪（之前审查误报 P2-10）
- dispose 链完整：`DataChartEditor.dispose → DataChartView.dispose → KanbanBoard.dispose`（timers/监听器/集合全清理）
#### 仍未修（维持评估结论）
- P2-16 `DataChartView extends DialogWrapper` 反模式：.form 绑定改造风险高，需单独批次
- P2-11 `mouseMoved` 每帧 `findCardAt`：O(n) 遍历开销极小，收益低
- 插件状态/反射类缓存（`isDatabasePluginEnabled` volatile 缓存已做，`METHOD_CACHE` 静态引用低风险）

### 39. 表格卡片高度去掉 400 上限（2026-08-20）
#### 现象
80 列的大表只显示前 ~20 列，底部出现 "... 共 80 列" 截断提示。
#### 根因
`KanbanBoard.addTableCard` / `loadFromChartData` 中 `height = Math.min(height, 400)` 硬编码 400px 上限，
`KanbanCard.updateTableInfo` 同样 `Math.min(400.0, ...)`，导致 `(400-60)/18 ≈ 18.8` 行装不下全部列。
#### 修复
**第一轮（2026-08-20）：去掉 400 上限**
三处 `Math.min(height, 400)` / `Math.min(400.0, ...)` 全部去掉 400 限制：
- `KanbanBoard.addTableCard`：`height = TABLE_CARD_BASE_HEIGHT + rowCount * TABLE_CARD_ROW_HEIGHT`
- `KanbanBoard.loadFromChartData`：同上
- `KanbanCard.updateTableInfo`：`h = Math.max(50.0, headerHeight + bodyH + padding)`（保留 50 仅防空表塌陷）
渲染端 `drawTableCard` 的 `maxRows` 是动态按 bounds 高度算的，无需改动。

**第二轮（2026-08-20）：高度公式三处统一 → 修底部 22px 空白**
去上限后发现底部仍有 ~22px 宽空白行——因为 `KanbanBoard.TABLE_CARD_BASE_HEIGHT = 60` 是历史遗留值，
与 `KanbanCard.drawTableCard` 实际用到的 `headerHeight(28) + padding(10) = 38` 不一致。
- `KanbanCard` 新增 `public static final int HEADER_HEIGHT = 28` / `PADDING = 10`，原实例字段保留指向常量（不破坏 14 处引用）
- `KanbanBoard.TABLE_CARD_BASE_HEIGHT` 改为 `KanbanCard.HEADER_HEIGHT + KanbanCard.PADDING`（=38），让画板计算高度 = 渲染实际可用高度
- `KanbanCard.updateTableInfo` 改用静态常量 `HEADER_HEIGHT / ROW_HEIGHT / PADDING`，公式与 `KanbanBoard` 完全同步
#### 副作用（预期内）
- 列多的卡（如 80 列）会变 ~1500px 高，可能遮挡/影响排布
- 导出 PDF/图片时包围盒自动包含整卡，图幅会变大
#### 设计原则
- **高度计算必须三处同步 + 公式常量共享**：`addTableCard`（新建）、`loadFromChartData`（加载）、`updateTableInfo`（同步表结构）必须共用同一组静态常量（`HEADER_HEIGHT`/`ROW_HEIGHT`/`PADDING`），不能各自算各自的
- **BASE_HEIGHT 与 drawTableCard 公式必须严格对齐**：否则底部会留多余空白（38 ≠ 60 是隐藏陷阱）
- 若后续要控制遮挡，建议在"卡片内滚动"或"折叠双栏"方案上做，不要回到全局 400 截断

### 44. 表头右键「跳转」：复用 Database 插件原生 Action（2026-09-21）

#### 需求
表头右键菜单增加跳转项（对齐 IDEA 数据库 ER 图里的 `Go To > Data / Go to DDL / Database Explorer`）。

#### 背景：IDEA Diagrams 是怎么做的（反编译 ideaIU-2023.2.6 得到）
图右键菜单**不是自己写的跳转逻辑**，而是「把自己的选中元素包成 PSI 上下文 + 组装现成 Action」：
- `com.intellij.uml.core.actions.DiagramSourceActionsGroup`（`plugins/uml/lib/uml-support.jar`）是基类；
  Database 侧 `com.intellij.database.diagram.DbDiagramProvider$2$1.getChildren()` 用
  `ActionManager.getInstance().getAction(id)` 取 `$Copy` / `CopyReference` / `FindUsages` / `DbDiagrams.SourceActionsGroup.GoTo` 等组装。
- `Go To` 子菜单定义在 `database-plugin.jar!/META-INF/DatabasePlugin.xml`：
  ```xml
  <group id="DbDiagrams.SourceActionsGroup.GoTo" popup="true">
    <reference ref="Jdbc.OpenEditor.Data"/>       <!-- Data -->
    <reference ref="Jdbc.OpenEditor.DDL"/>        <!-- Go to DDL -->
    <reference ref="sql.SelectInDatabaseView"/>   <!-- Database Explorer -->
  </group>
  ```
- 通用 Action 只认 `CommonDataKeys.PSI_ELEMENT`；`UmlFileEditorImpl implements DataProvider`，
  在 `getData()` 里把 `DiagramNode`（`PsiDiagramNode` 持 `SmartPsiElementPointer<PsiElement>`）映射成 PSI 元素。

#### 本插件实现（三处改动 + 一个新类）
1. **`DatabaseTableMetadataFetcher.resolveDbElement(project, datasourceName, tableName)`**（新增）
   按需把持久化信息解析回 PSI 元素（反射链）：
   ```
   DbPsiFacade.getInstance(project) → findDataSource(name) → DasUtil.getTables(ds) 匹配同名表
     → DbPsiFacade.findElement(DasObject) → DbElement（extends PsiFileSystemItem，即 PsiElement）
   ```
   用 `dbPsiFacadeClass.getMethod("findElement", dasObjectClass)` **精确签名**查找（按名查找会命中重载踩空）。
2. **`com.wd.db.TableNavigator`**（新增，纯静态）：
   - 常量 action id：`Jdbc.OpenEditor.Data` / `Jdbc.OpenEditor.DDL` / `sql.SelectInDatabaseView` / `FindUsages`
   - `isActionAvailable(actionId)`：`ActionManager.getAction() != null`（未装 Database 插件时为 false）
   - `performAction(project, info, actionId)`：解析 PSI → 构造 `DataContext` → `action.update()` 判 enable → `actionPerformed()`
3. **`BoardContextMenu.buildHeaderMenu(...)`** 增加第 4 个参数 `NavigateAction navigateAction`，
   生成「跳转」子菜单（`JMenu`，样式与「关系类型」子菜单一致：前景色 + `MenuItem.selectionForeground/Background` 两个 client property）。
   动作不存在时整组不显示。
4. **`KanbanBoard.showHeaderContextMenu`** 传入 `actionId -> navigateToTable(card, actionId)`；
   `navigateToTable` 失败时用 `NotificationUtil.info` 提示"请先在 Database 工具窗口中刷新"。

#### 可用 API（已在 2023.2.6 核对）
```java
DataContext ctx = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.PSI_ELEMENT, psiElement)
        .build();

AnActionEvent event = AnActionEvent.createFromDataContext(
        ActionPlaces.POPUP, action.getTemplatePresentation().clone(), ctx);

Boolean enabled = ReadAction.compute(() -> { action.update(event); return event.getPresentation().isEnabled(); });
if (enabled) action.actionPerformed(event);   // actionPerformed 放在 read action 外面
```

#### 踩坑
- **`CommonDataKeys.PSI_ELEMENT_ARRAY` 在 2023.2 不存在**（`CommonDataKeys` 只有 `PSI_ELEMENT`/`PSI_FILE`）→ 别加，编译直接报"找不到符号"。
- **不要直接复用 `DbDiagrams.SourceActionsGroup.GoTo` 这个组**：它继承 `DiagramSourceActionsGroup`，
  `update()` 要求 DataContext 里有 `DiagramDataKeys.BUILDER` 且 `DiagramSelectionService.getSingleSelectedNode(builder) != null`，
  否则整组 `setEnabledAndVisible(false)`，在画板里会整组置灰 → 必须自建菜单项。
- **`ActionPlaces` 在 2023.2 没有 `CONTEXT_MENU` 常量**，用 `ActionPlaces.POPUP`。
- **`ActionManager.createActionPopupMenu(place, group)` 没有带 component 的重载**（2023.2）；
  若要动态菜单用 `ActionPopupMenu.setDataContext(Supplier<? extends DataContext>)`（2023.2 已有），
  但它产出的 `JPopupMenu` 走 Swing 默认 L&F，hover 会白字 → 与现有自绘菜单风格不一致，所以本次仍用自建 `JMenuItem`。
- **文案由 Action 自己按 place 切换**：`OpenEditorAction$OpenDataAction.update()` 里
  `"EditorPopup".equals(place) ? "action.Jdbc.OpenEditor.Data.text"("Edit Data") : "...Data.GoTo.text"("Data")`；
  本插件自定了中文文案，不受影响。

#### 设计原则
- **跳转逻辑不要自己造**：能复用宿主插件（Database）的原生 Action 就复用，行为和用户预期一致（DDL、数据编辑器、Database 工具窗口定位都由对方维护）。
- **PSI 元素不持久化**：`.datachart` 只存 datasource + schema + tableName，右键时现场重新解析 —— 天然兼容旧文件、IDE 重启、数据源同步后元素失效。
- **PSI / DAS 访问必须在 ReadAction 中**：解析元素与 `action.update()` 都包 `ReadAction.compute`，`actionPerformed` 放外面（可能触发写操作或弹窗）。
- **可选依赖的动作要先判存在**：`ActionManager.getAction(id) == null` 表示未装 Database 插件，菜单项直接不显示。

#### 二次修复：「在 Database Explorer 中定位」报「未找到表」（2026-09-21）
#### 现象
点「跳转 → 在 Database Explorer 中定位」提示"未找到表 xxx（数据源：yyy@localhost）"。
（注意：该提示是 `TableNavigator` 的统一失败文案，**不代表一定卡在表名解析**。）

#### 根因 1：`sql.SelectInDatabaseView` 用合成 AnActionEvent 会被静默置灰
反编译 `SelectInDatabaseViewAction.update()` 的调用链：
```
update() → SelectInContextImpl.createContext(event)
         → new SelectInDatabaseView().canSelect(ctx, isStrict(dataContext))
         → presentation.setEnabledAndVisible(canSelect)
canSelect(ctx, strict) = !ctx.getVirtualFile().isDirectory() && askProvidersInner(ctx, strict) != null
askProvidersInner  = 遍历 com.intellij.database.selectInProvider 扩展的 findTarget(ctx, strict)
DbElementSelectInProvider.findTarget(ctx, strict):
     VirtualFile vf = ctx.getVirtualFile();
     if (!DbImplUtil.isDatabaseVirtualFile(vf)) return null;   // ← 关键
```
也就是说：**必须让 `SelectInContext.getVirtualFile()` 是 Database 文件系统里的虚拟文件**，否则 Provider 直接返回 null → `canSelect` false → 动作被置灰。
用 `AnActionEvent.createFromDataContext(...)` 合成的上下文（无 VIRTUAL_FILE / 无 CONTEXT_COMPONENT）根本构造不出这种 ctx，所以这条 action 路径走不通。

#### 修复 1：改走「直连」，调用该 Action 最终执行的那一步
`DatabaseView.select(PsiElement, boolean)` 是 Database 插件的 **public static** 方法，也是 `SelectInDatabaseView.selectIn` 内部真正干的活：
```java
// com.intellij.database.view.DatabaseView
public static Promise<Void> select(PsiElement element, boolean focus);
```
因此 `TableNavigator` 对 `ACTION_SELECT_IN_DATABASE_VIEW` **不再走 Action**，而是反射直接调用它，
完全绕开 `SelectInContext` / `canSelect` / `askProvidersInner` 的限制：
```java
Class<?> cls = Class.forName("com.intellij.database.view.DatabaseView");
Method m = cls.getMethod("select", PsiElement.class, boolean.class);   // 懒加载 + 缓存
m.invoke(null, element, Boolean.TRUE);
```
相应地 `isActionAvailable(ACTION_SELECT_IN_DATABASE_VIEW)` 改为判断 `DatabaseView.select` 是否可取到。

#### 修复 2：解析链路加三级兜底 + 失败日志
`DatabaseTableMetadataFetcher.resolveDbElement` 重写（每步都有回退）：
1. 数据源：`DbPsiFacade.findDataSource(name)` → 失败再 `findDataSourceLoosely`（忽略大小写、忽略/补齐 `@host` 后缀、工程内唯一数据源直接用）
2. 表：优先 `DbDataSource.getNameIndex().getObjectsByNameInsensitive(tableName)` 过滤 `DasTable`
   （**名称索引不依赖 introspection level、不受 schema 限定影响**，比 `DasUtil.getTables` 遍历更可靠）→ 回退 `DasUtil.getTables`
3. 元素：`DbDataSource.findElement(DasObject)` → 回退 `DbPsiFacade.findElement(DasObject)`；
   **必须用 `getMethod("findElement", DasObject.class)` 精确签名**（`DbDataSource` 上还有 `findElement(ObjectPath)` 重载）
4. 失败时 `LOG.warn` 打印**当前可用数据源列表**，排查"数据源改名/表被删/introspection 未完成"一眼可见

#### 修复 3：失败提示带上可操作信息
`TableNavigator.performAction` 改为返回 `Result`（成功/失败 + 原因），`KanbanBoard.navigateToTable` 直接把原因弹给用户，
不再统一说"未找到表"。

#### 环境变更（重要）
`build.gradle.kts` 的 `intellij.localPath` 已从下载版 `ideaIU-2023.2.6` 改为**本机安装版**
`/Applications/IntelliJ IDEA.app/Contents`（= IDEA 2024.1.6，build 241）。
本节涉及的全部反射目标已在 241 上重新核对通过：
`DatabaseView.select(PsiElement, boolean)`、`DbPsiFacade.{getDataSources,findDataSource,findElement}`、
`DbDataSource.{getNameIndex,findElement(DasObject)}`、`ModelNameIndex.getObjectsByNameInsensitive(String)`、
`DbElement extends PsiFileSystemItem`。

#### 新增踩坑
- **合成 `AnActionEvent` 不是万能的**：依赖 `SelectInContext`/`CONTEXT_COMPONENT`/`VIRTUAL_FILE` 的 Action（典型是各种 Select In / 定位类动作）
  在合成上下文里会被置灰或抛异常。遇到这种情况，去反编译该 Action 的 `update()/actionPerformed()`，
  找到它最终调用的**静态入口方法**直接调用，比硬凑 DataContext 稳。
- **`PSI_ELEMENT_ARRAY` 的正确归属**：`PlatformCoreDataKeys.PSI_ELEMENT_ARRAY`（`LangDataKeys extends PlatformCoreDataKeys`，通过子类访问也能解析）。
  写成 `CommonDataKeys.PSI_ELEMENT_ARRAY` 会编译报"找不到符号"。
- **诊断优先于猜测**：失败提示不要一句话盖所有分支，返回带原因的 `Result` 并打日志，一次点击就能定位问题环节。

### 45. 表头右键「查找引用」：Find Usages 在非编辑器组件里的正确接入（2026-09-21）

#### 需求
表头右键加「查找引用」，点击弹出 Find 结果窗口（等价 IDEA 图上 `Find Usages ⌥F7`）。
注意：图右键菜单里的 `Find Usages` 就是平台通用动作 id `FindUsages`，Database 插件并没有自己的实现。

#### 关键结论：2024.1 的 `FindUsagesAction` **不读 `CommonDataKeys.PSI_ELEMENT`**
反编译 `com.intellij.find.actions.FindUsagesAction` + `FindUsagesInFileAction`（app-client.jar）：

```java
// update()
boolean enabled = isEnabled(ctx);
presentation.setVisible(enabled || !ActionPlaces.isPopupPlace(place));  // ⚠️ popup 下不可用会被"隐藏"
presentation.setEnabled(enabled);

// isEnabled(ctx)
project != null
  && ctx.getData(EditorGutter.KEY) == null
  && !Boolean.TRUE.equals(ctx.getData(CommonDataKeys.EDITOR_VIRTUAL_SPACE))
  && (canFindUsages(project, ctx) || !ResolverKt.allTargets(ctx).isEmpty());

// canFindUsages(project, ctx) → 第一行就是 editor == null ? false
// allTargets(ctx) → SearchTargetVariantsDataRuleKt.targetVariants(ctx)，只认三样：
//   1) FindUsagesAction.SEARCH_TARGETS（新 API，Collection<SearchTarget>）
//   2) UsageView.USAGE_TARGETS_KEY（UsageTarget[]）
//   3) 有 EDITOR 时：TargetElementUtil.findReference(editor, caretOffset) 取光标处引用
```

即：**只往 DataContext 里塞 PSI 元素是没用的** —— 动作判定不可用，而且因为 place 是 popup，
`setVisible(false)` 会把它**直接隐藏**（连置灰都看不到）。

`actionPerformed` 的分支（交给 `ResolverKt.findShowUsages` 处理）：
- 目标数 = 1 → `target.handle(handler)` **直接查找**（等价 Alt+F7，正是我们要的）
- 目标数 > 1 → 弹 `TargetPopup` 让用户选
- 目标数 = 0 → 错误提示

#### 实现（`TableNavigator.performDatabaseAction`）
对 `ACTION_FIND_USAGES` 额外补一个 `UsageTarget`：

```java
SimpleDataContext.Builder builder = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.PSI_ELEMENT, element)
        .add(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, new PsiElement[]{element})
        .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, boardComponent);   // 弹窗定位锚点

if (ACTION_FIND_USAGES.equals(actionId)) {
    UsageTarget[] targets = ReadAction.compute(() ->
            new UsageTarget[]{ new PsiElement2UsageTargetAdapter(element, true) });  // 必须 ReadAction
    builder.add(UsageView.USAGE_TARGETS_KEY, targets);
}
// 之后照常 action.update(event) → action.actionPerformed(event)
```

执行链：`FindUsagesAction` → `PsiTargetVariant.handle(...)` → `startFindUsages(element)`（legacy 路径）
→ `FindUsagesManager` → Database 插件注册的 `findUsagesHandlerFactory`
（`com.intellij.database.psi.DbFindUsagesHandlerFactory`）→ 结果落在 Find 工具窗口，
SQL / XML 里引用该表名的位置都会被搜出来。

`CONTEXT_COMPONENT` 由 `KanbanBoard.navigateToTable` 传 `this`：`actionPerformed` 会调
`JBPopupFactory.guessBestPopupLocation(ctx)`，没有锚点组件时弹窗定位不可靠。

#### 菜单
- 「查找引用」放**顶层**（与 IDEA 图右键菜单一致：Find Usages 在 Go To 之前），
  「跳转」子菜单紧随其后，两者共用同一个 `NavigateAction` 回调，只是 actionId 不同。
- **不要在菜单项上显示快捷键提示**（2026-09-21 用户反馈后去掉）：曾用
  `ActionManager.getKeyboardShortcut(id)` + `JMenuItem.setAccelerator(...)` 在菜单项右侧显示
  ⌥F7 / ⌘B / F4 等提示，但这些键位在画板里**并不生效**（弹窗菜单只负责展示，不注册全局快捷键），
  显示出来反而误导用户。等真正注册了快捷键（`KanbanBoard` 的 `registerKeyboardAction`）再加提示。

#### 踩坑
- **`PsiElement2UsageTargetAdapter(PsiElement)` 在 2024.1 已 `@Deprecated(forRemoval=true)`**，编译会报
  "已过时, 且标记为待删除"；改用 `PsiElement2UsageTargetAdapter(element, boolean update)`（内部
  `new FindUsagesOptions(project)` + update 标志），语义等价。
- **popup place 下不可用的动作是"隐藏"而不是"置灰"**：`setVisible(enabled || !ActionPlaces.isPopupPlace(place))`。
  所以"菜单项不见了"要先怀疑动作自己判定不可用，而不是没注册。
- **`UsageTarget` 必须在 ReadAction 里构造**（内部是 `SmartPsiElementPointer`）。
- 用 `ActionPlaces.POPUP` 时 `FindUsagesAction` 的文案/可见性逻辑才符合预期。

### 46. 右键菜单 hover 整行变粉（macOS 系统强调色）+ 菜单配色统一自绘（2026-09-21）

#### 现象
右键菜单里**被 hover / 子菜单展开的那一行整行变成品红/粉色**（含右侧箭头区）。
先出现在「跳转」母项上，改完后又在「同步表结构」等**普通菜单项**上复现 —— 说明不是某一个组件的问题，
而是整个菜单配色链路的问题。

#### 根因（反编译字节码确认，两次修正后才定位）
Swing 的 `BasicMenuItemUI.paint` 把 **UI 字段 `selectionBackground`** 传给 `paintBackground`：

```java
// JDK: javax.swing.plaf.basic.BasicMenuItemUI
public void paint(Graphics g, JComponent c) {
    paintMenuItem(g, c, checkIcon, arrowIcon, selectionBackground, selectionForeground, gap);
}
protected void paintBackground(Graphics g, JMenuItem item, Color bgColor) {
    if (item.isOpaque()) { /* armed/selected → fillRect(bgColor) */ }
    else if (model.isArmed() || (item instanceof JMenu && model.isSelected())) {
        g.setColor(bgColor); g.fillRect(...);      // ★ opaque=false 也会填！
    }
}
```

而 IntelliJ 自己的菜单 UI 在 `installDefaults()` 里**直接覆盖**了这个字段：

```java
// com.intellij.ui.plaf.beg.BegMenuItemUI   （JMenuItem 用的 UI）
// com.intellij.ui.plaf.beg.IdeaMenuUI     （JMenu 用的 UI，继承 BasicMenuUI）
selectionBackground = JBColor.namedColor("Menu.selectionBackground",
                                         UIUtil.getListSelectionBackground(true));
// IdeaMenuUI 里的 hover 填充更加不受 opaque 控制：
private void fillBackground(...) {
    if (c.isOpaque()) { /* 仅填菜单底色 */ }
    if (model.isArmed() || model.isSelected()) paintHover(g, c, menu, arrowIcon);  // ★ 无条件
}
protected final void paintHover(...) { g.setColor(selectionBackground); ... }
```

**两个致命点**（解释了为什么之前所有 hover 配色修补都无效）：
1. `JBColor.namedColor(...)` 的结果是**全局缓存**的（首次解析后固定），所以
   `UIManager.put("MenuItem.selectionBackground", ...)` 和 `putClientProperty(...)` 都读不到；
2. `IdeaMenuUI` 的 hover 填充**不受 `isOpaque()` 控制**，所以"关掉 opaque"这招对 `JMenu` 完全无效
   （对 `BegMenuItemUI` 有效，但普通菜单项的粉是另一条路径）。

→ 结论：**只要还用 L&F 画菜单，就压不住它解析出来的颜色**（macOS 上是系统强调色，用户设粉色就整行粉）。

#### 修复：菜单项全部自绘，不再调用 `super.paintComponent`
`BoardContextMenu` 新增/改造三个自绘类，`paintComponent` 内**不调用 `super`**（L&F 的绘制因此完全不发生）：

| 类 | 用途 |
| --- | --- |
| `FlatMenuItem extends JMenuItem` | 普通菜单项（复制/同步/查找引用/删除表…） |
| `FlatMenu extends JMenu` | 子菜单母项（跳转 / 关系类型），多画一个右侧箭头 |
| `FlatCheckBoxMenuItem extends JCheckBoxMenuItem` | 关系类型的勾选项（原有，已改为同一套配色） |

绘制逻辑抽成两个静态方法共用，保证三类项的文字左边界、行高、配色完全一致：

```java
/** 底色 + 文字（+ 可选箭头）；highlighted 决定文字颜色 */
private static void paintMenuRow(Graphics g, JMenuItem row, boolean highlighted, boolean withArrow) {
    // 1. 底色始终 = row.getBackground()（菜单底色），绝不使用 L&F 的选中色
    // 2. 文字：禁用态灰色 > hover 蓝色 > 主题前景色
    // 3. withArrow 时在右侧自绘一个小三角
}
private static Dimension menuRowPreferredSize(JMenuItem row, boolean withArrow) { ... }
```

统一常量（避免文字左边界对不齐）：
`ROW_TEXT_LEFT=12` / `ROW_TEXT_RIGHT=12` / `ROW_VERTICAL_PADDING=4` /
`ARROW_WIDTH=4` / `ARROW_HEIGHT=8` / `ARROW_RIGHT=10` / `ARROW_GAP=16`。

配色全部 `JBColor` 双态（深色主题自动适配）：
- 悬停文字：`MENU_HOVER_FOREGROUND = JBColor(#2470B0, #4A90E2)`
- 禁用文字：`MENU_DISABLED_FOREGROUND = JBColor(#9E9E9E, #808080)`
- 危险操作：`DELETE_FOREGROUND = JBColor(#C62828, #FF6B6B)`（原有）

#### 关键经验（血泪）
- **macOS 上"整行粉色"= 系统强调色**，不是项目自己的颜色；改颜色前先确认这个颜色是谁画的。
- **IntelliJ 菜单 UI 会覆盖 Swing 的 `selectionBackground/selectionForeground`**：
  `UIManager.put(...)` 与 client property 都**不可靠**（`JBColor.namedColor` 全局缓存 + `installDefaults` 覆盖）。
  想彻底控制菜单配色 → **自绘**（`paintComponent` 不调 `super`）。
- **`setOpaque(false)` 不是万能开关**：JDK 的 `BasicMenuItemUI.paintBackground` 有
  `else if (armed||selected)` 分支照样填色，IntelliJ 的 `IdeaMenuUI.fillBackground` 更是完全无视 opaque。
  —— 这一点是本次第一轮修复失败的真正原因，值得记住。
- 自绘后**必须自己重写 `getPreferredSize()`**：父类按 L&F 的 checkIcon / accelerator 计算，
  结果不再可信（与 `FlatCheckBoxMenuItem` 当年在 Windows 上被截断是同一个坑）。
- **`patchMenuUiDefaults()` 已删除（2026-09-21）**：它往全局 `UIManager` 默认值表写
  `MenuItem.selectionForeground` / `MenuItem.selectionBackground` 等 key，对自绘项已经完全无效，
  却**可能影响 IDEA 自身菜单的配色**（全局副作用）。既然自绘已接管一切，就没有理由再动全局默认值。
  ⇒ **原则：插件不要写全局 `UIManager` 默认值**，要什么样式就在自己的组件里自绘；否则
  "改了别人的界面" 这种副作用很难排查。

### 47. 国际化（i18n）规范（2026-09-21）

#### 现状与资源包
- `src/main/resources/messages/` 下原有的 `DataToolsBoundle_*.properties` 是**遗留文件**：
  代码里 0 引用、key 与本插件无关（数据库连接 / Excel 导出 / 代码模板），且**没有 base 文件**
  （只有 `_zh` 和 `_en`）→ `ResourceBundle.getBundle` 在非中英文 locale 下会抛
  `MissingResourceException`。新的代码不要再用它。
- 本插件自己的资源包（沿用同一个 `messages/` 目录）：
  - `messages/DataChartBoundle.properties` —— **默认语言（英文），必需**
  - `messages/DataChartBoundle_zh.properties` —— 简体中文

#### 用法
```java
import com.wd.i18n.DataChartBundle;

JMenuItem item = new FlatMenuItem(DataChartBundle.message("DataChart.menu.find.usages"));
```
`DataChartBundle extends DynamicBundle`（不是裸 `ResourceBundle`）：IDE 切换 Language Pack 后
无需重启即可取到新语言文案。路径 `messages.DataChartBoundle` 是**相对 classpath 根**的
（`AbstractBundle(String)` 只是把路径原样存下，最终交给
`ResourceBundle.getBundle(path, locale, classloader)`）。

#### 规范（重要）
1. **Java 代码里不写死用户可见文案**：菜单项 / tooltip / 通知 / 对话框 / 按钮文字一律走
   `DataChartBundle.message(key)`。日志（`LOG.warn`）不算用户可见文案，可以直接写中文。
2. **key 命名**：`DataChart.<模块>.<语义>`，如 `DataChart.menu.find.usages`。
   新增 key 时**两个文件都要加**，否则英文环境抛 `MissingResourceException`。
3. **base 文件（英文）必须存在**，否则非 zh/en 的 locale 直接崩。
4. **中文写成 `\uXXXX` 转义**（`Properties` 是 ISO-8859-1 编码）。用 `native2ascii` 或脚本转换，别手敲。

#### 踩坑
- **Java 注释里也不能出现 `\uXXXX`**：Java 词法器在解析之前就扫描整个文件做 Unicode 转义，
  注释 / javadoc 里写 `{@code \uXXXX}` 会直接报 `错误: 非法的 Unicode 转义`。
  文档里要表达这种形式时，用「反斜杠 + u + 4 位十六进制」这类描述性写法。
- **验证方法**（比在 IDE 里试快得多，本次就是这么验的）：
  ```bash
  jshell -q --class-path src/main/resources
  # 中文：ResourceBundle.getBundle("messages.DataChartBoundle", new Locale("zh","CN"))
  #          .getString("DataChart.menu.find.usages")  → 查找引用
  # 英文：先 Locale.setDefault(Locale.ENGLISH) 再取              → Find References
  # 再比对两个文件的 keySet 是否一致（少了 key，英文环境会抛异常）
  ```
- Java 的 **default locale 回退**：机器默认 locale 是 zh_CN 时，
  `getBundle(name, Locale.ENGLISH)` 也会返回中文包（先试 `_en`，再试默认 locale 链）—— 这不是 bug。

#### 全量迁移结果（2026-09-21）
已把项目里**所有用户可见文案**迁到资源包（71 个 key，base/zh 完全对齐），涉及 7 个文件：

| 文件 | 迁移内容 |
| --- | --- |
| `BoardContextMenu` | 表头菜单 / 跳转子菜单 / 连线菜单（18 个 key，最早迁移） |
| `DataChartView` | 工具栏 tooltip、导出 PDF/图片的对话框标题 + 过滤器 + 进度标题 + 成功/失败通知、打开文件失败、搜索状态 tooltip |
| `KanbanBoard` | 卡片 tooltip、删除表二次确认（单选/多选）、同步失败/成功通知、跳转失败标题 |
| `KanbanCard` | 画布上的 `(未命名)` / `类型: x` / `... 共 N 列` |
| `DataChartEditor` | 加载中提示、保存失败（写文件 / 序列化） |
| `TableNavigator` | 全部 `Result.fail(...)` 文案（跳转失败原因） |
| `TableDropHandler` | 拖拽落点提示「拖放到看板」 |
| `Donation` | 捐赠对话框标题 + 两行说明 |

#### 什么**不**需要迁移
- **日志**（`LOG.warn/info/debug`、`printStackTrace` 替代品）：给开发者看的，保持中文即可，不必进资源包。
- **注释 / javadoc**：不是运行时文案。
- 数据本身（表名、列名、注释）：来自数据库，不算插件文案。

#### 迁移的机械做法（可复用）
写一次性 Python 脚本做批量替换，关键是**每条替换都断言命中次数**，任一条对不上就整体不写入，
避免"静默改错半行"。脚本还顺手做了三件事：
1. 自动补 `import com.wd.i18n.DataChartBundle;`（插到 import 块的字母序位置）；
2. 重建两个属性文件（非 ASCII 与换行统一转义）；
3. 输出改动清单。

#### 迁移后的自动校验（都做过，全绿）
```bash
# ① 文案出口反向扫描：所有 NotificationUtil.* / setToolTipText / setDialogTitle /
#    setDropPossible / setTitle / Result.fail 的参数里不应再有中文字面量
# ② key 完整性：代码引用的 key 集合 == base 文件 key 集合 == zh 文件 key 集合
# ③ jshell 实跑：带 {0} 占位符的中英文都要能正确格式化（含 \n 保留）
```
③ 尤其值得做 —— `AbstractBundle.getMessage` 走 `MessageFormat`，一旦文案里出现**单引号**就会被当成
转义引号；另外**占位符参数不要直接传 `int`**（`MessageFormat` 会按本地化数字格式加千分位），
统一 `String.valueOf(n)` 再传。

