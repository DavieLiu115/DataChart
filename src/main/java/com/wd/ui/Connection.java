package com.wd.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Point2D;

/**
 * 看板上的表连接线（贝塞尔曲线）
 *
 * @author lww
 */
public class Connection {

	private final KanbanCard source;
	private final int sourceRow;
	private final KanbanCard target;
	private final int targetRow;
	private Color color;
	private float strokeWidth = 1.6f;

	public Connection(KanbanCard source, int sourceRow, KanbanCard target, int targetRow, Color color) {
		this.source = source;
		this.sourceRow = sourceRow;
		this.target = target;
		this.targetRow = targetRow;
		this.color = color;
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

	public void draw(java.awt.Graphics2D g2d) {
		Point2D sourcePoint = source.getRowRight(sourceRow);
		Point2D targetPoint = target.getRowLeft(targetRow);
		if (sourcePoint == null || targetPoint == null) {
			return;
		}
		g2d.setColor(color);
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
	}
}