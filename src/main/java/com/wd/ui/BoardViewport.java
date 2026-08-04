package com.wd.ui;

import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * 看板视口几何：统一管理变换矩阵 {@code transform} 与缩放因子 {@code zoomFactor}，
 * 以及缩放 / 平移 / 聚焦 / 复位 / 屏幕↔画板坐标转换。
 *
 * <p>从 {@link KanbanBoard} 抽取，职责单一：只负责"视口几何"，不依赖 JPanel 渲染与事件。
 * 看板通过本类暴露的方法完成视图变换，再由上层触发重绘 / 通知。</p>
 *
 * <p>坐标约定：</p>
 * <ul>
 *   <li>画板坐标 = 世界坐标（卡片位置所在坐标系）</li>
 *   <li>屏幕坐标 = 画板坐标经过 {@code transform} 后的坐标</li>
 *   <li>{@code screen = board * zoomFactor + translate}</li>
 * </ul>
 *
 * @author lww
 */
public class BoardViewport {

	/** 变换矩阵（用于平移和缩放） */
	private AffineTransform transform = new AffineTransform();

	/** 缩放因子 */
	private double zoomFactor = 1.0;

	/** 缩放下限 / 上限 */
	private static final double MIN_ZOOM = 0.1;
	private static final double MAX_ZOOM = 10.0;

	/** 滚轮平移灵敏度（像素 / 滚轮单位） */
	private static final double PAN_SENSITIVITY = 20;

	/** focus 时内容左上角距视口左上角的留白（屏幕像素） */
	private static final double FOCUS_PADDING = 20;

	/**
	 * 复位视图（位置 + 缩放 = 回到 100% 且居中无偏移）
	 */
	public void reset() {
		transform = new AffineTransform();
		zoomFactor = 1.0;
	}

	/**
	 * 直接把缩放因子设为指定值，保持屏幕中心点对应的画板位置不变。
	 *
	 * <p>用于"1:1"等精确缩放需求（如 oneOne 按钮）。缩放后不改变视口中心锚定内容，
	 * 因此用户看到的画面中心不变，只是整体放大/缩小。</p>
	 *
	 * @param viewCenterX 屏幕中心 X（用于锚定）
	 * @param viewCenterY 屏幕中心 Y（用于锚定）
	 * @return 是否生效（目标缩放越界时返回 false）
	 */
	public boolean setZoomFactor(double viewCenterX, double viewCenterY) {
		return zoom(new Point2D.Double(viewCenterX, viewCenterY),
				1.0 / zoomFactor); // scaleFactor = target/current
	}

	/**
	 * 以指定屏幕点为锚点缩放。
	 *
	 * @param p           缩放锚点（屏幕坐标）
	 * @param scaleFactor 缩放倍数（>1 放大，<1 缩小）
	 * @return 是否生效（缩放范围越界时返回 false）
	 */
	public boolean zoom(Point2D p, double scaleFactor) {
		double newZoom = zoomFactor * scaleFactor;
		if (newZoom < MIN_ZOOM || newZoom > MAX_ZOOM) {
			return false;
		}
		zoomFactor = newZoom;

		AffineTransform old = new AffineTransform(transform);
		transform.setToIdentity();
		transform.translate(p.getX(), p.getY());
		transform.scale(scaleFactor, scaleFactor);
		transform.translate(-p.getX(), -p.getY());
		transform.concatenate(old);
		return true;
	}

	/**
	 * 根据滚轮事件计算并应用平移增量，支持水平 + 垂直双轴移动。
	 *
	 * <p>Mac 触控板：双指上下滑动 → 垂直平移，双指左右滑动 → 水平平移
	 * （JDK 将触控板横向滑动报告为 Shift+滚轮事件）。普通鼠标：滚轮 → 垂直平移，Shift+滚轮 → 水平平移。</p>
	 *
	 * @param e             滚轮事件
	 * @param wheelRotation 精确滚轮旋转量
	 */
	public void panByWheel(MouseWheelEvent e, double wheelRotation) {
		double deltaX = 0;
		double deltaY = -wheelRotation * PAN_SENSITIVITY;
		if (e.isShiftDown()) {
			deltaX = deltaY;
			deltaY = 0;
		}
		// 除以 zoomFactor，保证缩放后移动量跟手
		transform.translate(deltaX / zoomFactor, deltaY / zoomFactor);
	}

