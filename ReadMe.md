# DataChart

> 一款把数据库表结构「画」出来的 IntelliJ IDEA 插件。

DataChart 让你从 Database 工具窗口把数据库表直接拖到一块无限画布上，自动生成表结构卡片（字段、类型、注释、主键 / 索引标识），再用连线表达表之间的关联关系，形成一张可交互、可保存、可导出的 ER 图。

看板布局与表关系保存在自定义的 `.datachart` 文件中，可以用 Git 一起管理。

---

## 功能特性

### 画布与卡片
- **拖拽建卡**：从 Database 工具窗口拖入数据库表，自动读取字段、类型、注释、主键 / 索引信息并生成表卡片
- **表卡片渲染**：表头显示 `表名 / * 注释 *` 与 schema，正文逐行显示列名、类型、注释，主键用金色标识、索引用灰色圆点标识
- **无限画布**：滚轮平移、`Ctrl/Cmd + 滚轮` 缩放（10% ~ 1000%），支持 `Fit`（缩放 + 居中）、`Recenter`（只居中）、`100%`（只复位缩放）三个正交视图操作
- **拖拽磁吸**：拖动卡片时自动吸附其它卡片的左 / 中 / 右、上 / 中 / 下对齐位置，并显示虚线辅助线
- **框选多选**：在空白处按住左键拉出选框，可一次选中多张表；`Command/Ctrl + Del` 批量删除

### 表关系
- **拖拽连线**：在列行上按住左键拖到目标列松手即建立连线（右键起手同样支持）
- **关系类型**：一对一 / 一对多 / 多对一 / 多对多，连线端点按类型绘制「一」端直线、「多」端三叉（鸟爪）；右键连线可切换类型
- **颜色区分**：每条新连线自动从调色板分配不同颜色，方便在密集关系图中区分
- **连通高亮**：选中某一列后，沿连线图 BFS 遍历整个连通子图，所有关联列统一高亮，未被选中的连线则以自身颜色标注占用行

### 检索与同步
- **全文搜索**：`Cmd/Ctrl + F` 聚焦搜索框，匹配表名、表注释、列名、列注释（不区分大小写），命中行按黄色系高亮，`↑ / ↓` 在结果间跳转并自动滚动到视野中心
- **同步表结构**：表头右键「同步表结构」重新拉取数据库元信息；新增列以浅绿色滑动动画标识，删除列保留在卡片底部并加删除线（红色）
- **连线自动重定位**：同步表结构后按**列名**重新定位连线，列被删除的连线自动移除，避免列 index 错位

### 导出与剪贴板
- **导出 PDF**：矢量输出，中文字体按 iText Asian `STSong-Light` → Windows 宋体 → AWT 默认逐级回退
- **导出图片**：默认 2x 高 DPI 输出 JPG（高质量压缩），内容自适应包围盒 + 20px 边距
- **一键复制**：右键表头可复制表名 / 注释，右键列行左半区可复制列名 / 注释
- **原生跳转**：右键表头「跳转」子菜单复用 Database 插件自带动作（跳到 DDL / 查看数据 / 在 Database Explorer 中定位）；跳转目标按持久化的「数据源 + 表名」现场解析，旧文件与 IDE 重启后同样可用
- **查找用法**：右键表头「查找用法」复用 IDEA 的 Find Usages（Alt+F7），在工程内的 SQL / XML 等文件中搜索该表名的引用，结果展示在 Find 工具窗口

### 工程化
- **文件即数据**：`.datachart` 为可读 JSON，自带 `_aiGuide` 字段说明格式，方便人工或 AI 直接读取与修改
- **主题适配**：所有颜色、图标均提供深色 / 浅色两套，跟随 IDE 主题自动切换
- **后台化与稳定性**：拖表元信息查询、文件读写、导出渲染均走后台线程 + `ReadAction`，避免卡顿 EDT；编辑器 `dispose` 时完整清理定时器、监听器与拖拽目标

