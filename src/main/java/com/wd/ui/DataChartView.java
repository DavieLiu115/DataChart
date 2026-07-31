package com.wd.ui;

import com.intellij.openapi.project.Project;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nullable;

/**
 * @author lww
 * @date 2026-07-30 19:32
 */
public class DataChartView {

	private final JPanel rootPanel;
	private final Project project;

	public DataChartView(@Nullable Project project) {
		this.project = project;
		this.rootPanel = new JPanel(new BorderLayout());
		// TODO: 在此处添加图形编辑器的具体 UI 组件（画布、工具栏等）
	}

	public JComponent getRootComponent() {
		return rootPanel;
	}

	public Project getProject() {
		return project;
	}

	public void dispose() {
		rootPanel.removeAll();
	}
}
