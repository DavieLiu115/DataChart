package com.wd.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.wd.db.TableDropHandler;
import com.wd.db.TableInfo;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * 可拖拽的看板面板，简化自 DragableBoard
 *
 * <p>支持：画板平移、滚轮缩放、卡片拖拽、网格背景、空格复位</p>
 * <p>不包含：数据库表关系、连线、光点动画、右键菜单（按用户要求）</p>
 *
 * @author lww
 */
public class KanbanBoard extends JPanel {

	private static final Logger LOG = Logger.getInstance(KanbanBoard.class);

	/** 变换矩阵（用于平移和缩放） */
	private AffineTransform transform = new AffineTransform();

	/** 缩放因子 */
	private double zoomFactor = 1.0;

	/** 卡片列表 */
	private final List<KanbanCard> cards = new ArrayList<>();

	/** 当前被拖拽的卡片 */
	private KanbanCard draggedCard = null;

	/** 拖拽卡片时的偏移量（卡内坐标） */
	private final Point2D dragOffset = new Point2D.Double();

	/** 是否正在拖拽画板（空白区域按下） */
	private boolean isDraggingBoard = false;

	/** 鼠标上一个位置（用于计算拖拽增量） */
	private Point2D lastPoint;

	/** 当前选中的卡片 */
	private KanbanCard selectedCard = null;

	/** 是否显示网格 */
	private boolean showGrid = true;

	/** 网格大小 */
	private int gridSize = 20;

	/** 卡片默认尺寸 */
	private static final double DEFAULT_CARD_WIDTH = 200;
	private static final double DEFAULT_CARD_HEIGHT = 130;
	private static final double CARD_HSPACE = 30;
	private static final double CARD_VSPACE = 30;

	/** 表格卡片尺寸（列多，需要更高） */
	private static final double TABLE_CARD_WIDTH = 280;
	private static final double TABLE_CARD_ROW_HEIGHT = 18;
	private static final double TABLE_CARD_BASE_HEIGHT = 60;

	/** 背景色（适配深色 / 浅色主题） */
	private final Color backgroundColor = new JBColor(Gray._240, new Color(61, 63, 65));

	/** 网格颜色（适配深色 / 浅色主题） */
	private final Color gridColor = new JBColor(Gray._200, Gray._100);

	private final Project project;

	/** 拖拽目标处理器（接收 Database 表拖放） */
	private TableDropHandler dropHandler;

	public KanbanBoard(Project project) {
		this.project = project;
		setBackground(backgroundColor);
		setFocusable(true);
		initComponents();
		initDropTarget();
	}

	/**
	 * 注册拖拽目标：接收来自 Database 工具窗口的表格拖放
	 */
	private void initDropTarget() {
		dropHandler = new TableDropHandler(project, (info, dropPoint) -> {
			// 收到表元信息后在看板绘制表卡片
			addTableCard(info, dropPoint);
		});
		dropHandler.registerTo(this);
	}

