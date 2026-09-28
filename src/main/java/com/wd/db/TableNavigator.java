package com.wd.db;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.psi.PsiElement;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.wd.i18n.DataChartBundle;
import java.awt.Component;
import java.lang.reflect.Method;
import org.jetbrains.annotations.Nullable;

/**
 * 表的「跳转」入口：复用 Database 插件自带的原生能力。
 *
 * <p>背景：IDEA 的数据库 ER 图（Diagrams）右键菜单里的
 * {@code Go To > Data / Go to DDL / Database Explorer} 并不是插件自己写的跳转逻辑，
 * 而是把 Database 插件已有的 Action 组装进菜单（见 {@code DbDiagramProvider} 与
 * {@code DatabasePlugin.xml} 中的 {@code DbDiagrams.SourceActionsGroup.GoTo} 组）。
 * 这些 Action 只认 {@code CommonDataKeys.PSI_ELEMENT}，因此本类的职责是：</p>
 * <ol>
 *   <li>把 {@code .datachart} 中持久化的「数据源 + 表名」现场解析回 PSI 元素
 *       （{@code DbElement}，见 {@link DatabaseTableMetadataFetcher#resolveDbElement}）；</li>
 *   <li>构造携带该 PSI 元素的 {@link DataContext}，用 {@code ActionUtil.lastUpdateAndCheckDumb}
 *       刷新状态、判断可用性，再用 {@code ActionUtil.invokeAction} 执行
 *       （<b>不</b>直接调 {@code AnAction.update()} / {@code actionPerformed()}，二者是
 *       {@code @ApiStatus.OverrideOnly}，见 2026-09-28 的改法说明）。</li>
 * </ol>
 *
 * <p>不缓存 PSI 元素：数据源同步 / IDE 重启后元素会失效，每次跳转都重新解析，
 * 这样旧 {@code .datachart} 文件与重启场景都能正常工作。</p>
 *
 * @author lww
 */
public final class TableNavigator {

	private static final Logger LOG = Logger.getInstance(TableNavigator.class);

	/** 查看数据（图中显示为 "Data"），对应 {@code OpenEditorAction$OpenDataAction} */
	public static final String ACTION_OPEN_DATA = "Jdbc.OpenEditor.Data";

	/** 跳到 DDL，对应 {@code OpenEditorAction$OpenDDLAction} */
	public static final String ACTION_OPEN_DDL = "Jdbc.OpenEditor.DDL";

	/** 在 Database 工具窗口中定位，对应 {@code SelectInDatabaseViewAction}（本类走直连实现） */
	public static final String ACTION_SELECT_IN_DATABASE_VIEW = "sql.SelectInDatabaseView";

	/** 查找引用（平台内置动作） */
	public static final String ACTION_FIND_USAGES = "FindUsages";

	/** Database Explorer 的定位入口类 */
	private static final String DATABASE_VIEW_CLASS = "com.intellij.database.view.DatabaseView";

	private TableNavigator() {
	}

	/**
	 * 跳转结果：成功与否 + 失败原因（用于给用户精确提示）。
	 */
	public static final class Result {

		private final boolean success;
		private final String message;

		private Result(boolean success, String message) {
			this.success = success;
			this.message = message;
		}

		public static Result ok() {
			return new Result(true, null);
		}

		public static Result fail(String message) {
			return new Result(false, message);
		}

		public boolean isSuccess() {
			return success;
		}

		/** 失败原因（成功时为 null） */
		public String getMessage() {
			return message;
		}
	}

