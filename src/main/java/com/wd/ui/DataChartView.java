package com.wd.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.wd.i18n.DataChartBundle;
import com.wd.i18n.DataChartLanguage;
import com.wd.icon.PluginIcons;
import com.wd.model.ChartData;
import com.wd.model.ChartJsonUtil;
import com.alibaba.fastjson.JSON;
import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.KeyStroke;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jetbrains.annotations.NotNull;
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
	private JButton fitButton;
	private JButton donateButton;
	private JButton oneOneButton;
	/** 语言切换按钮（EN / 中文，默认英文，选择结果持久化） */
	private JButton languageButton;
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

	/**
	 * 给 AI 的文件使用说明（来自 .datachart JSON 的 _aiGuide 字段）。
	 *
	 * <p>加载时从 JSON 读取暂存，保存时写回，避免随 {@link KanbanBoard#toChartData()}
	 * 重建 {@link ChartData} 而丢失。新建文件时由模板提供默认说明。</p>
	 */
	private String aiGuide;

	public DataChartView(@Nullable Project project) {
		super(project);
		this.project = project;
		init();
		setupHeaderTool();
		setupSearchField();
		initKanbanBoard();
		setupToolBarButtons();
		setupFindShortcut();

		//fullScreamButton.setIcon(PluginIcons.fullScream);
		exportPDFButton.setIcon(PluginIcons.export);
		exportPictureButton.setIcon(PluginIcons.image);
		// focusButton 用 reset 图标（"回到原点/居中"的视觉语义）
		focusButton.setIcon(PluginIcons.autoLayout);
		oneOneButton.setIcon(PluginIcons.actualZoom);
		fitButton.setIcon(PluginIcons.fitContent);
		donateButton.setIcon(PluginIcons.Donation);
		donateButton.setRolloverIcon(PluginIcons.Donation_Enter);
		donateButton.setContentAreaFilled(false);
		donateButton.setBorderPainted(false);
		if (languageButton != null) {
			languageButton.addActionListener(e -> DataChartLanguage.toggle());
		}

		// 语言切换后刷新本视图文案；视图销毁时自动退订
		//（DialogWrapper 自身不是 Disposable，用 getDisposable() 拿它的生命周期对象）
		ApplicationManager.getApplication().getMessageBus()
				.connect(getDisposable())
				.subscribe(DataChartLanguage.CHANGED, (Runnable) this::applyTexts);

		// 按钮文字 / tooltip 统一在 applyTexts 里设置，切语言时重跑
		applyTexts();

		// 初次构造后立即刷新一次 zoom 显示（100%）
		updateSearchStatusLabel();
	}

	/**
	 * 应用当前语言下的界面文案（按钮文字 + tooltip）。
	 *
	 * <p>语言切换（订阅 {@link DataChartLanguage#CHANGED}）后会再次调用，因此这里要覆盖
	 * 视图里所有「建好之后不会再更新」的文案；右键菜单等每次现建的文案不在此列。</p>
	 */
	private void applyTexts() {
		if (focusButton != null) {
			focusButton.setText(DataChartBundle.message("DataChart.view.toolbar.recenter.text"));
			focusButton.setToolTipText(DataChartBundle.message("DataChart.view.toolbar.recenter.tooltip"));
		}
		if (fitButton != null) {
			fitButton.setText(DataChartBundle.message("DataChart.view.toolbar.fit.text"));
			fitButton.setToolTipText(DataChartBundle.message("DataChart.view.toolbar.fit.tooltip"));
		}
		if (oneOneButton != null) {
			// 100% 是数值，不参与翻译
			oneOneButton.setText("100%");
			oneOneButton.setToolTipText(DataChartBundle.message("DataChart.view.toolbar.zoom100.tooltip"));
		}
		if (exportPDFButton != null) {
			exportPDFButton.setToolTipText(DataChartBundle.message("DataChart.view.toolbar.exportPdf.tooltip"));
		}
		if (exportPictureButton != null) {
			exportPictureButton.setToolTipText(DataChartBundle.message("DataChart.view.toolbar.exportImage.tooltip"));
		}
		if (donateButton != null) {
			donateButton.setToolTipText(DataChartBundle.message("DataChart.view.toolbar.donate.tooltip"));
		}
		if (languageButton != null) {
			boolean chinese = DataChartLanguage.isChinese();
			// 语言按钮自身用「该语言怎么写」标注（EN / 中文），这是语言选择器的惯例，不参与翻译
			languageButton.setText(chinese ? DataChartLanguage.DISPLAY_ZH : DataChartLanguage.DISPLAY_EN);
			languageButton.setToolTipText(DataChartBundle.message(chinese
					? "DataChart.view.toolbar.language.tooltip.toEn"
					: "DataChart.view.toolbar.language.tooltip.toZh"));
		}
		// 按钮文字宽度变化后需要重排工具栏
		if (headerTool != null) {
			headerTool.revalidate();
			headerTool.repaint();
		}
	}

	/**
	 * 给工具栏按钮挂监听
	 */
	private void setupToolBarButtons() {
	if (focusButton != null) {
		focusButton.addActionListener(e -> {
			if (kanbanBoard != null) {
				kanbanBoard.focusView();
			}
		});
	}
	if (fitButton != null) {
		fitButton.addActionListener(e -> {
			if (kanbanBoard != null) {
				kanbanBoard.fitView();
			}
		});
	}
	//if (fullScreamButton != null) {
	//	fullScreamButton.setToolTipText("进入全屏模式");
	//	fullScreamButton.addActionListener(e -> toggleFullScreen());
	//}
	if (oneOneButton != null) {
		oneOneButton.addActionListener(e -> {
			if (kanbanBoard != null) {
				// 只重置缩放为 100%，不移动位置（职责与 Focus 正交）
				kanbanBoard.setZoomTo1();
			}
		});
	}
	if (exportPDFButton != null) {
			exportPDFButton.addActionListener(e -> exportAsPdf());
		}
		if (exportPictureButton != null) {
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
	 * 2026-08-20 新增：注册 Cmd+F (Mac) / Ctrl+F (Win/Linux) 全局快捷键，
	 * 触发时把焦点跳到搜索框，并全选现有内容方便覆盖输入。
	 *
	 * <p>用 {@link JComponent#registerKeyboardAction} 绑定到 kanbanBoard，
	 * 即便焦点不在搜索框也能触发（画板无文本输入焦点，KeyAdapter 监听不到）。</p>
	 */
	private void setupFindShortcut() {
		if (kanbanBoard == null || searchTextField == null) {
			return;
		}
		int modifiers = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		KeyStroke findStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, modifiers);
		kanbanBoard.registerKeyboardAction(e -> focusSearchField(),
				"FocusSearchField", findStroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
	}

	/**
	 * 把焦点跳到搜索框，并全选现有内容（方便覆盖输入）。
	 */
	private void focusSearchField() {
		if (searchTextField == null) {
			return;
		}
		javax.swing.JTextField editor = searchTextField.getTextEditor();
		editor.requestFocusInWindow();
		editor.selectAll();
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
			NotificationUtil.info(DataChartBundle.message("DataChart.notify.export.failed"), DataChartBundle.message("DataChart.view.export.empty"));
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(DataChartBundle.message("DataChart.view.export.pdf.chooserTitle"));
		chooser.setFileFilter(new FileNameExtensionFilter(DataChartBundle.message("DataChart.view.export.pdf.filter"), "pdf"));
		chooser.setSelectedFile(new File(generateDefaultFileName("pdf")));

		if (chooser.showSaveDialog(rootPanel) == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			if (file.getName().toLowerCase().endsWith(".pdf")) {
				// 用户已经写了 .pdf，直接用
			} else {
				file = new File(file.getParentFile(), file.getName() + ".pdf");
			}
			final File target = file;
			// 2026-08-27 优化：PDF 编码 + 文件 IO 移到后台线程；
			// Task.Modal 模态进度框会阻塞 EDT 交互，保证 board 状态不被修改（无竞态）
			ProgressManager.getInstance().run(new Task.Modal(project, DataChartBundle.message("DataChart.view.export.pdf.progressTitle"), true) {
				@Override
				public void run(@NotNull ProgressIndicator indicator) {
					indicator.setIndeterminate(true);
					boolean ok = BoardExportUtil.exportToPdf(kanbanBoard, target);
					ApplicationManager.getApplication().invokeLater(() -> {
						if (ok) {
							NotificationUtil.info(DataChartBundle.message("DataChart.notify.export.success"),
							DataChartBundle.message("DataChart.view.export.pdf.saved", target.getAbsolutePath()));
						} else {
							NotificationUtil.error(DataChartBundle.message("DataChart.notify.export.failed"), DataChartBundle.message("DataChart.view.export.pdf.failed"));
						}
					});
				}
			});
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
			NotificationUtil.info(DataChartBundle.message("DataChart.notify.export.failed"), DataChartBundle.message("DataChart.view.export.empty"));
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(DataChartBundle.message("DataChart.view.export.image.chooserTitle"));
		chooser.setFileFilter(new FileNameExtensionFilter(DataChartBundle.message("DataChart.view.export.image.filter"), "jpg", "jpeg"));
		chooser.setSelectedFile(new File(generateDefaultFileName("jpg")));

		if (chooser.showSaveDialog(rootPanel) == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			String lower = file.getName().toLowerCase();
			if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
				// 用户已带后缀
			} else {
				file = new File(file.getParentFile(), file.getName() + ".jpg");
			}
			final File target = file;
			// 2026-08-27 优化：大图创建（2.0 scale）+ JPEG 编码移到后台线程
			ProgressManager.getInstance().run(new Task.Modal(project, DataChartBundle.message("DataChart.view.export.image.progressTitle"), true) {
				@Override
				public void run(@NotNull ProgressIndicator indicator) {
					indicator.setIndeterminate(true);
					boolean ok = BoardExportUtil.exportToImage(kanbanBoard, target, "jpg", 2.0);
					ApplicationManager.getApplication().invokeLater(() -> {
						if (ok) {
							NotificationUtil.info(DataChartBundle.message("DataChart.notify.export.success"),
							DataChartBundle.message("DataChart.view.export.image.saved", target.getAbsolutePath()));
						} else {
							NotificationUtil.error(DataChartBundle.message("DataChart.notify.export.failed"), DataChartBundle.message("DataChart.view.export.image.failed"));
						}
					});
				}
			});
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
	 * 将当前看板状态序列化为 JSON 字符串（保存 .datachart 时使用）。
	 *
	 * <p>2026-09-24：改为输出<b>格式化 JSON</b>（多行 + 2 空格缩进）——
	 * 编辑器有 Text Tab 后，紧凑单行既不便阅读也不利于 git diff。
	 * {@code JSON.parseObject} 对空白不敏感，旧的紧凑文件仍可正常加载，
	 * 保存一次（或打开时自动迁移，见 {@code DataChartEditor#prettifyFileIfNeeded}）即转为格式化版本。</p>
	 */
	public String serializeToJson() {
		ChartData data = kanbanBoard.toChartData();
		data.setAiGuide(aiGuide);
		// 缩进宽度跟随 IDE 的 JSON 代码风格，保证与 Text Tab 里 Ctrl+Alt+L 的结果一致
		return ChartJsonUtil.toPrettyJson(data, ChartJsonUtil.resolveIndentSize(project));
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
		// 暂存给 AI 的使用说明，保存时写回，避免随看板重建丢失
		aiGuide = data.getAiGuide();
		kanbanBoard.loadFromChartData(data);
			// 加载新文件后清空搜索状态，避免旧搜索结果干扰
			if (searchTextField != null) {
				searchTextField.setText("");
			}
			if (kanbanBoard != null) {
				kanbanBoard.clearSearch();
			}
			updateSearchStatusLabel();
			// 打开文件后自动聚焦所有卡片（等价于点击 Focus 按钮），
			// 2026-08-07 修复：原用 SwingUtilities.invokeLater 一帧延迟，但 IDE 重启自动重开
			// 上次文件时，DataChartEditor.getComponent → ensureInitialized → loadFromFile 同步链路
			// 在 panel 还没嵌入 IDE 编辑区时就调用 loadFromJson，下一帧 panel 仍未真正完成
			// Swing 布局（doLayout/validateTree），getVisibleRect() 仍是 0/旧值 → focusOn
			// 把内容中心对齐到 (0,0) → 视口被推到负方向，画面跑到顶/左外。
			// 改为注册 ComponentListener 等待 panel 第一次有有效尺寸后再 focus，触发后立即注销。
			scheduleFocusWhenReady();
		} catch (Exception e) {
			// 解析失败：提示用户，避免静默丢数据（保留空看板）
			com.intellij.openapi.diagnostic.Logger.getInstance(DataChartView.class)
					.warn("loadFromJson 解析 .datachart 失败", e);
			NotificationUtil.error(DataChartBundle.message("DataChart.notify.open.failed"),
					DataChartBundle.message("DataChart.view.open.failed.content") + e.getMessage());
		}
	}

	/**
	 * 2026-08-07 新增：等待看板首次有有效尺寸后再调用 focusView。
	 * <p>IDE 重启自动重开 .datachart 时，panel 嵌入 IDE 编辑区前 loadFromJson 已被
	 * 同步链路触发，下一帧 Swing 布局仍未完成，getVisibleRect=0/旧值。
	 * 用 ComponentListener 监听首次 componentResized 拿到有效尺寸后 focus，触发后立即注销。</p>
	 */
	private java.awt.event.ComponentListener focusWhenReadyListener;

	private void scheduleFocusWhenReady() {
		if (kanbanBoard == null) {
			return;
		}
		// 如果当前已经布局完成（已显示过），直接 focus
		if (kanbanBoard.getWidth() > 0 && kanbanBoard.getHeight() > 0) {
			kanbanBoard.focusView();
			return;
		}
		// 移除旧监听器（多次 loadFromJson 时避免累积）
		if (focusWhenReadyListener != null) {
			kanbanBoard.removeComponentListener(focusWhenReadyListener);
		}
		// 否则注册监听器，首次有效布局后调用 focus
		focusWhenReadyListener = new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if (kanbanBoard.getWidth() > 0 && kanbanBoard.getHeight() > 0) {
					// 拿到有效尺寸后再延一帧，确保 Swing 完成当前布局 pass
					javax.swing.SwingUtilities.invokeLater(() -> {
						if (kanbanBoard != null) {
							kanbanBoard.focusView();
						}
					});
					if (focusWhenReadyListener != null) {
						kanbanBoard.removeComponentListener(focusWhenReadyListener);
						focusWhenReadyListener = null;
					}
				}
			}
		};
		kanbanBoard.addComponentListener(focusWhenReadyListener);
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
		// 2026-08-20 修复：清空文本（手动 Delete / 历史选空 / IDE 清空按钮）时同步清除高亮，
		// 仅在文本变空时清搜索，避免输入过程中反复 clearSearch 影响性能
		searchTextField.getTextEditor().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override public void insertUpdate(javax.swing.event.DocumentEvent e) { checkClearOnEmpty(); }
			@Override public void removeUpdate(javax.swing.event.DocumentEvent e) { checkClearOnEmpty(); }
			@Override public void changedUpdate(javax.swing.event.DocumentEvent e) { /* plain text 不触发 */ }

			private void checkClearOnEmpty() {
				if (kanbanBoard == null || searchTextField == null) {
					return;
				}
				String text = searchTextField.getText();
				if (text == null || text.isEmpty()) {
					kanbanBoard.clearSearch();
					updateSearchStatusLabel();
				}
			}
		});
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
			searchTextField.setToolTipText(DataChartBundle.message("DataChart.view.search.noMatch"));
		} else {
			searchTextField.setToolTipText(
					DataChartBundle.message("DataChart.view.search.matches", String.valueOf(count)));
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
		// 2026-08-07 修复：清理挂在 kanbanBoard 上的"等待布局完成后 focus"监听器，
		// 避免 listener 隐式引用 DataChartView 导致泄漏
		if (kanbanBoard != null && focusWhenReadyListener != null) {
			kanbanBoard.removeComponentListener(focusWhenReadyListener);
			focusWhenReadyListener = null;
		}
		if (kanbanBoard != null) {
			kanbanBoard.dispose();
		}
		super.dispose();
	}
}
