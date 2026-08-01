package com.wd.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nullable;

/**
 * 看板提醒 / 通知工具类。
 *
 * <p>集中管理两类提醒：</p>
 * <ul>
 *   <li>气泡通知（{@link #info} / {@link #error}），基于 {@link Notifications.Bus}，
 *       从 {@link DataChartView} 抽取</li>
 *   <li>确认对话框（{@link #confirmYesNo}），基于 {@link Messages}，
 *       从 {@link KanbanBoard} 抽取</li>
 * </ul>
 *
 * @author lww
 */
public final class NotificationUtil {

	/** 通知组 ID，必须与 META-INF/plugin.xml 中声明的 {@code <notificationGroup>} 一致 */
	private static final String GROUP_ID = "DataChart";

	private NotificationUtil() {
	}

	/**
	 * 显示 Info 气泡通知。
	 *
	 * @param title   通知标题
	 * @param content 通知内容
	 */
	public static void info(String title, String content) {
		Notifications.Bus.notify(new Notification(
				GROUP_ID, title, content, NotificationType.INFORMATION));
	}

	/**
	 * 显示 Error 气泡通知。
	 *
	 * @param title   通知标题
	 * @param content 通知内容
	 */
	public static void error(String title, String content) {
		Notifications.Bus.notify(new Notification(
				GROUP_ID, title, content, NotificationType.ERROR));
	}

	/**
	 * 显示"是/否"确认对话框。
	 *
	 * @param project 当前工程（可为 null）
	 * @param title   对话框标题
	 * @param message 提示内容
	 * @param yesText 确定按钮文案
	 * @param noText  取消按钮文案
	 * @return true 表示用户点击"是"
	 */
	public static boolean confirmYesNo(@Nullable Project project,
			String title, String message, String yesText, String noText) {
		return Messages.showYesNoDialog(
				project, message, title, yesText, noText, Messages.getQuestionIcon())
				== Messages.YES;
	}
}
