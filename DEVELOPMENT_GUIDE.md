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
- Database 插件仅 Ultimate 版提供，集成时必须用 `<depends optional="true">com.intellij.database</depends>`
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

### 14. ER 图交互规范（ER-style interaction）

#### 连线绘制
- 默认线宽 `Connection.DEFAULT_STROKE_WIDTH = 2.4f`（从 1.6 加粗，便于辨识）
- 选中连线时 `KanbanBoard.paintComponent` 临时把 `strokeWidth` 改成 2.5f 强调
- 端点形状由 `RelationType` 决定：
  - `ONE_TO_ONE` 两端单竖线（"1" 标识）
  - `ONE_TO_MANY` 源端单竖线、目标端三叉（crow's foot）
  - `MANY_TO_ONE` 反之
  - `MANY_TO_MANY` 两端都三叉
  - `UNKNOWN` 不画端点形状，保持简洁
- 形状大小随线宽缩放（`Math.max(8.0, strokeWidth * 4.5)`），描边略细于线本身

#### 起点背景色同步整条连线（需求 1）
- `Connection.resolveLineColor()` 优先用 `source.getHighlightedColorForRow(sourceRow)`，没有再用 palette 颜色
- 这样当起点行被高亮（用户选中 / 关联列 / 连线占用），整条线统一为该色

#### 关联列高亮（需求 2）
- `KanbanBoard.activeHighlightCard / activeHighlightRow`：当前用户左键选中的列
- `KanbanBoard.refreshRelatedRows()` 在每次重绘前重新计算 `relatedRowKeys`（cardId#row 形式）
- 关联列高亮色 `RELATED_ROW_COLOR = #FD9933`，与用户选中色 `#FE9933` 略区分
- 切换到其他列时，`toggleRowSelection` 会清空旧选中 → 自动恢复

#### 行高亮临时通道（让 Connection 拿到激活色）
- `KanbanCard.activeRowColors`：每帧重绘前由 `KanbanBoard` 写入
- `getHighlightedColorForRow()` 优先级：用户选中 > 临时激活色 > 连线占用色
- 用途：让连线的 `source.getHighlightedColorForRow()` 能感知"激活列"颜色

#### 复制到剪贴板（需求 3、4）
- 使用 `java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()` + `StringSelection`
- 不使用 IntelliJ 的 `CopyPasteManager`，因为本组件不依赖 IDE 编辑器上下文
- 表头右键统一弹"复制表名 / 复制注释"两个菜单项（不再分左右半）
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
- 点击 → `KanbanBoard.focusView()`：**保留当前 zoom**，计算所有卡片 bounds 合并矩形的中心，平移 transform 让其落到视口中心
- 与 `resetView()` 的区别：reset 是 zoom=100% + transform 清零（完全重置）；focus 是"画板内容回到视口"，zoom 不变
- 无卡片时不改变视图

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
- **方法**：`buildConnectionMenu(conn, onRepaint, onNotifyChanged, onRemove)` / `buildHeaderMenu(info)` / `buildColumnMenu(col)` / `copyToClipboard(text)`
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
