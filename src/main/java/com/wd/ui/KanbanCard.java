package com.wd.ui;

import com.intellij.ui.JBColor;
import com.wd.db.ColumnInfo;
import com.wd.db.TableInfo;
import com.wd.i18n.DataChartBundle;
import com.wd.icon.PluginIcons;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.Icon;

/**
 * 看板中的卡片元素，支持两种渲染模式：
 *
 * <ul>
 *   <li>图表模式（chart）：标题 + 类型 + 描述</li>
 *   <li>表格模式（table）：表名 + 列定义列表（类型 + 注释）</li>
 * </ul>
 *
 * @author lww
 */
public class KanbanCard {

	/** 卡片唯一 ID */
	private final String id;

	/** 卡片名称（图表模式显示在 header；表格模式作为表名） */
	private String name;

	/** 图表类型（图表模式使用）；表格模式不使用 */
	private String type;

	/** 描述（图表模式使用） */
	private String description;

	/** 数据库表元信息（表格模式使用） */
	private TableInfo tableInfo;

	/** 卡片位置和大小 */
	private Rectangle2D bounds;

	/** 选中状态 */
	private boolean selected;

	/** 用户手动选中的行集合（多个行可同时被用户选中，用橙色高亮） */
	private final java.util.Set<Integer> highlightedRows = new java.util.HashSet<>();

	/**
	 * 连线占用行集合的备份（仅用于序列化/反序列化的持久化）
	 *
	 * <p>实际绘制时，{@code KanbanBoard} 在每次重绘前会重新计算每张卡的
	 * {@code linkedRows}（从 {@code connections} 推导），不依赖此字段。
	 * 这里保留字段只是为了在加载时先恢复，绘制时立刻会被覆盖。</p>
	 */
	private final java.util.Map<Integer, java.awt.Color> connectionHighlightRows = new java.util.HashMap<>();

	// 样式
	/** header 区域高度（与 drawTableCard 的 bodyTop 计算保持一致，供外部计算卡片总高度） */
	public static final int HEADER_HEIGHT = 28;
	/** 卡片内边距（与 drawTableCard 的 maxBodyY 计算保持一致） */
	public static final int PADDING = 10;
	private int headerHeight = HEADER_HEIGHT;
	private int padding = PADDING;
	/** 表格卡片每行高度（与 drawTableCard 内部 rowHeight 保持一致，供外部精确滚动使用） */
	public static final int ROW_HEIGHT = 18;
	private Font headerFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
	private Font bodyFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	private Font columnFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	/** Header 注释斜体字体 */
	private Font italicHeaderFont = new Font(Font.SANS_SERIF, Font.ITALIC, 11);
	/** 列注释斜体字体 */
	private Font italicCommentFont = new Font(Font.SANS_SERIF, Font.ITALIC, 10);

	// 按 type 区分的配色（亮色 / 深色各一套，适配主题）
	private static final Color HEADER_LINE = new Color(0x4A90E2);
	private static final Color HEADER_BAR = new Color(0xF5A623);
	private static final Color HEADER_PIE = new Color(0x7ED321);
	private static final Color HEADER_SCATTER = new Color(0x9013FE);
	private static final Color HEADER_DEFAULT = new Color(0x9B9B9B);
	private static final Color HEADER_TABLE = new Color(0x6C7A89);
	private static final Color BG_LIGHT = new Color(0xFFFFFF);
	private static final Color BG_DARK = new Color(0x3C3F41);
	private static final Color BORDER = new Color(0xCCCCCC);
	private static final Color BORDER_SELECTED = new Color(0x4A90E2);
	private static final Color TEXT_DARK = new Color(0x333333);
	private static final Color TEXT_LIGHT = new Color(0xDDDDDD);
	private static final Color COMMENT_COLOR = new Color(0x888888);
	/** 表头背景颜色（浅色主题，浅蓝） */
	private static final Color HEADER_BG_LIGHT = new Color(0xE8F1FB);
	/** 表头背景颜色（深色主题，青蓝） */
	private static final Color HEADER_BG_DARK = new Color(0x2C5F8D);
	/** 表头文字颜色（浅色主题，黑色） */
	private static final Color HEADER_TEXT_LIGHT = new Color(0x222222);
	/** 表头文字颜色（深色主题，浅白） */
	private static final Color HEADER_TEXT_DARK = new Color(0xEEEEEE);
	/** 列名字体颜色（黑色，浅色主题）/ 浅白（深色主题） */
	private static final Color COLUMN_NAME_COLOR = new Color(0x222222);
	private static final Color COLUMN_NAME_COLOR_DARK = new Color(0xFFFFFF);
	/** 类型字体颜色（蓝色，浅色主题）/ 浅蓝（深色主题） */
	private static final Color TYPE_COLOR = new Color(0x2470B0);
	private static final Color TYPE_COLOR_DARK = new Color(0x6CB0F5);
	/** 行选中高亮背景色（粉色，参考 DataHelper） */
	private static final Color ROW_HIGHLIGHT_COLOR = new Color(0xFFB6E1);
	/** 用户手动选中行的高亮颜色（橙色） */
	private static final Color USER_HIGHLIGHT_COLOR = new Color(0xFE9933);
	/** 行选中高亮背景色（橙色，参考 DataHelper） */
	private static final Color ROW_HIGHLIGHT_COLOR_ORANGE = new Color(0xFF9F5B);
	/** 搜索命中行背景色（淡黄，柔和不刺眼，深色/浅色主题通用） */
	private static final Color SEARCH_HIGHLIGHT_COLOR = new Color(0xFFF3B0);
	/** 搜索焦点行背景色（深黄，键盘上下键导航到的当前行） */
	private static final Color SEARCH_HIGHLIGHT_FOCUS_COLOR = new Color(0xFFD24A);

	/**
	 * 构造方法（图表模式）
	 *
	 * @param id   卡片 ID
	 * @param name 图表名称
	 * @param type 图表类型
	 * @param x    x 坐标
	 * @param y    y 坐标
	 * @param w    宽度
	 * @param h    高度
	 */
	public KanbanCard(String id, String name, String type, String description,
			double x, double y, double w, double h) {
		this.id = id;
		this.name = name;
		this.type = type == null ? "default" : type.toLowerCase();
		this.description = description;
		this.bounds = new Rectangle2D.Double(x, y, w, h);
	}

	/**
	 * 构造方法（表格模式）
	 *
	 * @param id        卡片 ID（建议用 "datasource.table" 形式）
	 * @param tableInfo 数据库表元信息
	 * @param x         x 坐标
	 * @param y         y 坐标
	 * @param w         宽度
	 * @param h         高度
	 */
	public static KanbanCard forTable(String id, TableInfo tableInfo,
			double x, double y, double w, double h) {
		KanbanCard card = new KanbanCard(id, tableInfo.getName(), "table",
				tableInfo.getComment(), x, y, w, h);
		card.tableInfo = tableInfo;
		return card;
	}

