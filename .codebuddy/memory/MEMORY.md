# MEMORY（DataChart 项目长期记忆）

> 详细规范看 `DEVELOPMENT_GUIDE.md`，按需查阅下面各条指向的章节。

## 核心架构
- IntelliJ IDEA 插件，自定义 `.datachart` 文件类型（`com.wd.editor.*`）
- 编辑器是**双 Tab**（都由插件提供）：图形 "Board"（默认激活）+ 自研 JSON 高亮 "Text"（原始 JSON）；
  `DataChartEditorProvider`(HIDE_DEFAULT_EDITOR) + `DataChartTextEditorProvider`(NONE)，
  靠 `EditorFileSync` / `EditorSaveAllHook` 跨 Tab 同步，见第 11 条
- 画板核心：`KanbanBoard`（编排层）+ 工具类（`BoardPersistence` / `BoardExportUtil` / `BoardContextMenu` / `BoardSnapHelper` / `BoardSearchModel` / `BoardViewport` / `NotificationUtil`）
- 数据模型：`com.wd.model`（ChartData / TableCardModel / ChartRelation / RelationType）
- DB 元信息反射：`com.wd.db.DatabaseTableMetadataFetcher`（全反射访问 `com.intellij.database.*`）
- 表跳转：`com.wd.db.TableNavigator`（复用 Database 原生能力，见"第 10 条"）

## 项目约定
- **不要写死用户可见文案（用户明确要求）**：菜单项 / tooltip / 通知 / 对话框一律走
  `com.wd.i18n.DataChartBundle.message(key, params...)`；日志可以写中文。
  - 资源包：`resources/messages/DataChartBoundle.properties`（base 英文，**必需**）
    + `DataChartBoundle_zh.properties`（中文，非 ASCII 用 `\uXXXX` 转义）。key 命名 `DataChart.<模块>.<语义>`，
    新增 key 两个文件都要加。
  - `DataChartBundle.message(...)` **自己解析** `ResourceBundle`（路径 `"messages.DataChartBoundle"`，相对 classpath 根），
    **不用** `DynamicBundle` —— 它按 IDE locale 解析且平台缓存结果，会让插件自己的语言开关失效；
    `Control.getFallbackLocale` 返回 **null** 掐掉"回退 JVM 默认 locale"（否则中文系统上请求英文也会拿到 `_zh`）。
  - **语言按钮显示"将切到的语言"（用户明确要求）**：中文界面显示 `EN`、英文界面显示 `中文`（不是当前语言！），
    用该语言自己怎么写来标注、**不进资源包**；tooltip 同样是目标语义（"切换到英文/中文"）。
  - **插件语言独立于 IDE**：工具栏 `EN / 中文` 按钮 → `DataChartLanguage.toggle()`（存 `PropertiesComponent`，默认英文）
    → `DataChartBundle.invalidate()` + 广播 `DataChartLanguage.CHANGED`（`Topic<Runnable>`）。
    ⚠️ **凡"建好后不再更新"的用户可见文案都必须订阅 `CHANGED` 刷新**（工具栏按钮、编辑器内部页签）；
    每次现建的（右键菜单 / 通知 / 对话框 / 画布绘制时取文案）不用管。漏订阅的表现就是"切了英文这里还是中文"。
  - **2026-09-21 已完成全量迁移**：73 个 key（71 代码 + 2 plugin.xml），覆盖 BoardContextMenu /
    DataChartView / KanbanBoard / KanbanCard / DataChartEditor / TableNavigator / TableDropHandler / Donation；
    日志、注释、数据库来的数据（表名/列名）保持中文不动。
  - **plugin.xml 也本地化**：`<resource-bundle>messages.DataChartBoundle</resource-bundle>` +
    `<description>%DataChart.plugin.description</description>` +
    `<notificationGroup ... bundle="messages.DataChartBoundle" key="DataChart.notification.group"/>`。
    遗留的 `DataToolsBoundle_*.properties` 已删除。
  - ⚠️ MessageFormat 陷阱：文案里的**单引号**会被当转义引号；**占位符参数别直接传 `int`**（会加千分位），
    统一 `String.valueOf(n)`。详见 DEVELOPMENT_GUIDE 第 47 节。
  - ⚠️ `resources/messages/DataToolsBoundle_*.properties` 是遗留文件（0 引用、无 base 文件），新代码别用。
  - ⚠️ **Java 注释里也不能出现 `\uXXXX`**（词法器先做 Unicode 转义扫描 → 编译报"非法的 Unicode 转义"）。
- 菜单/弹窗样式：插件自己自绘（见"关键设计决策"第 10 条），**不要写全局 `UIManager` 默认值**。