	/**
	 * 初始化事件监听
	 */
	private void initComponents() {
		// 设置默认光标
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		MouseAdapter mouseHandler = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				lastPoint = e.getPoint();
				requestFocusInWindow();

				// 检查右键 - 直接返回，不弹菜单
				if (e.isPopupTrigger()) {
					return;
				}

				KanbanCard card = findCardAt(e.getPoint());
				if (card != null) {
					// 点击了卡片：开始拖拽卡片
					draggedCard = card;
					selectedCard = card;
					Point2D transformedPoint = transformPoint(e.getPoint());
					dragOffset.setLocation(
							transformedPoint.getX() - card.getBounds().getX(),
							transformedPoint.getY() - card.getBounds().getY());
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
					repaint();
				} else {
					// 点击空白区域：开始拖拽画板
					isDraggingBoard = true;
					selectedCard = null;
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
					repaint();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger()) {
					return;
				}
				isDraggingBoard = false;
				draggedCard = null;
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				repaint();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (draggedCard != null) {
					// 拖拽卡片
					try {
						Point2D transformedPoint = transformPoint(e.getPoint());
						double newX = transformedPoint.getX() - dragOffset.getX();
						double newY = transformedPoint.getY() - dragOffset.getY();
						Rectangle2D bounds = draggedCard.getBounds();
						bounds.setRect(newX, newY, bounds.getWidth(), bounds.getHeight());
						repaint();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				} else if (isDraggingBoard) {
					// 平移画板
					double dx = e.getX() - lastPoint.getX();
					double dy = e.getY() - lastPoint.getY();
					transform.translate(dx / zoomFactor, dy / zoomFactor);
					lastPoint = e.getPoint();
					repaint();
				}
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				double wheelRotation = e.getPreciseWheelRotation();
				Point2D p = e.getPoint();
				boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");

				if (isMac) {
					// Mac：双指捏合（带 Control/Meta/Alt）为缩放
					if (e.isControlDown() || e.isMetaDown() || e.isAltDown()) {
						double scaleFactor = Math.pow(1.1, -wheelRotation);
						zoom(p, scaleFactor);
					} else {
						// 双指滑动 = 平移（支持水平 + 垂直）
						panByWheel(e, wheelRotation);
					}
				} else {
					// 其他系统：Ctrl + 滚轮缩放
					if (e.isControlDown()) {
						double scaleFactor = wheelRotation < 0 ? 1.1 : 0.9;
						zoom(p, scaleFactor);
					} else {
						panByWheel(e, wheelRotation);
					}
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				KanbanCard card = findCardAt(e.getPoint());
				setCursor(card != null
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
						: Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			}
		};

		addMouseListener(mouseHandler);
		addMouseMotionListener(mouseHandler);
		addMouseWheelListener(mouseHandler);

		// 键盘监听：空格键复位视图
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_SPACE) {
					resetView();
				}
			}
		});
	}

	/**
	 * 根据滚轮事件平移画板，支持水平 + 垂直双轴移动
	 *
	 * <p>Mac 触控板：双指上下滑动 → 垂直平移，双指左右滑动 → 水平平移
	 * （JDK 将触控板横向滑动报告为 Shift+滚轮事件）</p>
	 *
	 * <p>普通鼠标滚轮：滚动 → 垂直平移，Shift+滚动 → 水平平移</p>
	 *
	 * @param e             滚轮事件
	 * @param wheelRotation 精确滚轮旋转量（正数为向上滚动）
	 */
	private void panByWheel(MouseWheelEvent e, double wheelRotation) {
		// 平移灵敏度（像素/滚轮单位）
		double sensitivity = 20;
		double deltaX = 0;
		double deltaY = -wheelRotation * sensitivity;

		// 横向滚动：Shift+滑动（Mac 触控板两指横向滑动也以 Shift 报告）
		if (e.isShiftDown()) {
			deltaX = deltaY;
			deltaY = 0;
		}

		// 除以 zoomFactor，保证缩放后移动量跟手
		transform.translate(deltaX / zoomFactor, deltaY / zoomFactor);
		repaint();
	}

	/**
	 * 屏幕坐标转换为画板坐标
	 */
	private Point2D transformPoint(Point2D screenPoint) {
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
	 * 查找指定屏幕坐标下的卡片
	 */
	private KanbanCard findCardAt(Point2D point) {
		try {
			Point2D transformedPoint = transformPoint(point);
			// 反向遍历（Z序：后添加的在上层）
			for (int i = cards.size() - 1; i >= 0; i--) {
				KanbanCard card = cards.get(i);
				if (card.getBounds().contains(transformedPoint)) {
					return card;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 添加卡片（自动平铺，避免重叠）
	 */
	public void addCard(KanbanCard card) {
		if (card == null) {
			return;
		}
		// 自动布局：横向平铺，到达右边界后换行
		if (!cards.isEmpty()) {
			KanbanCard last = cards.get(cards.size() - 1);
			Rectangle2D lastBounds = last.getBounds();
			double nextX = lastBounds.getX() + lastBounds.getWidth() + CARD_HSPACE;
			double nextY = lastBounds.getY();
			// 换行判断
			if (nextX + DEFAULT_CARD_WIDTH > 4 * DEFAULT_CARD_WIDTH) {
				nextX = 50;
				nextY = lastBounds.getY() + DEFAULT_CARD_HEIGHT + CARD_VSPACE;
			}
			card.setBounds(new Rectangle2D.Double(
					nextX, nextY, DEFAULT_CARD_WIDTH, DEFAULT_CARD_HEIGHT));
		} else {
			card.setBounds(new Rectangle2D.Double(
					50, 50, DEFAULT_CARD_WIDTH, DEFAULT_CARD_HEIGHT));
		}
		cards.add(card);
		repaint();
	}

	/**
	 * 删除卡片
	 */
	public boolean removeCard(KanbanCard card) {
		boolean result = cards.remove(card);
		if (result) {
			if (selectedCard == card) {
				selectedCard = null;
			}
			repaint();
		}
		return result;
	}

	/**
	 * 清空所有卡片
	 */
	public void clearCards() {
		cards.clear();
		selectedCard = null;
		repaint();
	}

	/**
	 * 复位视图（位置 + 缩放）
	 */
	public void resetView() {
		transform = new AffineTransform();
		zoomFactor = 1.0;
		repaint();
	}

	/**
	 * 以指定点为中心缩放
	 */
	public void zoom(Point2D p, double scaleFactor) {
		double newZoom = zoomFactor * scaleFactor;
		if (newZoom < 0.1 || newZoom > 10.0) {
			return;
		}
		zoomFactor = newZoom;

		AffineTransform old = new AffineTransform(transform);
		transform.setToIdentity();
		transform.translate(p.getX(), p.getY());
		transform.scale(scaleFactor, scaleFactor);
		transform.translate(-p.getX(), -p.getY());
		transform.concatenate(old);
		repaint();
	}

	public double getZoomFactor() {
		return zoomFactor;
	}

	public List<KanbanCard> getCards() {
		return cards;
	}

	public KanbanCard getSelectedCard() {
		return selectedCard;
	}

	public void setShowGrid(boolean showGrid) {
		this.showGrid = showGrid;
		repaint();
	}

	public boolean isShowGrid() {
		return showGrid;
	}

	/**
	 * 是否深色主题
	 */
	private boolean isDarkTheme() {
		Color bg = getBackground();
		if (bg == null) {
			return false;
		}
		// 根据亮度判断（YIQ 公式）
		double brightness = (bg.getRed() * 299 + bg.getGreen() * 587 + bg.getBlue() * 114) / 1000.0;
		return brightness < 128;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g.create();

		// 渲染提示
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		// 应用变换
		g2d.transform(transform);

		// 1. 绘制网格
		if (showGrid) {
			drawGrid(g2d);
		}

		// 2. 绘制所有卡片
		boolean dark = isDarkTheme();
		for (KanbanCard card : cards) {
			boolean isSelected = (card == selectedCard);
			card.setSelected(isSelected);
			card.draw(g2d, dark);
		}

		g2d.dispose();
	}

	/**
	 * 绘制网格
	 */
	private void drawGrid(Graphics2D g2d) {
		g2d.setColor(gridColor);
		g2d.setStroke(new BasicStroke(0.5f));

		Rectangle bounds = getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
			return;
		}

		// 计算模型空间的可见范围
		double[] pts = new double[4];
		try {
			AffineTransform inverse = transform.createInverse();
			Point2D p1 = new Point2D.Double();
			Point2D p2 = new Point2D.Double(bounds.width, bounds.height);
			inverse.transform(p1, p1);
			inverse.transform(p2, p2);
			double minX = Math.min(p1.getX(), p2.getX());
			double maxX = Math.max(p1.getX(), p2.getX());
			double minY = Math.min(p1.getY(), p2.getY());
			double maxY = Math.max(p1.getY(), p2.getY());

			int startX = (int) (Math.floor(minX / gridSize) * gridSize);
			int startY = (int) (Math.floor(minY / gridSize) * gridSize);
			int endX = (int) (Math.ceil(maxX / gridSize) * gridSize);
			int endY = (int) (Math.ceil(maxY / gridSize) * gridSize);

			// 缩放过小时不绘制网格（性能考虑）
			if (zoomFactor < 0.3) {
				return;
			}

			for (int x = startX; x <= endX; x += gridSize) {
				g2d.drawLine(x, startY, x, endY);
			}
			for (int y = startY; y <= endY; y += gridSize) {
				g2d.drawLine(startX, y, endX, y);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 添加一个数据库表卡片（自动布局）
	 *
	 * <p>根据 TableInfo 的列数自动计算卡片高度，默认宽度 280px。</p>
	 *
	 * @param info 表元信息（来自 {@code TableMetadataService}）
	 */
	public void addTableCard(TableInfo info) {
		addTableCard(info, null);
	}

	/**
	 * 添加一个数据库表卡片，可指定位置（拖放点）
	 *
	 * <p>如果指定了位置，卡片出现在该位置；否则自动平铺布局。</p>
	 *
	 * @param info      表元信息
	 * @param dropPoint 拖放位置（画板坐标），可为 null
	 */
	public void addTableCard(TableInfo info, Point dropPoint) {
		if (info == null) {
			LOG.warn("[看板] addTableCard 收到 null TableInfo，忽略");
			return;
		}
		// 高度 = 顶部 header + 列数行 + 底部 padding
		int rowCount = Math.max(3, info.getColumns().size()); // 最少显示 3 行高度
		double height = TABLE_CARD_BASE_HEIGHT + rowCount * TABLE_CARD_ROW_HEIGHT;
		// 限制最大高度（避免单卡过高）
		height = Math.min(height, 400);

		double x;
		double y;
		if (dropPoint != null) {
			// 拖放点转换为画板坐标
			Point2D boardPoint = transformPoint(dropPoint);
			// 让卡片中心对准拖放点
			x = boardPoint.getX() - TABLE_CARD_WIDTH / 2;
			y = boardPoint.getY() - height / 2;
		} else {
			x = 0;
			y = 0;
		}

		LOG.info("[看板] 开始绘制表卡片: 表=" + info.getName()
				+ ", 数据源=" + info.getDatasourceName()
				+ ", schema=" + info.getSchema()
				+ ", 字段数=" + info.getColumns().size()
				+ ", 拖放屏幕点=" + (dropPoint == null ? "null" : dropPoint.x + "," + dropPoint.y)
				+ ", 卡片画板坐标=(" + (int) x + "," + (int) y + ")"
				+ ", 卡片尺寸=" + (int) TABLE_CARD_WIDTH + "x" + (int) height
				+ ", 缩放=" + zoomFactor);

		KanbanCard card = KanbanCard.forTable(info.getId(), info,
				x, y, TABLE_CARD_WIDTH, height);
		if (dropPoint != null) {
			cards.add(card); // 直接添加到指定位置，不自动平铺
			LOG.info("[看板] 卡片已添加，当前卡片总数=" + cards.size() + "，请求重绘");
			repaint();
		} else {
			addCard(card);
		}
	}

	/**
	 * 获取 Project
	 */
	public Project getProject() {
		return project;
	}
}