# MEMORY（DataChart 项目长期记忆）

## 核心架构
- IntelliJ IDEA 插件，自定义 `.datachart` 文件类型（`com.wd.editor.*`）
- 画板核心：`KanbanBoard`（编排层）+ 工具类（`BoardPersistence` / `BoardExportUtil` / `BoardContextMenu` / `BoardSnapHelper` / `BoardSearchModel` / `BoardViewport` / `NotificationUtil`）
- 数据模型：`com.wd.model`（ChartData / TableCardModel / ChartRelation / RelationType）
- DB 元信息反射：`com.wd.db.DatabaseTableMetadataFetcher`（全反射访问 `com.intellij.database.*`）

## 关键设计决策

### 1. 卡片 ID 必须是 UUID（2026-08-07）
- 拖入重复表时 `TableInfo.setId(UUID.randomUUID().toString())` 覆盖默认 `schema.table` 拼接
- 加载旧 .datachart 时 `KanbanBoard.loadFromChartData` 检测重复 id 并补 UUID
- 详细：DEVELOPMENT_GUIDE.md 第 35 节 / memory/2026-08-07.md

### 2. 连线列定位用列名（2026-08-03）
- `ChartRelation.fromColumnName/toColumnName` 存列名，删除列后仍准确
- `BoardPersistence.resolveRowIndex` 优先按列名 → 回退列 index

### 3. 卡片宽度固定 280（2026-08-01，22 节）
- 所有表统一宽度，注释过长按宽度截断 + `...`

### 4. 卡片位置保留用户拖动结果（2026-08-01，26 节）
- 加载时保留 `model.x/y`，只修正尺寸
- 新建卡片从 (0, 0) 平铺，4 张/行

### 5. 默认关系类型一对一（2026-08-03，29 节）
- `RelationType.ONE_TO_ONE` 替代 UNKNOWN 作默认
- 旧文件加载时 UNKNOWN 也回退到 ONE_TO_ONE

### 6. 文件扩展名统一常量（DEVELOPMENT_GUIDE 第 9 条）
- `DataToolsFileType.EXTENSION = "datachart"`，不要硬编码

## 审查结论（2026-08-07 全项目）
### Connection.sourceRow/targetRow 可重定位
- 已从 `final` 放开为可变，新增 `setSourceRow/setTargetRow`
- 用途：`syncTableStructure` 同步表结构后用列名重新定位连线行，防止列 index 错位
- 教训：**所有引用"列 index"的地方在表结构变化后都要用列名重定位**

### 已知待确认项（未修）
- `DataChartEditor.dispose()` 关闭时 `if (modified) saveDocument()` 强制落盘，可能绕过 IDE 未保存确认

### 7. 视口状态不在 JSON 中持久化（2026-08-07 已知）
- 每次打开 .datachart 都重置 viewport，依赖 `focusView` 把卡片居中
- IDE 重启自动重开时 `getComponent` 同步链路触发 `loadFromJson`，panel 还没真正完成布局
- 修复见 DEVELOPMENT_GUIDE 第 37 节 / memory/2026-08-07.md
- 教训：**`invokeLater` 不等于"等组件布局完成"**；**focusOn 类算法必须 viewWidth 零值校验**

### 8. 关键设计陷阱：同步链路调 Swing 方法
- `FileEditor.getComponent` 同步链路 → `ensureInitialized` → `loadFromJson` → `focusView` 都同步执行
- 但 IDE 此时还没把 panel 加入可见容器，`getVisibleRect()` 返回 0
- 所有依赖 `getVisibleRect`/`getSize` 的方法必须有零值防御或异步等待布局完成

### 9. 卡片高度公式三处必须共享静态常量（2026-08-20）
- 三处计算位置：`KanbanBoard.addTableCard`（新建）+ `KanbanBoard.loadFromChartData`（加载）+ `KanbanCard.updateTableInfo`（同步表结构）
- 必须共用 `KanbanCard.HEADER_HEIGHT(28)` / `ROW_HEIGHT(18)` / `PADDING(10)` 这套静态常量，算出 `height = HEADER_HEIGHT + 列数×ROW_HEIGHT + PADDING`
- 教训：`KanbanBoard.TABLE_CARD_BASE_HEIGHT` 历史上写死成 60（= 28+10+22 多余空白），与 `drawTableCard` 实际公式 38 不对齐，导致 20 列的表底部留 ~22px 空白行
- 详见 DEVELOPMENT_GUIDE 第 39 节 / memory/2026-08-20.md

### 10. 借用 Database 插件原生 Action 实现"跳转"（2026-09-21 已实现并编译通过）
- IDEA 的 ER 图（Diagrams）右键菜单里的 `Go To > Data / Go to DDL / Database Explorer` **不是自己写的跳转逻辑**，而是把现成 Action 组装成 ActionGroup（uml 框架 `DiagramSourceActionsGroup` + Database 的 `DbDiagramProvider$2$1.getChildren()` 用 `ActionManager.getAction(id)` 取）。
- 可复用的 action id：`Jdbc.OpenEditor.Data`（Data/Edit Data）、`Jdbc.OpenEditor.DDL`（Go to DDL）、`sql.SelectInDatabaseView`（Database Explorer）、`FindUsages`、`$Copy`、`CopyReference`。
- 它们只认 `CommonDataKeys.PSI_ELEMENT`，所以只需自建 `DataContext`：
  `AnActionEvent.createFromDataContext(ActionPlaces.POPUP, action.getTemplatePresentation().clone(), SimpleDataContext.builder().add(CommonDataKeys.PROJECT, p).add(CommonDataKeys.PSI_ELEMENT, dbElement).build())` → `action.update(event)` 判 enable → `action.actionPerformed(event)`。
- 解析 PSI 元素链路（反射）：`DbPsiFacade.getInstance(project)` → `findDataSource(name)` → `DasUtil.getTables(ds)` 匹配同名 → `DbPsiFacade.findElement(DasObject)` → `DbElement`（`extends PsiFileSystemItem`，即 PsiElement）。
- **不要持久化 PSI 元素**（重启/同步后失效），右键时用 `TableInfo` 的 datasource+schema+tableName 现场重解析。
- 两个坑：① 组 `DbDiagrams.SourceActionsGroup.GoTo` 继承 `DiagramSourceActionsGroup`，其 `update()` 要求 DataContext 有 `DiagramDataKeys.BUILDER` 且有选中节点，否则整组被禁用 → 必须自建 `DefaultActionGroup`；② `ActionPopupMenu.setDataContext(Supplier)` 在 2023.2 可用，`ActionPlaces` 无 `CONTEXT_MENU` 常量（用 `POPUP`）。
- 已实现（2026-09-21）：`DatabaseTableMetadataFetcher.resolveDbElement`（反射 `DbPsiFacade.findElement(DasObject)`）+ 新类 `com.wd.db.TableNavigator.performAction/isActionAvailable` + `BoardContextMenu.buildHeaderMenu` 第 4 参 `NavigateAction`（生成「跳转」JMenu 子菜单）+ `KanbanBoard.navigateToTable`。`./gradlew compileJava` 通过。
- **实测坑：`CommonDataKeys.PSI_ELEMENT_ARRAY` 在 2023.2 不存在**（首次编译报找不到符号），只能用 `PSI_ELEMENT`。
- 详细调研与 API 清单：memory/2026-09-21.md；规范见 DEVELOPMENT_GUIDE 第 44 节
