package com.wd.ui;

import com.wd.model.RelationType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
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

	/** 起点/终点水平引出线长度下限（画板坐标，px） */
	private static final double LEAD_MIN = 24.0;
	/** 起点/终点水平引出线长度上限（画板坐标，px） */
	private static final double LEAD_MAX = 60.0;

	/** 端点椭圆中心向外偏移距离（画板坐标，px），让椭圆大部分露在卡片外，更醒目 */
	private static final double ELLIPSE_OFFSET = 6.0;

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

		// 计算起点/终点的水平引出线长度：
		// 引出线（始终水平）让连线在靠近卡片时是直的，再进入贝塞尔曲线过渡，视觉上更顺。
		double dx = targetPoint.getX() - sourcePoint.getX();
		// 取总水平距离的 1/3，最少 LEAD_MIN，最多 LEAD_MAX
		double leadLen = Math.max(LEAD_MIN, Math.min(LEAD_MAX, Math.abs(dx) / 3.0));
		// 终点在起点左侧（dx<0）时，引出线方向反转
		double dirSign = dx >= 0 ? 1.0 : -1.0;

		// 起点侧引出线终点（从源卡向右/左走 leadLen，保持水平）
		double leadStartX = sourcePoint.getX() + dirSign * leadLen;
		double leadStartY = sourcePoint.getY();
		// 终点侧引出线起点（从目标卡向左/右走 leadLen，保持水平）
		double leadEndX = targetPoint.getX() - dirSign * leadLen;
		double leadEndY = targetPoint.getY();

		// 1. 起点水平引出线
		g2d.draw(new Line2D.Double(sourcePoint.getX(), sourcePoint.getY(),
				leadStartX, leadStartY));

		// 2. 终点水平引出线（留到画完曲线后再画，避免与曲线交叠）
		// 3. 中间的贝塞尔曲线：起点用 leadStart（水平），终点用 leadEnd（水平）
		//    控制点的 X = 中点附近，Y 与各自端点 Y 相同 → 让曲线两端水平进入/水平离开
		double ctrlX1 = leadStartX + (leadEndX - leadStartX) / 2.0;
		double ctrlY1 = leadStartY;
		double ctrlX2 = leadEndX - (leadEndX - leadStartX) / 2.0;
		double ctrlY2 = leadEndY;

		g2d.draw(new CubicCurve2D.Double(
				leadStartX, leadStartY,
				ctrlX1, ctrlY1,
				ctrlX2, ctrlY2,
				leadEndX, leadEndY));

		// 4. 终点水平引出线（接曲线 → 终点）
		g2d.draw(new Line2D.Double(leadEndX, leadEndY,
				targetPoint.getX(), targetPoint.getY()));

		// 在源/目标端点处画开口椭圆（关系端点标识），用与线相同的颜色
		// 由于端点前一段是水平的，椭圆长轴 = 竖直方向，与连线方向垂直
		// 椭圆中心向外偏移 = 远离卡片：sourcePoint 在源卡右边 → 向右偏移；targetPoint 在目标卡左边 → 向左偏移
		// 偏移方向与连接方向无关（源/目标点的几何位置就决定"外侧"）
		drawEndpointShape(g2d, sourcePoint, +1, lineColor);
		drawEndpointShape(g2d, targetPoint, -1, lineColor);
	}

	/**
	 * 在端点处绘制一个开口椭圆（"环"/"眼"形状），用于标识关系端点。
	 *
	 * <p>形状说明：</p>
	 * <ul>
	 *   <li>椭圆中心在端点处，**长轴垂直于连线方向，短轴沿连线方向**</li>
	 *   <li>由于上一节已保证端点前的引出线始终是水平的，因此椭圆长轴 = 竖直方向</li>
	 *   <li>两端使用相同形状（不再区分 "1" / "多"），视觉更简洁一致</li>
	 *   <li>为让端点标识更醒目，椭圆整体**向外（远离卡片）偏移 ELLIPSE_OFFSET**，避免一半被卡片遮挡</li>
	 * </ul>
	 *
	 * @param shapeColor 形状颜色（与线色一致）
	 */
	private void drawEndpointShape(Graphics2D g2d, Point2D endpoint, double dirSign, Color shapeColor) {
		double size = Math.max(8.0, strokeWidth * 4.5);
		// 椭圆长轴（垂直）= size * 1.6，短轴（水平，沿线）= size * 0.7
		double halfMajor = size * 0.8;
		double halfMinor = size * 0.35;
		// 椭圆中心向"远离卡片"方向偏移 ELLIPSE_OFFSET，避免一半被卡片遮挡
		double offsetX = dirSign * ELLIPSE_OFFSET;

		// 备份当前 stroke，画椭圆时用细一点的描边，让环看起来更精致
		java.awt.Stroke oldStroke = g2d.getStroke();
		g2d.setColor(shapeColor);
		g2d.setStroke(new BasicStroke(Math.max(1.2f, strokeWidth * 0.85f),
				BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		// 椭圆：以端点向外偏移后为中心，长轴竖直（y 方向），短轴水平（x 方向）
		// Ellipse2D 的 x/y 是左上角坐标，width/height 是宽/高
		g2d.draw(new Ellipse2D.Double(
				endpoint.getX() + offsetX - halfMinor,
				endpoint.getY() - halfMajor,
				halfMinor * 2.0,
				halfMajor * 2.0));

		g2d.setStroke(oldStroke);
	}
}
