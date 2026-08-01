package com.wd.ui;

import com.intellij.ui.JBColor;
import com.wd.db.ColumnInfo;
import com.wd.db.TableInfo;
import com.wd.model.RelationType;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
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

	/** 菜单项 hover/selected 时的文字颜色（蓝色） */
	private static final java.awt.Color MENU_HOVER_FOREGROUND = new java.awt.Color(0x2470B0);

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

		// 关系类型子菜单
		JMenu typeMenu = new JMenu("关系类型");
		typeMenu.setForeground(JBColor.foreground());
		typeMenu.setBackground(JBColor.background());
		typeMenu.putClientProperty("MenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		typeMenu.putClientProperty("MenuItem.selectionBackground", JBColor.background());
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
		JCheckBoxMenuItem item = new JCheckBoxMenuItem(label);
		item.setSelected(conn.getRelationType() == type);
		item.setForeground(JBColor.foreground());
		item.setBackground(JBColor.background());
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
	 * 创建表头右键菜单（复制表名 / 复制注释 / 同步表结构 / 删除表）。
	 *
	 * @param info         表元信息
	 * @param onSyncStructure 同步表结构回调（重新获取元信息并刷新卡片）；可为 null 表示不显示该项
	 * @param onDeleteTable 删除表回调（效果等同 Command+Del：有连线先提示，无连线直接删除）；可为 null 表示不显示该项
	 */
	public static JPopupMenu buildHeaderMenu(TableInfo info, Runnable onSyncStructure,
			Runnable onDeleteTable) {
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
}
