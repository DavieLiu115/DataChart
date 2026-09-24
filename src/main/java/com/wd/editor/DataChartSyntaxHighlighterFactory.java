package com.wd.editor;

import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 给自定义语言 {@link DataChart}（.datachart）挂上 <b>JSON 的语法高亮</b>。
 *
 * <p>2026-09-24：Text Tab 交回平台默认文本编辑器后（这样 Board 的位置由
 * {@code FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR} 保证，见 {@link DataChartEditorProvider}），
 * 需要让平台文本编辑器对 .datachart 显示出 JSON 高亮。做法不是改文件类型的语言
 * （那会影响文件识别），而是给 dataChart 语言注册一个高亮工厂，内部直接借用平台
 * JSON 语言的高亮实现（{@code com.intellij.json.highlighting.JsonSyntaxHighlighterFactory}，
 * 静态入口 {@link SyntaxHighlighterFactory#getSyntaxHighlighter}）。</p>
 *
 * @author lww
 */
public class DataChartSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

	/** 兜底：万一 JSON 高亮取不到，退回纯文本高亮（不要抛异常，否则编辑器打不开） */
	private static final SyntaxHighlighter FALLBACK = new PlainSyntaxHighlighter();

	@Override
	public @NotNull SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project,
			@Nullable VirtualFile virtualFile) {
		// 借用平台 JSON 语言的高亮（内部查的是 language="JSON" 的 syntaxHighlighterFactory EP，
		// 不会再回到 dataChart，因此没有递归风险）
		SyntaxHighlighter highlighter = SyntaxHighlighterFactory
				.getSyntaxHighlighter(JsonLanguage.INSTANCE, project, virtualFile);
		return highlighter != null ? highlighter : FALLBACK;
	}
}
