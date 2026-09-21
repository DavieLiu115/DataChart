package com.wd.ui;

import com.intellij.ui.JBColor;
import com.wd.db.ColumnInfo;
import com.wd.db.TableInfo;
import com.wd.db.TableNavigator;
import com.wd.model.RelationType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
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

	/** 禁用态菜单项文字色：浅色中灰 / 深色亮灰 */
	private static final java.awt.Color MENU_DISABLED_FOREGROUND = new JBColor(
			new java.awt.Color(0x9E9E9E),
			new java.awt.Color(0x808080));

	/** 菜单行内文字距左边缘的距离（所有自绘菜单项共用，保证文字左对齐） */
	private static final int ROW_TEXT_LEFT = 12;
	/** 菜单行内文字距右边缘的距离 */
	private static final int ROW_TEXT_RIGHT = 12;
	/** 菜单行最小上下留白 */
	private static final int ROW_VERTICAL_PADDING = 4;
	/** 子菜单右侧箭头宽度 */
	private static final int ARROW_WIDTH = 4;
	/** 子菜单右侧箭头高度 */
	private static final int ARROW_HEIGHT = 8;
	/** 箭头距右边缘的距离 */
	private static final int ARROW_RIGHT = 10;
	/** 文字与箭头之间至少保留的间距 */
	private static final int ARROW_GAP = 16;

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
	 * 创建一个与当前主题适配的 JPopupMenu。
	 *
	 * <p>注意：弹窗与菜单项都<b>不碰 {@code UIManager} 的全局默认值</b>（历史上的
	 * {@code patchMenuUiDefaults()} 已删除）。它会往全局默认值表里写
	 * {@code MenuItem.selectionForeground} 等 key，从而可能影响 IDEA 自身菜单的配色；
	 * 而对我们自己的自绘项又完全没有作用（IntelliJ 菜单 UI 会用 {@code JBColor.namedColor}
	 * 覆盖 {@code selectionBackground}，且结果全局缓存）。</p>
	 */
	private static JPopupMenu buildStyledPopupMenu() {
		JPopupMenu menu = new JPopupMenu();
		menu.setForeground(JBColor.foreground());
		menu.setBackground(JBColor.background());
		return menu;
	}

	/**
	 * 创建一个自绘菜单项（hover/selected 时文字变蓝，背景始终是菜单底色）。
	 */
	private static JMenuItem buildStyledMenuItem(String label) {
		return new FlatMenuItem(label);
	}

	/**
	 * 自绘菜单项：彻底绕开 L&amp;F 的菜单配色。
	 *
	 * <p><b>为什么必须自绘（2026-09-21 查证字节码）</b>：IntelliJ 的菜单 UI 在
	 * {@code installDefaults()} 里直接覆盖了 {@code selectionBackground}：</p>
	 * <pre>
	 * // com.intellij.ui.plaf.beg.BegMenuItemUI / IdeaMenuUI
	 * selectionBackground = JBColor.namedColor("Menu.selectionBackground",
	 *                                          UIUtil.getListSelectionBackground(true));
	 * </pre>
	 * <p>两个关键点导致 {@code UIManager.put(...)} 和 client property <b>都改不动它</b>：</p>
	 * <ol>
	 *   <li>{@code JBColor.namedColor} 的结果被<b>全局缓存</b>（首次解析后就固定），
	 *       我们后 patch 的默认值永远读不到；</li>
	 *   <li>{@code IdeaMenuUI.fillBackground()} 里 hover 填充<b>不受 {@code isOpaque()} 控制</b>
	 *       （{@code if (armed||selected) paintHover(...)} 在 opaque 判断之外），
	 *       所以"关掉 opaque"也没用。</li>
	 * </ol>
	 * <p>结果就是 hover / 子菜单展开时整行被填成 {@code Menu.selectionBackground} 解析出来的颜色
	 * （macOS 上是系统强调色，用户设为粉色时整行变粉）。{@code paintComponent} 里
	 * <b>不调用 {@code super}</b>，L&amp;F 的绘制就完全不会发生，颜色只由本类决定。</p>
	 *
	 * <p>配色统一为「菜单底色 + 普通字，hover 时只把文字变蓝」，与
	 * {@link FlatCheckBoxMenuItem}、{@link FlatMenu} 保持一致；所有颜色用 {@link JBColor}
	 * 双态，深色 / 浅色主题自动适配。</p>
	 */
	private static class FlatMenuItem extends JMenuItem {

		FlatMenuItem(String text) {
			super(text);
			setOpaque(true);
			setForeground(JBColor.foreground());
			setBackground(JBColor.background());
		}

		/** 是否处于 hover / 被菜单选择器选中 */
		protected boolean isHighlighted() {
			return isArmed() || isSelected();
		}

		@Override
		protected void paintComponent(Graphics g) {
			// 不调 super.paintComponent：否则 L&F 会用它的 selectionBackground 盖掉整行
			paintMenuRow(g, this, isHighlighted(), false);
		}

		@Override
		public Dimension getPreferredSize() {
			// 自绘后父类按 L&F 的 checkIcon / accelerator 算尺寸不可靠，手动算
			return menuRowPreferredSize(this, false);
		}
	}

	/**
	 * 自绘子菜单（父项）：底色 + 文字 + 右侧箭头全部自绘，绘制逻辑与
	 * {@link FlatMenuItem} 共用，保证「跳转 / 关系类型」与兄弟菜单项的
	 * 文字左边界、行高、配色完全一致。
	 */
	private static final class FlatMenu extends JMenu {

		FlatMenu(String text) {
			super(text);
			setOpaque(true);
			setForeground(JBColor.foreground());
			setBackground(JBColor.background());
		}

		@Override
		protected void paintComponent(Graphics g) {
			boolean highlighted = isSelected() || getModel().isArmed() || getModel().isRollover();
			paintMenuRow(g, this, highlighted, true);
		}

		@Override
		public Dimension getPreferredSize() {
			return menuRowPreferredSize(this, true);
		}
	}

	/**
	 * 自绘一行菜单：底色 + 文字（+ 可选右侧子菜单箭头）。
	 *
	 * @param row         目标菜单项
	 * @param highlighted 是否 hover / 展开（决定文字颜色）
	 * @param withArrow   是否绘制右侧子菜单箭头
	 */
	private static void paintMenuRow(Graphics g, JMenuItem row, boolean highlighted, boolean withArrow) {
		Graphics2D g2d = (Graphics2D) g.create();
		try {
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			// 1. 底色：始终用组件自己的背景（菜单底色），绝不使用 L&F 的选中色
			g2d.setColor(row.getBackground());
			g2d.fillRect(0, 0, row.getWidth(), row.getHeight());

			// 2. 文字：禁用态灰色 > hover 蓝色 > 主题前景色
			Font font = row.getFont();
			g2d.setFont(font);
			FontMetrics fm = g2d.getFontMetrics(font);
			Color textColor;
			if (!row.isEnabled()) {
				textColor = MENU_DISABLED_FOREGROUND;
			} else if (highlighted) {
				textColor = MENU_HOVER_FOREGROUND;
			} else {
				textColor = row.getForeground();
			}
			g2d.setColor(textColor);
			String text = row.getText();
			int textY = (row.getHeight() - fm.getHeight()) / 2 + fm.getAscent();
			g2d.drawString(text == null ? "" : text, ROW_TEXT_LEFT, textY);

			// 3. 右侧子菜单箭头（自绘，避免依赖 L&F 的 Menu.arrowIcon 颜色）
			if (withArrow) {
				int arrowRight = row.getWidth() - ARROW_RIGHT;
				int centerY = row.getHeight() / 2;
				g2d.fillPolygon(
						new int[]{arrowRight - ARROW_WIDTH, arrowRight - ARROW_WIDTH, arrowRight},
						new int[]{centerY - ARROW_HEIGHT / 2, centerY + ARROW_HEIGHT / 2, centerY},
						3);
			}
		} finally {
			g2d.dispose();
		}
	}

	/** 自绘菜单行的首选尺寸（文字宽 + 左右留白 [+ 箭头占位]） */
	private static Dimension menuRowPreferredSize(JMenuItem row, boolean withArrow) {
		FontMetrics fm = row.getFontMetrics(row.getFont());
		String text = row.getText();
		int textWidth = (text == null) ? 0 : fm.stringWidth(text);
		int width = ROW_TEXT_LEFT + textWidth + ROW_TEXT_RIGHT;
		if (withArrow) {
			width += ARROW_GAP + ARROW_WIDTH;
		}
		int height = fm.getHeight() + ROW_VERTICAL_PADDING * 2;
		return new Dimension(width, height);
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

				// 1. 整行背景：与其它自绘菜单项保持一致，始终用菜单底色
				// （不再读 UIManager 的 MenuItem.selectionBackground —— IntelliJ 的菜单 UI 会在
				//  installDefaults() 里用 JBColor.namedColor 覆盖它，读到的是不可控的值）
				Color rowBg = getBackground();
				if (rowBg == null) {
					rowBg = JBColor.background();
				}
				g2.setColor(rowBg);
				g2.fillRect(0, 0, getWidth(), getHeight());

				// 2. 勾选框：垂直居中于行高
				int boxY = (getHeight() - BOX_SIZE) / 2;
				drawCheckBox(g2, BOX_LEFT_PADDING, boxY);

				// 3. 文字：与其它自绘菜单项保持一致（hover/选中变蓝，禁用态灰色）
				Color textColor = !isEnabled() ? MENU_DISABLED_FOREGROUND
						: (isArmed() || isSelected() ? MENU_HOVER_FOREGROUND : getForeground());
				g2.setColor(textColor);
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
