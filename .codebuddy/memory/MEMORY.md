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
