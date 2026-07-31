package com.wd.ui;

import com.intellij.ui.JBColor;
import com.wd.db.ColumnInfo;
import com.wd.db.TableInfo;
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

	/** 选中的行（-2 表示未选中，-1 表示表卡片整体被选中；>=0 表示具体列行） */
	private int selectedRowIndex = -2;

	// 样式
	private int headerHeight = 28;
	private int padding = 10;
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
	/** 行选中高亮背景色（橙色，参考 DataHelper） */
	private static final Color ROW_HIGHLIGHT_COLOR_ORANGE = new Color(0xFF9F5B);

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
		return selectedRowIndex;
	}

	public void setSelectedRowIndex(int index) {
		this.selectedRowIndex = index;
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
		int rowHeight = 18;
		double rowCenterY = bounds.getY() + headerHeight + rowIndex * rowHeight + rowHeight / 2.0;
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
		int rowHeight = 18;
		double rowCenterY = bounds.getY() + headerHeight + rowIndex * rowHeight + rowHeight / 2.0;
		double leftX = bounds.getX();
		return new java.awt.geom.Point2D.Double(leftX, rowCenterY);
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
		int rowHeight = 18;
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
		if (isTableMode()) {
			drawTableCard(g2d, isDark);
		} else {
			drawChartCard(g2d, isDark);
		}
	}

	/**
	 * 绘制图表卡片
	 */
	private void drawChartCard(Graphics2D g2d, boolean isDark) {
		Color bg = isDark ? BG_DARK : BG_LIGHT;
		Color border = selected ? BORDER_SELECTED : BORDER;
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
		String headerText = name == null ? "(未命名)" : name;
		int headerTextX = (int) bounds.getX() + padding;
		int headerTextY = (int) bounds.getY() + (headerHeight + headerFm.getAscent() - headerFm.getDescent()) / 2;
		g2d.drawString(headerText, headerTextX, headerTextY);

		// Body
		g2d.setColor(textColor);
		g2d.setFont(bodyFont);

		int bodyY = (int) bounds.getY() + headerHeight + padding + 5;
		String typeText = "类型: " + (type == null ? "" : type.toUpperCase());
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
	private void drawTableCard(Graphics2D g2d, boolean isDark) {
		Color bg = isDark ? BG_DARK : BG_LIGHT;
		Color border = selected ? BORDER_SELECTED : BORDER;
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
			int maxCommentW = (int) (bounds.getX() + bounds.getWidth() - padding - commentX);
			g2d.setFont(italicHeaderFont);
			g2d.setColor(isDark ? new Color(0xAAAAAA) : new Color(0x666666));
			FontMetrics italicFm = g2d.getFontMetrics();
			String commentText = "/* " + tableComment + " */";
			g2d.drawString(truncateByWidth(commentText, maxCommentW, italicFm),
					commentX, headerCenterY + (italicFm.getAscent() - italicFm.getDescent()) / 2);
			g2d.setFont(headerFont);
		}

		// Body：列定义列表
		List<ColumnInfo> columns = tableInfo.getColumns();
		double bodyTop = bounds.getY() + headerHeight;
		int leftX = (int) bounds.getX() + padding;
		double maxBodyY = bounds.getY() + bounds.getHeight() - padding;

		// 行高 18px（之前 20 太大，导致最后一行高度过大）
		int rowHeight = 18;

		g2d.setFont(columnFont);
		FontMetrics colFm = g2d.getFontMetrics();
		g2d.setFont(italicCommentFont);
		FontMetrics italicFm = g2d.getFontMetrics();
		g2d.setFont(columnFont);

		int maxRows = (int) ((maxBodyY - bodyTop) / rowHeight);
		int rowCount = Math.min(columns.size(), maxRows);

		// 颜色定义
		Color columnNameColor = isDark ? COLUMN_NAME_COLOR_DARK : COLUMN_NAME_COLOR;
		Color typeColor = isDark ? TYPE_COLOR_DARK : TYPE_COLOR;

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

			// 选中行高亮背景（在分隔线之后画，覆盖在卡片背景上）
			if (i == selectedRowIndex) {
				g2d.setColor(ROW_HIGHLIGHT_COLOR);
				g2d.fillRect(
						(int) bounds.getX() + 1,
						(int) rowTop + 1,
						(int) bounds.getWidth() - 2,
						rowHeight - 1);
			}

			// 1. 字段图标（按 主键/可空/索引 5 种组合）
			int iconX = leftX;
			int iconY = (int) (rowCenterY - 7);
			Icon colIcon = resolveColumnIcon(col, isDark);
			if (colIcon != null) {
				colIcon.paintIcon(null, g2d, iconX, iconY);
			}
			int colTextX = iconX + 20; // 图标和列名间距加大

			// 2. 列名（黑色）+ 冒号 + 类型（蓝色）
			g2d.setColor(columnNameColor);
			g2d.setFont(columnFont);
			g2d.drawString(col.getName(), colTextX, textY);

			int nameW = colFm.stringWidth(col.getName());
			int colonX = colTextX + nameW;
			g2d.setColor(isDark ? new Color(0x888888) : new Color(0x999999));
			g2d.drawString(" : ", colonX, textY);

			int colonW = colFm.stringWidth(" : ");
			int typeX = colonX + colonW;
			g2d.setColor(typeColor);
			g2d.drawString(col.getType(), typeX, textY);

			int typeW = colFm.stringWidth(col.getType());

			// 3. 注释（斜体灰色）
			String comment = col.getComment();
			if (comment != null && !comment.isEmpty()) {
				int cmtX = typeX + typeW + 6;
				int maxCmtW = (int) (bounds.getX() + bounds.getWidth() - padding - cmtX);
				if (maxCmtW > 10) {
					g2d.setFont(italicCommentFont);
					g2d.setColor(commentColor);
					String commentText = "/* " + comment + " */";
					g2d.drawString(truncateByWidth(commentText, maxCmtW, italicFm),
							cmtX, textY);
				}
			}
		}

		// 列数过多提示
		if (columns.size() > rowCount) {
			g2d.setColor(commentColor);
			g2d.setFont(italicCommentFont);
			g2d.drawString("... 共 " + columns.size() + " 列",
					leftX, (int) maxBodyY);
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
	 * 按宽度截断文本（省略号结尾）
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