	/**
	 * 表格卡片宽度最小值（保证至少能放下列名 + 短类型）。
	 *
	 * <p>2026-08-01 引入：用于 {@link #computeRequiredWidth(TableInfo)}。
	 * 字段少的表（如 3-5 个字段）维持在 280px，字段多或内容长的表自动加宽。</p>
	 *
	 * <p>⚠️ 2026-09-24 起卡片宽度固定为 280（用户要求"不用加表宽度"），
	 * 这个常量目前只作为"最小宽度"的语义保留，实际宽度取 {@code KanbanBoard.TABLE_CARD_WIDTH}（同值 280）。</p>
	 */
	private static final int TABLE_CARD_MIN_WIDTH = 280;

	/**
	 * 表格卡片最小宽度（公开常量，供 {@link KanbanBoard#addTableCard} 等外部使用）
	 */
	public static final int MIN_TABLE_CARD_WIDTH = TABLE_CARD_MIN_WIDTH;

	/**
	 * 计算容纳指定表所有列所需的最优宽度。
	 *
	 * <p>宽度组成：
	 * <ul>
	 *   <li>左侧 padding（10px）</li>
	 *   <li>字段图标宽（16px + 间距 4px = 20px）</li>
	 *   <li>列名 + " : " + 类型（columnFont 度量）</li>
	 *   <li>列注释（italicCommentFont 度量） + 6px 间距</li>
	 *   <li>右侧 padding（10px）</li>
	 * </ul>
	 * 同时考虑 header 行（表名 + 表注释）宽度。</p>
	 *
	 * <p>最后与 {@link #MIN_TABLE_CARD_WIDTH} 比较取最大，保证字段少的表不会过窄。</p>
	 *
	 * <p>实现：构造离屏 1×1 BufferedImage 拿到 FontMetrics，遍历所有列 + header，
	 * 真实度量字体宽度（而不是估算字符数），避免中英文混排估算不准。</p>
	 *
	 * @param tableInfo 表元信息
	 * @return 推荐宽度（最小 {@link #MIN_TABLE_CARD_WIDTH}）
	 */
	public static int computeRequiredWidth(TableInfo tableInfo) {
		return computeRequiredWidth(tableInfo, null);
	}
	// ⚠️ 2026-09-24 起 computeRequiredWidth / forTableAutoWidth 这一套"按内容加宽"暂时**不被调用**：
	// 用户明确要求"不用加表宽度"，卡片宽度固定 280（KanbanBoard.TABLE_CARD_WIDTH），
	// 长注释改为整句绘制、溢出卡片右侧（见 drawTableCard）。
	// 保留这套实现备用 —— 若以后要恢复自适应宽度，把 addTableCard / loadFromChartData /
	// setTableInfoWithDiff 三处的宽度换回 computeRequiredWidth(...) 即可（字体/间距已与绘制对齐）。

	/**
	 * 同上，并把 {@code extraColumns} 一起参与度量。
	 *
	 * <p>用途：结构同步时"已删除列"仍会画在卡片里（浅红底 + 中划线），
	 * 它们的「列名 : 类型 + 注释」也要算进宽度，否则那几行会被省略号截断。</p>
	 *
	 * @param tableInfo    表元信息
	 * @param extraColumns 额外参与度量的列（可为 null）
	 */
	public static int computeRequiredWidth(TableInfo tableInfo, List<ColumnInfo> extraColumns) {
		if (tableInfo == null) {
			return MIN_TABLE_CARD_WIDTH;
		}
		// 离屏 FontMetrics
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
				1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try {
			Font headerFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
			Font italicHeaderFont = new Font(Font.SANS_SERIF, Font.ITALIC, 11);
			Font columnFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
			Font italicCommentFont = new Font(Font.SANS_SERIF, Font.ITALIC, 10);

			FontMetrics headerFm = g.getFontMetrics(headerFont);
			FontMetrics italicHeaderFm = g.getFontMetrics(italicHeaderFont);
			FontMetrics colFm = g.getFontMetrics(columnFont);
			FontMetrics italicFm = g.getFontMetrics(italicCommentFont);

			int padding = 10;
			int iconZone = 20; // 图标 16 + 间距 4
			int headerIconZone = 18; // 图标 16 + 间距 2
			int commentGap = 6;
			int maxContent = 0;

			// 1. header 行：表名 + 6px + "/* 注释 */"
			String headerName = tableInfo.getName() == null ? "" : tableInfo.getName();
			String headerComment = tableInfo.getComment();
			int headerW = headerIconZone + headerFm.stringWidth(headerName);
			if (headerComment != null && !headerComment.isEmpty()) {
				String cmtText = "/* " + headerComment + " */";
				headerW += commentGap + italicHeaderFm.stringWidth(cmtText);
			}
			maxContent = Math.max(maxContent, headerW);

			// 2. 列行：列名 + " : " + 类型 + 6px + "/* 注释 */"
			//    extraColumns（结构同步时保留展示的"已删除列"）同样会画出来，一起度量
			List<ColumnInfo> measured = new java.util.ArrayList<>();
			if (tableInfo.getColumns() != null) {
				measured.addAll(tableInfo.getColumns());
			}
			if (extraColumns != null) {
				measured.addAll(extraColumns);
			}
			for (ColumnInfo col : measured) {
				String name = col.getName() == null ? "" : col.getName();
				String type = col.getType() == null ? "" : col.getType();
				String nameAndSep = name + " : " + type;
				int colW = iconZone + colFm.stringWidth(nameAndSep);
				String comment = col.getComment();
				if (comment != null && !comment.isEmpty()) {
					String cmtText = "/* " + comment + " */";
					colW += commentGap + italicFm.stringWidth(cmtText);
				}
				maxContent = Math.max(maxContent, colW);
			}

			// 3. 加上左右 padding
			int totalW = maxContent + padding * 2;
			return Math.max(MIN_TABLE_CARD_WIDTH, totalW);
		} finally {
			g.dispose();
			img.flush();
		}
	}

