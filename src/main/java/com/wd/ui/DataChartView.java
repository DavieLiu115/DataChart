package com.wd.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.wd.icon.PluginIcons;
import com.wd.model.ChartData;
import com.alibaba.fastjson.JSON;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jetbrains.annotations.Nullable;

/**
 * @author lww
 * @date 2026-07-30 19:32
 */
public class DataChartView extends DialogWrapper {

	private JPanel rootPanel;
	private JPanel headerTool;
	private JButton exportPDFButton;
	private JButton exportPictureButton;
	private SearchTextField searchTextField;
	private JLabel zoomPercentLabel;
	private JPanel dataView;
	private JButton focusButton;
	private JButton donateButton;
	private KanbanBoard kanbanBoard;
	private Project project;

	/** 看板内容变更回调（转发给 DataChartEditor 标记修改状态） */
	private Runnable boardChangeListener;

	/** 保存回调（Command+S 时触发，由 DataChartEditor 注册） */
	private Runnable saveListener;

	/** 是否处于全屏模式 */
	private boolean isFullScreen = false;

	/** 退出全屏时需要恢复显示的工具栏组件引用 */
	private final java.util.List<java.awt.Component> hiddenOnFullScreen = new java.util.ArrayList<>();

	/**
	 * 基础文件名（不含扩展名），用于导出 PDF / 图片的默认文件名。
	 * 由 {@code DataChartEditor} 注入，未注入时使用 "datachart"。
	 */
	private String baseFileName = "datachart";

	public DataChartView(@Nullable Project project) {
		super(project);
		this.project = project;
		init();
		setupHeaderTool();
		setupSearchField();
		initKanbanBoard();
		setupToolBarButtons();

		//fullScreamButton.setIcon(PluginIcons.fullScream);
		exportPDFButton.setIcon(PluginIcons.export);
		exportPictureButton.setIcon(PluginIcons.image);
		// focusButton 用 reset 图标（"回到原点/居中"的视觉语义）
		focusButton.setIcon(PluginIcons.autoLayout);
		focusButton.setText("Focus");
		donateButton.setIcon(PluginIcons.Donation);
		donateButton.setRolloverIcon(PluginIcons.Donation_Enter);
		donateButton.setContentAreaFilled(false);
		donateButton.setBorderPainted(false);
		donateButton.setToolTipText("Donation");

		// 初次构造后立即刷新一次 zoom 显示（100%）
		updateSearchStatusLabel();
	}

	/**
	 * 给工具栏按钮挂监听
	 */
	private void setupToolBarButtons() {
		if (focusButton != null) {
			focusButton.setToolTipText("聚焦画板（保留缩放，居中显示）");
			focusButton.addActionListener(e -> {
				if (kanbanBoard != null) {
					kanbanBoard.focusView();
				}
			});
		}
		//if (fullScreamButton != null) {
		//	fullScreamButton.setToolTipText("进入全屏模式");
		//	fullScreamButton.addActionListener(e -> toggleFullScreen());
		//}
		if (exportPDFButton != null) {
			exportPDFButton.setToolTipText("导出为 PDF 文件");
			exportPDFButton.addActionListener(e -> exportAsPdf());
		}
		if (exportPictureButton != null) {
			exportPictureButton.setToolTipText("导出为图片（JPG）");
			exportPictureButton.addActionListener(e -> exportAsImage());
		}
		if (donateButton != null) {
			donateButton.addActionListener(e -> {
				Donation donation = new Donation(project);
				donation.show();
			});
		}

	}

