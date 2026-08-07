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

