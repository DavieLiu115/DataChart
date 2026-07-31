package com.wd.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Rectangle2D;
import javax.swing.ImageIcon;

/**
 * 看板中的图表卡片元素
 *
 * @author lww
 */
public class KanbanCard {

	/** 卡片唯一 ID */
	private final String id;

	/** 图表名称 */
	private String name;

	/** 图表类型（如 line/bar/pie/scatter） */
	private String type;

	/** 描述 */
	private String description;

	/** 卡片位置和大小 */
	private Rectangle2D bounds;

	/** 选中状态 */
	private boolean selected;

	// 样式
	private int headerHeight = 28;
	private int padding = 10;
	private Font headerFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
	private Font bodyFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

	// 按 type 区分的配色（亮色 / 深色各一套，适配主题）
	private static final Color HEADER_LINE = new Color(0x4A90E2);
	private static final Color HEADER_BAR = new Color(0xF5A623);
	private static final Color HEADER_PIE = new Color(0x7ED321);
	private static final Color HEADER_SCATTER = new Color(0x9013FE);
	private static final Color HEADER_DEFAULT = new Color(0x9B9B9B);
	private static final Color BG_LIGHT = new Color(0xFFFFFF);
	private static final Color BG_DARK = new Color(0x3C3F41);
	private static final Color BORDER = new Color(0xCCCCCC);
	private static final Color BORDER_SELECTED = new Color(0x4A90E2);

	/**
	 * 构造方法
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
		Color bg = isDark ? BG_DARK : BG_LIGHT;
		Color border = selected ? BORDER_SELECTED : BORDER;

		// 1. 阴影（轻微的右下偏移增强立体感）
		g2d.setColor(new Color(0, 0, 0, 30));
		g2d.fillRoundRect(
				(int) bounds.getX() + 2,
				(int) bounds.getY() + 2,
				(int) bounds.getWidth(),
				(int) bounds.getHeight(),
				8, 8);

		// 2. 卡片背景
		g2d.setColor(bg);
		g2d.fillRoundRect(
				(int) bounds.getX(),
				(int) bounds.getY(),
				(int) bounds.getWidth(),
				(int) bounds.getHeight(),
				8, 8);

		// 3. Header 区域（顶部彩色条）
		g2d.setColor(getHeaderColor());
		g2d.fillRoundRect(
				(int) bounds.getX(),
				(int) bounds.getY(),
				(int) bounds.getWidth(),
				headerHeight,
				8, 8);
		// 抹掉 header 底部的圆角，让 header 与 body 平滑衔接
		g2d.fillRect(
				(int) bounds.getX(),
				(int) bounds.getY() + headerHeight - 8,
				(int) bounds.getWidth(),
				8);

		// 4. 边框
		g2d.setColor(border);
		g2d.setStroke(new BasicStroke(selected ? 2f : 1f));
		g2d.drawRoundRect(
				(int) bounds.getX(),
				(int) bounds.getY(),
				(int) bounds.getWidth(),
				(int) bounds.getHeight(),
				8, 8);

		// 5. Header 文字（图表名）
		g2d.setColor(Color.WHITE);
		g2d.setFont(headerFont);
		FontMetrics headerFm = g2d.getFontMetrics();
		String headerText = name == null ? "(未命名)" : name;
		int headerTextX = (int) bounds.getX() + padding;
		int headerTextY = (int) bounds.getY() + (headerHeight + headerFm.getAscent() - headerFm.getDescent()) / 2;
		g2d.drawString(headerText, headerTextX, headerTextY);

		// 6. Body 区域（type + description）
		g2d.setColor(isDark ? new Color(0xBBBBBB) : new Color(0x555555));
		g2d.setFont(bodyFont);

		int bodyY = (int) bounds.getY() + headerHeight + padding + 5;

		// 类型徽标
		String typeText = "类型: " + (type == null ? "" : type.toUpperCase());
		g2d.drawString(typeText, (int) bounds.getX() + padding, bodyY);
		bodyY += 18;

		// 描述
		if (description != null && !description.isEmpty()) {
			FontMetrics bodyFm = g2d.getFontMetrics();
			String[] lines = wrapText(description,
					(int) bounds.getWidth() - padding * 2, bodyFm);
			int maxLines = Math.min(lines.length,
					(int) ((bounds.getHeight() - headerHeight - padding * 2 - 25) / 16));
			for (int i = 0; i < maxLines; i++) {
				g2d.drawString(lines[i],
						(int) bounds.getX() + padding, bodyY);
				bodyY += 16;
			}
		}

		// 7. 类型图标占位（右下角小色块，未来可换成真实图标）
		g2d.setColor(getHeaderColor());
		int badgeSize = 8;
		g2d.fillOval(
				(int) (bounds.getX() + bounds.getWidth() - padding - badgeSize),
				(int) (bounds.getY() + bounds.getHeight() - padding - badgeSize),
				badgeSize, badgeSize);
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