---

## 环境要求

| 项目 | 要求 |
| --- | --- |
| JDK | 17（`sourceCompatibility / targetCompatibility = 17`） |
| IntelliJ IDEA | 2023.2.6 及以上（`since-build = 223`） |
| IDE 版本 | **Ultimate**（本插件依赖 Database 工具窗口） |
| Gradle | 使用仓库内 `gradlew` 即可，无需单独安装 |

> Database 插件是 Ultimate 版专属能力，因此本插件在 Community 版中可安装但无法拖入数据库表。

---

## 构建与运行

```bash
# 1. 编译
./gradlew compileJava

# 2. 打包插件（产物在 build/distributions/*.zip）
./gradlew buildPlugin

# 3. 启动一个带插件的沙箱 IDE 进行调试
./gradlew runIde
```

安装到正式 IDE：`Settings → Plugins → ⚙ → Install Plugin from Disk...`，选择 `build/distributions/DataChart-1.0.0.zip`。

> `build.gradle.kts` 中的 `intellij.localPath` 目前指向本机 IDE 目录（`/Users/lww/Downloads/ideaJar/ideaIU-2023.2.6`）。换机器构建时请改为自己的 IDEA Ultimate 安装路径，或改用 `version` / `type` 方式声明。

---

## 使用指南

1. **新建文件**：`File → New → DataChart`，或手动创建 `xxx.datachart` 文件，IDE 会用图形编辑器打开
2. **拖入表**：打开 `Database` 工具窗口，把表拖到画布上（鼠标位置 = 卡片左上角）
3. **建连线**：在源表的某一列上按住左键拖到目标列后松手；右键连线可切换关系类型
4. **调整布局**：拖动表头移动卡片（自动磁吸对齐），空白处拖拽框选，`Fit` 一键适配全图
5. **检索**：`Cmd/Ctrl + F` 输入关键字，`↑ / ↓` 跳转结果，`ESC` 清空
6. **保存**：`Cmd/Ctrl + S`，或直接关闭编辑器时自动落盘
7. **导出**：工具栏 `Export PDF` / `Export Picture`，文件名默认为 `{文件名}_{yyyyMMdd_HHmmss}`

### 快捷键

| 快捷键 | 功能 |
| --- | --- |
| `Cmd/Ctrl + F` | 聚焦搜索框并全选内容 |
| `↑` / `↓`（搜索框内） | 上一个 / 下一个搜索结果 |
| `ESC`（搜索框内） | 清空搜索 |
| `Cmd/Ctrl + S` | 保存当前 `.datachart` 文件 |
| `Command + Del`（Mac）/ `Ctrl + Backspace` | 删除选中的表（有连线时二次确认） |
| `空格` | 复位视图（缩放 100% + 位置归零） |
| `Ctrl/Cmd + 滚轮`（Mac 也支持 `Alt + 滚轮`） | 以光标为锚点缩放 |
| `滚轮` | 平移画布 |
| 左键拖动表头 | 移动卡片（带磁吸对齐） |
| 左键拖动空白 | 框选多张表 |
| 左键拖动列行 | 建立连线（移动 ≥ 4px 才升级为拖线，原地点击仍是选中） |

### 右键菜单

- **表头**：复制表名 / 复制注释 / 同步表结构 / 查找用法 / 跳转（跳到 DDL、查看数据、在 Database Explorer 中定位）/ 删除表（红色危险色）
- **列行左半区**：复制列名 / 复制注释
- **列行右半区 / 空白起手**：建立连线
- **连线**：关系类型（一对一 / 一对多 / 多对一 / 多对多）/ 删除连线

---

## .datachart 文件格式

`.datachart` 是一个自描述的 JSON 文件：

