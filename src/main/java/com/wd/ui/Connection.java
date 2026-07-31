package com.wd.ui;

import com.wd.model.RelationType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/**
 * 看板上的表连接线（贝塞尔曲线）
 *
 * @author lww
 */
public class Connection {

	/** 默认线宽（从 1.6 调到 2.4，更明显） */
	private static final float DEFAULT_STROKE_WIDTH = 2.4f;

	private final KanbanCard source;
	private final int sourceRow;
	private final KanbanCard target;
	private final int targetRow;
	private Color color;
	private float strokeWidth = DEFAULT_STROKE_WIDTH;

	/** 关系类型：决定起点/终点的形状（鸟爪/分叉等） */
	private RelationType relationType = RelationType.UNKNOWN;

	public Connection(KanbanCard source, int sourceRow, KanbanCard target, int targetRow, Color color) {
		this(source, sourceRow, target, targetRow, color, RelationType.UNKNOWN);
	}

	public Connection(KanbanCard source, int sourceRow, KanbanCard target, int targetRow,
			Color color, RelationType relationType) {
		this.source = source;
		this.sourceRow = sourceRow;
		this.target = target;
		this.targetRow = targetRow;
		this.color = color;
		this.relationType = relationType == null ? RelationType.UNKNOWN : relationType;
	}

	public KanbanCard getSource() {
		return source;
	}

	public int getSourceRow() {
		return sourceRow;
	}

	public KanbanCard getTarget() {
		return target;
	}

	public int getTargetRow() {
		return targetRow;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public float getStrokeWidth() {
		return strokeWidth;
	}

	public void setStrokeWidth(float strokeWidth) {
		this.strokeWidth = strokeWidth;
	}

	public RelationType getRelationType() {
		return relationType;
	}

	public void setRelationType(RelationType relationType) {
		this.relationType = relationType == null ? RelationType.UNKNOWN : relationType;
	}

	/**
	 * 决定线实际渲染颜色：
	 *
	 * <ul>
	 *   <li>如果源行被高亮（用户选中橙色 / 关联列橙色 / 连线占用色），用源行高亮色 → 整条线统一为该色</li>
	 *   <li>否则使用连线 palette 颜色</li>
	 * </ul>
	 *
	 * <p>需求 1：起点有背景颜色时，连线终点也用这个颜色，保持一整条线一个颜色。</p>
	 */
	private Color resolveLineColor() {
		Color sourceHighlight = source.getHighlightedColorForRow(sourceRow);
		if (sourceHighlight != null) {
			return sourceHighlight;
		}
		return color;
	}

	public void draw(Graphics2D g2d) {
		Point2D sourcePoint = source.getRowRight(sourceRow);
		Point2D targetPoint = target.getRowLeft(targetRow);
		if (sourcePoint == null || targetPoint == null) {
			return;
		}

		// 需求 1：起点行有背景色 → 整条线统一为该色
		Color lineColor = resolveLineColor();

		g2d.setColor(lineColor);
		g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		double dx = Math.abs(targetPoint.getX() - sourcePoint.getX());
		double ctrlX1 = sourcePoint.getX() + dx / 2.0;
		double ctrlY1 = sourcePoint.getY();
		double ctrlX2 = targetPoint.getX() - dx / 2.0;
		double ctrlY2 = targetPoint.getY();

		g2d.draw(new CubicCurve2D.Double(
				sourcePoint.getX(), sourcePoint.getY(),
				ctrlX1, ctrlY1,
				ctrlX2, ctrlY2,
				targetPoint.getX(), targetPoint.getY()));

		// 在源/目标端点处根据 relationType 绘制形状（鸟爪/竖线等），用与线相同的颜色
		drawEndpointShape(g2d, sourcePoint, targetPoint, true, lineColor);
		drawEndpointShape(g2d, targetPoint, sourcePoint, false, lineColor);
	}

	/**
	 * 在端点处根据 relationType 绘制小形状，标识关系类型
	 *
	 * <p>ER 图常用约定：</p>
	 * <ul>
	 *   <li>ONE_TO_ONE  → 两端单竖线（"1"）</li>
	 *   <li>ONE_TO_MANY → "1" 端单竖线，"多" 端分叉（crow's foot）</li>
	 *   <li>MANY_TO_ONE → "多" 端分叉，"1" 端单竖线</li>
	 *   <li>MANY_TO_MANY → 两端都分叉</li>
	 * </ul>
	 *
	 * <p>约定：源卡片 = "1" 侧，目标卡片 = "多" 侧（用户也可以从右到左连，因此根据 type 而非方向判断）。</p>
	 *
	 * @param shapeColor 形状颜色（通常与线色一致）
	 */
	private void drawEndpointShape(Graphics2D g2d, Point2D endpoint, Point2D otherEnd,
			boolean isSource, Color shapeColor) {
		// 决定本端是"1"端还是"多"端
		boolean isOneSide;
		switch (relationType) {
			case ONE_TO_ONE:
				isOneSide = true;
				break;
			case ONE_TO_MANY:
				isOneSide = isSource;
				break;
			case MANY_TO_ONE:
				isOneSide = !isSource;
				break;
			case MANY_TO_MANY:
				isOneSide = false;
				break;
			default:
				// UNKNOWN：不画端点形状，保持简洁
				return;
		}

		// 沿连线方向（指向对方）
		double dirX = otherEnd.getX() - endpoint.getX();
		double dirY = otherEnd.getY() - endpoint.getY();
		double len = Math.hypot(dirX, dirY);
		if (len < 1e-3) {
			return;
		}
		double ux = dirX / len;
		double uy = dirY / len;
		// 法线（垂直于连线）
		double nx = -uy;
		double ny = ux;

		// 形状大小（随线宽缩放）
		double size = Math.max(8.0, strokeWidth * 4.5);

		// 备份当前 stroke，画端点形状时用细一点的描边
		java.awt.Stroke oldStroke = g2d.getStroke();
		g2d.setColor(shapeColor);
		g2d.setStroke(new BasicStroke(Math.max(1.2f, strokeWidth * 0.85f),
				BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		if (isOneSide) {
			// 单竖线（"1" 端）：在与连线垂直方向画一条短线
			double x1 = endpoint.getX() + nx * size;
			double y1 = endpoint.getY() + ny * size;
			double x2 = endpoint.getX() - nx * size;
			double y2 = endpoint.getY() - ny * size;
			g2d.draw(new Line2D.Double(x1, y1, x2, y2));
		} else {
			// "多" 端：crow's foot（三叉），中间一根沿连线方向，两侧各 45°
			// 端点稍向内缩，避免遮挡卡片
			double bx = endpoint.getX() + ux * (size * 0.15);
			double by = endpoint.getY() + uy * (size * 0.15);
			// 中间一支（沿连线指向对方）
			double mx = bx + ux * size;
			double my = by + uy * size;
			g2d.draw(new Line2D.Double(bx, by, mx, my));
			// 左支
			double lx = bx + ux * size * 0.6 + nx * size * 0.8;
			double ly = by + uy * size * 0.6 + ny * size * 0.8;
			g2d.draw(new Line2D.Double(bx, by, lx, ly));
			// 右支
			double rx = bx + ux * size * 0.6 - nx * size * 0.8;
			double ry = by + uy * size * 0.6 - ny * size * 0.8;
			g2d.draw(new Line2D.Double(bx, by, rx, ry));
		}

		g2d.setStroke(oldStroke);
	}
}