## 构建环境（2026-09-21 更新）
- `build.gradle.kts` 的 `intellij.localPath` = `/Applications/IntelliJ IDEA.app/Contents`，
  即 **IntelliJ IDEA 2024.1.6 / build 241.19072.14（IU）**（不再是下载版 2023.2.6）。
- 核对 Database 插件 API 请用：`/Applications/IntelliJ IDEA.app/Contents/plugins/DatabaseTools/lib/database-plugin.jar`
- 编译命令：`./gradlew compileJava`（Java 17）；完整打包：`./gradlew clean buildPlugin`
- ⚠️ `:instrumentCode` 报 `taskdef class com.intellij.ant.InstrumentIdeaExtensions cannot be found` 时
  **先 `./gradlew clean buildPlugin`**（本次就是陈旧构建状态导致的假报错，clean 后成功）。
  `runIde` 也依赖 `instrumentCode`；**不要关闭 instrumentCode**（`.form` 绑定代码靠它生成）。

## 关键设计决策

### 1~6 基础约定
- **卡片 ID 用 UUID**：拖入重复表时 `TableInfo.setId(UUID.randomUUID())` 覆盖 `schema.table` 拼接；加载旧文件时检测重复 id 补 UUID（35 节）。
- **连线列定位用列名**：`ChartRelation.from/toColumnName` 存列名，删除列后仍准确；`BoardPersistence.resolveRowIndex` 优先列名 → 回退 index。**列 index 在表结构变化后必须用列名重定位**。
- **卡片宽度固定 280**：注释过长按宽度截断 + `...`（22 节）。
- **位置保留用户拖动结果**：加载时保留 `model.x/y` 只修正尺寸；新建卡片从 (0,0) 平铺，4 张/行（26 节）。
- **默认关系类型**：`RelationType.ONE_TO_ONE` 作默认，旧文件 UNKNOWN 也回退（29 节）。
- **扩展名常量**：`DataToolsFileType.EXTENSION = "datachart"`，不要硬编码（9 节）。

### 7~9 布局与尺寸（共同教训：同步/ 零值）
- **视口状态不在 JSON 中持久化**：每次打开都重置 viewport，靠 `focusView` 居中；IDE 重启自动重开时 `getComponent` 同步链路触发 `loadFromJson`，panel 尚未完成布局（37 节）。
- **同步链路调 Swing 必须做零值防御**：`getComponent` → `loadFromJson` → `focusView` 全同步，此时 `getVisibleRect()/getSize()` 可能为 0。**`invokeLater` 不等于"等布局完成"**，`focusOn` 类算法必须校验 viewWidth。
- **卡片高度公式三处共享静态常量**（39 节）：`KanbanBoard.addTableCard`（新建）+ `loadFromChartData`（加载）+ `KanbanCard.updateTableInfo`（同步）统一用 `KanbanCard.HEADER_HEIGHT(28)`/`ROW_HEIGHT(18)`/`PADDING(10)`，`height = HEADER + 列数×ROW + PADDING`；历史上 `TABLE_CARD_BASE_HEIGHT` 写死 60 与 `drawTableCard` 实际 38 不对齐，导致底部 ~22px 空白行。

### 其他审查结论
- `Connection.sourceRow/targetRow` 已从 `final` 放开（`setSourceRow/setTargetRow`），用于 `syncTableStructure` 后按列名重定位连线行。
- **待确认（未修）**：`DataChartEditor.dispose()` 关闭时 `if (modified) saveDocument()` 强制落盘，可能绕过 IDE 未保存确认。

