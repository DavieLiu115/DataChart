package com.wd.ui;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * 看板拖拽磁吸 + 对齐辅助线计算。
 *
 * <p>从 {@link KanbanBoard} 抽取，职责单一：拖拽卡片时计算"吸附位置 + 对齐辅助线"。
 * 本类为纯计算，不依赖 JPanel 渲染与事件。</p>
 *
 * <p>规则：</p>
 * <ul>
 *   <li>水平方向：把当前卡片 左/中/右 与其它卡片 左/中/右 对齐</li>
 *   <li>垂直方向：把当前卡片 上/中/下 与其它卡片 上/中/下 对齐</li>
 *   <li>距离 &le; {@link #SNAP_THRESHOLD} 直接吸附</li>
 *   <li>距离在 SNAP 与 {@link #ALIGN_THRESHOLD} 之间只显示辅助线，不吸附</li>
 * </ul>
 *
 * @author lww
 */
public final class BoardSnapHelper {

	/** 磁吸阈值（画板坐标像素），水平/垂直方向独立判断 */
	private static final double SNAP_THRESHOLD = 8.0;

	/** 对齐辅助线触发阈值（比磁吸稍大，便于提前显示） */
	private static final double ALIGN_THRESHOLD = 10.0;

	private BoardSnapHelper() {
	}

	/**
	 * 拖拽过程中计算对齐 + 磁吸。
	 *
	 * <p>直接修改 {@code moving} 的 bounds（吸附时），并返回辅助线的屏幕坐标（未命中时对应值为 NaN）。</p>
	 *
	 * @param moving      当前正在拖动的卡片
	 * @param all         所有卡片（含 moving，内部会跳过自身）
	 * @param zoomFactor  当前缩放倍率（用于把画板坐标转屏幕坐标）
	 * @param translateX  变换矩阵 X 平移量
	 * @param translateY  变换矩阵 Y 平移量
	 * @param panelWidth  面板宽（辅助线纵向范围）
	 * @param panelHeight 面板高（辅助线横向范围）
	 * @return 辅助线屏幕坐标（guideVX = 垂直辅助线 X，guideHY = 水平辅助线 Y，无则 NaN）
	 */
	public static SnapResult compute(KanbanCard moving, List<KanbanCard> all,
			double zoomFactor, double translateX, double translateY,
			int panelWidth, int panelHeight) {
		SnapResult result = new SnapResult();
		if (moving == null) {
			return result;
		}
		Rectangle2D mb = moving.getBounds();
		double cardW = mb.getWidth();
		double cardH = mb.getHeight();
		// 自己的 3 条候选边：左、中、右（X 方向）；上、中、下（Y 方向）
		double myLeft = mb.getX();
		double myCenterX = mb.getX() + cardW / 2.0;
		double myRight = mb.getX() + cardW;
		double myTop = mb.getY();
		double myCenterY = mb.getY() + cardH / 2.0;
		double myBottom = mb.getY() + cardH;

		// 收集其它卡片的 3 条候选边
		List<Double> xCandidates = new java.util.ArrayList<>();
		List<Double> yCandidates = new java.util.ArrayList<>();
		for (KanbanCard other : all) {
			if (other == moving) {
				continue;
			}
			Rectangle2D ob = other.getBounds();
			xCandidates.add(ob.getX());
			xCandidates.add(ob.getX() + ob.getWidth() / 2.0);
			xCandidates.add(ob.getX() + ob.getWidth());
			yCandidates.add(ob.getY());
			yCandidates.add(ob.getY() + ob.getHeight() / 2.0);
			yCandidates.add(ob.getY() + ob.getHeight());
		}

		if (xCandidates.isEmpty() && yCandidates.isEmpty()) {
			return result;
		}

		// X 方向：找最近的 (mySide, otherSide) 对，记录 mySide 是 0=左/1=中/2=右
		double bestDx = Double.POSITIVE_INFINITY;
		double bestSnapX = 0;
		int bestMyXSide = 0;
		boolean hasSnapX = false;
		if (!xCandidates.isEmpty()) {
			double[][] myXArr = {
					{myLeft, 0}, {myCenterX, 1}, {myRight, 2}
			};
			for (double[] mine : myXArr) {
				double mx = mine[0];
				int side = (int) mine[1];
				for (double ox : xCandidates) {
					double abs = Math.abs(ox - mx);
					if (abs < bestDx) {
						bestDx = abs;
						bestSnapX = ox;
						bestMyXSide = side;
						hasSnapX = true;
					}
				}
			}
		}

		// Y 方向：同上
		double bestDy = Double.POSITIVE_INFINITY;
		double bestSnapY = 0;
		int bestMyYSide = 0;
		boolean hasSnapY = false;
		if (!yCandidates.isEmpty()) {
			double[][] myYArr = {
					{myTop, 0}, {myCenterY, 1}, {myBottom, 2}
			};
			for (double[] mine : myYArr) {
				double my = mine[0];
				int side = (int) mine[1];
				for (double oy : yCandidates) {
					double abs = Math.abs(oy - my);
					if (abs < bestDy) {
						bestDy = abs;
						bestSnapY = oy;
						bestMyYSide = side;
						hasSnapY = true;
					}
				}
			}
		}

		// 吸附：把 bestSnap 写到对应的那条边（不是统一写左上角）
		if (hasSnapX && bestDx <= SNAP_THRESHOLD) {
			double newX;
			switch (bestMyXSide) {
				case 0: newX = bestSnapX; break;
				case 1: newX = bestSnapX - cardW / 2.0; break;
				case 2: newX = bestSnapX - cardW; break;
				default: newX = bestSnapX;
			}
			mb.setRect(newX, mb.getY(), cardW, cardH);
		}
		if (hasSnapY && bestDy <= SNAP_THRESHOLD) {
			double newY;
			switch (bestMyYSide) {
				case 0: newY = bestSnapY; break;
				case 1: newY = bestSnapY - cardH / 2.0; break;
				case 2: newY = bestSnapY - cardH; break;
				default: newY = bestSnapY;
			}
			mb.setRect(mb.getX(), newY, cardW, cardH);
		}

		// 对齐辅助线（在 ALIGN_THRESHOLD 内显示，画在吸附位置 = bestSnap，屏幕坐标）
		if (hasSnapX && bestDx <= ALIGN_THRESHOLD) {
			double screenX = bestSnapX * zoomFactor + translateX;
			result.guideVX = screenX;
			result.guideVFromY = 0;
			result.guideVToY = panelHeight;
		}
		if (hasSnapY && bestDy <= ALIGN_THRESHOLD) {
			double screenY = bestSnapY * zoomFactor + translateY;
			result.guideHY = screenY;
			result.guideHFromX = 0;
			result.guideHToX = panelWidth;
		}
		return result;
	}

	/**
	 * 磁吸 + 辅助线计算结果。
	 *
	 * <p>辅助线坐标均为屏幕坐标；无对应辅助线时值为 NaN。</p>
	 */
	public static class SnapResult {
		/** 垂直辅助线 X（屏幕坐标），无则为 NaN */
		public double guideVX = Double.NaN;
		/** 垂直辅助线 Y 范围 */
		public double guideVFromY;
		public double guideVToY;
		/** 水平辅助线 Y（屏幕坐标），无则为 NaN */
		public double guideHY = Double.NaN;
		/** 水平辅助线 X 范围 */
		public double guideHFromX;
		public double guideHToX;

		public boolean hasVertical() {
			return !Double.isNaN(guideVX);
		}

		public boolean hasHorizontal() {
			return !Double.isNaN(guideHY);
		}
	}
}
