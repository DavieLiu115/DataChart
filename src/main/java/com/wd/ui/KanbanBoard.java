package com.wd.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.wd.db.TableDropHandler;
import com.wd.db.TableInfo;
import com.wd.model.ChartData;
import com.wd.model.ChartRelation;
import com.wd.model.RelationType;
import java.awt.AlphaComposite;
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
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;

/**
 * 可拖拽的看板面板。
 *
 * <p>支持：画板平移、滚轮缩放、卡片拖拽、网格背景、空格复位、连线、搜索、磁吸对齐。</p>
 *
 * <p>架构（2026-08-01 重构拆分类）：</p>
 * <ul>
 *   <li>{@link BoardViewport}：视口几何（变换矩阵 / 缩放 / 平移 / 聚焦 / 坐标转换）</li>
 *   <li>{@link BoardSearchModel}：搜索状态与导航逻辑</li>
 *   <li>{@link BoardSnapHelper}：拖拽磁吸 + 对齐辅助线计算</li>
 *   <li>{@link BoardContextMenu}：右键菜单构建与剪贴板</li>
 *   <li>{@link BoardPersistence}：与 {@link ChartData} 的持久化转换</li>
 *   <li>{@link BoardExportUtil}：导出 PDF / 图片</li>
 *   <li>{@link NotificationUtil}：提醒 / 通知</li>
 * </ul>
 *
 * <p>本类聚焦：JPanel 生命周期、事件监听、渲染编排（paintComponent / paintForExport）、
 * 卡片与连线管理、关联列高亮协调。</p>
 *
 * @author lww
 */
public class KanbanBoard extends JPanel {

	private static final Logger LOG = Logger.getInstance(KanbanBoard.class);

	/** 视口几何（变换矩阵 + 缩放） */
	private final BoardViewport viewport = new BoardViewport();

	/** 搜索模型 */
	private final BoardSearchModel searchModel = new BoardSearchModel();

	/** 卡片列表 */
	private final List<KanbanCard> cards = new ArrayList<>();

	/** 当前被拖拽的卡片 */
	private KanbanCard draggedCard = null;

	/** 拖拽卡片时的偏移量（卡内坐标） */
	private final Point2D dragOffset = new Point2D.Double();

	/** 是否正在拖拽画板（空白区域按下，2026-08-20 起空白拖拽改为框选，此字段保留兼容） */
	private boolean isDraggingBoard = false;

	/** 鼠标上一个位置（用于计算拖拽增量） */
	private Point2D lastPoint;

	/** 当前选中的卡片（主选中卡，兼容单卡逻辑；多选时是 selectedCards 中 Z 序最上层的一张） */
	private KanbanCard selectedCard = null;

	/** 多选集合（2026-08-20 框选引入：空白拖拽画选区，相交卡片全部选中） */
	private final java.util.Set<KanbanCard> selectedCards = new java.util.LinkedHashSet<>();

	/** 是否正在框选（空白区域按下拖动） */
	private boolean isSelecting = false;

	/** 框选起点（画板坐标） */
	private Point2D selectionStartBoard = null;

	/** 当前框选矩形（画板坐标，实时更新） */
	private java.awt.geom.Rectangle2D selectionRect = null;

	/** 空白拖拽升级为框选的最小拖动距离（画板坐标，避免单击空白误触发选区） */
	private static final double SELECTION_DRAG_THRESHOLD = 4;

	/** 连线列表 */
	private final List<Connection> connections = new ArrayList<>();

	/**
	 * 连线占用行缓存。2026-08-03 优化：null 表示需重算（连线增删时置 null），
	 * 非 null 时复用，避免每次重绘全量遍历连线。
	 */
	private Map<KanbanCard, Map<Integer, Color>> linkedRowsCache;

	/** 连线预览高亮（鼠标拖动时临时高亮源/目标行） */
	private final Map<KanbanCard, Map<Integer, Color>> previewHighlightRows = new HashMap<>();

	/** 当前激活的"用户选中列"（用于触发关联列高亮） */
	private KanbanCard activeHighlightCard = null;
	private int activeHighlightRow = -1;

	/** 关联列集合：与 activeHighlight 构成连线的对方列（card, rowIndex） */
	private final Set<String> relatedRowKeys = new HashSet<>();

	/** 当前帧需要绘制的对齐辅助线（屏幕坐标，null 表示无） */
	private Line2D.Double activeSnapGuideV = null;
	private Line2D.Double activeSnapGuideH = null;

	/** 当前选中的连线 */
	private Connection selectedConnection = null;

	/** 连线模式：源卡片和源行 */
	private KanbanCard connectionSource = null;
	private int connectionSourceRow = -2;
	/** 连线模式：当前鼠标位置（画板坐标） */
	private Point2D connectionCurrentPoint = null;
	/** 连线模式：临时预览线 */
	private boolean isConnecting = false;

	/**
	 * 连线拖拽过程中最后一次命中的目标卡片/行（2026-08-04 修复）：
	 * 鼠标悬停在 leader 等目标列行时记录，即使松手时鼠标飘到空白处，仍按该目标建线。
	 * 解决"鼠标移到 leader 上没点击，点一下别处连接就没了"的问题。
	 */
	private KanbanCard lastHoverTargetCard = null;
	private int lastHoverTargetRow = -1;

	/**
	 * 连线起手判定（2026-08-04 修复 Win 系统"列行左键起手不能连线"）：
	 * 在列行区域按下左键时，先进入"待连线"状态（不立即显示预览线，也不触发列高亮），
	 * 等鼠标移动超过 {@link #CONNECTION_DRAG_THRESHOLD} 像素才升级为真正连线模式。
	 * 这样既能保留"快速点击列 = 选中高亮"的原有交互，又能让左键起手拖拽自动连线。
	 */
	private KanbanCard pendingConnectionSource = null;
	private int pendingConnectionSourceRow = -2;
	private Point pendingConnectionPressPoint = null;
	/** 左键拖动升级为连线的距离阈值（屏幕像素） */
	private static final int CONNECTION_DRAG_THRESHOLD = 4;

	/** 是否显示网格 */
	private boolean showGrid = true;

	/** 网格大小 */
	private int gridSize = 20;

	/** 卡片默认尺寸 */
	private static final double DEFAULT_CARD_WIDTH = 200;

	/** 连线颜色集合（浅色不饱和，每条连线用一种） */
	private static final Color[] CONNECTION_COLOR_PALETTE = {
			new Color(0xB0C4DE), // 浅钢蓝
			new Color(0xC8A2C8), // 淡紫
			new Color(0xFFD1A4), // 浅橙
			new Color(0xC1E1C5), // 浅绿
			new Color(0xFFB7B2), // 浅粉红
			new Color(0xFFE9A8), // 浅黄
			new Color(0xAEC6CF), // 浅蓝灰
			new Color(0xD7BDE2)  // 淡紫罗兰
	};

	/** 当前连线颜色索引（循环分配） */
	private int connectionColorIndex = 0;

	/** 行选中高亮颜色（用户点击选中行时使用） */
	private static final Color SELECTED_ROW_COLOR = new Color(0xFE9933);

	/** 关联列高亮颜色（选中某列时，与之有连线的另一列也用此色高亮） */
	private static final Color RELATED_ROW_COLOR = new Color(0xFD9933);

	/** 连线模式临时预览颜色（2026-08-04 由粉色改为深灰色，浅色主题深灰 / 深色主题浅灰） */
	private static final Color CONNECTION_PREVIEW_COLOR = new JBColor(
			new Color(0x757575), // 浅色主题：深灰，清晰可见
			new Color(0xAAAAAA)); // 深色主题：浅灰，避免与暗背景对比不足

	/** 对齐辅助线颜色（深色主题下稍亮，浅色主题下稍深） */
	private static final Color ALIGN_GUIDE_COLOR_LIGHT = new Color(0xFE9933);
	private static final Color ALIGN_GUIDE_COLOR_DARK = new Color(0xFFB266);

	private static final double DEFAULT_CARD_HEIGHT = 130;
	private static final double CARD_HSPACE = 30;
	private static final double CARD_VSPACE = 30;

	/** 表格卡片尺寸（列多，需要更高） */
	private static final double TABLE_CARD_WIDTH = 280;
	private static final double TABLE_CARD_ROW_HEIGHT = KanbanCard.ROW_HEIGHT;
	/**
	 * 卡片高度 = header + 行数×rowHeight + 上下边距
	 * （2026-08-20：去掉 400 上限的同时，把 BASE 改成由 KanbanCard 常量派生，
	 * 与 drawTableCard 实际渲染公式保持完全一致，消除底部 ~22px 多余空白）
	 */
	private static final double TABLE_CARD_BASE_HEIGHT =
			KanbanCard.HEADER_HEIGHT + KanbanCard.PADDING; // 28 + 10 = 38

	/** 背景色（适配深色 / 浅色主题） */
	private final Color backgroundColor = new JBColor(Gray._240, new Color(61, 63, 65));

	/** 网格颜色（适配深色 / 浅色主题） */
	private final Color gridColor = new JBColor(Gray._200, Gray._100);

	private final Project project;

	/** 拖拽目标处理器（接收 Database 表拖放） */
	private TableDropHandler dropHandler;

	/** 看板内容变更监听器 */
	private Runnable changeListener;

	/**
	 * 视图变化监听器：zoom / pan / reset / focusView 之后触发，
	 * 用于通知上层更新 zoom 百分比显示等。
	 */
	private Runnable viewChangeListener;

	/** 保存动作（Command+S / Ctrl+S 触发） */
	private Runnable saveAction;