### 10. 借用 Database 插件原生 Action 实现"跳转"（2026-09-21 已实现并编译通过）
- **原理**：ER 图右键的 `Go To > Data / DDL / Database Explorer` 不是自研跳转，而是把现成 Action 用 `ActionManager.getAction(id)` 组装成 ActionGroup。
- **可复用 action id**：`Jdbc.OpenEditor.Data`（Edit Data）、`Jdbc.OpenEditor.DDL`、`sql.SelectInDatabaseView`、`FindUsages`、`$Copy`、`CopyReference`。
- 它们只认 `CommonDataKeys.PSI_ELEMENT`，故自建 `DataContext`：`AnActionEvent.createFromDataContext(ActionPlaces.POPUP, templatePresentation.clone(), SimpleDataContext.builder().add(CommonDataKeys.PROJECT, p).add(CommonDataKeys.PSI_ELEMENT, elem).build())` → `action.update(event)` 判 enable → `action.actionPerformed(event)`。
- **PSI 解析链路**（反射）：`DbPsiFacade.getInstance(project)` → `findDataSource(name)` → `DasUtil.getTables(ds)` 匹配同名 → `DbPsiFacade.findElement(DasObject)` → `DbElement`（`extends PsiFileSystemItem`）。**PSI 元素不要持久化**（重启/同步后失效），右键时用 `TableInfo` 的 datasource+schema+tableName 现场重解析。
- 坑：`DbDiagrams.SourceActionsGroup.GoTo` 的 `update()` 要求 `DiagramDataKeys.BUILDER` + 选中节点，否则整组禁用 → 必须自建 `DefaultActionGroup`；`ActionPlaces` 无 `CONTEXT_MENU`（用 `POPUP`）。
- 已实现（2026-09-21）：`DatabaseTableMetadataFetcher.resolveDbElement`（反射 `DbPsiFacade.findElement(DasObject)`）+ 新类 `com.wd.db.TableNavigator.performAction/isActionAvailable` + `BoardContextMenu.buildHeaderMenu` 第 4 参 `NavigateAction`（生成「跳转」JMenu 子菜单）+ `KanbanBoard.navigateToTable`。`./gradlew compileJava` 通过。
- **实测坑**：`PSI_ELEMENT_ARRAY` 归属 `PlatformCoreDataKeys.PSI_ELEMENT_ARRAY`，写成 `CommonDataKeys.*` 编译报找不到符号。
- **selector 类 Action 不可用**：`sql.SelectInDatabaseView.update()` 要求 `SelectInContext.getVirtualFile()` 是 Database 虚拟文件，合成事件必被置灰 → 改为反射直调 `com.intellij.database.view.DatabaseView.select(PsiElement, boolean)`（静态 public）。
- `resolveDbElement` 三级兜底：数据源松散匹配（忽略大小写/`@host`）→ `DbDataSource.getNameIndex().getObjectsByNameInsensitive` → `DbDataSource.findElement(DasObject)`/`DbPsiFacade.findElement`（**签名必须精确**）。全部反射目标已在 build 241 复核；`performAction` 返回带原因的 `Result`。
- **「查找用法」= 平台 `FindUsages` 动作**（Database 插件无实现）。2024.1 的 `FindUsagesAction` **不读 `PSI_ELEMENT`**，只认 `UsageView.USAGE_TARGETS_KEY` / `FindUsagesAction.SEARCH_TARGETS` / 编辑器光标 → 必须补 `UsageView.USAGE_TARGETS_KEY = { new PsiElement2UsageTargetAdapter(element, true) }`（`ReadAction` 内构造）+ `CONTEXT_COMPONENT` 作锚点；恰好 1 个目标才直接查找（等价 Alt+F7）。
- **popup place 下不可用动作是"隐藏"而非"置灰"**（`FindUsagesInFileAction.updateFindUsagesAction`）。
- **菜单项不要显示快捷键提示**（用户要求）：`JMenuItem.setAccelerator` 在弹窗菜单只展示不生效，会误导用户。
- **菜单 hover 整行变粉（macOS 强调色）→ 必须完全自绘**：IntelliJ 的 `BegMenuItemUI`/`IdeaMenuUI` 在 `installDefaults()` 用 `JBColor.namedColor(...)` 覆盖 `selectionBackground` 且**全局缓存**，`UIManager.put` 与 client property 全部无效，`IdeaMenuUI.fillBackground()` 的 hover 填充也不受 `isOpaque()` 控制。**唯一可靠解**：`BoardContextMenu` 的 `FlatMenuItem`/`FlatMenu`/`FlatCheckBoxMenuItem` 自绘（`paintComponent` 不调 `super`），共用 `paintMenuRow`/`menuRowPreferredSize`，配色只用 `JBColor`，并自己重写 `getPreferredSize()`（46 节）。
- 调研与 API 清单：memory/2026-09-21.md；规范见 DEVELOPMENT_GUIDE 第 44、45 节

### 11. `.datachart` 编辑器：Board（图形）+ Text（JSON 高亮）双页签（2026-09-24 定稿）
- **用户偏好（明确要求）**：**不要**改 `.datachart` 的 fileType 语言（保留自定义语言类 `com.wd.editor.DataChart`、Board 行为不变）。
  曾用 `DataChart extends JsonLanguage` 借 JSON 能力 → **实测导致项目视图不显示 .datachart，已回退**。