```jsonc
{
  "_aiGuide": "给 AI / 使用者的格式说明（新建文件时由模板写入）",
  "version": "1.0",
  "name": "看板名称",
  "tables": [
    {
      "id": "UUID",                  // 卡片唯一 ID
      "datasource": "数据源",
      "schema": "public",
      "tableName": "sys_user",
      "comment": "用户表",
      "x": 0, "y": 0,                // 画布坐标
      "width": 280, "height": 200,   // 卡片尺寸
      "columns": [
        { "name": "id", "type": "bigint", "comment": "主键",
          "isPrimaryKey": true, "isNullable": false, "isIndexed": false }
      ],
      "highlightedRows": [0]         // 用户手动高亮的行索引
    }
  ],
  "relations": [
    {
      "fromCardId": "卡片ID", "fromColumn": "0", "fromColumnName": "id",
      "relationType": "ONE_TO_MANY",
      "toCardId": "卡片ID", "toColumn": "3", "toColumnName": "user_id"
    }
  ]
}
```

### 两个关键约定

1. **连线列以列名为准**：`fromColumn` / `toColumn` 存的是列 index，但解析与重定位时**优先使用 `fromColumnName` / `toColumnName`**。列 index 会因增删列而错位，列名不会。
2. **卡片 ID 必须唯一**：虽然历史格式里 id 形如 `datasource.schema.tableName`，但同一张表可以被拖入多次，因此运行时一律分配 UUID；加载旧文件时检测到重复 id 会自动补齐 UUID。

---

## 项目结构

```
src/main/
├── java/com/wd/
│   ├── editor/                      # 编辑器接入层
│   │   ├── DataChart.java           # 自定义语言定义
│   │   ├── DataToolsFileType.java   # 文件类型（EXTENSION = "datachart"）
│   │   ├── DataChartEditor.java     # FileEditor 实现（生命周期 / 加载 / 保存）
│   │   └── DataChartEditorProvider.java
│   ├── ui/
│   │   ├── DataChartView.java       # 顶部工具栏 + 搜索 + 导出入口（GUI Designer .form）
│   │   ├── KanbanBoard.java         # 画布编排层：绘制 / 交互 / 视图调度
│   │   ├── KanbanCard.java          # 卡片渲染与命中测试
│   │   ├── Connection.java          # 连线模型与绘制（贝塞尔 + 关系类型端点）
│   │   ├── BoardViewport.java       # 视口几何（transform / zoom / focus / fit）
│   │   ├── BoardSnapHelper.java     # 拖拽磁吸与对齐辅助线计算
│   │   ├── BoardSearchModel.java    # 搜索模型（结果集 / 焦点 / 滚动定位）
│   │   ├── BoardContextMenu.java    # 右键菜单（含自绘勾选项）
│   │   ├── BoardPersistence.java    # 看板 ↔ ChartData 序列化
│   │   ├── BoardExportUtil.java     # 导出 PDF / 图片
│   │   ├── NotificationUtil.java    # 统一通知与确认弹窗
│   │   └── Donation.java            # 捐赠对话框
│   ├── db/                          # 数据库元信息层
│   │   ├── TableMetadataService.java        # Project 级服务入口
│   │   ├── TableMetadataFetcher.java        # 取数接口
│   │   ├── DatabaseTableMetadataFetcher.java# Database 插件反射实现
│   │   ├── TableDropHandler.java            # Database 拖拽目标
│   │   ├── TableInfo.java / ColumnInfo.java # 元信息模型
│   │   └── ...
│   ├── model/                       # 持久化模型
│   │   ├── ChartData.java           # .datachart 根模型（含 TableCardModel）
│   │   ├── ChartRelation.java
│   │   └── RelationType.java
│   └── icon/PluginIcons.java        # 图标集中管理
└── resources/
    ├── META-INF/plugin.xml          # 扩展点注册
    ├── META-INF/databasePlugin.xml  # Database 可选依赖声明
    ├── fileTemplates/DataChart.datachart.ft  # 新建文件模板
    ├── messages/                    # 国际化文案
    └── icons/                       # SVG / JPG 图标
```