	/**
	 * 卡片<b>实际绘制</b>需要的宽度（含会溢出到卡片外的注释文字）。
	 *
	 * <p>2026-09-24 新增：注释不再按宽度截断后会画到卡片右边框之外，而 {@code bounds} 只有 280 宽。
	 * 导出（图片 / PDF）的范围若只按 {@code bounds} 算，最右侧卡片溢出的那段文字就会被图片边界切掉
	 * —— 用户反馈"导出的图片右边不完整"。</p>
	 *
	 * <p>⚠️ 多留 {@value #OVERFLOW_SLACK}px 余量：导出绘制开了
	 * {@code FRACTIONALMETRICS_ON / TEXT_ANTIALIAS_ON}，实测字宽比离屏度量略大，不留余量仍可能被切掉。</p>
	 *
	 * @return {@code max(bounds.width, 内容所需宽度 + 余量)}
	 */
	public double getContentRequiredWidth() {
		double boundsWidth = bounds.getWidth();
		if (tableInfo == null) {
			return boundsWidth;
		}
		// 结构同步时还会额外画"已删除列"，它们也要参与溢出测算
		int required = computeRequiredWidth(tableInfo, deletedColumns);
		if (required <= boundsWidth) {
			return boundsWidth;
		}
		return required + OVERFLOW_SLACK;
	}

	/** 字体度量与实际渲染的宽度差余量（px），仅用于 {@link #getContentRequiredWidth()}。 */
	private static final int OVERFLOW_SLACK = 12;