	/**
	 * 判断指定跳转当前是否可用（未安装 / 未启用 Database 插件时返回 false）。
	 *
	 * @param actionId 动作 id，取值见本类常量
	 */
	public static boolean isActionAvailable(@Nullable String actionId) {
		if (actionId == null) {
			return false;
		}
		// 「在 Database Explorer 中定位」走直连 DatabaseView，不依赖 action 是否注册
		if (ACTION_SELECT_IN_DATABASE_VIEW.equals(actionId)) {
			return databaseViewSelectMethod() != null;
		}
		try {
			return ActionManager.getInstance().getAction(actionId) != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 对指定表执行跳转。
	 *
	 * <p>必须在 EDT 调用（由右键菜单触发）。内部会先做 PSI 解析与可用性判断，
	 * 失败时返回带原因的 {@link Result}，由调用方提示用户。</p>
	 *
	 * @param project  当前工程
	 * @param info     表元信息（提供数据源名与表名，用于现场解析 PSI 元素）
	 * @param actionId 动作 id，取值见本类常量
	 */
	public static Result performAction(@Nullable Project project,
			@Nullable TableInfo info, @Nullable String actionId) {
		return performAction(project, info, actionId, null);
	}

	/**
	 * 对指定表执行跳转（带父组件，用于弹窗 / 对话框定位）。
	 *
	 * @param contextComponent 作为 {@code PlatformCoreDataKeys.CONTEXT_COMPONENT} 注入，
	 *                         让 {@code FindUsages} 之类的弹窗能相对该组件定位；可为 null
	 */
	public static Result performAction(@Nullable Project project, @Nullable TableInfo info,
			@Nullable String actionId, @Nullable Component contextComponent) {
		if (project == null || project.isDisposed() || info == null || actionId == null) {
			return Result.fail(DataChartBundle.message("DataChart.navigate.invalid"));
		}
		if (ACTION_SELECT_IN_DATABASE_VIEW.equals(actionId)) {
			return selectInDatabaseView(project, info);
		}
		return performDatabaseAction(project, info, actionId, contextComponent);
	}

	/**
	 * 「在 Database Explorer 中定位」。
	 *
	 * <p><b>为什么不用 {@code sql.SelectInDatabaseView} 这个 Action？</b>
	 * 它的 {@code update()} 走 {@code SelectInContextImpl.createContext(event)} +
	 * {@code SelectInDatabaseView.canSelect(ctx, strict)}，而 {@code canSelect} 要求
	 * {@code ctx.getVirtualFile()} 必须是 Database 文件系统里的虚拟文件
	 * （{@code DbImplUtil.isDatabaseVirtualFile}）。用合成的 {@code AnActionEvent}
	 * 拿不到这种上下文，动作会被静默置灰。</p>
	 *
	 * <p>因此这里直接调用该 Action 最终执行的那一步：
	 * {@code DatabaseView.select(PsiElement, boolean)}（与在 Database 工具窗口里双击定位同一入口）。</p>
	 */
	public static Result selectInDatabaseView(Project project, TableInfo info) {
		Method select = databaseViewSelectMethod();
		if (select == null) {
			return Result.fail(DataChartBundle.message("DataChart.navigate.noDatabasePlugin.explorer"));
		}
		PsiElement element = resolvePsiElement(project, info);
		if (element == null) {
			return Result.fail(notFoundMessage(info));
		}
		try {
			select.setAccessible(true);
			select.invoke(null, element, Boolean.TRUE);
			LOG.info("定位到 Database Explorer 成功: " + info.getName());
			return Result.ok();
		} catch (Exception e) {
			LOG.warn("DatabaseView.select 调用失败: " + info.getName(), e);
			return Result.fail(DataChartBundle.message("DataChart.navigate.explorer.failed"));
		}
	}

	/**
	 * 通过 Database 插件的原生 Action 执行跳转（Data / DDL / FindUsages 等）。
	 */
	private static Result performDatabaseAction(Project project, TableInfo info,
			String actionId, @Nullable Component contextComponent) {
		AnAction action = ActionManager.getInstance().getAction(actionId);
		if (action == null) {
			return Result.fail(DataChartBundle.message("DataChart.navigate.noDatabasePlugin"));
		}
		PsiElement element = resolvePsiElement(project, info);
		if (element == null) {
			return Result.fail(notFoundMessage(info));
		}

		SimpleDataContext.Builder builder = SimpleDataContext.builder()
				.add(CommonDataKeys.PROJECT, project)
				.add(CommonDataKeys.PSI_ELEMENT, element)
				.add(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, new PsiElement[]{element});
		if (contextComponent != null) {
			builder.add(PlatformCoreDataKeys.CONTEXT_COMPONENT, contextComponent);
		}
		if (ACTION_FIND_USAGES.equals(actionId)) {
			// 2024.1 起 FindUsagesAction 只认 UsageView.USAGE_TARGETS_KEY（或 FindUsagesAction.SEARCH_TARGETS，
			// 或编辑器光标位置），**不读 CommonDataKeys.PSI_ELEMENT**。
			// 只给 PSI 元素的话 update() 判定为不可用，菜单项会因 place 是 popup 而被直接隐藏。
			// 这里补一个 UsageTarget，让动作走「单目标」分支直接查找（等价 Alt+F7）。
			try {
				// 注意：单参构造器在 2024.1 已 @Deprecated(forRemoval=true)，用 (element, update=true) 等价替代
				UsageTarget[] usageTargets = ReadAction.compute(() ->
						new UsageTarget[]{new PsiElement2UsageTargetAdapter(element, true)});
				builder.add(UsageView.USAGE_TARGETS_KEY, usageTargets);
			} catch (Exception e) {
				LOG.warn("构造 FindUsages UsageTarget 失败: " + info.getName(), e);
				return Result.fail(DataChartBundle.message(
						"DataChart.navigate.findUsages.failed", info.getName()));
			}
		}
		DataContext dataContext = builder.build();

		AnActionEvent event = AnActionEvent.createFromDataContext(
				ActionPlaces.POPUP, action.getTemplatePresentation().clone(), dataContext);

		// 2026-09-28：不再直接调 AnAction.update() / actionPerformed() ——
		// 两者都标了 @ApiStatus.OverrideOnly（"只由平台调用 / 只由动作自己覆写"），
		// Plugin Verifier 会报 "override-only API usage violation"。改用平台公开入口：
		//   ① ActionUtil.lastUpdateAndCheckDumb(...)：让平台刷新 presentation
		//      （内部处理 dumb 模式与 ActionUpdateThread 的线程要求），之后读 isEnabled() 判断可用性；
		//      ⚠️ 它内部会先 commitDocumentsIfNeeded() —— 平台执行动作前的常规步骤，可接受；
		//   ② ActionUtil.invokeAction(...)：真正执行（平台内部会再跑一次 update + actionPerformed）。
		boolean enabled = true;
		try {
			ActionUtil.lastUpdateAndCheckDumb(action, event, false);
			enabled = event.getPresentation().isEnabled();
		} catch (Exception e) {
			// 版本 / 线程差异导致的异常不该影响功能：跳过可用性判断，直接尝试执行
			LOG.warn("跳转动作 update 检查失败，改为直接尝试执行: " + actionId, e);
		}
		if (!enabled) {
			LOG.warn("跳转动作在当前上下文被置灰: " + actionId + ", place=" + ActionPlaces.POPUP);
			return Result.fail(DataChartBundle.message("DataChart.navigate.action.disabled", actionId));
		}
		try {
			ActionUtil.invokeAction(action, dataContext, ActionPlaces.POPUP, null, null);
		} catch (Exception e) {
			LOG.warn("跳转动作执行异常: " + actionId, e);
			return Result.fail(DataChartBundle.message("DataChart.navigate.action.failed", actionId));
		}
		return Result.ok();
	}

	/**
	 * 把持久化的表信息解析为 Database 插件的 PSI 元素（{@code DbElement}）。
	 */
	@Nullable
	private static PsiElement resolvePsiElement(Project project, TableInfo info) {
		Object element;
		try {
			element = ReadAction.compute(() -> {
				TableMetadataFetcher fetcher = TableMetadataService.getInstance(project).getFetcher();
				if (fetcher instanceof DatabaseTableMetadataFetcher) {
					return ((DatabaseTableMetadataFetcher) fetcher)
							.resolveDbElement(project, info.getDatasourceName(), info.getName());
				}
				return null;
			});
		} catch (Exception e) {
			LOG.warn("解析表 PSI 元素异常: " + info.getName(), e);
			return null;
		}
		if (element instanceof PsiElement) {
			PsiElement psi = (PsiElement) element;
			if (psi.isValid()) {
				return psi;
			}
			LOG.warn("解析出的 PSI 元素已失效: " + info.getName());
		}
		return null;
	}

	/** 解析不到表时给用户的提示（同时指向 idea.log 里的 resolveDbElement 日志） */
	private static String notFoundMessage(TableInfo info) {
		return DataChartBundle.message("DataChart.navigate.tableNotFound",
				info.getName(), info.getDatasourceName());
	}

	// ========== DatabaseView.select 反射（懒加载 + 缓存） ==========

	private static volatile boolean databaseViewResolved = false;
	private static Method databaseViewSelectMethod;

	/**
	 * 反射取 {@code DatabaseView.select(PsiElement, boolean)}（Database 插件未安装时返回 null）。
	 */
	private static synchronized Method databaseViewSelectMethod() {
		if (databaseViewResolved) {
			return databaseViewSelectMethod;
		}
		databaseViewResolved = true;
		try {
			Class<?> cls = Class.forName(DATABASE_VIEW_CLASS);
			databaseViewSelectMethod = cls.getMethod("select", PsiElement.class, boolean.class);
		} catch (Throwable t) {
			LOG.warn("DatabaseView.select 不可用（Database 插件未安装 / 未启用）");
			databaseViewSelectMethod = null;
		}
		return databaseViewSelectMethod;
	}
}