	public KanbanBoard(Project project) {
		this.project = project;
		setBackground(backgroundColor);
		setFocusable(true);
		initComponents();
		initDropTarget();
	}

	/**
	 * 注册拖拽目标：接收来自 Database 工具窗口的表格拖放。
	 */
	private void initDropTarget() {
		dropHandler = new TableDropHandler(project, (info, dropPoint) -> {
			addTableCard(info, dropPoint);
			notifyBoardChanged();
		});
		dropHandler.registerTo(this);
	}

	/**
	 * 释放资源：注销 DnD 拖拽目标 + 停止所有卡片动画定时器，
	 * 避免编辑器关闭后仍持有对已释放组件的引用。
	 *
	 * <p>由上层（{@link DataChartView#dispose()}）在编辑器销毁时调用。</p>
	 */
	public void dispose() {
		if (dropHandler != null) {
			try {
				dropHandler.unregisterFrom(this);
			} catch (Exception ignore) {
				// 注销失败不影响整体释放
			}
			dropHandler = null;
		}
		// 2026-08-27：停止所有卡片动画 Timer，防止 Timer 持有卡片引用泄漏
		for (KanbanCard card : cards) {
			card.disposeTimers();
		}
		cards.clear();
		connections.clear();
		linkedRowsCache = null;
		selectedCards.clear();
		selectedCard = null;
		activeHighlightCard = null;
	}

	/**
	 * 设置看板内容变更监听器（新增/删除/移动卡片时触发，用于标记文件已修改）。
	 */
	public void setChangeListener(Runnable listener) {
		this.changeListener = listener;
	}

	/**
	 * 注册视图变化监听器：zoom / pan / reset / focusView 之后触发。
	 */
	public void setViewChangeListener(Runnable listener) {
		this.viewChangeListener = listener;
	}

	/**
	 * 通知视图变化。
	 */
	private void notifyViewChanged() {
		if (viewChangeListener != null) {
			viewChangeListener.run();
		}
	}

	/**
	 * 通知上层看板内容已变更。
	 */
	void notifyBoardChanged() {
		if (changeListener != null) {
			changeListener.run();
		}
	}

	/**
	 * 初始化事件监听。
	 */
	private void initComponents() {
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		MouseAdapter mouseHandler = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				lastPoint = e.getPoint();
				requestFocusInWindow();

				// 检查是否命中连线
				Connection hitConn = hitTestConnection(e.getPoint());
				if (hitConn != null) {
					selectedConnection = hitConn;
					if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
						showConnectionContextMenu(hitConn, e.getPoint());
					} else {
						repaint();
					}
					return;
				}
				// 点击其他区域：取消选中连线
				if (selectedConnection != null) {
					selectedConnection = null;
					repaint();
				}

