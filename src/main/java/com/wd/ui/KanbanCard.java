package com.wd.ui;

import com.wd.db.ColumnInfo;
import com.wd.db.TableInfo;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.ImageIcon;

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

	// 样式
	private int headerHeight = 28;
	private int padding = 10;
	private Font headerFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
	private Font bodyFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	private Font columnFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	private Font commentFont = new Font(Font.SANS_SERIF, Font.ITALIC, 10);

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
	private static final Color PRIMARY_COLOR = new Color(0xE8B548);

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
		drawHeader(g2d, Color.WHITE);

		// 边框
		g2d.setColor(border);
		g2d.setStroke(new BasicStroke(selected ? 2f : 1f));
		g2d.drawRoundRect(
				(int) bounds.getX(),
				(int) bounds.getY(),
				(int) bounds.getWidth(),
				(int) bounds.getHeight(),
				8, 8);

		// Header 文字
		g2d.setColor(Color.WHITE);
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
		drawHeader(g2d, Color.WHITE);

		// 边框
		g2d.setColor(border);
		g2d.setStroke(new BasicStroke(selected ? 2f : 1f));
		g2d.drawRoundRect(
				(int) bounds.getX(),
				(int) bounds.getY(),
				(int) bounds.getWidth(),
				(int) bounds.getHeight(),
				8, 8);

		// Header 文字 - 表名（带 PK 图标风格）
		g2d.setColor(Color.WHITE);
		g2d.setFont(headerFont);
		FontMetrics headerFm = g2d.getFontMetrics();
		String tableName = tableInfo.getName();
		String tableComment = tableInfo.getComment();
		String headerText = tableName;
		if (tableComment != null && !tableComment.isEmpty()) {
			headerText = tableName + "  /  * " + tableComment + " *";
		}
		int headerTextX = (int) bounds.getX() + padding;
		int headerTextY = (int) bounds.getY() + (headerHeight + headerFm.getAscent() - headerFm.getDescent()) / 2;
		g2d.drawString(headerText, headerTextX, headerTextY);

		// Header 右侧显示 schema 标识（小字）
		if (tableInfo.getSchema() != null && !tableInfo.getSchema().isEmpty()) {
			String schema = "[" + tableInfo.getSchema() + "]";
			int schemaW = headerFm.stringWidth(schema);
			g2d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
			g2d.setColor(new Color(255, 255, 255, 200));
			g2d.drawString(schema,
					(int) (bounds.getX() + bounds.getWidth() - padding - schemaW),
					(int) (bounds.getY() + headerHeight - 6));
			g2d.setFont(headerFont);
		}

		// Body：列定义列表
		List<ColumnInfo> columns = tableInfo.getColumns();
		int bodyY = (int) bounds.getY() + headerHeight + padding;
		int leftX = (int) bounds.getX() + padding;
		int maxBodyY = (int) (bounds.getY() + bounds.getHeight() - padding);

		// 列定义区底色（极淡分隔）
		g2d.setColor(separatorColor);
		g2d.drawLine(leftX - 2, bodyY - 4,
				(int) (bounds.getX() + bounds.getWidth()) - padding, bodyY - 4);

		g2d.setFont(columnFont);
		FontMetrics colFm = g2d.getFontMetrics();

		int maxRows = (maxBodyY - bodyY) / 18;
		int rowCount = Math.min(columns.size(), maxRows);

		for (int i = 0; i < rowCount; i++) {
			ColumnInfo col = columns.get(i);
			int rowY = bodyY + colFm.getAscent();

			// 1. 主键图标（金色小方块）
			if (col.isPrimaryKey()) {
				g2d.setColor(PRIMARY_COLOR);
				g2d.fillRect(leftX, rowY - 9, 5, 11);
			} else if (col.isIndexed()) {
				// 索引图标（灰色小圆点）
				g2d.setColor(isDark ? new Color(0x999999) : new Color(0xBBBBBB));
				g2d.fillOval(leftX, rowY - 7, 6, 6);
			}

			// 2. 列名
			g2d.setColor(textColor);
			String colName = col.getName();
			g2d.drawString(colName, leftX + 12, rowY);

			// 3. 类型
			String type = col.getType();
			int typeX = leftX + 130;
			g2d.setColor(commentColor);
			g2d.drawString(type, typeX, rowY);

			// 4. 注释（超出宽度截断）
			String comment = col.getComment();
			if (comment != null && !comment.isEmpty()) {
				g2d.setFont(commentFont);
				FontMetrics cmtFm = g2d.getFontMetrics();
				int cmtX = typeX + 80;
				int maxCmtW = (int) (bounds.getX() + bounds.getWidth() - padding - cmtX);
				String truncated = truncateByWidth(comment, maxCmtW, cmtFm);
				g2d.drawString(truncated, cmtX, rowY);
				g2d.setFont(columnFont);
			}

			bodyY += 18;
		}

		// 列数过多提示
		if (columns.size() > rowCount) {
			g2d.setColor(commentColor);
			g2d.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
			g2d.drawString("... 共 " + columns.size() + " 列",
					leftX, maxBodyY - 4);
		}
	}

	/**
	 * 绘制通用 Header（圆角彩色顶条）
	 */
	private void drawHeader(Graphics2D g2d, Color textColor) {
		// Header 区域（顶部彩色条）
		g2d.setColor(getHeaderColor());
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