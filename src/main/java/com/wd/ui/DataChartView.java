package com.wd.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.wd.icon.PluginIcons;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
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
	private JTextField searchTextField;
	private JButton fullScreamButton;
	private JLabel zoomPercentLabel;
	private JButton searchButton;
	private JPanel dataView;
	private Project project;

	public DataChartView(@Nullable Project project) {
		super(project);
		this.project = project;
		init();
		setupHeaderTool();
		// TODO: 在此处添加图形编辑器的具体 UI 组件（画布、工具栏等）

		searchTextField.setToolTipText("Please input search content");
		searchButton.setIcon(PluginIcons.search);
		fullScreamButton.setIcon(PluginIcons.fullScream);
		exportPDFButton.setIcon(PluginIcons.export);
		exportPictureButton.setIcon(PluginIcons.image);
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