	/**
	 * 按指定像素增量平移画板（除以缩放保证跟手）。
	 */
	public void pan(double dx, double dy) {
		transform.translate(dx / zoomFactor, dy / zoomFactor);
	}

	/**
	 * 把所有卡片整体"左上对齐"到视口，保留当前缩放倍率。
	 *
	 * <p>无卡片时不改变视图。</p>
	 *
	 * <p>对齐策略：让内容包围盒的左上角落到视口的 {@value #FOCUS_PADDING} 屏幕像素处。
	 * 这样最左 / 最上的卡片一定完整露出；右侧 / 下方超出部分不做缩放、由用户滚动查看。</p>
	 *
	 * @param cards       卡片列表
	 * @param viewWidth   视口宽（屏幕坐标）
	 * @param viewHeight  视口高（屏幕坐标）
	 * @return 是否有卡片（false 表示无卡片，视图未改变）
	 */
	public boolean focusOn(List<KanbanCard> cards, int viewWidth, int viewHeight) {
		if (cards == null || cards.isEmpty()) {
			return false;
		}
		// 1. 计算所有卡片的合并包围盒（画板坐标）
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (KanbanCard card : cards) {
			Rectangle2D b = card.getBounds();
			if (b.getX() < minX) {
				minX = b.getX();
			}
			if (b.getY() < minY) {
				minY = b.getY();
			}
			if (b.getX() + b.getWidth() > maxX) {
				maxX = b.getX() + b.getWidth();
			}
			if (b.getY() + b.getHeight() > maxY) {
				maxY = b.getY() + b.getHeight();
			}
		}

		// 2. 让内容包围盒左上角 (minX, minY) 落到视口 (FOCUS_PADDING, FOCUS_PADDING)
		double targetScreenX = FOCUS_PADDING;
		double targetScreenY = FOCUS_PADDING;
		double currentScreenX = minX * zoomFactor + transform.getTranslateX();
		double currentScreenY = minY * zoomFactor + transform.getTranslateY();

		// 3. 反推 translate，让 (minX, minY) 落到目标屏幕点
		double dx = targetScreenX - currentScreenX;
		double dy = targetScreenY - currentScreenY;
		transform.translate(dx / zoomFactor, dy / zoomFactor);
		return true;
	}

	/**
	 * 平移画板让指定画板点落到视口中心（搜索结果滚动 / 聚焦用）。
	 *
	 * @param boardX      目标画板 X
	 * @param boardY      目标画板 Y
	 * @param viewWidth   视口宽（屏幕坐标）
	 * @param viewHeight  视口高（屏幕坐标）
	 */
	public void centerOn(double boardX, double boardY, int viewWidth, int viewHeight) {
		double viewCenterX = viewWidth / 2.0;
		double viewCenterY = viewHeight / 2.0;
		double currentScreenX = boardX * zoomFactor + transform.getTranslateX();
		double currentScreenY = boardY * zoomFactor + transform.getTranslateY();
		double dx = viewCenterX - currentScreenX;
		double dy = viewCenterY - currentScreenY;
		transform.translate(dx / zoomFactor, dy / zoomFactor);
	}

	/**
	 * 屏幕坐标转换为画板坐标。
	 */
	public Point2D transformPoint(Point2D screenPoint) {
		try {
			AffineTransform inverse = transform.createInverse();
			Point2D out = new Point2D.Double();
			inverse.transform(screenPoint, out);
			return out;
		} catch (Exception e) {
			return new Point2D.Double(screenPoint.getX(), screenPoint.getY());
		}
	}

	/**
	 * 获取当前变换矩阵的逆矩阵（供网格可见范围计算等使用）。
	 */
	public AffineTransform getInverse() {
		try {
			return transform.createInverse();
		} catch (Exception e) {
			return new AffineTransform();
		}
	}

	public AffineTransform getTransform() {
		return transform;
	}

	public double getZoomFactor() {
		return zoomFactor;
	}
}
