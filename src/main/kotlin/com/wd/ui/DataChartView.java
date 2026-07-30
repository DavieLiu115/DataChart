package com.wd.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nullable;

/**
 * @author lww
 * @date 2026-07-30 19:32
 */
public class DataChartView extends DialogWrapper {

	private JPanel rootPanel;
	private Project project;

	public DataChartView(@Nullable Project project) {
		super(project);
		this.project = project;
		init();
	}

	@Override
	public void doOKAction() {
		// 禁用回车键的默认行为
	}

	@Override
	protected @Nullable JComponent createCenterPanel() {
		return rootPanel;
	}

	public JComponent getRootComponent() {
		return rootPanel;
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
