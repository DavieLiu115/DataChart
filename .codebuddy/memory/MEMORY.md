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