	/**
	 * 构造方法（表格模式，自动按列内容计算最优宽度）
	 *
	 * <p>宽度 = max(实际列内容所需宽度, {@link #MIN_TABLE_CARD_WIDTH})。
	 * 高度按行数计算后传入。如果 {@code explicitWidth} &gt; 实际需要宽度，
	 * 用 {@code explicitWidth}（支持从持久化恢复时保留用户的尺寸）。</p>
	 *
	 * @param id            卡片 ID
	 * @param tableInfo     表元信息
	 * @param x             x 坐标
	 * @param y             y 坐标
	 * @param explicitWidth 显式指定宽度（0 表示自动）；非 0 时用较大值
	 * @param h             高度
	 */
	public static KanbanCard forTableAutoWidth(String id, TableInfo tableInfo,
			double x, double y, double explicitWidth, double h) {
		int required = computeRequiredWidth(tableInfo);
		double w = (explicitWidth <= 0)
				? required
				: Math.max(explicitWidth, required);
		return forTable(id, tableInfo, x, y, w, h);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type == null ? "default" : type.toLowerCase();
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public TableInfo getTableInfo() {
		return tableInfo;
	}

	/** 增量同步差异列状态：新增列名字集合 */
	private final java.util.Set<String> addedColumnNames = new java.util.HashSet<>();
	/** 增量同步差异列状态：被删除列数据列表（只在内存展示删除状态，不序列化） */
	private final java.util.List<ColumnInfo> deletedColumns = new java.util.ArrayList<>();
	/** 新增列划入动画当前进度 (0.0 -> 1.0) */
	private float addedSlideProgress = 1.0f;
	/** 动画 Timer */
	private javax.swing.Timer slideAnimTimer = null;

	/**
	 * 停止并释放动画定时器（2026-08-27：编辑器 dispose 时调用，
	 * 防止 Swing Timer 持有卡片引用造成内存泄漏）。
	 */
	public void disposeTimers() {
		if (slideAnimTimer != null) {
			slideAnimTimer.stop();
			slideAnimTimer = null;
		}
	}

	/**
	 * 替换卡片绑定的表元信息（带增量同步对比提示）。
	 *
	 * @param newInfo 新的表元信息
	 * @param repaintCallback 动画每一帧的回调（通常是 kanbanBoard::repaint）
	 */
	public void setTableInfoWithDiff(TableInfo newInfo, Runnable repaintCallback) {
		if (newInfo == null) {
			return;
		}
		// 停止上一次未完成的动画
		if (slideAnimTimer != null && slideAnimTimer.isRunning()) {
			slideAnimTimer.stop();
		}

		addedColumnNames.clear();
		deletedColumns.clear();

		if (this.tableInfo != null && this.tableInfo.getColumns() != null && newInfo.getColumns() != null) {
			java.util.Map<String, ColumnInfo> newColMap = new java.util.HashMap<>();
			for (ColumnInfo col : newInfo.getColumns()) {
				newColMap.put(col.getName(), col);
			}

			java.util.Set<String> oldColNames = new java.util.HashSet<>();
			for (ColumnInfo oldCol : this.tableInfo.getColumns()) {
				oldColNames.add(oldCol.getName());
				// 如果新结构中不存在该列，标记为已删除列
				if (!newColMap.containsKey(oldCol.getName())) {
					deletedColumns.add(oldCol);
				}
			}

			for (ColumnInfo newCol : newInfo.getColumns()) {
				// 如果旧结构中不存在，标记为新增列
				if (!oldColNames.contains(newCol.getName())) {
					addedColumnNames.add(newCol.getName());
				}
			}
		}

		this.tableInfo = newInfo;
		this.name = newInfo.getName();
		this.description = newInfo.getComment();

		// 重新计算高度（包含新增列和保留展示的已删除列）
		// 2026-08-20 与 KanbanBoard.TABLE_CARD_BASE_HEIGHT 用同一套静态常量，
		// 画板计算高度 == drawTableCard 实际可用高度，底部不再有多余空白
		int totalColCount = (newInfo.getColumns() == null ? 0 : newInfo.getColumns().size()) + deletedColumns.size();
		double bodyH = totalColCount * ROW_HEIGHT;
		double h = Math.max(50.0, HEADER_HEIGHT + bodyH + PADDING);
		// 宽度不动（2026-09-24 定稿：固定 280，长注释溢出展示，不加宽也不截断）
		bounds.setRect(bounds.getX(), bounds.getY(), bounds.getWidth(), h);

		// 如果有新增列，触发左侧划入动画
		if (!addedColumnNames.isEmpty()) {
			addedSlideProgress = 0.0f;
			long startTime = System.currentTimeMillis();
			int duration = 300; // 300ms 动画

			slideAnimTimer = new javax.swing.Timer(16, e -> {
				long elapsed = System.currentTimeMillis() - startTime;
				float p = (float) elapsed / duration;
				if (p >= 1.0f) {
					p = 1.0f;
					((javax.swing.Timer) e.getSource()).stop();
				}
				// ease-out 效果
				addedSlideProgress = (float) (1.0 - Math.pow(1.0 - p, 2));
				if (repaintCallback != null) {
					repaintCallback.run();
				}
			});
			slideAnimTimer.start();
		} else {
			addedSlideProgress = 1.0f;
		}
	}

	/**
	 * 替换卡片绑定的表元信息（用于普通场景/无动画回调）。
	 *
	 * @param newInfo 新的表元信息
	 */
	public void setTableInfo(TableInfo newInfo) {
		setTableInfoWithDiff(newInfo, null);
	}

	/**
	 * 获取当前主题下的卡片背景色
	 *
	 * <p>2026-08-01 暴露：供 {@code KanbanBoard.paintForExport} 在导出时使用，
	 * 让画板背景 = 卡片背景，避免"两种背景色"问题。</p>
	 *
	 * @param dark 是否深色主题（与 {@code KanbanBoard.isDarkTheme()} 一致）
	 * @return 卡片背景色（浅色: #FFFFFF，深色: #3C3F41）
	 */
	public static Color getCardBackgroundColor(boolean dark) {
		return dark ? BG_DARK : BG_LIGHT;
	}

	/**
	 * 用 YIQ 公式判断给定颜色是否为"亮色"（亮度 ≥ 128）。
	 *
	 * <p>用于行高亮时自适应选择文字颜色：背景亮 → 用深色文字；背景暗 → 用浅色文字。</p>
	 */
	public static boolean isLightColor(Color c) {
		if (c == null) {
			return false;
		}
		double brightness = (c.getRed() * 299
				+ c.getGreen() * 587
				+ c.getBlue() * 114) / 1000.0;
		return brightness >= 128;
	}

	/** 是否表格模式（带有 TableInfo 元数据） */
	public boolean isTableMode() {
		return tableInfo != null;
	}

	public Rectangle2D getBounds() {
		return bounds;
	}

	public void setBounds(Rectangle2D bounds) {
		this.bounds = bounds;
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public int getSelectedRowIndex() {
		return highlightedRows.isEmpty() ? -2 : highlightedRows.iterator().next();
	}

	/**
	 * 兼容旧 API：设置"用户选中"行（橙色高亮），会覆盖之前的用户选中
	 */
	public void setSelectedRowIndex(int index) {
		highlightedRows.clear();
		if (index >= 0) {
			highlightedRows.add(index);
		}
	}

	/**
	 * 临时高亮行（用于"激活列"传递颜色给 Connection 端点使用）
	 *
	 * <p>每次重绘前由 {@code KanbanBoard} 调用，传入当前激活列对应的颜色（橙色等）。
	 * 读 {@link #getHighlightedColorForRow(int)} 时会优先使用此临时色，覆盖连线占用色，
	 * 让"激活列"统一显色。</p>
	 */
	private final java.util.Map<Integer, java.awt.Color> activeRowColors = new java.util.HashMap<>();

	/**
	 * 搜索命中的行集合（区别于用户选中的橙色高亮，使用淡黄色高亮）
	 *
	 * <p>由 {@code KanbanBoard.search()} 写入，{@link #drawTableCard} 读取后渲染。
	 * 当前"焦点行"（键盘上下键导航到的命中项）也会写入此集合，但用更深的颜色标记。</p>
	 */
	private final java.util.Set<Integer> searchMatchedRows = new java.util.HashSet<>();

	/** 当前搜索的"焦点行"（键盘上下键导航到的那一行），-1 表示无焦点行 */
	private int searchFocusRow = -1;

	/**
	 * 是否当前是搜索的"焦点卡片"（键盘导航到的卡片）
	 *
	 * <p>用于在表头命中（rowIndex=-1）时把卡片边框换成搜索黄色作为视觉标记。</p>
	 */
	private boolean isSearchFocusCard = false;

	/**
	 * 获取搜索命中行集合
	 */
	public java.util.Set<Integer> getSearchMatchedRows() {
		return searchMatchedRows;
	}

	/**
	 * 设置搜索命中行集合（覆盖之前的）
	 */
	public void setSearchMatchedRows(java.util.Collection<Integer> rows) {
		searchMatchedRows.clear();
		if (rows != null) {
			searchMatchedRows.addAll(rows);
		}
	}

	/**
	 * 清除搜索命中行集合
	 */
	public void clearSearchMatchedRows() {
		searchMatchedRows.clear();
		searchFocusRow = -1;
		isSearchFocusCard = false;
	}

	/**
	 * 获取当前搜索焦点行（-1 表示无）
	 */
	public int getSearchFocusRow() {
		return searchFocusRow;
	}

	/**
	 * 设置当前搜索焦点行（-1 表示清除）
	 */
	public void setSearchFocusRow(int row) {
		this.searchFocusRow = row;
	}

	/**
	 * 是否当前是搜索的"焦点卡片"
	 */
	public boolean isSearchFocusCard() {
		return isSearchFocusCard;
	}

	/**
	 * 设置是否为搜索的"焦点卡片"
	 */
	public void setSearchFocusCard(boolean focus) {
		this.isSearchFocusCard = focus;
	}

	/**
	 * 设置临时行高亮色
	 */
	public void setActiveRowColor(int row, java.awt.Color color) {
		if (color == null) {
			activeRowColors.remove(row);
		} else {
			activeRowColors.put(row, color);
		}
	}

	/**
	 * 清除所有临时行高亮色
	 */
	public void clearActiveRowColors() {
		activeRowColors.clear();
	}

	/**
	 * 获取用户手动选中的行集合
	 */
	public java.util.Set<Integer> getHighlightedRows() {
		return highlightedRows;
	}

	/**
	 * 添加用户选中的行
	 */
	public void addHighlightedRow(int row) {
		highlightedRows.add(row);
	}

	/**
	 * 移除用户选中的行
	 */
	public void removeHighlightedRow(int row) {
		highlightedRows.remove(row);
	}

	/**
	 * 清除所有用户选中的行
	 */
	public void clearHighlightedRows() {
		highlightedRows.clear();
	}

	/**
	 * 兼容旧 API：设置行高亮颜色（橙色）—— 等价于添加/清除用户选中行
	 */
	public void setRowHighlightColor(java.awt.Color color) {
		// 不再依赖单一颜色字段；为保持兼容，颜色变化时不清除高亮
		// 实际高亮管理由 highlightedRows/connectionHighlightRows 各自负责
	}

	/**
	 * 兼容旧 API：获取行高亮颜色（用于 removeConnection 等）
	 */
	public java.awt.Color getRowHighlightColor() {
		return null;
	}

	/**
	 * 设置连线占用行（持久化用，绘制时会被重写）
	 */
	public void setConnectionHighlightRow(int row, java.awt.Color color) {
		if (color == null) {
			connectionHighlightRows.remove(row);
		} else {
			connectionHighlightRows.put(row, color);
		}
	}

	/**
	 * 获取连线占用行 Map
	 */
	public java.util.Map<Integer, java.awt.Color> getConnectionHighlightRows() {
		return connectionHighlightRows;
	}

	/**
	 * 获取列行在画板坐标系的右侧点（用于连线起点）
	 *
	 * @param rowIndex 行索引（0-based，对应 columns 列表）
	 * @return 行右侧点，若行索引越界返回 null
	 */
	public java.awt.geom.Point2D getRowRight(int rowIndex) {
		if (tableInfo == null || rowIndex < 0 || rowIndex >= tableInfo.getColumns().size()) {
			return null;
		}
		double rowCenterY = bounds.getY() + headerHeight + rowIndex * ROW_HEIGHT + ROW_HEIGHT / 2.0;
		double rightX = bounds.getX() + bounds.getWidth();
		return new java.awt.geom.Point2D.Double(rightX, rowCenterY);
	}

	/**
	 * 获取列行在画板坐标系的左侧点（用于连线终点）
	 */
	public java.awt.geom.Point2D getRowLeft(int rowIndex) {
		if (tableInfo == null || rowIndex < 0 || rowIndex >= tableInfo.getColumns().size()) {
			return null;
		}
		double rowCenterY = bounds.getY() + headerHeight + rowIndex * ROW_HEIGHT + ROW_HEIGHT / 2.0;
		double leftX = bounds.getX();
		return new java.awt.geom.Point2D.Double(leftX, rowCenterY);
	}

	/**
	 * 表头内"表名"区域的右边界 x 坐标（用于 hit test 区分 header 左右半部分）
	 *
	 * <p>返回的 x 为表名文本的右端位置 + 一个小间距。表名/表注释 hit test 用此判断
	 * 当前点击是表名（左半）还是注释（右半）。</p>
	 */
	public double getHeaderNameRightX() {
		return bounds.getX() + padding + 18 // 图标宽 16 + 间距 2
				+ getHeaderFontMetricsCache().stringWidth(
						tableInfo == null ? name : tableInfo.getName());
	}

	/**
	 * 列行内"列名 + 冒号 + 类型"区域的右边界 x 坐标
	 *
	 * <p>用于 hit test 区分列行左半（列名/类型）和右半（注释）：</p>
	 * <ul>
	 *   <li>左半（< 返回值）→ 弹复制列名/注释菜单</li>
	 *   <li>右半（≥ 返回值）→ 走连线模式</li>
	 * </ul>
	 */
	public double getColumnNameRightX(int rowIndex) {
		if (tableInfo == null || rowIndex < 0 || rowIndex >= tableInfo.getColumns().size()) {
			return -1;
		}
		com.wd.db.ColumnInfo col = tableInfo.getColumns().get(rowIndex);
		FontMetrics colFm = getColumnFontMetricsCache();
		// 列文本起点 = bounds.getX() + padding + 20 (图标 16 + 间距 4)
		double textStartX = bounds.getX() + padding + 20;
		String nameAndSep = col.getName() + " : " + col.getType();
		return textStartX + colFm.stringWidth(nameAndSep);
	}

	/**
	 * 获取列行（field row）在画板坐标的 y 范围 (top, bottom)
	 */
	public double getRowTop(int rowIndex) {
		if (tableInfo == null || rowIndex < 0 || rowIndex >= tableInfo.getColumns().size()) {
			return -1;
		}
		return bounds.getY() + headerHeight + rowIndex * ROW_HEIGHT;
	}

	/**
	 * 返回该行当前的"高亮背景色"（如果有的话）。
	 *
	 * <p>优先级（与 {@code drawTableCard} 中行高亮一致）：</p>
	 * <ol>
	 *   <li>用户手动选中（橙色 #FE9933）</li>
	 *   <li>关联列高亮（橙色 #FD9933，由 {@code KanbanBoard.refreshRelatedRows} 写入）</li>
	 *   <li>连线占用（连线的 palette 颜色）</li>
	 * </ol>
	 *
	 * <p>供 {@code Connection} 用来决定线色（需求 1：起点有背景色时整条线统一为该色）。</p>
	 *
	 * @param rowIndex 行索引
	 * @return 当前生效的高亮色；无高亮返回 null
	 */
	public java.awt.Color getHighlightedColorForRow(int rowIndex) {
		if (tableInfo == null || rowIndex < 0 || rowIndex >= tableInfo.getColumns().size()) {
			return null;
		}
		if (highlightedRows.contains(rowIndex)) {
			return USER_HIGHLIGHT_COLOR;
		}
		// 临时激活色（关联列 → 橙色）
		java.awt.Color active = activeRowColors.get(rowIndex);
		if (active != null) {
			return active;
		}
		java.awt.Color c = connectionHighlightRows.get(rowIndex);
		if (c != null) {
			return c;
		}
		return null;
	}

	/**
	 * header 字体度量（懒加载，避免重复构造 FontMetrics）
	 */
	private FontMetrics headerFontMetricsCache;

	private FontMetrics getHeaderFontMetricsCache() {
		if (headerFontMetricsCache == null) {
			// 这里的 FontMetrics 仅用于估算宽度，使用默认 font
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
					1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			headerFontMetricsCache = g.getFontMetrics(headerFont);
			g.dispose();
		}
		return headerFontMetricsCache;
	}

	/**
	 * column 字体度量（懒加载，避免 hit-test 热路径反复创建离屏 BufferedImage）
	 */
	private FontMetrics columnFontMetricsCache;

	private FontMetrics getColumnFontMetricsCache() {
		if (columnFontMetricsCache == null) {
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
					1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			columnFontMetricsCache = g.getFontMetrics(columnFont);
			g.dispose();
		}
		return columnFontMetricsCache;
	}

	/**
	 * 根据画板坐标获取行索引（-2 表示未命中，-1 表示命中 header）
	 *
	 * @param boardX 画板 x 坐标
	 * @param boardY 画板 y 坐标
	 * @return 行索引
	 */
	public int getRowIndexAt(double boardX, double boardY) {
		if (tableInfo == null) {
			return -2;
		}
		double x = bounds.getX();
		double y = bounds.getY();
		double w = bounds.getWidth();
		double h = bounds.getHeight();
		if (boardX < x || boardX > x + w || boardY < y || boardY > y + h) {
			return -2; // 不在卡片内
		}
		if (boardY < y + headerHeight) {
			return -1; // header 区域
		}
		int rowHeight = ROW_HEIGHT;
		double bodyTop = y + headerHeight;
		int rowIndex = (int) ((boardY - bodyTop) / rowHeight);
		if (rowIndex < 0 || rowIndex >= tableInfo.getColumns().size()) {
			return -2;
		}
		return rowIndex;
	}

	/**
	 * 根据图表类型返回 header 颜色
	 */
	private Color getHeaderColor() {
		if (isTableMode()) {
			return HEADER_TABLE;
		}
		switch (type) {
			case "line":
				return HEADER_LINE;
			case "bar":
				return HEADER_BAR;
			case "pie":
				return HEADER_PIE;
			case "scatter":
				return HEADER_SCATTER;
			default:
				return HEADER_DEFAULT;
		}
	}

	/**
	 * 绘制卡片
	 */
	public void draw(Graphics2D g2d, boolean isDark) {
		draw(g2d, isDark, java.util.Collections.emptyMap());
	}

	/**
	 * 绘制卡片（表格模式支持高亮行）
	 *
	 * @param g2d 图形对象
	 * @param isDark 是否深色主题
	 * @param linkedRows 外部传入的连线占用行 Map（每次重绘前由 KanbanBoard 计算）
	 */
	public void draw(Graphics2D g2d, boolean isDark,
			java.util.Map<Integer, java.awt.Color> linkedRows) {
		if (isTableMode()) {
			drawTableCard(g2d, isDark, linkedRows);
		} else {
			drawChartCard(g2d, isDark);
		}
	}

	/**
	 * 绘制图表卡片
	 */
	private void drawChartCard(Graphics2D g2d, boolean isDark) {
		Color bg = isDark ? BG_DARK : BG_LIGHT;
		Color border = isSearchFocusCard ? SEARCH_HIGHLIGHT_FOCUS_COLOR
				: selected ? BORDER_SELECTED : BORDER;
		Color textColor = isDark ? TEXT_LIGHT : TEXT_DARK;
		Color commentColor = isDark ? new Color(0xBBBBBB) : COMMENT_COLOR;

		// 阴影
		g2d.setColor(new Color(0, 0, 0, 30));
		g2d.fillRoundRect(
				(int) bounds.getX() + 2,
				(int) bounds.getY() + 2,
				(int) bounds.getWidth(),
				(int) bounds.getHeight(),
				8, 8);

		// 卡片背景
		g2d.setColor(bg);
		g2d.fillRoundRect(
				(int) bounds.getX(),
				(int) bounds.getY(),
				(int) bounds.getWidth(),
				(int) bounds.getHeight(),
				8, 8);

		// Header 区域
		drawHeader(g2d, isDark);

		// 边框
		g2d.setColor(border);
		g2d.setStroke(new BasicStroke(selected ? 2f : 1f));
		g2d.drawRoundRect(
				(int) bounds.getX(),
				(int) bounds.getY(),
				(int) bounds.getWidth(),
				(int) bounds.getHeight(),
				8, 8);

		// Header 文字（按主题选择颜色）
		g2d.setColor(isDark ? HEADER_TEXT_DARK : HEADER_TEXT_LIGHT);
		g2d.setFont(headerFont);
		FontMetrics headerFm = g2d.getFontMetrics();
		String headerText = name == null ? DataChartBundle.message("DataChart.card.unnamed") : name;
		int headerTextX = (int) bounds.getX() + padding;
		int headerTextY = (int) bounds.getY() + (headerHeight + headerFm.getAscent() - headerFm.getDescent()) / 2;
		g2d.drawString(headerText, headerTextX, headerTextY);

		// Body
		g2d.setColor(textColor);
		g2d.setFont(bodyFont);

		int bodyY = (int) bounds.getY() + headerHeight + padding + 5;
		String typeText = DataChartBundle.message("DataChart.card.type",
				type == null ? "" : type.toUpperCase());
		g2d.drawString(typeText, (int) bounds.getX() + padding, bodyY);
		bodyY += 18;

		if (description != null && !description.isEmpty()) {
			FontMetrics bodyFm = g2d.getFontMetrics();
			String[] lines = wrapText(description,
					(int) bounds.getWidth() - padding * 2, bodyFm);
			int maxLines = Math.min(lines.length,
					(int) ((bounds.getHeight() - headerHeight - padding * 2 - 25) / 16));
			for (int i = 0; i < maxLines; i++) {
				g2d.setColor(commentColor);
				g2d.drawString(lines[i],
						(int) bounds.getX() + padding, bodyY);
				bodyY += 16;
			}
		}

		// 右下角小色块
		g2d.setColor(getHeaderColor());
		int badgeSize = 8;
		g2d.fillOval(
				(int) (bounds.getX() + bounds.getWidth() - padding - badgeSize),
				(int) (bounds.getY() + bounds.getHeight() - padding - badgeSize),
				badgeSize, badgeSize);
	}

	/**
	 * 绘制表格卡片（数据库表结构）
	 */
	private void drawTableCard(Graphics2D g2d, boolean isDark,
			java.util.Map<Integer, java.awt.Color> linkedRows) {
		Color bg = isDark ? BG_DARK : BG_LIGHT;
		Color border = isSearchFocusCard ? SEARCH_HIGHLIGHT_FOCUS_COLOR
				: selected ? BORDER_SELECTED : BORDER;
		Color textColor = isDark ? TEXT_LIGHT : TEXT_DARK;
		Color commentColor = isDark ? new Color(0xBBBBBB) : COMMENT_COLOR;
		Color separatorColor = isDark ? new Color(0x555555) : new Color(0xEEEEEE);

		// 阴影
		g2d.setColor(new Color(0, 0, 0, 30));
		g2d.fillRoundRect(
				(int) bounds.getX() + 2,
				(int) bounds.getY() + 2,
				(int) bounds.getWidth(),
				(int) bounds.getHeight(),
				8, 8);

		// 卡片背景
		g2d.setColor(bg);
		g2d.fillRoundRect(
				(int) bounds.getX(),
				(int) bounds.getY(),
				(int) bounds.getWidth(),
				(int) bounds.getHeight(),
				8, 8);

		// Header 区域
		drawHeader(g2d, isDark);

		// 边框
		g2d.setColor(border);
		g2d.setStroke(new BasicStroke(selected ? 2f : 1f));
		g2d.drawRoundRect(
				(int) bounds.getX(),
				(int) bounds.getY(),
				(int) bounds.getWidth(),
				(int) bounds.getHeight(),
				8, 8);

		// Header：表图标 + 表名 + 斜体注释（垂直居中）
		int headerLeftX = (int) bounds.getX() + padding;
		int headerCenterY = (int) (bounds.getY() + headerHeight / 2);

		// 1. 表图标（垂直居中）
		Icon tableIcon = isDark ? PluginIcons.dataSchema_dark : PluginIcons.dataSchema;
		int headerTextX = headerLeftX;
		if (tableIcon != null) {
			int iconY = headerCenterY - 8; // 图标 16px，居中
			tableIcon.paintIcon(null, g2d, headerLeftX, iconY);
			headerTextX = headerLeftX + 18; // 图标和文字间距
		}

		g2d.setColor(isDark ? HEADER_TEXT_DARK : HEADER_TEXT_LIGHT);
		g2d.setFont(headerFont);
		FontMetrics headerFm = g2d.getFontMetrics();
		String tableName = tableInfo.getName();
		// 表名垂直居中（基于文字基线）
		int headerTextY = headerCenterY + (headerFm.getAscent() - headerFm.getDescent()) / 2;
		g2d.drawString(tableName, headerTextX, headerTextY);

		// 表注释（斜体，灰白/灰色，与表名垂直居中）
		String tableComment = tableInfo.getComment();
		if (tableComment != null && !tableComment.isEmpty()) {
			int commentX = headerTextX + headerFm.stringWidth(tableName) + 6;
			g2d.setFont(italicHeaderFont);
			g2d.setColor(isDark ? new Color(0xAAAAAA) : new Color(0x666666));
			FontMetrics italicFm = g2d.getFontMetrics();
			// 2026-09-24 不截断：注释整句画出来，允许溢出到卡片右侧（用户要求"不省略、也不加宽卡片"）
			g2d.drawString("/* " + tableComment + " */",
					commentX, headerCenterY + (italicFm.getAscent() - italicFm.getDescent()) / 2);
			g2d.setFont(headerFont);
		}

		// Body：列定义列表
		List<ColumnInfo> columns = tableInfo.getColumns();
		double bodyTop = bounds.getY() + headerHeight;
		int leftX = (int) bounds.getX() + padding;
		double maxBodyY = bounds.getY() + bounds.getHeight() - padding;

		// 行高 18px（之前 20 太大，导致最后一行高度过大）
		int rowHeight = ROW_HEIGHT;

		g2d.setFont(columnFont);
		FontMetrics colFm = g2d.getFontMetrics();
		// 2026-09-24：注释不再按宽度截断，所以这里不再需要 italicCommentFont 的 FontMetrics

		int maxRows = (int) ((maxBodyY - bodyTop) / rowHeight);
		int rowCount = Math.min(columns.size(), maxRows);

		// 颜色定义
		Color columnNameColor = isDark ? COLUMN_NAME_COLOR_DARK : COLUMN_NAME_COLOR;
		Color typeColor = isDark ? TYPE_COLOR_DARK : TYPE_COLOR;
		Color colonColor = isDark ? new Color(0x888888) : new Color(0x999999);

		for (int i = 0; i < rowCount; i++) {
			ColumnInfo col = columns.get(i);
			double rowTop = bodyTop + i * rowHeight;
			double rowCenterY = rowTop + rowHeight / 2;
			// 文字基线（垂直居中）
			int textY = (int) (rowCenterY + colFm.getAscent() / 2 - 1);

			// 行分隔线
			g2d.setColor(separatorColor);
			g2d.drawLine((int) bounds.getX(), (int) rowTop,
					(int) (bounds.getX() + bounds.getWidth()), (int) rowTop);

			// 行高亮背景（在分隔线之后画，覆盖在卡片背景上）
			// 优先级：同步新增（浅绿） > 用户选中（橙色） > 搜索焦点行（深黄） > 搜索命中行（淡黄） > 连线占用（线色）> 普通
			Color highlightColor = null;
			boolean isAdded = addedColumnNames.contains(col.getName());
			if (isAdded) {
				// 浅绿色背景代表新增列
				highlightColor = isDark ? new Color(0x2E4A32) : new Color(0xD4EDDA);
			} else if (highlightedRows.contains(i)) {
				// 用户手动选中（橙色 #FE9933）
				highlightColor = USER_HIGHLIGHT_COLOR;
			} else if (searchMatchedRows.contains(i)) {
				// 搜索命中：焦点行用更深的黄色（#FFD24A），普通命中用淡黄色（#FFF3B0）
				highlightColor = (i == searchFocusRow)
						? SEARCH_HIGHLIGHT_FOCUS_COLOR
						: SEARCH_HIGHLIGHT_COLOR;
			} else if (linkedRows != null && linkedRows.get(i) != null) {
				// 连线占用（用连线自身的颜色）
				highlightColor = linkedRows.get(i);
			}

			if (highlightColor != null) {
				g2d.setColor(highlightColor);
				if (isAdded && addedSlideProgress < 1.0f) {
					// 新增列从左侧划入动画：clip 填充区域宽度为 width * addedSlideProgress
					int fillW = (int) ((bounds.getWidth() - 2) * addedSlideProgress);
					g2d.fillRect(
							(int) bounds.getX() + 1,
							(int) rowTop + 1,
							fillW,
							rowHeight - 1);
				} else {
					g2d.fillRect(
							(int) bounds.getX() + 1,
							(int) rowTop + 1,
							(int) bounds.getWidth() - 2,
							rowHeight - 1);
				}
			}

			// 自适应文字颜色：行高亮为亮色时用深色文字，否则按主题默认色（深色主题白字，浅色主题黑字）
			// 解决深色主题下，连线占用（浅蓝/粉色等亮色背景）白字看不清的问题
			boolean adaptDarkText = highlightColor != null && isLightColor(highlightColor);
			Color rowColumnNameColor = adaptDarkText ? new Color(0x222222) : columnNameColor;
			Color rowTypeColor = adaptDarkText ? new Color(0x0E5A8E) : typeColor;
			Color rowColonColor = adaptDarkText ? new Color(0x666666) : colonColor;
			Color rowCommentColor = adaptDarkText ? new Color(0x555555) : commentColor;

			// 1. 字段图标（按 主键/可空/索引 5 种组合）
			int iconX = leftX;
			int iconY = (int) (rowCenterY - 7);
			Icon colIcon = resolveColumnIcon(col, isDark);
			if (colIcon != null) {
				colIcon.paintIcon(null, g2d, iconX, iconY);
			}
			int colTextX = iconX + 20; // 图标和列名间距加大

			// 2. 列名 + 冒号 + 类型
			g2d.setColor(rowColumnNameColor);
			g2d.setFont(columnFont);
			g2d.drawString(col.getName(), colTextX, textY);

			int nameW = colFm.stringWidth(col.getName());
			int colonX = colTextX + nameW;
			g2d.setColor(rowColonColor);
			g2d.drawString(" : ", colonX, textY);

			int colonW = colFm.stringWidth(" : ");
			int typeX = colonX + colonW;
			g2d.setColor(rowTypeColor);
			g2d.drawString(col.getType(), typeX, textY);

			int typeW = colFm.stringWidth(col.getType());

			// 3. 注释（斜体灰色）
			//    2026-09-24 不截断：整句画出，允许溢出到卡片右侧（用户要求"不省略、也不加宽卡片"）；
			//    原来还有 `maxCommentW > 10` 才画的门槛，注释一长甚至会被整条丢掉，一并去掉
			String comment = col.getComment();
			if (comment != null && !comment.isEmpty()) {
				g2d.setFont(italicCommentFont);
				g2d.setColor(rowCommentColor);
				g2d.drawString("/* " + comment + " */", typeX + typeW + 6, textY);
			}
		}

		// 渲染同步中被删除的列（浅红色背景 + 删除线/中划线）
		for (int i = 0; i < deletedColumns.size() && (rowCount + i) < maxRows; i++) {
			ColumnInfo delCol = deletedColumns.get(i);
			double rowTop = bodyTop + (rowCount + i) * rowHeight;
			double rowCenterY = rowTop + rowHeight / 2;
			int textY = (int) (rowCenterY + colFm.getAscent() / 2 - 1);

			// 分隔线
			g2d.setColor(separatorColor);
			g2d.drawLine((int) bounds.getX(), (int) rowTop,
					(int) (bounds.getX() + bounds.getWidth()), (int) rowTop);

			// 浅红色背景
			Color delBg = isDark ? new Color(0x4A2E2E) : new Color(0xF8D7DA);
			g2d.setColor(delBg);
			g2d.fillRect(
					(int) bounds.getX() + 1,
					(int) rowTop + 1,
					(int) bounds.getWidth() - 2,
					rowHeight - 1);

			Color delTextColor = isDark ? new Color(0xE08080) : new Color(0x721C24);

			int iconX = leftX;
			int iconY = (int) (rowCenterY - 7);
			Icon colIcon = resolveColumnIcon(delCol, isDark);
			if (colIcon != null) {
				colIcon.paintIcon(null, g2d, iconX, iconY);
			}
			int colTextX = iconX + 20;

			g2d.setColor(delTextColor);
			g2d.setFont(columnFont);
			g2d.drawString(delCol.getName(), colTextX, textY);

			int nameW = colFm.stringWidth(delCol.getName());
			int colonX = colTextX + nameW;
			g2d.drawString(" : ", colonX, textY);

			int colonW = colFm.stringWidth(" : ");
			int typeX = colonX + colonW;
			g2d.drawString(delCol.getType(), typeX, textY);

			int typeW = colFm.stringWidth(delCol.getType());

			String comment = delCol.getComment();
			if (comment != null && !comment.isEmpty()) {
				// 2026-09-24 同上：不截断、不设宽度门槛
				g2d.setFont(italicCommentFont);
				g2d.drawString("/* " + comment + " */", typeX + typeW + 6, textY);
			}

			// 画贯穿一整行的删除线（中划线）
			g2d.setStroke(new BasicStroke(1.2f));
			g2d.drawLine(leftX, (int) rowCenterY, (int) (bounds.getX() + bounds.getWidth() - padding), (int) rowCenterY);
		}

		// 列数过多提示
		if (columns.size() > rowCount) {
			g2d.setColor(commentColor);
			g2d.setFont(italicCommentFont);
			g2d.drawString(DataChartBundle.message("DataChart.card.truncatedColumns",
					String.valueOf(columns.size())), leftX, (int) maxBodyY);
		}

		// 最后一行下方的分隔线（与上方各行分隔线一致，闭合卡片 body）
		if (rowCount > 0) {
			double lastRowBottom = bodyTop + rowCount * rowHeight;
			g2d.setColor(separatorColor);
			g2d.drawLine((int) bounds.getX(), (int) lastRowBottom,
					(int) (bounds.getX() + bounds.getWidth()), (int) lastRowBottom);
		}
	}

	/**
	 * 绘制通用 Header（圆角彩色顶条）
	 */
	private void drawHeader(Graphics2D g2d, boolean isDark) {
		// Header 背景（按主题选择，不按 type 区分）
		Color headerBg = isDark ? HEADER_BG_DARK : HEADER_BG_LIGHT;
		g2d.setColor(headerBg);
		g2d.fillRoundRect(
				(int) bounds.getX(),
				(int) bounds.getY(),
				(int) bounds.getWidth(),
				headerHeight,
				8, 8);
		// 抹掉 header 底部的圆角
		g2d.fillRect(
				(int) bounds.getX(),
				(int) bounds.getY() + headerHeight - 8,
				(int) bounds.getWidth(),
				8);
	}

	/**
	 * 根据字段属性解析对应的图标（5 种组合）
	 *
	 * <ul>
	 *   <li>主键 → colGoldKeyDotIndex（金钥匙，优先匹配）</li>
	 *   <li>可空 + 是索引 → colIndex</li>
	 *   <li>可空 + 不是索引 → dataColumn</li>
	 *   <li>不可空 + 是索引 → colDotIndex</li>
	 *   <li>不可空 + 不是索引 → colDot</li>
	 * </ul>
	 */
	private static Icon resolveColumnIcon(ColumnInfo col, boolean isDark) {
		if (col.isPrimaryKey()) {
			return isDark ? PluginIcons.colGoldKeyDotIndex_dark : PluginIcons.colGoldKeyDotIndex;
		}
		if (col.isIndexed() && col.isNullable()) {
			return PluginIcons.colIndex; // 无 _dark 版
		}
		if (col.isIndexed()) {
			return isDark ? PluginIcons.colDotIndex_dark : PluginIcons.colDotIndex;
		}
		if (col.isNullable()) {
			return PluginIcons.dataColumn; // 无 _dark 版
		}
		return isDark ? PluginIcons.colDot_dark : PluginIcons.colDot;
	}

	/**
	 * 简单文本换行（按像素宽度切分）
	 */
	private static String[] wrapText(String text, int maxWidth, FontMetrics fm) {
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (char c : text.toCharArray()) {
			current.append(c);
			if (fm.stringWidth(current.toString()) > maxWidth) {
				current.deleteCharAt(current.length() - 1);
				lines.add(current.toString());
				current.setLength(0);
				current.append(c);
			}
		}
		if (current.length() > 0) {
			lines.add(current.toString());
		}
		return lines.toArray(new String[0]);
	}

	/**
	 * 按宽度截断文本（省略号结尾）。
	 *
	 * <p>⚠️ 2026-09-24 起<b>不再被调用</b>：用户要求注释"不省略"，改为整句绘制、允许溢出卡片右侧
	 * （同时明确要求"不用加表宽度"，所以卡片宽度也保持固定 280）。
	 * 方法保留备用 —— 若哪天要恢复限宽，直接把它套回 {@code drawTableCard} 的注释绘制处即可。</p>
	 */
	private static String truncateByWidth(String text, int maxWidth, FontMetrics fm) {
		if (fm.stringWidth(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisW = fm.stringWidth(ellipsis);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			sb.append(text.charAt(i));
			if (fm.stringWidth(sb.toString() + ellipsis) > maxWidth) {
				return sb.substring(0, sb.length() - 1) + ellipsis;
			}
		}
		return text;
	}

	/**
	 * 工具方法：加载图片（用于未来扩展图标）
	 */
	@SuppressWarnings("unused")
	private static Image loadImage(String path) {
		try {
			java.net.URL url = KanbanCard.class.getResource(path);
			if (url != null) {
				return new ImageIcon(url).getImage();
			}
		} catch (Exception ignore) {
		}
		return null;
	}
}