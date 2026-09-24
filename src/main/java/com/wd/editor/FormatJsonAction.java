package com.wd.editor;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.wd.model.ChartJsonUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 「格式化 JSON」：把 .datachart 当前编辑器里的 JSON 按 IDE 的 JSON 代码风格重排。
 *
 * <p>为什么自带一个动作：.datachart 的语言是插件自定义的 {@code dataChart}（通过
 * {@link DataChartSyntaxHighlighterFactory} 只借用了 JSON 的<b>高亮</b>），
 * 没有注册 JSON 的 formatter，因此平台自带的 Reformat Code 对它无效。
 * 这里直接复用保存时用的 {@link ChartJsonUtil#prettifyText}（只改空白、不改字段顺序与取值）。</p>
 *
 * @author lww
 */
public class FormatJsonAction extends AnAction {

	@Override
	public void actionPerformed(@NotNull AnActionEvent e) {
		Project project = e.getProject();
		Editor editor = e.getData(CommonDataKeys.EDITOR);
		if (project == null || editor == null) {
			return;
		}
		Document document = editor.getDocument();
		String formatted = ChartJsonUtil.prettifyText(document.getText(),
				ChartJsonUtil.resolveIndentSize(project));
		if (formatted == null || formatted.equals(document.getText())) {
			return; // 不是合法 JSON，或已经是格式化版本
		}
		WriteCommandAction.runWriteCommandAction(project, () -> document.setText(formatted));
	}

	@Override
	public void update(@NotNull AnActionEvent e) {
		Editor editor = e.getData(CommonDataKeys.EDITOR);
		VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
		boolean available = editor != null && file != null
				&& DataToolsFileType.EXTENSION.equalsIgnoreCase(file.getExtension());
		e.getPresentation().setEnabledAndVisible(available);
	}

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		// update() 只读 DataContext，不需要 EDT
		return ActionUpdateThread.BGT;
	}
}