- **定稿：编辑器内部页签（JBTabs），IDE 层只有一个 tab**
  - `DataChartEditorProvider` policy = `HIDE_OTHER_EDITORS`（隐藏平台自带文本编辑器等；IDE 层只剩我们一个 tab，tab 名用文件名）；
  - `DataChartEditor` 内部 `JBTabsFactory.createTabs(project, this)`：**Board 页**（`DataChartView`，默认选中）+ **Text 页**（`DataChartJsonPanel`）；
  - **Text 页高亮为什么完美**：内容放在 `new LightVirtualFile(name + ".json", JsonFileType.INSTANCE, text)` 里 →
    编辑器的**文件就是 JSON 语言** → JSON 全套机制生效（token 高亮 + PSI 层 `JsonRainbowVisitor` 的**属性键紫色** +
    `Ctrl+Alt+L` 格式化 + 校验 + Structure View）；
  - 保存：`saveDocument()` 保存**当前页**；**切换页签先保存离开的那一页**；平台 Cmd+S 由 `EditorSaveAllHook` 补位；
    外部改动由 `EditorFileSync` 触发重载；打开时紧凑 JSON 仍由 `prettifyFileIfNeeded` 自动重排。
- **为什么必须下沉到编辑器内部**：IDE 层面的多编辑器 Tab 顺序插件控制不了 —— 反编译确认
  `PLACE_BEFORE/AFTER_DEFAULT_EDITOR` **平台没有任何代码读取**（全 jar 只有 `DiffPatchFileEditorProvider` /
  `PerspectiveFileEditorProvider` 声明使用），EP 的 `order="first"/"last"` 实测无效，
  平台 text provider 自己 `order="first"` 且 provider 列表走协程（`filterableLazySequence`）。
- **其它反编译结论**：`HIDE_DEFAULT_EDITOR` 只移除 `DefaultPlatformFileEditorProvider`（`postProcessResult` 的谓词），
  挡不住 `PsiAwareTextEditorProvider`（要精准抑制用 `FileEditorProviderSuppressor`，带 project/file 参数，⚠️ 全局 EP 需自己过滤）；
  `JSON_PROPERTY_KEY` **不在** `MyHighlighter` 的 token 映射表里（`IDENTIFIER→JSON_IDENTIFIER`、`DOUBLE_QUOTED_STRING→JSON_STRING`）
  → 键色只能在 PSI 层拿到（自定义 lexer 改 token 类型没用）。
- **保留的机制**：`EditorFileSync`（VFS 监听 + `writeContent` 双写 VFS/Document；自触发事件**只能用
  `event.getRequestor() == owner`** 识别，事件异步派发、saving/stamp 不可靠；写盘期间 `UndoUtil.disableUndoFor` 隔离撤销栈）、
  `EditorSaveAllHook`（`beforeAllDocumentsSaving` 补位保存画布）、
  `ChartJsonUtil`（缩进跟随 IDE 的 JSON 代码风格 + `"k": v` + `prettifyText` 必须带 `Feature.OrderedField`；打开时自动重排、幂等）。
- **平台自带的文本编辑器必须用 suppressor 抑制**：`<fileEditorProviderSuppressor implementation="com.wd.editor.DataChartTextEditorSuppressor"/>`
  （`isSuppressed(Project, VirtualFile, FileEditorProvider)` 带文件参数，只对 .datachart 抑制 `instanceof TextEditorProvider`；
  ⚠️ 该 EP 全局注册，实现里必须自己按文件类型过滤）。否则 IDE 层会多一个纯文本 "Text" tab（按自定义语言 `dataChart` 渲染、无高亮），
  极易被误判成"我们的 Text 页没高亮"。`HIDE_DEFAULT_EDITOR` / `HIDE_OTHER_EDITORS` 都挡不住它。
- **内部页签贴底部**（用户要求）：`tabs.getPresentation().setTabsPosition(JBTabsPosition.bottom)`
  —— 默认 top 会与 IDE 自己的 Tab 栏叠在一起。⚠️ `setTabsPosition` 在 `JBTabsPresentation` 上（不是 `JBTabs`）；
  `JBTabsPosition` 枚举常量是**小写** `top/left/bottom/right`；平台只有四边、无"靠右"选项，底部时页签自左侧排列。
- 页签名走 i18n：`DataChart.editor.tab.board`（Board / 看板）、`DataChart.editor.tab.text`（Text / 文本）；
  **切语言后要刷新**（页签文字建好就固定）：`DataChartEditor` 订阅 `DataChartLanguage.CHANGED` → `applyTabTexts()` 重设
  两个 `TabInfo` + `revalidate()/repaint()`（`connect(this)` 随 dispose 释放）。
- 教训：`loading` 标志要在 `loadFromJson` **之后**解除，否则重建看板的回调会把刚打开的文件标记成已修改。
- 详见 DEVELOPMENT_GUIDE 第 50 节「✅✅ 最终方案：编辑器内部页签」。
