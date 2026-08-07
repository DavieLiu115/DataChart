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

	/** focus 时内容左上角距视口左上角的留白（屏幕像素，内容能完整展示时的留白） */
	private static final double FOCUS_PADDING = 20;

	/** focus 时内容超出视口、左对齐场景下的左侧留白（屏幕像素，更大便于查看） */
	private static final double FOCUS_PADDING_LEFT = 80;

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
	 * 把所有卡片整体对齐到视口，保留当前缩放倍率。
	 *
	 * <p>无卡片时不改变视图。</p>
	 *
	 * <p>对齐策略（自适应）：</p>
	 * <ul>
	 *   <li><b>能完整展示</b>（内容宽 ≤ 视口宽 且 内容高 ≤ 视口高）：上下左右居中</li>
	 *   <li><b>展示不完</b>（宽或高超限）：<b>左对齐 + 上下居中</b>，左侧留白用更大的
	 *       {@value #FOCUS_PADDING_LEFT}，保证最左卡片完整露出且不贴边；右侧/下方超出由用户滚动查看</li>
	 * </ul>
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

		// 内容在屏幕上的宽 / 高（画板尺寸 × zoom）
		double contentW = (maxX - minX) * zoomFactor;
		double contentH = (maxY - minY) * zoomFactor;

		// 是否能在视口内完整展示（加上基础留白后仍放得下）
		boolean fits = contentW <= viewWidth - 2 * FOCUS_PADDING
				&& contentH <= viewHeight - 2 * FOCUS_PADDING;

		double targetScreenX;
		double targetScreenY;
		double contentCenterX = (minX + maxX) / 2.0;
		double contentCenterY = (minY + maxY) / 2.0;
		double viewCenterX = viewWidth / 2.0;
		double viewCenterY = viewHeight / 2.0;

		// 内容中心当前在屏幕上的位置
		double curCenterScreenX = contentCenterX * zoomFactor + transform.getTranslateX();
		double curCenterScreenY = contentCenterY * zoomFactor + transform.getTranslateY();

		double dx;
		double dy;
		if (fits) {
			// 情况 A：能完整展示 → 上下左右居中：内容中心落到视口中心
			dx = viewCenterX - curCenterScreenX;
			dy = viewCenterY - curCenterScreenY;
		} else {
			// 情况 B：展示不完 → 左对齐 + 上下居中，左侧留白更大
			// 左对齐：内容 minX 落到 FOCUS_PADDING_LEFT（最左卡片不贴边）
			dx = FOCUS_PADDING_LEFT - (minX * zoomFactor + transform.getTranslateX());
			// 上下居中：内容垂直中心落到视口垂直中心
			dy = viewCenterY - curCenterScreenY;
		}
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
	 * 缩放到能完整展示所有卡片，并居中（Fit to Window）。
	 *
	 * <p>与 {@link #focusOn} 的区别：focusOn 保留当前缩放只移动位置；
	 * 本方法会<b>重新计算缩放比例</b>，让所有卡片加上留白后完整落入视口内，再居中。</p>
	 *
	 * @param cards      卡片列表
	 * @param viewWidth  视口宽（屏幕坐标）
	 * @param viewHeight 视口高（屏幕坐标）
	 * @return 是否有卡片（false 表示无卡片，视图未改变）
	 */
	public boolean fit(List<KanbanCard> cards, int viewWidth, int viewHeight) {
		if (cards == null || cards.isEmpty() || viewWidth <= 0 || viewHeight <= 0) {
			return false;
		}
		// 1. 计算所有卡片的合并包围盒（画板坐标）
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (KanbanCard card : cards) {
			Rectangle2D b = card.getBounds();
			minX = Math.min(minX, b.getX());
			minY = Math.min(minY, b.getY());
			maxX = Math.max(maxX, b.getX() + b.getWidth());
			maxY = Math.max(maxY, b.getY() + b.getHeight());
		}

		// 2. 计算能完整放入视口（含留白）的缩放比例，限制在 [MIN_ZOOM, MAX_ZOOM]
		double contentW = maxX - minX;
		double contentH = maxY - minY;
		double availW = viewWidth - 2 * FOCUS_PADDING;
		double availH = viewHeight - 2 * FOCUS_PADDING;
		double scaleX = contentW <= 0 ? 1.0 : availW / contentW;
		double scaleY = contentH <= 0 ? 1.0 : availH / contentH;
		double targetZoom = Math.min(scaleX, scaleY);
		targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, targetZoom));

		// 3. 应用缩放（以视口中心为锚，避免内容跑到视野外）
		zoom(new Point2D.Double(viewWidth / 2.0, viewHeight / 2.0),
				targetZoom / zoomFactor);

		// 4. 居中：内容中心（画板）落到视口中心
		double contentCenterX = (minX + maxX) / 2.0;
		double contentCenterY = (minY + maxY) / 2.0;
		double viewCenterX = viewWidth / 2.0;
		double viewCenterY = viewHeight / 2.0;
		double curCenterScreenX = contentCenterX * zoomFactor + transform.getTranslateX();
		double curCenterScreenY = contentCenterY * zoomFactor + transform.getTranslateY();
		transform.translate((viewCenterX - curCenterScreenX) / zoomFactor,
				(viewCenterY - curCenterScreenY) / zoomFactor);
		return true;
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