---

## 架构设计

```
DataChartEditor (FileEditor)
        │  延迟初始化 / 加载 / 保存 / dispose
        ▼
DataChartView (DialogWrapper + .form)
        │  工具栏（搜索 / Fit / Recenter / 100% / 导出 / 捐赠）
        ▼
KanbanBoard (编排层)
   ├── BoardViewport       视口几何
   ├── BoardSnapHelper     磁吸计算
   ├── BoardSearchModel    搜索结果
   ├── BoardContextMenu    右键菜单
   ├── BoardPersistence    序列化
   └── BoardExportUtil     导出
        │
        ├── KanbanCard / Connection   （渲染模型）
        └── TableDropHandler ──► TableMetadataService ──► DatabaseTableMetadataFetcher（反射）
```

### 几条重要的设计原则

- **编排层只做编排**：`KanbanBoard` 负责事件 → 调用模块 → 刷新视图；纯计算逻辑抽为静态工具类（`BoardSnapHelper` / `BoardPersistence` / `BoardContextMenu` / `BoardExportUtil`），有状态逻辑抽为实例类（`BoardViewport` / `BoardSearchModel`），模块间用函数式接口（`FindCard` / `AddConnection` / `Runnable`）解耦。
- **对 Database 插件的访问全部走反射**：Database 插件为 Ultimate 专属，为兼容 Community 版，所有 `com.intellij.database.*` 的访问都通过反射 + 运行时插件启用检查完成，并配合 `optional="true" config-file="databasePlugin.xml"` 声明依赖。
- **后台线程 + ReadAction 三要素**：后台任务结果回到 EDT 时必须校验 ① 工程是否已 `dispose` ② 目标对象（卡片 / 组件）是否仍存在 ③ Swing 更新必须在 EDT。
- **坐标系先对齐再谈语义**：IDE DnD 给出的屏幕绝对坐标必须先减去 `getLocationOnScreen()` 转为面板局部坐标，才能交给视口做逆变换。
- **高度 / 尺寸公式三处共享常量**：新建卡片、加载文件、同步表结构三个入口必须共用 `KanbanCard.HEADER_HEIGHT / ROW_HEIGHT / PADDING`，否则卡片底部会出现空白或截断。

> 更完整的开发规范、UI 准则与踩坑记录见 [`DEVELOPMENT_GUIDE.md`](./DEVELOPMENT_GUIDE.md)。

---

## 技术栈

| 依赖 | 用途 |
| --- | --- |
| IntelliJ Platform Gradle Plugin `1.17.3` | 插件构建与沙箱运行 |
| Kotlin JVM Plugin `1.9.24` | 构建脚本 |
| `com.alibaba:fastjson:1.2.83` | `.datachart` JSON 序列化 |
| `com.itextpdf:itextpdf:5.5.13` | PDF 导出 |
| `com.itextpdf:itext-asian:5.2.0` | PDF 中文字体 |

---

## 兼容性说明

- **`since-build = 223`**：适配 IDEA 2023.2 及以后版本，`untilBuild` 未限制（`intellij.updateSinceUntilBuild = false`）
- **修改状态属性名兼容**：`FileEditor.PROP_MODIFIED`（旧版常量）与 `FileEditor.getPropModified()`（新版静态方法）通过反射自动选择，结果静态缓存
- **Database 插件可选**：未安装时插件仍可正常打开 / 编辑 `.datachart` 文件（数据已随文件持久化），仅无法从工具窗口拖入新表

---

## 作者

- **Davie Liu** — lerder@foxmail.com
- 插件定制开发请联系上述邮箱；如果觉得插件还不错，欢迎在编辑器右上角点击捐赠按钮支持作者。

## 许可证

本项目暂未声明开源许可证，如需商用或二次分发请先与作者联系。
