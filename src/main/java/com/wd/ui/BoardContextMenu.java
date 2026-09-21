package com.wd.ui;

import com.intellij.ui.JBColor;
import com.wd.db.ColumnInfo;
import com.wd.db.TableInfo;
import com.wd.db.TableNavigator;
import com.wd.model.RelationType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;

/**
 * 看板右键菜单构建：统一生成与当前主题适配的弹出菜单。
 *
 * <p>从 {@link KanbanBoard} 抽取，职责单一：</p>
 * <ul>
 *   <li>连线右键菜单（关系类型 + 删除）</li>
 *   <li>表头右键菜单（复制表名 / 复制注释）</li>
 *   <li>列行右键菜单（复制列名 / 复制注释）</li>
 *   <li>主题适配的菜单样式（修复 hover 白字问题）</li>
 * </ul>
 *
 * @author lww
 */
public final class BoardContextMenu {

	/**
	 * 菜单项 hover/selected 时的文字颜色。
	 *
	 * <p>浅色主题用深蓝 {@code #2470B0}（与列类型文字色一致）；深色主题用更亮的
	 * {@code #4A90E2}，否则深蓝压在暗背景上对比度不足看不清（与
	 * {@link FlatCheckBoxMenuItem#CHECKED_FILL} 的双态色系保持一致）。</p>
	 */
	private static final java.awt.Color MENU_HOVER_FOREGROUND = new JBColor(
			new java.awt.Color(0x2470B0), // 浅色：深蓝
			new java.awt.Color(0x4A90E2)); // 深色：亮蓝

	/** 危险操作菜单项（删除表）文字颜色：红色，适配深色/浅色主题 */
	private static final java.awt.Color DELETE_FOREGROUND = new JBColor(
			new java.awt.Color(0xC62828), // 浅色主题：深红
			new java.awt.Color(0xFF6B6B)); // 深色主题：亮红

	/** 是否已对 UIManager 设置过 menu 颜色（避免重复设置） */
	private static boolean menuUiPatched = false;

	private BoardContextMenu() {
	}

	/**
	 * 复制文本到系统剪贴板。
	 */
	public static void copyToClipboard(String text) {
		if (text == null) {
			return;
		}
		StringSelection selection = new StringSelection(text);
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(selection, null);
	}

	/**
	 * 创建连线右键菜单（关系类型子菜单 + 删除）。
	 *
	 * @param conn           目标连线
	 * @param onRepaint      重绘回调
	 * @param onNotifyChanged 内容变更通知回调
	 * @param onRemove       删除连线回调
	 */
	public static JPopupMenu buildConnectionMenu(Connection conn,
			Runnable onRepaint, Runnable onNotifyChanged, Runnable onRemove) {
		JPopupMenu menu = buildStyledPopupMenu();

		// 关系类型子菜单（自绘父项，见 FlatMenu）
		JMenu typeMenu = new FlatMenu("关系类型");
		addRelationTypeItem(typeMenu, "一对一", RelationType.ONE_TO_ONE, conn, onRepaint, onNotifyChanged);
		addRelationTypeItem(typeMenu, "一对多", RelationType.ONE_TO_MANY, conn, onRepaint, onNotifyChanged);
		addRelationTypeItem(typeMenu, "多对一", RelationType.MANY_TO_ONE, conn, onRepaint, onNotifyChanged);
		addRelationTypeItem(typeMenu, "多对多", RelationType.MANY_TO_MANY, conn, onRepaint, onNotifyChanged);
		menu.add(typeMenu);

		menu.addSeparator();

		JMenuItem deleteItem = buildStyledMenuItem("删除连线");
		deleteItem.addActionListener(e -> onRemove.run());
		menu.add(deleteItem);

		return menu;
	}

