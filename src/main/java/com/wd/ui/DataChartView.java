package com.wd.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.wd.icon.PluginIcons;
import com.wd.model.ChartData;
import com.alibaba.fastjson.JSON;
import java.awt.BorderLayout;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.MatteBorder;
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
	private JButton fullScreamButton;
	private JLabel zoomPercentLabel;
	private JPanel dataView;
	private KanbanBoard kanbanBoard;
	private Project project;

	/** 看板内容变更回调（转发给 DataChartEditor 标记修改状态） */
	private Runnable boardChangeListener;

	/** 保存回调（Command+S 时触发，由 DataChartEditor 注册） */
	private Runnable saveListener;

	public DataChartView(@Nullable Project project) {
		super(project);
		this.project = project;
		init();
		setupHeaderTool();
		setupSearchField();
		initKanbanBoard();

		fullScreamButton.setIcon(PluginIcons.fullScream);
		exportPDFButton.setIcon(PluginIcons.export);
		exportPictureButton.setIcon(PluginIcons.image);
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
		searchTextField.getTextEditor().addActionListener(e -> doSearch());
	}

	/**
	 * 执行搜索逻辑
	 */
	private void doSearch() {
		String keyword = searchTextField.getText();
		if (keyword == null || keyword.isEmpty()) {
			return;
		}
		searchTextField.addCurrentTextToHistory();
		// TODO: 在此处编写实际的搜索逻辑
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