				KanbanCard card = findCardAt(e.getPoint());
				if (card != null) {
					Point2D transformedPoint = viewport.transformPoint(e.getPoint());
					int rowIndex = card.getRowIndexAt(
							transformedPoint.getX(), transformedPoint.getY());
					if (rowIndex == -1) {
						// header 区域：右键统一弹"复制表名/复制注释"菜单
						if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
							showHeaderContextMenu(card, e.getPoint());
						}
						// header 区域允许拖拽整张卡片
						draggedCard = card;
						selectOnly(card);
						dragOffset.setLocation(
								transformedPoint.getX() - card.getBounds().getX(),
								transformedPoint.getY() - card.getBounds().getY());
						setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
						repaint();
						return;
					}
					if (rowIndex >= 0) {
						// 点击了某一行
						if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
							// 右键：左半弹列名/注释菜单，右半走连线模式
							double colNameRightX = card.getColumnNameRightX(rowIndex);
							boolean isLeftHalf = colNameRightX > 0
									&& transformedPoint.getX() <= colNameRightX;
							if (isLeftHalf) {
								showColumnContextMenu(card, rowIndex, e.getPoint());
							} else {
								connectionSource = card;
								connectionSourceRow = rowIndex;
								connectionCurrentPoint = transformedPoint;
								isConnecting = true;
								lastHoverTargetCard = null;
								lastHoverTargetRow = -1;
								setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
								// 2026-08-04：起手连线时清除源行用户选中态，
								// 避免建线后源行保留橙色高亮与连线"占用色"语义冲突
								clearUserRowSelection(card, rowIndex);
								repaint();
							}
						} else {
							// 左键点击列行（2026-08-04）：
							// 先进入"待连线"状态，等鼠标移动超过阈值才升级为连线模式；
							// 如果松手前没移动过阈值，mouseReleased 会按"普通点击"触发列高亮。
							// 这样既支持 Win 系统下"左键起手拖拽自动连线"，
							// 又保留"快速左键点击列 = 选中高亮"的原有交互。
							pendingConnectionSource = card;
							pendingConnectionSourceRow = rowIndex;
							pendingConnectionPressPoint = e.getPoint();
							lastHoverTargetCard = null;
							lastHoverTargetRow = -1;
						}
						return;
					}
					// 否则：拖拽卡片
					draggedCard = card;
					selectOnly(card);
					dragOffset.setLocation(
							transformedPoint.getX() - card.getBounds().getX(),
							transformedPoint.getY() - card.getBounds().getY());
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
					repaint();
				} else {
					// 点击空白区域：开始框选（2026-08-20，替代原"平移画板"；
					// 画板平移保留滚轮 pan。拖动超过阈值才画选区，单击空白则清空选中）
					isSelecting = true;
					selectionStartBoard = viewport.transformPoint(e.getPoint());
					selectionRect = new java.awt.geom.Rectangle2D.Double(
							selectionStartBoard.getX(), selectionStartBoard.getY(), 0, 0);
					clearActiveHighlight();
					setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
					repaint();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger()) {
					return;
				}
				boolean wasDraggingCard = draggedCard != null;
				isDraggingBoard = false;
				draggedCard = null;
				activeSnapGuideV = null;
				activeSnapGuideH = null;

				// 框选结束（2026-08-20）：几乎没拖动（单击空白）视为取消选择
				if (isSelecting) {
					isSelecting = false;
					boolean tinyDrag = selectionRect == null
							|| (selectionRect.getWidth() < SELECTION_DRAG_THRESHOLD
									&& selectionRect.getHeight() < SELECTION_DRAG_THRESHOLD);
					if (tinyDrag) {
						selectedCards.clear();
						selectedCard = null;
					}
					selectionStartBoard = null;
					selectionRect = null;
					repaint();
				}

				// 待连线但未升级（2026-08-04）：鼠标没移动过阈值就松手，
				// 按"普通左键点击列行"处理 → 触发列高亮，保持原有交互。
				if (pendingConnectionSource != null) {
					KanbanCard pendingCard = pendingConnectionSource;
					int pendingRow = pendingConnectionSourceRow;
					pendingConnectionSource = null;
					pendingConnectionSourceRow = -2;
					pendingConnectionPressPoint = null;
					toggleRowSelection(pendingCard, pendingRow);
				}

				// 连线模式释放：尝试建立连接
				if (isConnecting) {
					// 2026-08-04：优先用拖拽过程中最后一次 hover 的目标建线，
					// 避免松手点飘到空白处时连线丢失（用户反馈"鼠标移到 leader 上没点击，
					// 点一下别处连接就没了"）。
					KanbanCard targetCard = lastHoverTargetCard;
					int targetRow = lastHoverTargetRow;
					if (targetCard == null || targetRow < 0
							|| targetCard == connectionSource) {
						// 没有 hover 过有效目标，回退到松手点位置判断（兼容快速点击场景）
						Point2D transformedPoint = viewport.transformPoint(e.getPoint());
						targetCard = findCardAt(e.getPoint());
						if (targetCard != null && targetCard != connectionSource) {
							targetRow = targetCard.getRowIndexAt(
									transformedPoint.getX(), transformedPoint.getY());
						}
					}
					if (targetCard != null && targetCard != connectionSource
							&& targetRow >= 0) {
						// 建线后用 palette 分配新颜色，让多条连线视觉可区分
						addConnection(connectionSource, connectionSourceRow,
								targetCard, targetRow);
					}
					previewHighlightRows.clear();
					connectionSource = null;
					connectionSourceRow = -2;
					connectionCurrentPoint = null;
					lastHoverTargetCard = null;
					lastHoverTargetRow = -1;
					isConnecting = false;
				}

				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				repaint();
				if (wasDraggingCard) {
					notifyBoardChanged();
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (isConnecting) {
					// 连线模式：更新预览线终点，并高亮源/目标行
					connectionCurrentPoint = viewport.transformPoint(e.getPoint());
					KanbanCard targetCard = findCardAt(e.getPoint());
					previewHighlightRows.clear();
					if (connectionSource != null) {
						previewHighlightRows
								.computeIfAbsent(connectionSource, k -> new HashMap<>())
								.put(connectionSourceRow, CONNECTION_PREVIEW_COLOR);
					}
					if (targetCard != null && targetCard != connectionSource) {
						Point2D tp = viewport.transformPoint(e.getPoint());
						int targetRow = targetCard.getRowIndexAt(tp.getX(), tp.getY());
						if (targetRow >= 0) {
							previewHighlightRows
									.computeIfAbsent(targetCard, k -> new HashMap<>())
									.put(targetRow, CONNECTION_PREVIEW_COLOR);
							// 2026-08-04：记录最后一次 hover 的目标，
							// 让 mouseReleased 即使松手在空白处也能建线
							lastHoverTargetCard = targetCard;
							lastHoverTargetRow = targetRow;
						}
					}
					repaint();
				} else if (pendingConnectionSource != null) {
					// 待连线状态（2026-08-04）：鼠标移动距离超过阈值才升级为真正连线
					if (pendingConnectionPressPoint != null) {
						double dx = e.getX() - pendingConnectionPressPoint.getX();
						double dy = e.getY() - pendingConnectionPressPoint.getY();
						if (dx * dx + dy * dy >= CONNECTION_DRAG_THRESHOLD
								* (double) CONNECTION_DRAG_THRESHOLD) {
							KanbanCard upgradeSource = pendingConnectionSource;
							int upgradeRow = pendingConnectionSourceRow;
							connectionSource = upgradeSource;
							connectionSourceRow = upgradeRow;
							connectionCurrentPoint = viewport.transformPoint(e.getPoint());
							isConnecting = true;
							lastHoverTargetCard = null;
							lastHoverTargetRow = -1;
							setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
							pendingConnectionSource = null;
							pendingConnectionSourceRow = -2;
							pendingConnectionPressPoint = null;
							// 2026-08-04：建线后源行不应保留用户选中的橙色高亮，
							// 否则会和连线的"占用色"语义冲突（图1 → 图2 现象）。
							clearUserRowSelection(upgradeSource, upgradeRow);
							repaint();
						}
					}
				} else if (draggedCard != null) {
					// 拖拽卡片（含磁吸 + 对齐辅助线）
					try {
						Point2D transformedPoint = viewport.transformPoint(e.getPoint());
						double newX = transformedPoint.getX() - dragOffset.getX();
						double newY = transformedPoint.getY() - dragOffset.getY();
						Rectangle2D bounds = draggedCard.getBounds();
						bounds.setRect(newX, newY, bounds.getWidth(), bounds.getHeight());

						// 计算对齐辅助线 + 磁吸
						Rectangle view = getBounds();
						BoardSnapHelper.SnapResult snap = BoardSnapHelper.compute(
								draggedCard, cards,
								viewport.getZoomFactor(),
								viewport.getTransform().getTranslateX(),
								viewport.getTransform().getTranslateY(),
								view.width, view.height);
						applySnapResult(snap);

						repaint();
					} catch (Exception ex) {
						LOG.warn("磁吸吸附计算失败", ex);
					}
				} else if (isSelecting) {
					// 框选（2026-08-20）：更新选区矩形（画板坐标），实时选中相交的卡片
					Point2D curBoard = viewport.transformPoint(e.getPoint());
					if (selectionStartBoard != null) {
						double minX = Math.min(selectionStartBoard.getX(), curBoard.getX());
						double minY = Math.min(selectionStartBoard.getY(), curBoard.getY());
						double w = Math.abs(curBoard.getX() - selectionStartBoard.getX());
						double h = Math.abs(curBoard.getY() - selectionStartBoard.getY());
						selectionRect.setRect(minX, minY, w, h);
						updateSelectionFromRect();
					}
					repaint();
				} else if (isDraggingBoard) {
					// 平移画板（2026-08-20 起空白拖拽被框选占用，此分支保留兼容，不再触发）
					double dx = e.getX() - lastPoint.getX();
					double dy = e.getY() - lastPoint.getY();
					viewport.pan(dx, dy);
					lastPoint = e.getPoint();
					notifyViewChanged();
					repaint();
				}
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				double wheelRotation = e.getPreciseWheelRotation();
				Point2D p = e.getPoint();
				boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");

				if (isMac) {
					if (e.isControlDown() || e.isMetaDown() || e.isAltDown()) {
						viewport.zoom(p, Math.pow(1.1, -wheelRotation));
					} else {
						viewport.panByWheel(e, wheelRotation);
					}
				} else {
					if (e.isControlDown()) {
						viewport.zoom(p, wheelRotation < 0 ? 1.1 : 0.9);
					} else {
						viewport.panByWheel(e, wheelRotation);
					}
				}
				notifyViewChanged();
				repaint();
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				if (isConnecting) {
					return;
				}
				KanbanCard card = findCardAt(e.getPoint());
				setCursor(card != null
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
						: Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

				// 只更新 tooltip（不改变高亮）
				if (card != null) {
					Point2D transformedPoint = viewport.transformPoint(e.getPoint());
					int rowIndex = card.getRowIndexAt(
							transformedPoint.getX(), transformedPoint.getY());
					if (rowIndex >= 0) {
						com.wd.db.TableInfo ti = card.getTableInfo();
						if (ti != null && rowIndex < ti.getColumns().size()) {
							com.wd.db.ColumnInfo col = ti.getColumns().get(rowIndex);
							String tip = buildColumnTooltip(col);
							KanbanBoard.this.setToolTipText(tip);
						}
					} else if (rowIndex == -1) {
						com.wd.db.TableInfo ti = card.getTableInfo();
						if (ti != null) {
							String tip = "表: " + ti.getName() +
									(ti.getComment() == null || ti.getComment().isEmpty()
											? "" : "\n注释: " + ti.getComment());
							KanbanBoard.this.setToolTipText(tip);
						}
					} else {
						KanbanBoard.this.setToolTipText(null);
					}
				} else {
					KanbanBoard.this.setToolTipText(null);
				}
			}

			/**
			 * 切换行选中状态（左键单击行时调用）。
			 */
			private void toggleRowSelection(KanbanCard card, int rowIndex) {
				java.util.Set<Integer> highlighted = card.getHighlightedRows();
				if (highlighted.contains(rowIndex)) {
					// 已是用户选中 → 取消
					highlighted.remove(rowIndex);
					if (activeHighlightCard == card && activeHighlightRow == rowIndex) {
						clearActiveHighlight();
					}
					setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				} else {
					// 切换：清除所有卡的用户选中，再设当前
					for (KanbanCard c : cards) {
						c.getHighlightedRows().clear();
					}
					highlighted.add(rowIndex);
					setActiveHighlight(card, rowIndex);
					setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				}
				repaint();
			}
		};

		addMouseListener(mouseHandler);
		addMouseMotionListener(mouseHandler);
		addMouseWheelListener(mouseHandler);

		// 键盘监听：空格复位视图，Command+Del(Mac)/Ctrl+Backspace(其他) 删除选中表，Command+S/Ctrl+S 保存
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_SPACE) {
					resetView();
					return;
				}

				int keyCode = e.getKeyCode();
				boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");

				// Command+S (Mac) / Ctrl+S (其他系统) 保存
				if (keyCode == KeyEvent.VK_S && (e.isMetaDown() || e.isControlDown())) {
					if (saveAction != null) {
						saveAction.run();
					}
					return;
				}

				// 删除选中卡片：Command+Del (Mac) / Ctrl+Backspace (其他系统)
				boolean isDelete = (isMac && (e.isMetaDown() || e.isControlDown())
						&& (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE))
						|| (!isMac && e.isControlDown() && keyCode == KeyEvent.VK_BACK_SPACE);

				if (isDelete) {
					deleteSelectedCard();
				}
			}
		});
	}

	/**
	 * 注册保存动作（Command+S / Ctrl+S 触发）。
	 */
	public void registerSaveAction(Runnable action) {
		this.saveAction = action;
	}

	/**
	 * 把磁吸计算结果应用到当前帧的辅助线。
	 */
	private void applySnapResult(BoardSnapHelper.SnapResult snap) {
		if (snap == null) {
			activeSnapGuideV = null;
			activeSnapGuideH = null;
			return;
		}
		activeSnapGuideV = snap.hasVertical()
				? new Line2D.Double(snap.guideVX, snap.guideVFromY, snap.guideVX, snap.guideVToY)
				: null;
		activeSnapGuideH = snap.hasHorizontal()
				? new Line2D.Double(snap.guideHFromX, snap.guideHY, snap.guideHToX, snap.guideHY)
				: null;
	}

	// ====================== 搜索（委托 BoardSearchModel） ======================

	/**
	 * 执行搜索（遍历所有卡片，匹配 表名/表注释/列名/列注释，不区分大小写）。
	 *
	 * @return 搜索结果数量
	 */
	public int search(String keyword) {
		int count = searchModel.search(cards, keyword);
		applySearchFocus();
		repaint();
		return count;
	}

	/**
	 * 清空搜索状态。
	 */
	public void clearSearch() {
		searchModel.clearSearchState(cards);
		repaint();
	}

	public int getSearchResultCount() {
		return searchModel.getResultCount();
	}

	public int getSearchFocusIndex() {
		return searchModel.getFocusIndex();
	}

	/**
	 * 切换到下一个搜索结果（下箭头）。
	 */
	public void focusNextSearchResult() {
		if (searchModel.focusNext() == null) {
			return;
		}
		applySearchFocus();
		scrollToFocusResult();
		repaint();
	}

	/**
	 * 切换到上一个搜索结果（上箭头）。
	 */
	public void focusPrevSearchResult() {
		if (searchModel.focusPrev() == null) {
			return;
		}
		applySearchFocus();
		scrollToFocusResult();
		repaint();
	}

	/**
	 * 把当前焦点结果写入对应卡片。
	 */
	private void applySearchFocus() {
		searchModel.applyFocus(cards, this::findCardById);
	}

	/**
	 * 平移画板让当前焦点结果进入视口。
	 */
	private void scrollToFocusResult() {
		Rectangle view = getVisibleRect();
		searchModel.scrollToFocus(cards, this::findCardById, viewport,
				(int) view.getWidth(), (int) view.getHeight());
		notifyViewChanged();
	}

	// ====================== 卡片管理 ======================

	/**
	 * 单选一张卡片：清空多选集合，只保留该卡（2026-08-20 框选引入后统一入口）。
	 */
	private void selectOnly(KanbanCard card) {
		if (card == null) {
			return;
		}
		selectedCards.clear();
		selectedCards.add(card);
		selectedCard = card;
	}

	/**
	 * 清空当前选中（卡片多选 + 主选中卡）。
	 */
	private void clearSelectedCards() {
		selectedCards.clear();
		selectedCard = null;
	}

	/**
	 * 根据当前框选矩形更新多选集合（2026-08-20）。
	 *
	 * <p>判定规则：卡片 bounds 与选区相交即选中（部分重叠也算）。</p>
	 */
	private void updateSelectionFromRect() {
		if (selectionRect == null) {
			return;
		}
		selectedCards.clear();
		KanbanCard topCard = null;
		for (KanbanCard c : cards) {
			if (selectionRect.intersects(c.getBounds())) {
				selectedCards.add(c);
				topCard = c; // 遍历到最后的即 Z 序最上层
			}
		}
		selectedCard = topCard;
	}

	/**
	 * 删除所有选中的卡片（Command+Del）。
	 *
	 * <p>2026-08-20 支持多选：先统一检查是否含连线并弹一次确认，再逐个删除。</p>
	 */
	private void deleteSelectedCard() {
		java.util.Set<KanbanCard> toDelete = new java.util.LinkedHashSet<>(selectedCards);
		if (selectedCard != null) {
			toDelete.add(selectedCard);
		}
		if (toDelete.isEmpty()) {
			return;
		}

		boolean hasRelations = false;
		for (KanbanCard c : toDelete) {
			if (hasRelationsFor(c.getId())) {
				hasRelations = true;
				break;
			}
		}
		if (hasRelations) {
			boolean confirmed = NotificationUtil.confirmYesNo(project,
					"删除数据库表",
					"选中的 " + toDelete.size() + " 张表中存在与其他表的连线，删除后连线关系将一并移除。\n\n确定要删除吗？",
					com.intellij.openapi.ui.Messages.getYesButton(),
					com.intellij.openapi.ui.Messages.getNoButton());
			if (!confirmed) {
				return;
			}
		}

		for (KanbanCard c : toDelete) {
			deleteCard(c, false); // 外层已统一确认连线，不再逐卡弹窗
		}
		clearSelectedCards();
		repaint();
	}

	/**
	 * 删除指定卡片（删除前检查是否有连线，如有则弹二次确认）。
	 *
	 * <p>效果等同 Command+Del：有连线先提示，无连线直接删除。</p>
	 */
	private void deleteCard(KanbanCard card) {
		deleteCard(card, true);
	}

	/**
	 * 删除指定卡片。
	 *
	 * @param confirmRelations 是否弹连线二次确认；多选删除时外层已统一确认过，传 false 避免重复弹窗
	 */
	private void deleteCard(KanbanCard card, boolean confirmRelations) {
		if (card == null) {
			return;
		}

		if (confirmRelations && hasRelationsFor(card.getId())) {
			boolean confirmed = NotificationUtil.confirmYesNo(project,
					"删除数据库表",
					"表 \"" + card.getName() + "\" 存在与其他表的连线，删除后连线关系将一并移除。\n\n确定要删除吗？",
					com.intellij.openapi.ui.Messages.getYesButton(),
					com.intellij.openapi.ui.Messages.getNoButton());
			if (!confirmed) {
				return;
			}
		}

		cards.remove(card);
		connections.removeIf(conn -> conn.getSource() == card || conn.getTarget() == card);
		linkedRowsCache = null; // 删表可能移除连线，失效占用行缓存
		if (selectedCard == card) {
			selectedCard = null;
		}
		selectedCards.remove(card);
		if (activeHighlightCard == card) {
			clearActiveHighlight();
		}
		// 同步清理搜索结果中被删除卡片的项
		searchModel.removeResultsForCard(card.getId());
		applySearchFocus();
		repaint();
		notifyBoardChanged();
	}

	/**
	 * 检查某张表是否存在连线关系。
	 */
	private boolean hasRelationsFor(String cardId) {
		for (Connection conn : connections) {
			if (cardId.equals(conn.getSource().getId())
					|| cardId.equals(conn.getTarget().getId())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 查找指定屏幕坐标下的卡片。
	 */
	private KanbanCard findCardAt(Point2D point) {
		try {
			Point2D transformedPoint = viewport.transformPoint(point);
			// 反向遍历（Z序：后添加的在上层）
			for (int i = cards.size() - 1; i >= 0; i--) {
				KanbanCard card = cards.get(i);
				if (card.getBounds().contains(transformedPoint)) {
					return card;
				}
			}
		} catch (Exception e) {
			LOG.warn("findCardAt 坐标转换异常", e);
		}
		return null;
	}

	/**
	 * 添加卡片（自动平铺，避免重叠）。
	 */
	public void addCard(KanbanCard card) {
		if (card == null) {
			return;
		}
		if (!cards.isEmpty()) {
			KanbanCard last = cards.get(cards.size() - 1);
			Rectangle2D lastBounds = last.getBounds();
			double nextX = lastBounds.getX() + lastBounds.getWidth() + CARD_HSPACE;
			double nextY = lastBounds.getY();
			// 2026-08-01 修复：保留 card 原宽度 / 高度，不强制用 DEFAULT_CARD_WIDTH/HEIGHT
			// 否则 table card（280 宽）会被 addCard 覆盖为 200，导致 calculateTotalBounds 算小
			double cardW = card.getBounds().getWidth() > 0
					? card.getBounds().getWidth() : DEFAULT_CARD_WIDTH;
			double cardH = card.getBounds().getHeight() > 0
					? card.getBounds().getHeight() : DEFAULT_CARD_HEIGHT;
			if (nextX + cardW > 4 * DEFAULT_CARD_WIDTH) {
				nextX = 0;
				nextY = lastBounds.getY() + cardH + CARD_VSPACE;
			}
			card.setBounds(new Rectangle2D.Double(
					nextX, nextY, cardW, cardH));
		} else {
			// 2026-08-01 修复：首张 card 起点 (0, 0) 而非 (50, 50)
			// 旧 (50, 50) 导致 exportArea.x = 30，画板 0~50 范围被画到设备负坐标被 clip，
			// 用户感觉"导出图片左边留白太多"——其实不是 exportArea 算错，是 cards 起点固定 50 太靠右
			double cardW = card.getBounds().getWidth() > 0
					? card.getBounds().getWidth() : DEFAULT_CARD_WIDTH;
			double cardH = card.getBounds().getHeight() > 0
					? card.getBounds().getHeight() : DEFAULT_CARD_HEIGHT;
			card.setBounds(new Rectangle2D.Double(0, 0, cardW, cardH));
		}
		cards.add(card);
		repaint();
	}

	/**
	 * 删除卡片。
	 */
	public boolean removeCard(KanbanCard card) {
		boolean result = cards.remove(card);
		if (result) {
			if (selectedCard == card) {
				selectedCard = null;
			}
			selectedCards.remove(card);
			repaint();
		}
		return result;
	}

	/**
	 * 清空所有卡片。
	 */
	public void clearCards() {
		cards.clear();
		selectedCard = null;
		selectedCards.clear();
		repaint();
	}

	/**
	 * 复位视图（位置 + 缩放 = 回到 100% 且居中无偏移）。
	 */
	public void resetView() {
		viewport.reset();
		notifyViewChanged();
		repaint();
	}

	/**
	 * 以指定点为中心缩放。
	 */
	public void zoom(Point2D p, double scaleFactor) {
		viewport.zoom(p, scaleFactor);
		notifyViewChanged();
		repaint();
	}

	/**
	 * 把缩放倍率直接设为 1:1（保持视口中心锚定，缩放后不变）。
	 */
	public void setZoomTo1() {
		Rectangle view = getVisibleRect();
		double cx = view.getX() + view.getWidth() / 2.0;
		double cy = view.getY() + view.getHeight() / 2.0;
		if (viewport.setZoomFactor(cx, cy)) {
			notifyViewChanged();
			repaint();
		}
	}

	/**
	 * 把所有卡片整体对齐到视口（保留当前缩放倍率）。
	 * <p>自适应策略：能完整展示则上下左右居中；展示不完则左对齐 + 上下居中，最左卡片完整露出。</p>
	 */
	public void focusView() {
		Rectangle view = getVisibleRect();
		// 2026-08-07 修复：刚加载文件时面板可能还没布局完成，viewWidth/viewHeight=0
		// 此时 focusOn 会把内容当作"放得下"按情况 A 居中，但 viewCenter=(0,0) → 视口被推到
		// 负方向，画面跑到顶/左外。viewWidth==0 直接跳过，等组件首次布局完成后再 focus。
		if (view.getWidth() <= 0 || view.getHeight() <= 0) {
			return;
		}
		if (viewport.focusOn(cards, (int) view.getWidth(), (int) view.getHeight())) {
			notifyViewChanged();
			repaint();
		}
	}

	/**
	 * 2026-08-07 新增：缩放到能完整展示所有卡片并居中（Fit to Window）。
	 * <p>与 {@link #focusView()} 的区别：focusView 保留缩放只移动位置，
	 * 本方法会重新计算缩放让全部卡片落入视口后再居中。</p>
	 */
	public void fitView() {
		Rectangle view = getVisibleRect();
		if (view.getWidth() <= 0 || view.getHeight() <= 0) {
			return;
		}
		if (viewport.fit(cards, (int) view.getWidth(), (int) view.getHeight())) {
			notifyViewChanged();
			repaint();
		}
	}

	public double getZoomFactor() {
		return viewport.getZoomFactor();
	}

	// ====================== 导出 ======================

	/**
	 * 计算所有卡片（含阴影）的完整包围盒，用于导出范围。
	 *
	 * <p>委托给 {@link BoardExportUtil#calculateTotalBounds(List)}。</p>
	 */
	public Rectangle2D calculateTotalBounds() {
		return BoardExportUtil.calculateTotalBounds(cards);
	}

	/**
	 * 导出用绘制方法：把画板内容绘制到传入的 Graphics2D，不包含屏幕坐标的对齐辅助线 / tooltip。
	 *
	 * <p>包级可见，由 {@link BoardExportUtil} 调用；依赖本类内部状态，故保留在此。</p>
	 */
	void paintForExport(Graphics2D g2d, Rectangle2D exportArea, boolean dark) {
		paintForExport(g2d, exportArea, dark, 1.0,
				(int) exportArea.getWidth(), (int) exportArea.getHeight());
	}

	/**
	 * 导出用绘制方法（支持高 DPI 缩放）。
	 */
	void paintForExport(Graphics2D g2d, Rectangle2D exportArea, boolean dark, double scale,
			int deviceW, int deviceH) {
		if (scale <= 0) {
			scale = 1.0;
		}
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
				RenderingHints.VALUE_FRACTIONALMETRICS_ON);

		double minX = exportArea.getX();
		double minY = exportArea.getY();
		int width = (int) exportArea.getWidth();
		int height = (int) exportArea.getHeight();

		// 1. 绘制背景（设备坐标 (0,0,deviceW,deviceH) 完整覆盖，避免黑色透出）
		g2d.setColor(KanbanCard.getCardBackgroundColor(dark));
		g2d.fillRect(0, 0, deviceW, deviceH);
		g2d.setBackground(KanbanCard.getCardBackgroundColor(dark));
		g2d.clearRect(0, 0, deviceW, deviceH);

		// 2. 应用 transform：先 translate 使 exportArea 起点 (minX, minY) 到 (0, 0)，再 scale
		g2d.translate(-minX, -minY);
		if (scale != 1.0) {
			g2d.scale(scale, scale);
		}

		// 3. 绘制网格（导出时只在 exportArea 范围内画）
		if (showGrid) {
			drawGrid(g2d, exportArea);
		}

		// 4. 绘制连线
		for (Connection conn : connections) {
			conn.draw(g2d);
		}

		// 5. 绘制所有卡片（导出场景传 null 不裁剪，需要全量）
		drawCards(g2d, dark, null);
	}

	/**
	 * 绘制所有卡片（含连线占用行高亮、关联列高亮、预览高亮）。
	 *
	 * <p>2026-08-27 优化：支持可见区域裁剪（画板坐标），与视口不相交的卡片
	 * 跳过绘制，避免几十张高列卡片在每次 repaint 时全量 drawString 卡帧。
	 * 传 {@code null} 表示不裁剪（导出场景需画全量）。</p>
	 */
	private void drawCards(Graphics2D g2d, boolean dark, Rectangle2D visibleArea) {
		// 2026-08-03 优化：连线占用行缓存只在连线增删时失效，避免每次重绘全量重算
		if (linkedRowsCache == null) {
			linkedRowsCache = computeLinkedRows();
		}
		refreshRelatedRows();
		for (KanbanCard c : cards) {
			c.clearActiveRowColors();
		}
		if (activeHighlightCard != null && activeHighlightRow >= 0) {
			activeHighlightCard.setActiveRowColor(activeHighlightRow, RELATED_ROW_COLOR);
		}
		for (String key : relatedRowKeys) {
			RelatedRowPos rk = parseRelatedKeyById(key);
			if (rk != null) {
				rk.card.setActiveRowColor(rk.row, RELATED_ROW_COLOR);
			}
		}
		for (KanbanCard card : cards) {
			boolean isSelected = (card == selectedCard || selectedCards.contains(card));
			card.setSelected(isSelected);
			// 可见区域裁剪：选中状态仍更新（保持状态一致），但跳过绘制
			if (visibleArea != null && !visibleArea.intersects(card.getBounds())) {
				continue;
			}
			Map<Integer, Color> merged = new HashMap<>(
					linkedRowsCache.getOrDefault(card, Collections.emptyMap()));
			for (String key : relatedRowKeys) {
				RelatedRowPos rk = parseRelatedKeyById(key);
				if (rk != null && rk.card == card) {
					merged.put(rk.row, RELATED_ROW_COLOR);
				}
			}
			Map<Integer, Color> preview = previewHighlightRows.get(card);
			if (preview != null) {
				merged.putAll(preview);
			}
			card.draw(g2d, dark, merged);
		}
	}

	/**
	 * 计算当前视口在画板坐标系下的可见矩形（供绘制裁剪用）。
	 *
	 * <p>JPanel 的 {@link #getBounds()} 是屏幕坐标，经 viewport 逆变换得到画板坐标范围；
	 * 面板未布局（宽高<=0）时返回 null 表示不裁剪。</p>
	 */
	private Rectangle2D computeVisibleBoardArea() {
		// 注意：必须用 (0,0,w,h) 局部坐标而非 getBounds()（后者是父容器坐标，
		// 若面板在容器中非 (0,0) 起始会导致裁剪区域偏移、误剪掉可见卡片）
		int w = getWidth();
		int h = getHeight();
		if (w <= 0 || h <= 0) {
			return null;
		}
		Rectangle view = new Rectangle(0, 0, w, h);
		try {
			AffineTransform inverse = viewport.getInverse();
			Point2D p1 = new Point2D.Double(view.x, view.y);
			Point2D p2 = new Point2D.Double(view.x + view.width, view.y + view.height);
			inverse.transform(p1, p1);
			inverse.transform(p2, p2);
			double minX = Math.min(p1.getX(), p2.getX());
			double maxX = Math.max(p1.getX(), p2.getX());
			double minY = Math.min(p1.getY(), p2.getY());
			double maxY = Math.max(p1.getY(), p2.getY());
			return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
		} catch (Exception e) {
			return null; // 逆变换异常时退回不裁剪
		}
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
	 * 是否深色主题。
	 */
	private boolean isDarkTheme() {
		return BoardExportUtil.isDarkTheme(getBackground());
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g.create();

		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		// 应用变换
		g2d.transform(viewport.getTransform());

		// 1. 绘制网格
		if (showGrid) {
			drawGrid(g2d);
		}

		// 2. 绘制所有连线（在卡片下方）
		for (Connection conn : connections) {
			if (conn == selectedConnection) {
				float oldWidth = conn.getStrokeWidth();
				conn.setStrokeWidth(2.5f);
				conn.draw(g2d);
				conn.setStrokeWidth(oldWidth);
			} else {
				conn.draw(g2d);
			}
		}

		// 3. 绘制所有卡片（2026-08-27：按可见区域裁剪，跳过视口外卡片）
		drawCards(g2d, isDarkTheme(), computeVisibleBoardArea());

		// 3.5 绘制框选矩形（2026-08-20：空白拖拽选区，画板坐标，画在卡片上方）
		if (isSelecting && selectionRect != null) {
			// 注意：new Color(0x4A90E2) 是 RGB 解析（alpha=255），配合 composite 产生半透明填充
			g2d.setColor(new Color(0x4A90E2));
			g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
			g2d.fill(selectionRect);
			g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
			g2d.setColor(new Color(0x4A90E2));
			g2d.setStroke(new BasicStroke(1f));
			g2d.draw(selectionRect);
		}

		// 4. 绘制连线预览（鼠标跟随）
		if (isConnecting && connectionSource != null && connectionCurrentPoint != null) {
			Point2D sourcePoint = connectionSource.getRowRight(connectionSourceRow);
			if (sourcePoint != null) {
				g2d.setColor(CONNECTION_PREVIEW_COLOR);
				g2d.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				double dx = Math.abs(connectionCurrentPoint.getX() - sourcePoint.getX());
				double ctrlX1 = sourcePoint.getX() + dx / 2.0;
				double ctrlY1 = sourcePoint.getY();
				double ctrlX2 = connectionCurrentPoint.getX() - dx / 2.0;
				double ctrlY2 = connectionCurrentPoint.getY();
				g2d.draw(new CubicCurve2D.Double(
						sourcePoint.getX(), sourcePoint.getY(),
						ctrlX1, ctrlY1,
						ctrlX2, ctrlY2,
						connectionCurrentPoint.getX(), connectionCurrentPoint.getY()));
			}
		}

		g2d.dispose();

		// 5. 绘制对齐辅助线（在屏幕坐标下，覆盖在最上层）
		drawSnapGuides(g);
	}

	/**
	 * 绘制网格。
	 *
	 * <p>2026-08-01 优化：导出时传入 {@code exportArea} 限定网格范围，
	 * 避免画到 JPanel 整个屏幕（2000x1500）导致 exportArea 范围外也画网格。</p>
	 *
	 * @param g2d         目标 Graphics2D（已应用 translate/scale 变换）
	 * @param rangeOverride 网格范围（画板坐标，null 则用 JPanel 屏幕范围）
	 */
	private void drawGrid(Graphics2D g2d, Rectangle2D rangeOverride) {
		g2d.setColor(gridColor);
		g2d.setStroke(new BasicStroke(0.5f));

		Rectangle bounds = getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
			return;
		}

		try {
			double minX;
			double maxX;
			double minY;
			double maxY;
			if (rangeOverride != null) {
				// 导出模式：用 exportArea 范围（已含 padding）
				minX = rangeOverride.getX();
				maxX = rangeOverride.getX() + rangeOverride.getWidth();
				minY = rangeOverride.getY();
				maxY = rangeOverride.getY() + rangeOverride.getHeight();
			} else {
				// 屏幕模式：用 JPanel 屏幕范围，逆变换到画板坐标
				AffineTransform inverse = viewport.getInverse();
				Point2D p1 = new Point2D.Double();
				Point2D p2 = new Point2D.Double(bounds.width, bounds.height);
				inverse.transform(p1, p1);
				inverse.transform(p2, p2);
				minX = Math.min(p1.getX(), p2.getX());
				maxX = Math.max(p1.getX(), p2.getX());
				minY = Math.min(p1.getY(), p2.getY());
				maxY = Math.max(p1.getY(), p2.getY());
			}

			int startX = (int) (Math.floor(minX / gridSize) * gridSize);
			int startY = (int) (Math.floor(minY / gridSize) * gridSize);
			int endX = (int) (Math.ceil(maxX / gridSize) * gridSize);
			int endY = (int) (Math.ceil(maxY / gridSize) * gridSize);

			// 缩放过小时不绘制网格（性能考虑）
			if (viewport.getZoomFactor() < 0.3) {
				return;
			}

			for (int x = startX; x <= endX; x += gridSize) {
				g2d.drawLine(x, startY, x, endY);
			}
			for (int y = startY; y <= endY; y += gridSize) {
				g2d.drawLine(startX, y, endX, y);
			}
		} catch (Exception e) {
			LOG.warn("drawGrid 绘制网格异常", e);
		}
	}

	/**
	 * 绘制网格（无范围覆盖，用 JPanel 屏幕范围）。
	 *
	 * <p>保持原签名（paintComponent 调用）。</p>
	 */
	private void drawGrid(Graphics2D g2d) {
		drawGrid(g2d, null);
	}

	/**
	 * 添加一个数据库表卡片（自动布局）。
	 */
	public void addTableCard(TableInfo info) {
		addTableCard(info, null);
	}

	/**
	 * 添加一个数据库表卡片，可指定位置（拖放点）。
	 */
	public void addTableCard(TableInfo info, Point dropPoint) {
		if (info == null) {
			LOG.warn("[看板] addTableCard 收到 null TableInfo，忽略");
			return;
		}
		int rowCount = Math.max(3, info.getColumns().size());
		double height = TABLE_CARD_BASE_HEIGHT + rowCount * TABLE_CARD_ROW_HEIGHT;
		// 2026-08-20 高度完全由列数决定，不设上下限（80 列的卡会很高，属预期）

		// 2026-08-01 改回固定宽度：所有表格卡片统一宽度，注释过长按宽度截断 + 省略号
		// （之前 21 节按需加宽会让不同表宽度不一致，且注释过长也不会触发 truncateByWidth）
		double cardWidth = TABLE_CARD_WIDTH;

		double x;
		double y;
		if (dropPoint != null) {
			// 2026-08-20 修复：DnDEvent.getPointOn(null) 给的是 IDE 屏幕绝对坐标，
			// 与 mouseDragged 的 MouseEvent.getPoint()（JPanel 局部坐标）坐标系不一致。
			// 这里把屏幕坐标转成 KanbanBoard 自身局部坐标后，再走 viewport.transformPoint，
			// 与绘制链路 (paintComponent 用 viewport.transform 正向变换) 完全一致。
			java.awt.Point screenPoint = dropPoint;
			java.awt.Point localPoint;
			try {
				java.awt.Point panelLocationOnScreen = getLocationOnScreen();
				localPoint = new java.awt.Point(
						screenPoint.x - panelLocationOnScreen.x,
						screenPoint.y - panelLocationOnScreen.y);
			} catch (Exception ex) {
				// 极端情况（panel 还未显示）按原值兜底
				localPoint = screenPoint;
			}
			Point2D boardPoint = viewport.transformPoint(localPoint);
			// 拖放契约：鼠标位置 = 卡片左上角（符合 draw.io / Freeform 等画板习惯）
			x = boardPoint.getX();
			y = boardPoint.getY();
		} else {
			// 2026-08-01 修复"导出图片左边留白太多"：
			// 旧逻辑调 addCard(card) 会把 table card 的位置/尺寸覆盖为 (50, 50, 200, 130)
			// 导致 calculateTotalBounds 算的 minX=50、宽度偏小，exportArea 偏左
			// 这里改为 table card 走自己的布局：保持 addTableCard 设定的 (0, 0, 280, h)，
			// 后续 addTableCard 通过 cards 列表自动平铺
			//
			// 平铺规则：找已存在 cards 中"最右边的卡片"——同一行卡片
			// 如果这一行还不到 4 张：放在最右卡片右边 + spacing
			// 如果这一行已有 4 张：换行，新行起点 (0, 上一行底部 + spacing)
			x = 0;
			y = 0;
			if (cards.isEmpty()) {
				// 首张卡片在 (0, 0)
			} else {
				// 1. 找最右卡片（按 x + width 最大者）
				KanbanCard rightmost = cards.get(0);
				for (KanbanCard c : cards) {
					double curRight = rightmost.getBounds().getX()
							+ rightmost.getBounds().getWidth();
					double testRight = c.getBounds().getX()
							+ c.getBounds().getWidth();
					if (testRight > curRight) {
						rightmost = c;
					}
				}
				// 2. 同一行卡片数 = 与 rightmost 同 y 的卡片
				int countInSameRow = 0;
				for (KanbanCard c : cards) {
					if (Math.abs(c.getBounds().getY()
							- rightmost.getBounds().getY()) < 5) {
						countInSameRow++;
					}
				}
				int maxPerRow = 4;
				if (countInSameRow < maxPerRow) {
					// 同一行还没满，放在 rightmost 右边
					x = rightmost.getBounds().getX()
							+ rightmost.getBounds().getWidth() + CARD_HSPACE;
					y = rightmost.getBounds().getY();
				} else {
					// 同一行满了，换行：找该行最底 y
					double rowBottomY = 0;
					for (KanbanCard c : cards) {
						if (Math.abs(c.getBounds().getY()
								- rightmost.getBounds().getY()) < 5) {
							double cb = c.getBounds().getY()
									+ c.getBounds().getHeight();
							if (cb > rowBottomY) {
								rowBottomY = cb;
							}
						}
					}
					x = 0;
					y = rowBottomY + CARD_VSPACE;
				}
			}
		}

		LOG.info("[看板] 开始绘制表卡片: 表=" + info.getName()
				+ ", 数据源=" + info.getDatasourceName()
				+ ", schema=" + info.getSchema()
				+ ", 字段数=" + info.getColumns().size()
				+ ", 拖放屏幕点=" + (dropPoint == null ? "null" : dropPoint.x + "," + dropPoint.y)
				+ ", 卡片画板坐标=(" + (int) x + "," + (int) y + ")"
				+ ", 卡片尺寸=" + (int) cardWidth + "x" + (int) height
				+ " (fixedWidth)"
				+ ", 缩放=" + viewport.getZoomFactor());

		// 2026-08-07 修复：拖入重复表时（如两张 sys_user），TableInfo 默认 id 是
		// schema.table 拼接（"public.sys_user"），两张卡片 id 完全相同，重新打开后
		// 按 id 找卡只能找到第一张，所有指向该 id 的连线都打到第一张上 → 线乱了
		// 这里用 UUID 覆盖 id 保证画板上的每张卡 id 全局唯一
		info.setId(java.util.UUID.randomUUID().toString());

		KanbanCard card = KanbanCard.forTable(info.getId(), info,
				x, y, cardWidth, height);
		cards.add(card);
		LOG.info("[看板] 卡片已添加，当前卡片总数=" + cards.size() + "，请求重绘");
		repaint();
	}

	/**
	 * 获取 Project。
	 */
	public Project getProject() {
		return project;
	}

	// ====================== 连线管理 ======================

	/**
	 * 添加连线（自动分配颜色）。
	 */
	public Connection addConnection(KanbanCard source, int sourceRow,
			KanbanCard target, int targetRow) {
		return addConnection(source, sourceRow, target, targetRow, RelationType.ONE_TO_ONE);
	}

	/**
	 * 添加连线（指定关系类型）。
	 */
	public Connection addConnection(KanbanCard source, int sourceRow,
			KanbanCard target, int targetRow, RelationType relationType) {
		if (source == null || target == null
				|| source == target || sourceRow < 0 || targetRow < 0) {
			return null;
		}
		for (Connection conn : connections) {
			if (conn.getSource() == source && conn.getSourceRow() == sourceRow
					&& conn.getTarget() == target && conn.getTargetRow() == targetRow) {
				return null;
			}
		}
		Color color = CONNECTION_COLOR_PALETTE[connectionColorIndex
				% CONNECTION_COLOR_PALETTE.length];
		connectionColorIndex++;
		Connection conn = new Connection(source, sourceRow, target, targetRow, color, relationType);
		connections.add(conn);
		linkedRowsCache = null; // 连线结构变化，失效占用行缓存
		repaint();
		notifyBoardChanged();
		return conn;
	}

	/**
	 * 计算每张卡的连线占用行（每次重绘前调用）。
	 */
	private Map<KanbanCard, Map<Integer, Color>> computeLinkedRows() {
		Map<KanbanCard, Map<Integer, Color>> result = new HashMap<>();
		for (Connection conn : connections) {
			Color c = conn.getColor();
			result.computeIfAbsent(conn.getSource(), k -> new HashMap<>())
					.putIfAbsent(conn.getSourceRow(), c);
			result.computeIfAbsent(conn.getTarget(), k -> new HashMap<>())
					.putIfAbsent(conn.getTargetRow(), c);
		}
		return result;
	}

	/**
	 * 删除连线。
	 */
	public void removeConnection(Connection conn) {
		if (conn == null) {
			return;
		}
		connections.remove(conn);
		if (selectedConnection == conn) {
			selectedConnection = null;
		}
		linkedRowsCache = null; // 连线结构变化，失效占用行缓存
		repaint();
		notifyBoardChanged();
	}

	/**
	 * 检测鼠标位置是否命中某条连线（简单矩形近似）。
	 */
	private Connection hitTestConnection(java.awt.Point screenPoint) {
		Point2D boardPoint = viewport.transformPoint(screenPoint);
		for (int i = connections.size() - 1; i >= 0; i--) {
			Connection conn = connections.get(i);
			Point2D from = conn.getSource().getRowRight(conn.getSourceRow());
			Point2D to = conn.getTarget().getRowLeft(conn.getTargetRow());
			if (from == null || to == null) {
				continue;
			}
			double minX = Math.min(from.getX(), to.getX());
			double maxX = Math.max(from.getX(), to.getX());
			double minY = Math.min(from.getY(), to.getY());
			double maxY = Math.max(from.getY(), to.getY());
			if (boardPoint.getX() >= minX - 4 && boardPoint.getX() <= maxX + 4
					&& boardPoint.getY() >= minY - 8 && boardPoint.getY() <= maxY + 8) {
				return conn;
			}
		}
		return null;
	}

	// ====================== 右键菜单（委托 BoardContextMenu） ======================

	/**
	 * 在指定屏幕坐标处弹出连线右键菜单（删除 / 关系类型）。
	 */
	private void showConnectionContextMenu(Connection conn, java.awt.Point screenPoint) {
		if (conn == null) {
			return;
		}
		selectedConnection = conn;
		repaint();
		javax.swing.JPopupMenu menu = BoardContextMenu.buildConnectionMenu(conn,
				this::repaint, this::notifyBoardChanged, () -> removeConnection(conn));
		if (menu != null) {
			menu.show(this, screenPoint.x, screenPoint.y);
		}
	}

	/**
	 * 表头右键菜单（复制表名 / 复制注释 / 同步表结构 / 删除表）。
	 */
	private void showHeaderContextMenu(KanbanCard card, java.awt.Point screenPoint) {
		if (card == null) {
			return;
		}
		javax.swing.JPopupMenu menu = BoardContextMenu.buildHeaderMenu(
				card.getTableInfo(),
				() -> syncTableStructure(card),
				() -> deleteCard(card));
		if (menu != null) {
			menu.show(this, screenPoint.x, screenPoint.y);
		}
	}

	/**
	 * 同步指定卡片的表结构：重新获取元信息，更新卡片并重绘。
	 *
	 * <p>从 {@link com.wd.db.TableMetadataService} 拿到 fetcher，
	 * 调用 {@code fetchTableInfo(project, datasource, tableName)} 拉取最新表结构，
	 * 成功后通过 {@link KanbanCard#setTableInfo} 替换卡片绑定。</p>
	 *
	 * <p>2026-08-27 优化：查库（反射 + 可能的远程数据库 IO）移到后台线程，
	 * 避免慢数据源冻结 EDT；回 EDT 后校验 project 存活、卡片是否仍在画板。</p>
	 *
	 * <p>失败时弹错误提示（不动卡片）。</p>
	 */
	private void syncTableStructure(KanbanCard card) {
		if (card == null || card.getTableInfo() == null || project == null) {
			return;
		}
		TableInfo old = card.getTableInfo();
		String dsName = old.getDatasourceName();
		String tableName = old.getName();
		com.wd.db.TableMetadataService svc =
				com.wd.db.TableMetadataService.getInstance(project);

		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			final TableInfo fresh;
			try {
				fresh = ReadAction.compute(() ->
						svc.getFetcher().fetchTableInfo(project, dsName, tableName));
			} catch (Exception e) {
				LOG.warn("后台同步表结构查询失败: " + tableName, e);
				return;
			}
			javax.swing.SwingUtilities.invokeLater(() -> {
				if (project.isDisposed()) {
					return;
				}
				applySyncedStructure(card, old, dsName, tableName, fresh);
			});
		});
	}

	/**
	 * 在 EDT 上应用同步结果（syncTableStructure 的后续处理）。
	 *
	 * <p>查询期间用户可能删除/关闭了卡片，回到 EDT 后先校验卡片仍在画板。</p>
	 */
	private void applySyncedStructure(KanbanCard card, TableInfo old,
									  String dsName, String tableName, TableInfo fresh) {
		if (fresh == null) {
			NotificationUtil.error("同步失败",
					"无法获取表结构：" + tableName + "（数据源：" + dsName + "）");
			return;
		}
		// 查询期间卡片可能已被删除，此时丢弃同步结果
		if (!cards.contains(card)) {
			return;
		}

		// 2026-08-07 修复：替换表结构前先记录涉及该卡片的连线的列名，
		// 替换后用列名重新定位连线行 index（Connection.sourceRow/targetRow 存的是列 index，
		// 同步后列顺序可能变化，直接沿用 index 会导致连线指向错误列）
		java.util.Map<Connection, Integer> sourceRowByColName = new java.util.HashMap<>();
		java.util.Map<Connection, Integer> targetRowByColName = new java.util.HashMap<>();
		for (Connection conn : connections) {
			if (conn.getSource() == card) {
				sourceRowByColName.put(conn, conn.getSourceRow());
			}
			if (conn.getTarget() == card) {
				targetRowByColName.put(conn, conn.getTargetRow());
			}
		}

		card.setTableInfoWithDiff(fresh, this::repaint);

		// 用旧列名重新定位连线行（只对仍存在的列重定位；被删除的列则丢弃该连线）
		java.util.Map<Integer, String> oldRowToName = oldRowToColumnName(old);
		java.util.List<Connection> toRemove = new java.util.ArrayList<>();
		for (java.util.Map.Entry<Connection, Integer> e : sourceRowByColName.entrySet()) {
			Connection conn = e.getKey();
			String colName = oldRowToName.get(e.getValue());
			if (colName == null) {
				toRemove.add(conn);
				continue;
			}
			int newRow = findColumnIndex(fresh, colName);
			if (newRow < 0) {
				toRemove.add(conn); // 该列已被删除，连线无法保留
			} else {
				conn.setSourceRow(newRow);
			}
		}
		for (java.util.Map.Entry<Connection, Integer> e : targetRowByColName.entrySet()) {
			Connection conn = e.getKey();
			String colName = oldRowToName.get(e.getValue());
			if (colName == null) {
				toRemove.add(conn);
				continue;
			}
			int newRow = findColumnIndex(fresh, colName);
			if (newRow < 0) {
				toRemove.add(conn); // 该列已被删除，连线无法保留
			} else {
				conn.setTargetRow(newRow);
			}
		}
		for (Connection conn : toRemove) {
			connections.remove(conn);
			if (selectedConnection == conn) {
				selectedConnection = null;
			}
		}
		if (!toRemove.isEmpty()) {
			linkedRowsCache = null; // 连线结构变化，失效占用行缓存
		}

		notifyBoardChanged();
		repaint();
		NotificationUtil.info("同步成功",
				"已重新获取 " + tableName + "（" + fresh.getColumns().size() + " 列）"
						+ (toRemove.isEmpty() ? "" : "，已移除 " + toRemove.size() + " 条失效连线"));
	}

	/**
	 * 把卡片旧表结构的「行 index → 列名」映射出来（同步表结构后重定位连线用）。
	 */
	private static java.util.Map<Integer, String> oldRowToColumnName(TableInfo info) {
		java.util.Map<Integer, String> map = new java.util.HashMap<>();
		if (info == null || info.getColumns() == null) {
			return map;
		}
		List<com.wd.db.ColumnInfo> cols = info.getColumns();
		for (int i = 0; i < cols.size(); i++) {
			String name = cols.get(i).getName();
			if (name != null) {
				map.put(i, name);
			}
		}
		return map;
	}

	/**
	 * 在表结构中按列名查找列 index；找不到返回 -1。
	 */
	private static int findColumnIndex(TableInfo info, String colName) {
		if (info == null || colName == null || info.getColumns() == null) {
			return -1;
		}
		List<com.wd.db.ColumnInfo> cols = info.getColumns();
		for (int i = 0; i < cols.size(); i++) {
			if (colName.equals(cols.get(i).getName())) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 列行右键菜单（复制列名 / 复制注释）。
	 */
	private void showColumnContextMenu(KanbanCard card, int rowIndex, java.awt.Point screenPoint) {
		if (card == null || card.getTableInfo() == null
				|| rowIndex < 0 || rowIndex >= card.getTableInfo().getColumns().size()) {
			return;
		}
		javax.swing.JPopupMenu menu = BoardContextMenu.buildColumnMenu(
				card.getTableInfo().getColumns().get(rowIndex));
		if (menu != null) {
			menu.show(this, screenPoint.x, screenPoint.y);
		}
	}

	public Connection getSelectedConnection() {
		return selectedConnection;
	}

	/**
	 * 构建字段的 tooltip（仅显示注释）。
	 */
	private String buildColumnTooltip(com.wd.db.ColumnInfo col) {
		String comment = col.getComment();
		if (comment == null || comment.isEmpty()) {
			return null;
		}
		return "<html>" + comment + "</html>";
	}

	public List<Connection> getConnections() {
		return connections;
	}

	// ====================== 持久化（委托 BoardPersistence） ======================

	/**
	 * 将看板状态序列化为图数据模型（用于保存到 .datachart）。
	 */
	public ChartData toChartData() {
		return BoardPersistence.toChartData(cards, connections);
	}

	/**
	 * 从图数据模型恢复看板状态（打开 .datachart 文件时调用）。
	 */
	public void loadFromChartData(ChartData data) {
		if (data == null) {
			return;
		}
		cards.clear();

		// 2026-08-01 改回保留位置策略：
		// 25 节强制归一化位置到 (0, 0)+4 张/行平铺 → 破坏了用户拖动过的位置（用户反馈"位置变了"）
		// 现在改为：保留 model.getX()/getY() 位置，**只修正尺寸**：
		//   1. table card 宽度统一为 TABLE_CARD_WIDTH（280）
		//   2. 高度按字段数计算（不再用 saved height）
		// 这样 calculateTotalBounds 用真实 bounds 算，导出图正确；同时保留用户布局意图
		for (ChartData.TableCardModel model : data.getTables()) {
			TableInfo info = BoardPersistence.resolveTableInfo(model, project);
			// 高度 = base + 行数 * 行高（2026-08-20 不设上下限，列全部展示）
			int rowCount = Math.max(3, info.getColumns().size());
			double height = TABLE_CARD_BASE_HEIGHT + rowCount * TABLE_CARD_ROW_HEIGHT;

			// 2026-08-07 兼容旧 .datachart 文件：旧文件里 id 是 schema.table 拼接，
			// 同一张表拖入两次时 id 完全相同，加载后连线全部指向 cards 列表里第一张
			// 这里在 cards 列表里查重，发现 id 已存在就给当前 model 补一个 UUID，
			// 保证画板上的每张卡 id 全局唯一，连线 id 指向精确
			String modelId = model.getId();
			if (modelId == null || isCardIdExists(modelId)) {
				String newId = java.util.UUID.randomUUID().toString();
				LOG.info("[看板] 加载时检测到重复/缺失 id (" + modelId
						+ ")，给表 " + info.getName() + " 补 UUID=" + newId);
				info.setId(newId);
				model.setId(newId);
			}

			// 保留 model 的 x, y（用户拖动过的位置）
			KanbanCard card = KanbanCard.forTable(info.getId(), info,
					model.getX(), model.getY(), TABLE_CARD_WIDTH, height);
			if (model.getHighlightedRows() != null) {
				for (Integer row : model.getHighlightedRows()) {
					if (row != null) {
						card.addHighlightedRow(row);
					}
				}
			}
			cards.add(card);
		}

		// 恢复连线
		connections.clear();
		linkedRowsCache = null;
		connectionColorIndex = 0;
		BoardPersistence.loadFromChartData(data,
				this::findCardById, this::addConnection);

		selectedCard = null;
		selectedCards.clear();
		repaint();
	}

	/**
	 * 根据卡片 ID 查找卡片。
	 */
	private KanbanCard findCardById(String id) {
		if (id == null) {
			return null;
		}
		for (KanbanCard c : cards) {
			if (id.equals(c.getId())) {
				return c;
			}
		}
		return null;
	}

	/**
	 * 检查 cards 列表中是否已存在指定 id（2026-08-07 兼容旧 .datachart 重复 id 用）。
	 */
	private boolean isCardIdExists(String id) {
		if (id == null) {
			return false;
		}
		for (KanbanCard c : cards) {
			if (id.equals(c.getId())) {
				return true;
			}
		}
		return false;
	}

	// ====================== 关联列高亮（需求 2） ======================

	/**
	 * 关联列的解析结果：哪个 card 的哪一行需要高亮。
	 */
	private static class RelatedRowPos {
		final KanbanCard card;
		final int row;

		RelatedRowPos(KanbanCard card, int row) {
			this.card = card;
			this.row = row;
		}
	}

	/**
	 * 拼装关联列 key（用 cardId 而非 card 引用，避免删除卡片时 key 失效）。
	 */
	private static String makeRelatedKey(KanbanCard card, int row) {
		return card.getId() + "#" + row;
	}

	/**
	 * 解析关联列 key（按 cardId 找到 card）。
	 */
	private RelatedRowPos parseRelatedKeyById(String key) {
		if (key == null) {
			return null;
		}
		int hash = key.lastIndexOf('#');
		if (hash < 0) {
			return null;
		}
		String cardId = key.substring(0, hash);
		int row;
		try {
			row = Integer.parseInt(key.substring(hash + 1));
		} catch (NumberFormatException e) {
			return null;
		}
		KanbanCard card = findCardById(cardId);
		if (card == null) {
			return null;
		}
		return new RelatedRowPos(card, row);
	}

	/**
	 * 设置激活高亮（用户左键选中的列）。
	 */
	private void setActiveHighlight(KanbanCard card, int row) {
		this.activeHighlightCard = card;
		this.activeHighlightRow = row;
		refreshRelatedRows();
	}

	/**
	 * 清空激活高亮（点击空白区域、取消选中、删除连线等）。
	 */
	private void clearActiveHighlight() {
		this.activeHighlightCard = null;
		this.activeHighlightRow = -1;
		relatedRowKeys.clear();
	}

	/**
	 * 清除指定卡片的指定行用户选中态（2026-08-04 连线起手时调用）：
	 * <ul>
	 *   <li>从卡片的 {@code getHighlightedRows()} 集合移除</li>
	 *   <li>若该卡该行是当前激活高亮（{@code activeHighlightCard/Row}），同步调 {@link #clearActiveHighlight()}</li>
	 * </ul>
	 * 不调用 {@link #repaint()}，由调用方按需统一刷新；不切换光标，连线模式下由调用方维持 CROSSHAIR。
	 */
	private void clearUserRowSelection(KanbanCard card, int rowIndex) {
		if (card == null || rowIndex < 0) {
			return;
		}
		java.util.Set<Integer> highlighted = card.getHighlightedRows();
		if (highlighted.remove(rowIndex)
				&& activeHighlightCard == card && activeHighlightRow == rowIndex) {
			clearActiveHighlight();
		}
	}

	/**
	 * 重新计算关联列集合（2026-08-07 改为 BFS 沿连线图遍历）：
	 * <p>从选中的 (activeHighlightCard, activeHighlightRow) 出发，
	 * 沿 {@link #connections} 双向遍历所有可达的 (card, row) 端点，
	 * 加入 {@link #relatedRowKeys}。渲染时这些行统一用 {@link #RELATED_ROW_COLOR}
	 * 高亮，覆盖默认的"连线占用色"（紫色）。</p>
	 *
	 * <p>连通图遍历的语义：选一行 → 它所在连通子图里所有"被连线涉及的行"都变橙。
	 * 不属于该连通子图的孤立连线行不受影响。</p>
	 */
	private void refreshRelatedRows() {
		relatedRowKeys.clear();
		if (activeHighlightCard == null || activeHighlightRow < 0) {
			return;
		}
		// 构造 (cardId, row) → 邻接端点列表
		java.util.Map<String, java.util.List<RelatedRowPos>> adjacency = new java.util.HashMap<>();
		for (Connection conn : connections) {
			String srcKey = makeRelatedKey(conn.getSource(), conn.getSourceRow());
			String tgtKey = makeRelatedKey(conn.getTarget(), conn.getTargetRow());
			adjacency.computeIfAbsent(srcKey, k -> new java.util.ArrayList<>())
					.add(new RelatedRowPos(conn.getTarget(), conn.getTargetRow()));
			adjacency.computeIfAbsent(tgtKey, k -> new java.util.ArrayList<>())
					.add(new RelatedRowPos(conn.getSource(), conn.getSourceRow()));
		}

		// BFS：从选中端点出发，遍历连通子图
		java.util.Deque<RelatedRowPos> queue = new java.util.ArrayDeque<>();
		java.util.Set<String> visited = new java.util.HashSet<>();
		String startKey = makeRelatedKey(activeHighlightCard, activeHighlightRow);
		queue.add(new RelatedRowPos(activeHighlightCard, activeHighlightRow));
		visited.add(startKey);
		relatedRowKeys.add(startKey);

		while (!queue.isEmpty()) {
			RelatedRowPos cur = queue.poll();
			String curKey = makeRelatedKey(cur.card, cur.row);
			java.util.List<RelatedRowPos> neighbours = adjacency.get(curKey);
			if (neighbours == null) {
				continue;
			}
			for (RelatedRowPos nb : neighbours) {
				String nbKey = makeRelatedKey(nb.card, nb.row);
				if (visited.add(nbKey)) {
					relatedRowKeys.add(nbKey);
					queue.add(nb);
				}
			}
		}
	}

	// ====================== 对齐辅助线绘制 ======================

	/**
	 * 绘制对齐辅助线（在屏幕坐标系下，覆盖在最上层）。
	 */
	private void drawSnapGuides(Graphics g) {
		if (activeSnapGuideV == null && activeSnapGuideH == null) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		boolean dark = isDarkTheme();
		Color c = dark ? ALIGN_GUIDE_COLOR_DARK : ALIGN_GUIDE_COLOR_LIGHT;
		g2.setColor(c);
		g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
				10f, new float[]{4f, 4f}, 0f));
		if (activeSnapGuideV != null) {
			g2.draw(activeSnapGuideV);
		}
		if (activeSnapGuideH != null) {
			g2.draw(activeSnapGuideH);
		}
		g2.dispose();
	}
}