	/**
	 * 生成导出默认文件名：基础名 + yyyyMMdd_HHmmss
	 *
	 * <p>例如 "schema_20260801_153012"。</p>
	 */
	private String generateDefaultFileName(String extension) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
		return baseFileName + "_" + sdf.format(new Date()) + "." + extension;
	}

	/**
	 * 导出当前画板为 PDF
	 *
	 * <p>弹文件保存对话框，默认文件名 = 当前 datachart 文件名 + yyyyMMdd_HHmmss.pdf。
	 * 无卡片时给出提示，不弹文件框。</p>
	 */
	private void exportAsPdf() {
		if (kanbanBoard == null) {
			return;
		}
		if (kanbanBoard.getCards() == null || kanbanBoard.getCards().isEmpty()) {
			NotificationUtil.info("导出失败", "画板为空，无内容可导出");
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("导出为 PDF");
		chooser.setFileFilter(new FileNameExtensionFilter("PDF 文件 (*.pdf)", "pdf"));
		chooser.setSelectedFile(new File(generateDefaultFileName("pdf")));

		if (chooser.showSaveDialog(rootPanel) == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			if (file.getName().toLowerCase().endsWith(".pdf")) {
				// 用户已经写了 .pdf，直接用
			} else {
				file = new File(file.getParentFile(), file.getName() + ".pdf");
			}
		boolean ok = BoardExportUtil.exportToPdf(kanbanBoard, file);
		if (ok) {
			NotificationUtil.info("导出成功", "PDF 已保存到：" + file.getAbsolutePath());
		} else {
			NotificationUtil.error("导出失败", "保存 PDF 失败，请查看日志");
		}
		}
	}

	/**
	 * 导出当前画板为图片（JPG）
	 *
	 * <p>默认文件名 = 当前 datachart 文件名 + yyyyMMdd_HHmmss.jpg。</p>
	 */
	private void exportAsImage() {
		if (kanbanBoard == null) {
			return;
		}
		if (kanbanBoard.getCards() == null || kanbanBoard.getCards().isEmpty()) {
			NotificationUtil.info("导出失败", "画板为空，无内容可导出");
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("导出为图片");
		chooser.setFileFilter(new FileNameExtensionFilter("JPG 图片 (*.jpg)", "jpg", "jpeg"));
		chooser.setSelectedFile(new File(generateDefaultFileName("jpg")));

		if (chooser.showSaveDialog(rootPanel) == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			String lower = file.getName().toLowerCase();
			if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
				// 用户已带后缀
			} else {
				file = new File(file.getParentFile(), file.getName() + ".jpg");
			}
		boolean ok = BoardExportUtil.exportToImage(kanbanBoard, file, "jpg", 2.0);
		if (ok) {
			NotificationUtil.info("导出成功", "图片已保存到：" + file.getAbsolutePath());
		} else {
			NotificationUtil.error("导出失败", "保存图片失败，请查看日志");
		}
		}
	}

	/**
	 * 切换全屏模式
	 *
	 * <p>全屏时隐藏 headerTool 内的搜索框 + AutoLayout/Focus/Export 按钮，
	 * 只保留 FullScream 按钮（按钮文案变 ExitFullScream，图标换 exit_fullScream），
	 * 让 dataView 撑满整个 rootPanel。</p>
	 */
	private void toggleFullScreen() {
		if (headerTool == null) {
			return;
		}
		isFullScreen = !isFullScreen;
		if (isFullScreen) {
			enterFullScreen();
		} else {
			exitFullScreen();
		}
		// 重绘以让 dataView 重新布局
		if (rootPanel != null) {
			rootPanel.revalidate();
			rootPanel.repaint();
		}
	}

	/**
	 * 进入全屏：隐藏非 FullScream 按钮 + 搜索框，记录到 hiddenOnFullScreen 便于恢复
	 */
	private void enterFullScreen() {
		hiddenOnFullScreen.clear();
		// 仅保留 fullScreamButton 在工具栏可见，其他组件 hidden
		java.awt.Component[] toHide = new java.awt.Component[]{
				searchTextField, focusButton, exportPDFButton, exportPictureButton
		};
		for (java.awt.Component c : toHide) {
			if (c != null && c.isVisible()) {
				c.setVisible(false);
				hiddenOnFullScreen.add(c);
			}
		}
		// 隐藏 zoomPercentLabel 也不太合理（用户期望全屏后还能看到 zoom），但为了"工具栏只留退出按钮"也隐藏
		if (zoomPercentLabel != null && zoomPercentLabel.isVisible()) {
			zoomPercentLabel.setVisible(false);
			hiddenOnFullScreen.add(zoomPercentLabel);
		}
		// 切换按钮外观：图标 + 文字
		//fullScreamButton.setIcon(PluginIcons.exit_fullScream);
		//fullScreamButton.setText("ExitFullScream");
		//fullScreamButton.setToolTipText("退出全屏");
	}

	/**
	 * 退出全屏：恢复所有被隐藏的组件
	 */
	private void exitFullScreen() {
		for (java.awt.Component c : hiddenOnFullScreen) {
			if (c != null) {
				c.setVisible(true);
			}
		}
		hiddenOnFullScreen.clear();
		//fullScreamButton.setIcon(PluginIcons.fullScream);
		//fullScreamButton.setText("FullScream");
		//fullScreamButton.setToolTipText("进入全屏模式");
	}

	/**
	 * 初始化看板（占用 dataView 区域）
	 */
	private void initKanbanBoard() {
		if (dataView == null) {
			return;
		}
		dataView.setLayout(new BorderLayout());
		kanbanBoard = new KanbanBoard(project);
		dataView.add(kanbanBoard, BorderLayout.CENTER);
		// 看板内容变更时通知上层（DataChartEditor 标记文件已修改）
		kanbanBoard.setChangeListener(() -> {
			if (boardChangeListener != null) {
				boardChangeListener.run();
			}
		});
		// 视图变化（zoom/pan/reset/focusView）时刷新 zoom 百分比显示
		kanbanBoard.setViewChangeListener(this::updateSearchStatusLabel);
		// Command+S / Ctrl+S 保存
		kanbanBoard.registerSaveAction(() -> {
			if (saveListener != null) {
				saveListener.run();
			}
		});
		// 看板初始为空，等待用户从 Database 工具窗口拖入表
	}

	/**
	 * 设置看板内容变更回调（由 DataChartEditor 注册，用于标记文件修改状态）
	 */
	public void setBoardChangeListener(Runnable listener) {
		this.boardChangeListener = listener;
	}

	/**
	 * 设置保存回调（由 DataChartEditor 注册，Command+S 时调用 saveDocument）
	 */
	public void setSaveListener(Runnable listener) {
		this.saveListener = listener;
	}

	/**
	 * 设置基础文件名（用于导出 PDF / 图片的默认文件名）
	 *
	 * <p>由 {@code DataChartEditor} 在初始化时调用，传入当前 .datachart 文件名（不含扩展名）。
	 * 未调用时使用 "datachart"。</p>
	 */
	public void setBaseFileName(String name) {
		if (name != null && !name.isEmpty()) {
			this.baseFileName = name;
		}
	}

	/**
	 * 获取当前基础文件名（导出默认文件名用）
	 */
	public String getBaseFileName() {
		return baseFileName;
	}

	/**
	 * 将当前看板状态序列化为 JSON 字符串（保存 .datachart 时使用）
	 */
	public String serializeToJson() {
		ChartData data = kanbanBoard.toChartData();
		return JSON.toJSONString(data);
	}

	/**
	 * 从 JSON 字符串加载看板状态（打开 .datachart 时使用）
	 */
	public void loadFromJson(String json) {
		if (json == null || json.isEmpty()) {
			return;
		}
		try {
			ChartData data = JSON.parseObject(json, ChartData.class);
			kanbanBoard.loadFromChartData(data);
			// 加载新文件后清空搜索状态，避免旧搜索结果干扰
			if (searchTextField != null) {
				searchTextField.setText("");
			}
			if (kanbanBoard != null) {
				kanbanBoard.clearSearch();
			}
			updateSearchStatusLabel();
			// 打开文件后自动居中所有卡片（等价于点击 Focus 按钮），
			// 延迟到组件布局完成后再执行，保证视口尺寸正确
			javax.swing.SwingUtilities.invokeLater(() -> {
				if (kanbanBoard != null) {
					kanbanBoard.focusView();
				}
			});
		} catch (Exception e) {
			// 解析失败时忽略，保持空看板
		}
	}

	/**
	 * 获取看板组件
	 */
	public KanbanBoard getKanbanBoard() {
		return kanbanBoard;
	}

	/**
	 * 为工具栏添加底部 1px 分隔线，适配深色/浅色主题
	 * （上下间距由 .form 中的 margin 控制）
	 */
	private void setupHeaderTool() {
		if (headerTool == null) {
			return;
		}
		headerTool.setBorder(new MatteBorder(0, 0, 1, 0, JBColor.border()));
	}

	/**
	 * 初始化搜索框：设置占位符、历史最大数量、绑定搜索事件
	 */
	private void setupSearchField() {
		if (searchTextField == null) {
			return;
		}
		searchTextField.setToolTipText("Please input search content");
		searchTextField.getTextEditor().getEmptyText().setText("Search");
		searchTextField.setHistorySize(10);
		// 回车触发搜索
		searchTextField.getTextEditor().addActionListener(e -> doSearch());
		// 上下方向键在搜索结果中切换
		searchTextField.getTextEditor().addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (kanbanBoard == null) {
					return;
				}
				int count = kanbanBoard.getSearchResultCount();
				if (count == 0) {
					return;
				}
				if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					kanbanBoard.focusNextSearchResult();
					updateSearchStatusLabel();
					e.consume();
				} else if (e.getKeyCode() == KeyEvent.VK_UP) {
					kanbanBoard.focusPrevSearchResult();
					updateSearchStatusLabel();
					e.consume();
				} else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					// ESC 清空搜索
					searchTextField.setText("");
					kanbanBoard.clearSearch();
					updateSearchStatusLabel();
					e.consume();
				}
			}
		});
	}

	/**
	 * 执行搜索逻辑
	 */
	private void doSearch() {
		String keyword = searchTextField.getText();
		if (keyword == null || keyword.isEmpty()) {
			kanbanBoard.clearSearch();
			updateSearchStatusLabel();
			return;
		}
		searchTextField.addCurrentTextToHistory();
		int count = kanbanBoard.search(keyword);
		updateSearchStatusLabel();
		if (count == 0) {
			// 无命中，给个轻量提示
			searchTextField.setToolTipText("未找到匹配项");
		} else {
			searchTextField.setToolTipText("找到 " + count + " 条匹配项，↑/↓ 切换，Esc 清除");
		}
	}

	/**
	 * 刷新缩放百分比 + 搜索状态标签
	 *
	 * <p>格式：150% | 3/12（150% 是当前 zoom 倍率，后面是搜索结果当前/总数）
	 * 无搜索结果时只显示 zoom 百分比。</p>
	 *
	 * <p>由以下时机调用：</p>
	 * <ul>
	 *   <li>KanbanBoard 视图变化（zoom/pan/reset/focusView）时通过 viewChangeListener</li>
	 *   <li>搜索结果变化时（doSearch / 上下键 / ESC / 加载文件）</li>
	 *   <li>初次构造后</li>
	 * </ul>
	 */
	private void updateSearchStatusLabel() {
		if (zoomPercentLabel == null) {
			return;
		}
		String zoomText = getZoomPercentText();
		if (kanbanBoard == null) {
			zoomPercentLabel.setText(zoomText);
			return;
		}
		int total = kanbanBoard.getSearchResultCount();
		if (total == 0) {
			zoomPercentLabel.setText(zoomText);
			return;
		}
		int current = kanbanBoard.getSearchFocusIndex() + 1;
		zoomPercentLabel.setText(zoomText + "  |  " + current + "/" + total);
	}

	/**
	 * 获取"纯"缩放百分比文本（取自 {@link KanbanBoard#getZoomFactor()}，整数化）
	 *
	 * <p>实现：每次都从 kanbanBoard 实时读取 zoomFactor，不再依赖 zoomPercentLabel 的旧值。
	 * 这样 zoom/pan/focus 任何时候都会反映最新值。</p>
	 */
	private String getZoomPercentText() {
		if (kanbanBoard == null) {
			return "100%";
		}
		int percent = (int) Math.round(kanbanBoard.getZoomFactor() * 100);
		return percent + "%";
	}

	@Override
	protected @Nullable JComponent createCenterPanel() {
		return rootPanel;
	}

	public JComponent getRootComponent() {
		return rootPanel;
	}

	public Project getProject() {
		return project;
	}

	@Override
	public void doOKAction() {
		// 禁用回车键的默认行为
	}

	@Override
	protected Action[] createActions() {
		return new Action[0];
	}

	@Override
	public void dispose() {
		super.dispose();
	}
}