	/**
	 * 向关系类型菜单添加一项，选中后修改连线端点形状。
	 */
	private static void addRelationTypeItem(JMenu parent, String label,
			RelationType type, Connection conn, Runnable onRepaint, Runnable onNotifyChanged) {
		// 使用 FlatCheckBoxMenuItem 自绘勾选框，避免 IntelliJ 主题下 Swing L&F 默认勾选框
		// 颜色与菜单背景对比度过低（浅色下显示为深灰填充，看不清勾选状态）。
		FlatCheckBoxMenuItem item = new FlatCheckBoxMenuItem(label, conn.getRelationType() == type);
		item.putClientProperty("MenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		item.putClientProperty("MenuItem.selectionBackground", JBColor.background());
		item.addActionListener(e -> {
			conn.setRelationType(type);
			onRepaint.run();
			onNotifyChanged.run();
		});
		parent.add(item);
	}

	/**
	 * 跳转动作回调。
	 *
	 * <p>把「点击了哪个跳转项」与「具体怎么跳」解耦：菜单只负责构建 UI 与传 actionId，
	 * 实际执行由调用方委托给 {@link TableNavigator}（复用 Database 插件原生 Action）。</p>
	 */
	@FunctionalInterface
	public interface NavigateAction {
		/**
		 * @param actionId Database 插件动作 id，取值见 {@link TableNavigator}
		 * @return 是否执行成功
		 */
		boolean run(String actionId);
	}

	/**
	 * 创建表头右键菜单（复制表名 / 复制注释 / 同步表结构 / 跳转 / 删除表）。
	 *
	 * <p>「跳转」子菜单复用 Database 插件自带动作（跳到 DDL / 查看数据 /
	 * 在 Database Explorer 中定位），未安装 Database 插件时整组不显示。</p>
	 *
	 * @param info         表元信息
	 * @param onSyncStructure 同步表结构回调（重新获取元信息并刷新卡片）；可为 null 表示不显示该项
	 * @param onDeleteTable 删除表回调（效果等同 Command+Del：有连线先提示，无连线直接删除）；可为 null 表示不显示该项
	 * @param navigateAction 跳转回调；可为 null 表示不显示「跳转」子菜单
	 */
	public static JPopupMenu buildHeaderMenu(TableInfo info, Runnable onSyncStructure,
			Runnable onDeleteTable, NavigateAction navigateAction) {
		if (info == null) {
			return null;
		}
		JPopupMenu menu = buildStyledPopupMenu();

		JMenuItem copyName = buildStyledMenuItem("复制表名");
		copyName.addActionListener(e -> copyToClipboard(info.getName()));
		menu.add(copyName);

		String comment = info.getComment();
		JMenuItem copyComment = buildStyledMenuItem("复制注释");
		copyComment.setEnabled(comment != null && !comment.isEmpty());
		copyComment.addActionListener(e -> copyToClipboard(comment));
		menu.add(copyComment);

		if (onSyncStructure != null) {
			JMenuItem syncItem = buildStyledMenuItem("同步表结构");
			syncItem.setToolTipText("重新获取表结构信息");
			syncItem.addActionListener(e -> onSyncStructure.run());
			menu.add(syncItem);
		}

		boolean hasFindUsages = navigateAction != null
				&& TableNavigator.isActionAvailable(TableNavigator.ACTION_FIND_USAGES);
		JMenu gotoMenu = buildNavigateMenu(navigateAction);
		if (hasFindUsages || gotoMenu != null) {
			menu.addSeparator();
		}
		if (hasFindUsages) {
			JMenuItem findUsagesItem = buildStyledMenuItem("查找用法");
			findUsagesItem.addActionListener(e -> navigateAction.run(TableNavigator.ACTION_FIND_USAGES));
			menu.add(findUsagesItem);
		}
		if (gotoMenu != null) {
			menu.add(gotoMenu);
		}

		if (onDeleteTable != null) {
			menu.addSeparator();
			JMenuItem deleteItem = buildStyledMenuItem("删除表");
			deleteItem.setForeground(DELETE_FOREGROUND);
			deleteItem.putClientProperty("MenuItem.selectionForeground", DELETE_FOREGROUND);
			deleteItem.setToolTipText("删除该表（有连线时会先提示）");
			deleteItem.addActionListener(e -> onDeleteTable.run());
			menu.add(deleteItem);
		}

		return menu;
	}

	/**
	 * 创建「跳转」子菜单（复用 Database 插件原生动作：跳到 DDL / 查看数据 /
	 * 在 Database Explorer 中定位）。
	 *
	 * <p>未安装 / 未启用 Database 插件（动作取不到）时返回 null，调用方不显示该子菜单。</p>
	 */
	private static JMenu buildNavigateMenu(NavigateAction navigateAction) {
		if (navigateAction == null) {
			return null;
		}
		boolean hasDdl = TableNavigator.isActionAvailable(TableNavigator.ACTION_OPEN_DDL);
		boolean hasData = TableNavigator.isActionAvailable(TableNavigator.ACTION_OPEN_DATA);
		boolean hasExplorer = TableNavigator.isActionAvailable(TableNavigator.ACTION_SELECT_IN_DATABASE_VIEW);
		if (!hasDdl && !hasData && !hasExplorer) {
			return null;
		}

		// 与「关系类型」子菜单保持一致的样式
		JMenu gotoMenu = new FlatMenu("跳转");

		if (hasDdl) {
			gotoMenu.add(buildNavigateItem("跳到 DDL", TableNavigator.ACTION_OPEN_DDL, navigateAction));
		}
		if (hasData) {
			gotoMenu.add(buildNavigateItem("查看数据", TableNavigator.ACTION_OPEN_DATA, navigateAction));
		}
		if (hasExplorer) {
			gotoMenu.add(buildNavigateItem("在 Database Explorer 中定位",
					TableNavigator.ACTION_SELECT_IN_DATABASE_VIEW, navigateAction));
		}
		return gotoMenu;
	}

	/**
	 * 创建一个跳转菜单项：点击后把 actionId 交给调用方执行。
	 */
	private static JMenuItem buildNavigateItem(String label, String actionId, NavigateAction navigateAction) {
		JMenuItem item = buildStyledMenuItem(label);
		item.addActionListener(e -> navigateAction.run(actionId));
		return item;
	}

	/**
	 * 创建列行右键菜单（复制列名 / 复制注释）。
	 */
	public static JPopupMenu buildColumnMenu(ColumnInfo col) {
		if (col == null) {
			return null;
		}
		JPopupMenu menu = buildStyledPopupMenu();

		JMenuItem copyName = buildStyledMenuItem("复制列名");
		copyName.addActionListener(e -> copyToClipboard(col.getName()));
		menu.add(copyName);

		String comment = col.getComment();
		JMenuItem copyComment = buildStyledMenuItem("复制注释");
		copyComment.setEnabled(comment != null && !comment.isEmpty());
		copyComment.addActionListener(e -> copyToClipboard(comment));
		menu.add(copyComment);

		return menu;
	}

	/**
	 * 创建一个与当前主题适配的 JPopupMenu（修复 hover 文字看不清）。
	 */
	private static JPopupMenu buildStyledPopupMenu() {
		patchMenuUiDefaults();
		JPopupMenu menu = new JPopupMenu();
		menu.setForeground(JBColor.foreground());
		menu.setBackground(JBColor.background());
		return menu;
	}

	/**
	 * 创建一个菜单项，hover/selected 文字颜色固定为蓝色（修复白字问题）。
	 */
	private static JMenuItem buildStyledMenuItem(String label) {
		patchMenuUiDefaults();
		JMenuItem item = new JMenuItem(label);
		item.setForeground(JBColor.foreground());
		item.setBackground(JBColor.background());
		item.setOpaque(true);
		item.setSelected(false);
		item.putClientProperty("MenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		item.putClientProperty("MenuItem.selectionBackground", JBColor.background());
		return item;
	}

	/**
	 * 改 {@code UIManager} 的全局 menu 默认值，覆盖 L&F 的硬编码白色。
	 */
	private static void patchMenuUiDefaults() {
		if (menuUiPatched) {
			return;
		}
		javax.swing.UIDefaults defaults = javax.swing.UIManager.getDefaults();
		defaults.put("MenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		defaults.put("MenuItem.selectionBackground", JBColor.background());
		defaults.put("Menu.selectionForeground", MENU_HOVER_FOREGROUND);
		defaults.put("Menu.selectionBackground", JBColor.background());
		defaults.put("MenuItem.acceleratorSelectionForeground", MENU_HOVER_FOREGROUND);
		defaults.put("MenuItem.acceleratorForeground", MENU_HOVER_FOREGROUND);
		defaults.put("CheckBoxMenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		defaults.put("CheckBoxMenuItem.selectionBackground", JBColor.background());
		defaults.put("RadioButtonMenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		defaults.put("RadioButtonMenuItem.selectionBackground", JBColor.background());
		menuUiPatched = true;
	}

	/**
	 * 自绘背景的子菜单（父项）：修复「跳转 / 关系类型」整行在子菜单展开或悬停时
	 * 被系统强调色填充的问题。
	 *
	 * <p><b>问题（2026-09-21）</b>：IntelliJ 只为 {@code JMenuItem} 提供了自己的 UI 实现，
	 * {@code JMenu} 仍走 Swing L&amp;F 的菜单实现，其选中背景取 {@code Menu.selectionBackground}；
	 * macOS 下该值就是<b>系统强调色</b> —— 用户把强调色设成粉色时，整行（含右侧箭头区）
	 * 都会变成粉色，与兄弟菜单项「菜单底色 + 蓝字」的风格完全不一致；
	 * 而 {@code UIManager} 层面的 patch 并不可靠（UI 安装时可能已缓存颜色）。</p>
	 *
	 * <p><b>方案</b>：Swing 的 {@code BasicMenuItemUI.paintBackground} 只在
	 * {@code menuItem.isOpaque()} 为 true 时才填充背景，因此这里
	 * <b>关掉 opaque</b> 让 L&amp;F 不再填充强调色，改由本类自绘底色
	 * （{@link JBColor#background()}，随主题自动切换深/浅）；</p>
	 * <ul>
	 *   <li>文字、右侧子菜单箭头、内边距（insets）<b>仍由 L&amp;F 绘制</b> —— 与兄弟菜单项
	 *       的字体、对齐、箭头样式完全一致，不需要自己算 textX / preferredSize；</li>
	 *   <li>悬停/展开时的文字色取 {@code Menu.selectionForeground}，
	 *       已由 {@link #patchMenuUiDefaults()} 统一成 {@link #MENU_HOVER_FOREGROUND}（双态色）。</li>
	 * </ul>
	 */
	private static final class FlatMenu extends JMenu {

		FlatMenu(String text) {
			super(text);
			// 关掉 opaque → L&F 跳过背景填充（这一步是消除"整行粉色"的关键）
			setOpaque(false);
			setForeground(JBColor.foreground());
			setBackground(JBColor.background());
		}

		@Override
		protected void paintComponent(Graphics g) {
			// 1. 自己铺底色（L&F 因 opaque=false 已不再填背景）
			Graphics2D g2d = (Graphics2D) g.create();
			try {
				g2d.setColor(getBackground());
				g2d.fillRect(0, 0, getWidth(), getHeight());
			} finally {
				g2d.dispose();
			}
			// 2. 交给 L&F 画文字 + 箭头（保留其排版与配色逻辑，确保与兄弟项对齐）
			super.paintComponent(g);
		}
	}

	/**
	 * 自定义勾选菜单项：完整自绘勾选框，绕开 Swing L&F 的 {@code CheckBoxMenuItemUI}，
	 * 使其在 IntelliJ 浅色 / 深色主题下都有清晰的勾选视觉。
	 *
	 * <p>问题（2026-08-03 修复）：默认 L&F 渲染的勾选框在浅色主题下用 {@code Gray._40}
	 * 深灰填充方框作为"勾"，与白色菜单背景对比度低，看不出勾选状态。本类直接用
	 * {@code Graphics2D} 画一个清晰的"蓝底白对勾"（选中）或"白底灰边方框"（未选中），
	 * 颜色由 {@link JBColor} 双态自动适配主题。</p>
	 *
	 * <p>设计要点：</p>
	 * <ul>
	 *   <li>保留 {@link JCheckBoxMenuItem} 的 {@code isSelected}/{@code setSelected} 行为，
	 *       原有 ActionListener / Model 业务逻辑不需要改</li>
	 *   <li>勾选框大小 = 14×14，与 Swing 默认 CheckBox 视觉尺寸一致</li>
	 *   <li>文字、选中态、disabled 态仍由 Swing L&F 渲染，视觉与普通 JMenuItem 协调</li>
	 *   <li>勾选框在文字左侧预留 18px（与 L&F 默认对齐），不影响整行布局</li>
	 * </ul>
	 */
	private static final class FlatCheckBoxMenuItem extends JCheckBoxMenuItem {

		/** 勾选框尺寸（画板坐标像素） */
		private static final int BOX_SIZE = 14;
		/** 文字左侧给勾选框预留的宽度（含间距） */
		private static final int BOX_LEFT_PADDING = 18;

		/** 选中态方框填充色：浅色深蓝 / 深色亮蓝，与菜单 hover 文字色系一致 */
		private static final Color CHECKED_FILL = new JBColor(
				new Color(0x2470B0), // 浅色：与 MENU_HOVER_FOREGROUND 同色
				new Color(0x4A90E2)); // 深色：稍亮，避免与暗背景对比不足
		/** 未选中态方框边框色：浅色中灰 / 深色浅灰 */
		private static final Color UNCHECKED_BORDER = new JBColor(
				new Color(0xB0B0B0),
				new Color(0x6B6B6B));
		/** 未选中态方框填充色：浅色白 / 深色跟随菜单背景（用 background，不透明） */
		private static final Color UNCHECKED_FILL = new JBColor(
				new Color(0xFFFFFF),
				new Color(0x3C3F41));
		/** 对勾颜色：固定白色，对蓝底始终清晰 */
		private static final Color CHECK_MARK_COLOR = new Color(0xFFFFFF);

		FlatCheckBoxMenuItem(String text, boolean selected) {
			super(text, selected);
			// 关键：让父类 L&F 不再画默认 checkIcon（实际靠 BasicMenuItemUI 缓存 checkIcon，
			// 关闭 opaque 让自定义 paint 接管整个 cell 渲染）
			setOpaque(true);
			setBorderPainted(false);
			// 使用 JBColor 让文字随主题切换；背景由我们自绘
			setForeground(JBColor.foreground());
		}

		@Override
		public java.awt.Dimension getPreferredSize() {
			// 自绘勾选框后 Swing 默认的 BasicMenuItemUI.getPreferredSize() 不再可靠
			//（它依赖 checkIcon，而这里我们关掉了），结果会导致子菜单宽度按默认 icon 0 宽算，
			// 在 Win 系统下出现"一对多/多对多"被截断的问题。这里手动算：勾选框占位 + 文字宽度 + 边距。
			java.awt.FontMetrics fm = getFontMetrics(getFont());
			String text = getText();
			int textWidth = (text == null) ? 0 : fm.stringWidth(text);
			int width = BOX_LEFT_PADDING + BOX_SIZE + 6 + textWidth + 12;
			int height = Math.max(BOX_SIZE + 8, fm.getHeight() + 6);
			return new java.awt.Dimension(width, height);
		}

		@Override
		public void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				// 抗锯齿
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
						RenderingHints.VALUE_STROKE_PURE);

				// 1. 整行背景：先画菜单背景（避免后续绘制时露出组件默认色）
				Color rowBg = isArmed() || isSelected()
						? UIManager.getColor("MenuItem.selectionBackground")
						: getBackground();
				if (rowBg == null) {
					rowBg = JBColor.background();
				}
				g2.setColor(rowBg);
				g2.fillRect(0, 0, getWidth(), getHeight());

				// 2. 勾选框：垂直居中于行高
				int boxY = (getHeight() - BOX_SIZE) / 2;
				drawCheckBox(g2, BOX_LEFT_PADDING, boxY);

				// 3. 文字：从 BOX_LEFT_PADDING + BOX_SIZE + 间距 开始绘制
				//    用 Swing 内部 BasicMenuItemUI 渲染文字以保持和其它菜单项一致
				//    简化做法：直接用 Graphics2D.drawString，文字颜色取 foreground
				g2.setColor(getForeground());
				int textX = BOX_LEFT_PADDING + BOX_SIZE + 6;
				int textY = computeTextY(g2);
				String text = getText();
				if (text != null) {
					g2.drawString(text, textX, textY);
				}
			} finally {
				g2.dispose();
			}
		}

		/**
		 * 绘制勾选框（含对勾 / 空框 / disabled 三态）。
		 */
		private void drawCheckBox(Graphics2D g2, int x, int y) {
			boolean selected = isSelected();
			// 未选中：白/灰底 + 灰边；选中：蓝底（无独立边框）+ 白对勾
			if (selected) {
				g2.setColor(CHECKED_FILL);
				g2.fillRoundRect(x, y, BOX_SIZE, BOX_SIZE, 3, 3);
			} else {
				g2.setColor(UNCHECKED_FILL);
				g2.fillRoundRect(x, y, BOX_SIZE, BOX_SIZE, 3, 3);
				g2.setColor(UNCHECKED_BORDER);
				Stroke old = g2.getStroke();
				g2.setStroke(new BasicStroke(1.0f));
				g2.drawRoundRect(x, y, BOX_SIZE - 1, BOX_SIZE - 1, 3, 3);
				g2.setStroke(old);
			}

			// 对勾：只在选中时画
			if (selected) {
				g2.setColor(CHECK_MARK_COLOR);
				Stroke old = g2.getStroke();
				g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				// 14x14 方框内画 √ ：从 (x+3, y+7) -> (x+6, y+10) -> (x+11, y+4)
				int[] xs = {x + 3, x + 6, x + 11};
				int[] ys = {y + 7, y + 10, y + 4};
				g2.drawPolyline(xs, ys, 3);
				g2.setStroke(old);
			}
		}

		/**
		 * 文字基线 Y 坐标：让文字垂直居中（与 Swing 默认 JMenuItem 行为一致）。
		 */
		private int computeTextY(Graphics2D g2) {
			java.awt.FontMetrics fm = g2.getFontMetrics(getFont());
			int textHeight = fm.getAscent() - fm.getDescent();
			return (getHeight() - textHeight) / 2 + fm.getAscent() - 1;
		}
	}
}
