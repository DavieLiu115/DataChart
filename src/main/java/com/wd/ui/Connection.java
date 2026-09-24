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

	/**
	 * 默认线宽（从 1.6 调到 2.4，更明显）。
	 *
	 * <p>2026-09-24 由 {@code private} 改为 {@code public}：{@link KanbanBoard} 画"连线预览"时也复用它 ——
	 * 预览线原来写死 1.6f，颜色统一之后宽度还差一档，拖拽松手会看到线"变粗"一下（用户要求完全一致）。</p>
	 */
	public static final float DEFAULT_STROKE_WIDTH = 2.4f;

	/** 起点/终点水平引出线长度下限（画板坐标，px） */
	private static final double LEAD_MIN = 24.0;
	/** 起点/终点水平引出线长度上限（画板坐标，px） */
	private static final double LEAD_MAX = 60.0;

	private final KanbanCard source;
	private int sourceRow;
	private final KanbanCard target;
	private int targetRow;
	private Color color;
	private float strokeWidth = DEFAULT_STROKE_WIDTH;

	/**
	 * 该连线在调色板中的序号（{@code -1} = 不是调色板分配的色）。
	 *
	 * <p>2026-09-24 新增，仅用于持久化：保存时写进 {@code ChartRelation.colorIndex}，
	 * 让重新打开文件后颜色保持不变（详见 {@link com.wd.model.ChartRelation#getColorIndex()}）。</p>
	 */
	private int colorIndex = -1;

	/** 关系类型：决定起点/终点的形状（鸟爪/分叉等）；新建连线默认一对一 */
	private RelationType relationType = RelationType.ONE_TO_ONE;

	public Connection(KanbanCard source, int sourceRow, KanbanCard target, int targetRow, Color color) {
		this(source, sourceRow, target, targetRow, color, RelationType.ONE_TO_ONE);
	}

	public Connection(KanbanCard source, int sourceRow, KanbanCard target, int targetRow,
			Color color, RelationType relationType) {
		this.source = source;
		this.sourceRow = sourceRow;
		this.target = target;
		this.targetRow = targetRow;
		this.color = color;
		this.relationType = relationType == null ? RelationType.ONE_TO_ONE : relationType;
	}

	public KanbanCard getSource() {
		return source;
	}

	public int getSourceRow() {
		return sourceRow;
	}

	/**
	 * 重定位源行（2026-08-07 同步表结构后按列名重新定位连线时使用）。
	 */
	public void setSourceRow(int sourceRow) {
		this.sourceRow = sourceRow;
	}

	public KanbanCard getTarget() {
		return target;
	}

	public int getTargetRow() {
		return targetRow;
	}

	/**
	 * 重定位目标行（2026-08-07 同步表结构后按列名重新定位连线时使用）。
	 */
	public void setTargetRow(int targetRow) {
		this.targetRow = targetRow;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	/** 调色板序号（{@code -1} 表示非调色板色）；仅供持久化使用。 */
	public int getColorIndex() {
		return colorIndex;
	}

	public void setColorIndex(int colorIndex) {
		this.colorIndex = colorIndex;
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
		this.relationType = relationType == null ? RelationType.ONE_TO_ONE : relationType;
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

	/**
	 * 对外暴露连线实际渲染色（{@link #resolveLineColor()} 的公开入口）。
	 *
	 * <p>供 {@code KanbanBoard} 计算"连线占用行/终点行背景色"使用，
	 * 保证：线是什么颜色 → 它落在的那一行背景就是什么颜色。</p>
	 *
	 * @return 连线实际渲染色（起点行高亮色优先，否则 palette 色）
	 */
	public Color getResolvedLineColor() {
		return resolveLineColor();
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

		// 根据 relationType 判断源端（source）和目标端（target）是否为"多"端：
		// ONE_TO_ONE: 源 1, 目标 1 -> 都不画三叉（直线）
		// ONE_TO_MANY: 源 1, 目标 多 -> 源不画三叉，目标画三叉
		// MANY_TO_ONE: 源 多, 目标 1 -> 源画三叉，目标不画三叉
		// MANY_TO_MANY / UNKNOWN: 源 多, 目标 多 -> 两端都画三叉
		boolean sourceIsMany = relationType == RelationType.MANY_TO_ONE || relationType == RelationType.MANY_TO_MANY || relationType == RelationType.UNKNOWN;
		boolean targetIsMany = relationType == RelationType.ONE_TO_MANY || relationType == RelationType.MANY_TO_MANY || relationType == RelationType.UNKNOWN;

		if (sourceIsMany) {
			drawEndpointShape(g2d, sourcePoint, +1, lineColor);
		}
		if (targetIsMany) {
			drawEndpointShape(g2d, targetPoint, -1, lineColor);
		}
	}

	/**
	 * 在端点处绘制"三叉/鸟爪"(Crow's foot)分叉线（代表"多"的一端）。
	 *
	 * <p>形状说明：</p>
	 * <ul>
	 *   <li>从卡片边缘 (endpoint) 向外部引出 3 根线分支，收拢合并到主连线上 (endpoint + dirSign * forkLength)</li>
	 *   <li>中线：直接从卡片边缘 (endpoint) 水平连接到汇合点 (cx, cy)</li>
	 *   <li>上线：从 (endpoint.x, cy - spread) 斜着连接到汇合点 (cx, cy)</li>
	 *   <li>下线：从 (endpoint.x, cy + spread) 斜着连接到汇合点 (cx, cy)</li>
	 * </ul>
	 *
	 * @param shapeColor 形状颜色（与线色一致）
	 */
	private void drawEndpointShape(Graphics2D g2d, Point2D endpoint, double dirSign, Color shapeColor) {
		// 分叉在水平方向的延伸长度（沿连线方向远离卡片）
		double forkLength = Math.max(10.0, strokeWidth * 4.5);
		// 上下分叉在卡片边缘的张开半高度
		double spread = Math.max(6.0, strokeWidth * 2.8);

		double ex = endpoint.getX();
		double ey = endpoint.getY();

		// 3根分支在主线上汇合的点
		double cx = ex + dirSign * forkLength;
		double cy = ey;

		java.awt.Stroke oldStroke = g2d.getStroke();
		g2d.setColor(shapeColor);
		g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		// 中线：卡片边缘 (ex, ey) -> 汇合点 (cx, cy)
		g2d.draw(new Line2D.Double(ex, ey, cx, cy));
		// 上线：卡片边缘偏上 (ex, ey - spread) -> 汇合点 (cx, cy)
		g2d.draw(new Line2D.Double(ex, ey - spread, cx, cy));
		// 下线：卡片边缘偏下 (ex, ey + spread) -> 汇合点 (cx, cy)
		g2d.draw(new Line2D.Double(ex, ey + spread, cx, cy));

		g2d.setStroke(oldStroke);
	}
}
