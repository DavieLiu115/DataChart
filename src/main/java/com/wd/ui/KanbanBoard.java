package com.wd.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.wd.db.ColumnInfo;
import com.wd.db.TableDropHandler;
import com.wd.db.TableInfo;
import com.wd.db.TableMetadataService;
import com.wd.model.ChartData;
import com.wd.model.ChartRelation;
import com.wd.model.RelationType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
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

	/** 连线列表 */
	private final List<Connection> connections = new ArrayList<>();

	/** 连线占用行缓存（每次重绘前重新计算，不依赖元素状态） */
	private final Map<KanbanCard, Map<Integer, Color>> linkedRowsCache = new HashMap<>();

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

	/** 是否显示网格 */
	private boolean showGrid = true;

	/** 网格大小 */
	private int gridSize = 20;

	/** 卡片默认尺寸 */
	private static final double DEFAULT_CARD_WIDTH = 200;

	/** 连线颜色集合（浅色不饱和，每条连线用一种） */
	private static final java.awt.Color[] CONNECTION_COLOR_PALETTE = {
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

	/** 连线模式临时高亮颜色（半透明粉色，标识当前正在连的行） */
	private static final Color CONNECTION_PREVIEW_COLOR = new Color(0xFFB6E1);

	/** 对齐辅助线颜色（深色主题下稍亮，浅色主题下稍深） */
	private static final Color ALIGN_GUIDE_COLOR_LIGHT = new Color(0xFE9933);
	private static final Color ALIGN_GUIDE_COLOR_DARK = new Color(0xFFB266);

	/** 磁吸阈值（画板坐标像素），水平/垂直方向独立判断 */
	private static final double SNAP_THRESHOLD = 8.0;

	/** 对齐辅助线触发阈值（比磁吸稍大，便于提前显示） */
	private static final double ALIGN_THRESHOLD = 10.0;
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

	/** 看板内容变更监听器 */
	private Runnable changeListener;

	/** 保存动作（Command+S / Ctrl+S 触发） */
	private Runnable saveAction;

	/**
	 * 搜索结果列表：按"卡片 + 行"为单位的有序列表
	 *
	 * <p>每次执行 {@link #search(String)} 后重建；上下键导航时通过 {@link #focusSearchResultAt(int)}
	 * 滚动到指定结果。</p>
	 */
	private final List<SearchResult> searchResults = new ArrayList<>();

	/** 当前"焦点"搜索结果在 {@link #searchResults} 中的下标，-1 表示无焦点 */
	private int searchFocusIndex = -1;

	/**
	 * 搜索结果单元：表示 (cardId, rowIndex) 形式的命中位置
	 */
	public static class SearchResult {
		private final String cardId;
		private final int rowIndex;

		public SearchResult(String cardId, int rowIndex) {
			this.cardId = cardId;
			this.rowIndex = rowIndex;
		}

		public String getCardId() {
			return cardId;
		}

		public int getRowIndex() {
			return rowIndex;
		}
	}

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
			notifyBoardChanged();
		});
		dropHandler.registerTo(this);
	}

	/**
	 * 设置看板内容变更监听器（新增/删除/移动卡片时触发，用于标记文件已修改）
	 *
	 * @param listener 变更回调
	 */
	public void setChangeListener(Runnable listener) {
		this.changeListener = listener;
	}

	/**
	 * 通知上层看板内容已变更
	 */
	private void notifyBoardChanged() {
		if (changeListener != null) {
			changeListener.run();
		}
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

				// 检查是否命中连线
				Connection hitConn = hitTestConnection(e.getPoint());
				if (hitConn != null) {
					selectedConnection = hitConn;
					if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
						// 右键：弹出删除菜单
						showConnectionContextMenu(hitConn, e.getPoint());
					} else {
						// 左键：选中（重绘加粗）
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
					Point2D transformedPoint = transformPoint(e.getPoint());
					int rowIndex = card.getRowIndexAt(
							transformedPoint.getX(), transformedPoint.getY());
					if (rowIndex == -1) {
						// header 区域：右键统一弹"复制表名/复制注释"菜单
						if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
							showHeaderContextMenu(card, e.getPoint());
						}
						// header 区域允许拖拽整张卡片
						draggedCard = card;
						selectedCard = card;
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
							// 右键点击：判断是左半（列名/类型）还是右半（注释）
							double colNameRightX = card.getColumnNameRightX(rowIndex);
							boolean isLeftHalf = colNameRightX > 0
									&& transformedPoint.getX() <= colNameRightX;
							if (isLeftHalf) {
								// 左半：弹列名/注释菜单
								showColumnContextMenu(card, rowIndex, e.getPoint());
							} else {
								// 右半：走连线模式（从行右拖出连线）
								connectionSource = card;
								connectionSourceRow = rowIndex;
								connectionCurrentPoint = transformedPoint;
								isConnecting = true;
								setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
								repaint();
							}
						} else {
							// 左键点击：切换普通选中（橙色 #FE9933），并触发关联列高亮
							toggleRowSelection(card, rowIndex);
						}
						return;
					}
					// 否则：拖拽卡片
					draggedCard = card;
					selectedCard = card;
					dragOffset.setLocation(
							transformedPoint.getX() - card.getBounds().getX(),
							transformedPoint.getY() - card.getBounds().getY());
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
					repaint();
				} else {
					// 点击空白区域：开始拖拽画板，同时清空列选中态
					isDraggingBoard = true;
					selectedCard = null;
					clearActiveHighlight();
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
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
				// 清除对齐辅助线
				activeSnapGuideV = null;
				activeSnapGuideH = null;

				// 连线模式释放：尝试建立连接
				if (isConnecting) {
					Point2D transformedPoint = transformPoint(e.getPoint());
					KanbanCard targetCard = findCardAt(e.getPoint());
					if (targetCard != null && targetCard != connectionSource) {
						int targetRow = targetCard.getRowIndexAt(
								transformedPoint.getX(), transformedPoint.getY());
						if (targetRow >= 0) {
							addConnection(connectionSource, connectionSourceRow,
									targetCard, targetRow);
						}
					}
					// 退出连线模式：清空预览高亮，保留用户选中
					previewHighlightRows.clear();
					connectionSource = null;
					connectionSourceRow = -2;
					connectionCurrentPoint = null;
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
					connectionCurrentPoint = transformPoint(e.getPoint());
					KanbanCard targetCard = findCardAt(e.getPoint());
					previewHighlightRows.clear();
					// 高亮源行
					if (connectionSource != null) {
						previewHighlightRows
								.computeIfAbsent(connectionSource, k -> new HashMap<>())
								.put(connectionSourceRow, CONNECTION_PREVIEW_COLOR);
					}
					// 高亮目标行
					if (targetCard != null && targetCard != connectionSource) {
						Point2D tp = transformPoint(e.getPoint());
						int targetRow = targetCard.getRowIndexAt(tp.getX(), tp.getY());
						if (targetRow >= 0) {
							previewHighlightRows
									.computeIfAbsent(targetCard, k -> new HashMap<>())
									.put(targetRow, CONNECTION_PREVIEW_COLOR);
						}
					}
					repaint();
				} else if (draggedCard != null) {
					// 拖拽卡片（含磁吸 + 对齐辅助线）
					try {
						Point2D transformedPoint = transformPoint(e.getPoint());
						double newX = transformedPoint.getX() - dragOffset.getX();
						double newY = transformedPoint.getY() - dragOffset.getY();
						Rectangle2D bounds = draggedCard.getBounds();
						bounds.setRect(newX, newY, bounds.getWidth(), bounds.getHeight());

						// 计算对齐辅助线 + 磁吸
						applySnapAndGuides(draggedCard);

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
				if (isConnecting) {
					return;
				}
				KanbanCard card = findCardAt(e.getPoint());
				setCursor(card != null
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
						: Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

				// 只更新 tooltip（不改变高亮），高亮由点击/连线模式控制
				if (card != null) {
					Point2D transformedPoint = transformPoint(e.getPoint());
					int rowIndex = card.getRowIndexAt(
							transformedPoint.getX(), transformedPoint.getY());
					if (rowIndex >= 0) {
						// 字段 tooltip（仅注释）
						com.wd.db.TableInfo ti = card.getTableInfo();
						if (ti != null && rowIndex < ti.getColumns().size()) {
							com.wd.db.ColumnInfo col = ti.getColumns().get(rowIndex);
							String tip = buildColumnTooltip(col);
							KanbanBoard.this.setToolTipText(tip);
						}
					} else if (rowIndex == -1) {
						// header 区域：tooltip 显示表注释
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

			// 给 mouseDragged 等已存在的 handler 添加 tooltip 清理逻辑
			private void clearTooltipAndHighlight() {
				KanbanBoard.this.setToolTipText(null);
			}

			/**
			 * 切换行选中状态（左键单击行时调用）
			 *
			 * <ul>
			 *   <li>未选中 → 选中（橙色 #FE9933），同时清除其他表的用户选中；
			 *       并将本列设为激活高亮，所有与之有连线的列也高亮（橙色 #FD9933）</li>
			 *   <li>已选中 → 取消选中，并清空关联列高亮</li>
			 *   <li>被连线占用的行（不影响，computeLinkedRows 会自动管理）</li>
			 * </ul>
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
					// 设置激活高亮：触发关联列计算
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
	 * 注册保存动作（Command+S / Ctrl+S 触发）
	 */
	public void registerSaveAction(Runnable action) {
		this.saveAction = action;
	}

	/**
	 * 执行搜索：遍历所有卡片，对每张卡片的表名/列名/列注释做不区分大小写的子串匹配
	 *
	 * <p>匹配命中后写入 {@link #searchResults} 列表，同时把所有命中行加入
	 * 对应卡片的 {@code searchMatchedRows} 集合中。第一个命中作为当前焦点。</p>
	 *
	 * @param keyword 搜索关键词（null 或空字符串表示清空搜索）
	 * @return 搜索结果数量
	 */
	public int search(String keyword) {
		clearSearch();

		if (keyword == null || keyword.trim().isEmpty()) {
			repaint();
			return 0;
		}

		String lower = keyword.trim().toLowerCase();
		for (KanbanCard card : cards) {
			// 1. 表名命中（表名行号约定为 -1，与 drawTableCard 的 header 区域一致）
			String tableName = card.getName() == null ? "" : card.getName();
			String tableComment = card.getDescription() == null ? "" : card.getDescription();
			if (tableName.toLowerCase().contains(lower)
					|| tableComment.toLowerCase().contains(lower)) {
				// 表名命中：只记录一个虚拟结果，rowIndex = -1（表示表头）
				searchResults.add(new SearchResult(card.getId(), -1));
			}

			// 2. 列名 / 列注释命中
			if (card.isTableMode() && card.getTableInfo() != null) {
				List<ColumnInfo> columns = card.getTableInfo().getColumns();
				for (int i = 0; i < columns.size(); i++) {
					ColumnInfo col = columns.get(i);
					String colName = col.getName() == null ? "" : col.getName();
					String colCmt = col.getComment() == null ? "" : col.getComment();
					if (colName.toLowerCase().contains(lower)
							|| colCmt.toLowerCase().contains(lower)) {
						searchResults.add(new SearchResult(card.getId(), i));
						card.getSearchMatchedRows().add(i);
					}
				}
			}
		}

		// 默认聚焦第一个结果（如果存在）
		if (!searchResults.isEmpty()) {
			searchFocusIndex = 0;
			applySearchFocus();
		}

		repaint();
		return searchResults.size();
	}

	/**
	 * 清空搜索状态（命中行 + 焦点行 + 结果列表）
	 */
	public void clearSearch() {
		searchResults.clear();
		searchFocusIndex = -1;
		for (KanbanCard card : cards) {
			card.clearSearchMatchedRows();
		}
		repaint();
	}

	/**
	 * 获取当前搜索结果数量
	 */
	public int getSearchResultCount() {
		return searchResults.size();
	}

	/**
	 * 获取当前搜索焦点下标（-1 表示无焦点）
	 */
	public int getSearchFocusIndex() {
		return searchFocusIndex;
	}

	/**
	 * 切换到下一个搜索结果（下箭头）
	 */
	public void focusNextSearchResult() {
		if (searchResults.isEmpty()) {
			return;
		}
		searchFocusIndex = (searchFocusIndex + 1) % searchResults.size();
		applySearchFocus();
		scrollToFocusResult();
		repaint();
	}

	/**
	 * 切换到上一个搜索结果（上箭头）
	 */
	public void focusPrevSearchResult() {
		if (searchResults.isEmpty()) {
			return;
		}
		searchFocusIndex = (searchFocusIndex - 1 + searchResults.size()) % searchResults.size();
		applySearchFocus();
		scrollToFocusResult();
		repaint();
	}

	/**
	 * 把当前焦点结果写入对应卡片的 searchFocusRow
	 */
	private void applySearchFocus() {
		// 先清掉所有卡片的旧焦点
		for (KanbanCard card : cards) {
			card.setSearchFocusRow(-1);
			card.setSearchFocusCard(false);
		}
		if (searchFocusIndex < 0 || searchFocusIndex >= searchResults.size()) {
			return;
		}
		SearchResult r = searchResults.get(searchFocusIndex);
		KanbanCard target = findCardById(r.getCardId());
		if (target != null) {
			target.setSearchFocusRow(r.getRowIndex());
			// 焦点卡片标志：用于表头命中（rowIndex=-1）时把卡片边框变黄
			target.setSearchFocusCard(true);
		}
	}

	/**
	 * 平移画板让当前焦点结果进入视口
	 */
	private void scrollToFocusResult() {
		if (searchFocusIndex < 0 || searchFocusIndex >= searchResults.size()) {
			return;
		}
		SearchResult r = searchResults.get(searchFocusIndex);
		KanbanCard target = findCardById(r.getCardId());
		if (target == null) {
			return;
		}
		Rectangle2D b = target.getBounds();
		double cardCenterX, cardCenterY;
		if (r.getRowIndex() < 0) {
			// 焦点在表头：把卡片整体居中
			cardCenterX = b.getX() + b.getWidth() / 2.0;
			cardCenterY = b.getY() + b.getHeight() / 2.0;
		} else {
			// 焦点在某一行：精确滚动到行中心
			double rowTop = target.getRowTop(r.getRowIndex());
			double rowCenterY = rowTop + KanbanCard.ROW_HEIGHT / 2.0;
			cardCenterX = b.getX() + b.getWidth() / 2.0;
			cardCenterY = rowCenterY;
		}
		// 视口中心（屏幕坐标）
		java.awt.Rectangle view = getVisibleRect();
		double viewCenterX = view.getX() + view.getWidth() / 2.0;
		double viewCenterY = view.getY() + view.getHeight() / 2.0;
		// 当前屏幕坐标 = 画板坐标 * zoomFactor + translate
		// 要让 cardCenter 落在 viewCenter，反推 translate
		double currentScreenX = cardCenterX * zoomFactor + transform.getTranslateX();
		double currentScreenY = cardCenterY * zoomFactor + transform.getTranslateY();
		double dx = viewCenterX - currentScreenX;
		double dy = viewCenterY - currentScreenY;
		transform.translate(dx / zoomFactor, dy / zoomFactor);
	}

	/**
	 * 删除选中的卡片。
	 *
	 * <p>删除前检查是否有与其他表的连线，如有则弹出二次确认对话框。</p>
	 */
	private void deleteSelectedCard() {
		if (selectedCard == null) {
			return;
		}
		KanbanCard card = selectedCard;

		// 检查是否存在涉及该表的连线
		boolean hasRelations = hasRelationsFor(card.getId());
		if (hasRelations) {
			boolean confirmed = Messages.showYesNoDialog(
					project,
					"表 \"" + card.getName() + "\" 存在与其他表的连线，删除后连线关系将一并移除。\n\n确定要删除吗？",
					"删除数据库表",
					Messages.getYesButton(),
					Messages.getNoButton(),
					Messages.getQuestionIcon()) == Messages.YES;
			if (!confirmed) {
				return;
			}
		}

		cards.remove(card);
		// 同时移除涉及该表的所有连线
		connections.removeIf(conn -> conn.getSource() == card || conn.getTarget() == card);
		if (selectedCard == card) {
			selectedCard = null;
		}
		// 如果激活高亮指向被删除的卡，清掉
		if (activeHighlightCard == card) {
			clearActiveHighlight();
		}
		// 同步清理搜索结果中被删除卡片的项
		removeSearchResultsForCard(card.getId());
		repaint();
		notifyBoardChanged();
	}

	/**
	 * 从搜索结果中移除指定 cardId 的所有项，并修正焦点下标
	 */
	private void removeSearchResultsForCard(String cardId) {
		if (searchResults.isEmpty()) {
			return;
		}
		java.util.Iterator<SearchResult> it = searchResults.iterator();
		int removedBeforeFocus = 0;
		int currentIndex = 0;
		while (it.hasNext()) {
			it.next();
			if (cardId.equals(searchResults.get(currentIndex).getCardId())) {
				it.remove();
				if (currentIndex < searchFocusIndex) {
					removedBeforeFocus++;
				} else if (currentIndex == searchFocusIndex) {
					removedBeforeFocus++; // 焦点项被移除，等价于"前移"以便 clamp 后落在下一个
				}
			}
			currentIndex++;
		}
		if (removedBeforeFocus > 0) {
			searchFocusIndex -= removedBeforeFocus;
		}
		if (searchFocusIndex >= searchResults.size()) {
			searchFocusIndex = searchResults.isEmpty() ? -1 : searchResults.size() - 1;
		}
		// 重设焦点
		applySearchFocus();
	}

	/**
	 * 检查某张表是否存在连线关系
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

		// 2. 绘制所有连线（在卡片下方）
		for (Connection conn : connections) {
			// 选中连线加粗
			if (conn == selectedConnection) {
				float oldWidth = conn.getStrokeWidth();
				conn.setStrokeWidth(2.5f);
				conn.draw(g2d);
				conn.setStrokeWidth(oldWidth);
			} else {
				conn.draw(g2d);
			}
		}

		// 3. 绘制所有卡片
		boolean dark = isDarkTheme();
		// 重新计算每张卡的连线占用行（参考 DataHelper，绘制前动态计算，不依赖元素状态）
		Map<KanbanCard, Map<Integer, Color>> linkedRowsCache = computeLinkedRows();
		// 需求 2：刷新 activeHighlight → relatedRowKeys
		refreshRelatedRows();
		// 先清掉所有卡的临时行高亮色
		for (KanbanCard c : cards) {
			c.clearActiveRowColors();
		}
		// 把"激活列本身" + "关联列" 都标记为 RELATED_ROW_COLOR，让 Connection 端点能拿到一致色
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
			boolean isSelected = (card == selectedCard);
			card.setSelected(isSelected);
			// 合并：连线占用行 + 关联列高亮 + 预览高亮行
			// 优先级：用户选中（橙） > 预览高亮（粉） > 关联列（#FD9933） > 连线占用（线色）
			Map<Integer, Color> merged = new HashMap<>(
					linkedRowsCache.getOrDefault(card, Collections.emptyMap()));
			// 关联列覆盖连线色（视觉上保持统一橙色，强调"相连"）
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

		// 4. 绘制连线预览（鼠标跟随）
		if (isConnecting && connectionSource != null && connectionCurrentPoint != null) {
			Point2D sourcePoint = connectionSource.getRowRight(connectionSourceRow);
			if (sourcePoint != null) {
				g2d.setColor(Color.PINK);
				g2d.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				double dx = Math.abs(connectionCurrentPoint.getX() - sourcePoint.getX());
				double ctrlX1 = sourcePoint.getX() + dx / 2.0;
				double ctrlY1 = sourcePoint.getY();
				double ctrlX2 = connectionCurrentPoint.getX() - dx / 2.0;
				double ctrlY2 = connectionCurrentPoint.getY();
				g2d.draw(new java.awt.geom.CubicCurve2D.Double(
						sourcePoint.getX(), sourcePoint.getY(),
						ctrlX1, ctrlY1,
						ctrlX2, ctrlY2,
						connectionCurrentPoint.getX(), connectionCurrentPoint.getY()));
			}
		}

		g2d.dispose();

		// 5. 绘制对齐辅助线（在屏幕坐标下，覆盖在整个画板最上层）
		drawSnapGuides(g);
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

	/**
	 * 添加连线（自动分配颜色）
	 *
	 * @param source    源卡片
	 * @param sourceRow 源行索引
	 * @param target    目标卡片
	 * @param targetRow 目标行索引
	 * @return 新创建的连接，重复则返回 null
	 */
	public Connection addConnection(KanbanCard source, int sourceRow,
			KanbanCard target, int targetRow) {
		return addConnection(source, sourceRow, target, targetRow, RelationType.UNKNOWN);
	}

	/**
	 * 添加连线（指定关系类型）
	 */
	public Connection addConnection(KanbanCard source, int sourceRow,
			KanbanCard target, int targetRow, RelationType relationType) {
		if (source == null || target == null
				|| source == target || sourceRow < 0 || targetRow < 0) {
			return null;
		}
		// 防止重复连线
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
		// 不再直接修改卡片高亮状态，绘制时由 computeLinkedRows() 动态推导
		repaint();
		notifyBoardChanged();
		return conn;
	}

	/**
	 * 计算每张卡的连线占用行（每次重绘前调用，参考 DataHelper 设计）
	 *
	 * <p>遍历所有连线，把源行/目标行映射到对应卡的 Map，key=行索引，value=连线色。
	 * {@code putIfAbsent} 保证同一行被多次连接时取第一次连线的颜色。</p>
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
	 * 删除连线（两端高亮会在下次重绘时通过 computeLinkedRows 重新计算，不需要手动清）
	 */
	public void removeConnection(Connection conn) {
		if (conn == null) {
			return;
		}
		connections.remove(conn);
		if (selectedConnection == conn) {
			selectedConnection = null;
		}
		repaint();
		notifyBoardChanged();
	}

	/**
	 * 检测鼠标位置是否命中某条连线（简单矩形近似：源行右 → 目标行左之间的中线矩形）
	 *
	 * @return 命中的连线，未命中返回 null
	 */
	private Connection hitTestConnection(java.awt.Point screenPoint) {
		Point2D boardPoint = transformPoint(screenPoint);
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
			// 上下扩展 8 像素，点击灵敏度
			if (boardPoint.getX() >= minX - 4 && boardPoint.getX() <= maxX + 4
					&& boardPoint.getY() >= minY - 8 && boardPoint.getY() <= maxY + 8) {
				return conn;
			}
		}
		return null;
	}

	/**
	 * 在指定屏幕坐标处弹出连线右键菜单（删除 / 关系类型）
	 */
	private void showConnectionContextMenu(Connection conn, java.awt.Point screenPoint) {
		if (conn == null) {
			return;
		}
		selectedConnection = conn;
		repaint();
		javax.swing.JPopupMenu menu = buildStyledPopupMenu();

		// --- 关系类型子菜单（需求 7）---
		javax.swing.JMenu typeMenu = new javax.swing.JMenu("关系类型");
		typeMenu.setForeground(JBColor.foreground());
		typeMenu.setBackground(JBColor.background());
		typeMenu.putClientProperty("MenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		typeMenu.putClientProperty("MenuItem.selectionBackground", JBColor.background());
		addRelationTypeItem(typeMenu, "一对一", RelationType.ONE_TO_ONE, conn);
		addRelationTypeItem(typeMenu, "一对多", RelationType.ONE_TO_MANY, conn);
		addRelationTypeItem(typeMenu, "多对一", RelationType.MANY_TO_ONE, conn);
		addRelationTypeItem(typeMenu, "多对多", RelationType.MANY_TO_MANY, conn);
		menu.add(typeMenu);

		menu.addSeparator();

		// --- 删除 ---
		javax.swing.JMenuItem deleteItem = buildStyledMenuItem("删除连线");
		deleteItem.addActionListener(e -> removeConnection(conn));
		menu.add(deleteItem);

		menu.show(this, screenPoint.x, screenPoint.y);
	}

	/**
	 * 向关系类型菜单添加一项，并实现选中后修改连线端点形状
	 */
	private void addRelationTypeItem(javax.swing.JMenu parent, String label,
			RelationType type, Connection conn) {
		javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(label);
		item.setSelected(conn.getRelationType() == type);
		item.setForeground(JBColor.foreground());
		item.setBackground(JBColor.background());
		item.putClientProperty("MenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		item.putClientProperty("MenuItem.selectionBackground", JBColor.background());
		item.addActionListener(e -> {
			conn.setRelationType(type);
			repaint();
			notifyBoardChanged();
		});
		parent.add(item);
	}

	/**
	 * 获取当前选中的连线
	 */
	public Connection getSelectedConnection() {
		return selectedConnection;
	}

	/**
	 * 构建字段的 tooltip（仅显示注释）
	 */
	private String buildColumnTooltip(com.wd.db.ColumnInfo col) {
		String comment = col.getComment();
		if (comment == null || comment.isEmpty()) {
			return null;
		}
		return "<html>" + comment + "</html>";
	}

	/**
	 * 获取所有连线
	 */
	public List<Connection> getConnections() {
		return connections;
	}

	/**
	 * 将看板状态序列化为图数据模型（用于保存到 .datachart）
	 *
	 * @return ChartData，包含所有表卡片及其位置、列信息
	 */
	public ChartData toChartData() {
		ChartData data = new ChartData();
		for (KanbanCard card : cards) {
			TableInfo info = card.getTableInfo();
			if (info == null) {
				continue;
			}
			ChartData.TableCardModel model = new ChartData.TableCardModel();
			model.setId(card.getId());
			model.setDatasource(info.getDatasourceName());
			model.setSchema(info.getSchema());
			model.setTableName(info.getName());
			model.setComment(info.getComment());
			Rectangle2D b = card.getBounds();
			model.setX(b.getX());
			model.setY(b.getY());
			model.setWidth(b.getWidth());
			model.setHeight(b.getHeight());
			// 保存列信息（避免重新打开时重新查数据库）
			model.setColumns(new java.util.ArrayList<>(info.getColumns()));
			// 保存用户手动选中的行（橙色高亮持久化）
			model.setHighlightedRows(new java.util.ArrayList<>(card.getHighlightedRows()));
			data.getTables().add(model);
		}
		// 保存连线
		for (Connection conn : connections) {
			ChartRelation rel = new ChartRelation(
					conn.getSource().getId(), Integer.toString(conn.getSourceRow()),
					conn.getTarget().getId(), Integer.toString(conn.getTargetRow()),
					conn.getRelationType());
			data.getRelations().add(rel);
		}
		return data;
	}

	/**
	 * 从图数据模型恢复看板状态（打开 .datachart 文件时调用）
	 *
	 * <p>优先使用 JSON 中保存的列信息（快速、离线可用），
	 * 如未保存列信息则尝试重新查询元信息（失败时用空 TableInfo）。</p>
	 *
	 * @param data 图数据模型
	 */
	public void loadFromChartData(ChartData data) {
		if (data == null) {
			return;
		}
		cards.clear();
		for (ChartData.TableCardModel model : data.getTables()) {
			TableInfo info;
			List<ColumnInfo> savedColumns = model.getColumns();
			if (savedColumns != null && !savedColumns.isEmpty()) {
				// 优先用 JSON 里保存的列（快速、离线可用）
				info = new TableInfo(model.getId(), model.getTableName(),
						model.getSchema(), model.getDatasource(),
						model.getComment(), savedColumns);
			} else {
				// 没有保存列信息，尝试重新查询元信息
				TableMetadataService svc = TableMetadataService.getInstance(project);
				info = svc.getFetcher().fetchTableInfo(
						project, model.getDatasource(), model.getTableName());
				if (info == null) {
					// 查询失败，构造空 TableInfo
					info = new TableInfo(model.getId(), model.getTableName(),
							model.getSchema(), model.getDatasource(),
							model.getComment(), Collections.emptyList());
				}
			}
			KanbanCard card = KanbanCard.forTable(info.getId(), info,
					model.getX(), model.getY(), model.getWidth(), model.getHeight());
			// 恢复用户手动选中的行（橙色高亮）
			if (model.getHighlightedRows() != null) {
				for (Integer row : model.getHighlightedRows()) {
					if (row != null) {
						card.addHighlightedRow(row);
					}
				}
			}
			cards.add(card);
		}

		// 恢复连线：根据 relations 中的 fromCardId/fromColumn + toCardId/toColumn 找到对应卡片/行
		connections.clear();
		connectionColorIndex = 0;
		if (data.getRelations() != null) {
			for (ChartRelation rel : data.getRelations()) {
				KanbanCard src = findCardById(rel.getFromCardId());
				KanbanCard tgt = findCardById(rel.getToCardId());
				if (src == null || tgt == null) {
					continue;
				}
				int srcRow = parseRowIndex(rel.getFromColumn());
				int tgtRow = parseRowIndex(rel.getToColumn());
				if (srcRow < 0 || tgtRow < 0) {
					continue;
				}
				RelationType type = rel.getRelationType() == null
						? RelationType.UNKNOWN : rel.getRelationType();
				addConnection(src, srcRow, tgt, tgtRow, type);
			}
		}

		selectedCard = null;
		repaint();
	}

	/**
	 * 根据卡片 ID 查找卡片
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
	 * 从字符串解析行索引
	 */
	private int parseRowIndex(String s) {
		try {
			return Integer.parseInt(s);
		} catch (Exception e) {
			return -1;
		}
	}

	// ====================== 复制到剪贴板（需求 3、4） ======================

	/**
	 * 复制文本到系统剪贴板
	 */
	private void copyToClipboard(String text) {
		if (text == null) {
			return;
		}
		StringSelection selection = new StringSelection(text);
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(selection, null);
	}

	/** 菜单项 hover/selected 时的文字颜色（蓝色） */
	private static final Color MENU_HOVER_FOREGROUND = new Color(0x2470B0);

	/** 是否已对 UIManager 设置过 menu 颜色（避免重复设置） */
	private static boolean menuUiPatched = false;

	/**
	 * 创建一个与当前主题适配的 JPopupMenu（修复 hover 文字看不清）
	 *
	 * <p>Swing 默认 popup 菜单在 IntelliJ 主题下选中/hover 文字是白色，背景看 L&F。
	 * 解决：</p>
	 * <ol>
	 *   <li>首次调用时改 {@code UIManager} 的全局默认 {@code MenuItem.selectionForeground/Background}，
	 *       让 L&F 在自绘时读取这个颜色</li>
	 *   <li>显式给每个菜单项 setForeground/Background 作为兜底</li>
	 * </ol>
	 */
	private javax.swing.JPopupMenu buildStyledPopupMenu() {
		patchMenuUiDefaults();
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		menu.setForeground(JBColor.foreground());
		menu.setBackground(JBColor.background());
		return menu;
	}

	/**
	 * 创建一个菜单项，hover/selected 文字颜色固定为蓝色（修复白字问题）
	 */
	private javax.swing.JMenuItem buildStyledMenuItem(String label) {
		patchMenuUiDefaults();
		javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
		item.setForeground(JBColor.foreground());
		item.setBackground(JBColor.background());
		item.setOpaque(true);
		// 显式 set selection 颜色，部分 L&F 仍然读这些
		item.setSelected(false);
		item.putClientProperty("MenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		item.putClientProperty("MenuItem.selectionBackground", JBColor.background());
		return item;
	}

	/**
	 * 改 {@code UIManager} 的全局 menu 默认值，覆盖 L&F 的硬编码白色
	 *
	 * <p>这是修复"白字白底"问题的关键：Swing 的 {@code BasicMenuItemUI} 在
	 * paintMenuItem 时优先读 {@code UIManager.get("MenuItem.selectionForeground")}，
	 * 如果 L&F 没显式覆盖（例如 IntelliJ 的合成 L&F），就会拿到默认白色。
	 * 改全局默认值后，所有 menuItem 的 hover 文字都会用蓝色。</p>
	 */
	private void patchMenuUiDefaults() {
		if (menuUiPatched) {
			return;
		}
		javax.swing.UIDefaults defaults = javax.swing.UIManager.getDefaults();
		defaults.put("MenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		defaults.put("MenuItem.selectionBackground", JBColor.background());
		defaults.put("Menu.selectionForeground", MENU_HOVER_FOREGROUND);
		defaults.put("Menu.selectionBackground", JBColor.background());
		defaults.put("MenuItem.acceleratorSelectionForeground", MENU_HOVER_FOREGROUND);
		defaults.put("MenuItem.acceleratorForeground", MENU_HOVER_FOREGROUND);
		// CheckBoxMenuItem / RadioButtonMenuItem 也要改
		defaults.put("CheckBoxMenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		defaults.put("CheckBoxMenuItem.selectionBackground", JBColor.background());
		defaults.put("RadioButtonMenuItem.selectionForeground", MENU_HOVER_FOREGROUND);
		defaults.put("RadioButtonMenuItem.selectionBackground", JBColor.background());
		menuUiPatched = true;
	}

	/**
	 * 表头右键菜单（统一两项：复制表名 / 复制注释）
	 *
	 * <p>表头无论左半（表名）还是右半（注释）右键，都弹出相同的两个菜单项，
	 * 注释为空时"复制注释"灰显。</p>
	 */
	private void showHeaderContextMenu(KanbanCard card, java.awt.Point screenPoint) {
		if (card == null) {
			return;
		}
		TableInfo info = card.getTableInfo();
		if (info == null) {
			return;
		}
		javax.swing.JPopupMenu menu = buildStyledPopupMenu();

		javax.swing.JMenuItem copyName = buildStyledMenuItem("复制表名");
		copyName.addActionListener(e -> copyToClipboard(info.getName()));
		menu.add(copyName);

		String comment = info.getComment();
		javax.swing.JMenuItem copyComment = buildStyledMenuItem("复制注释");
		copyComment.setEnabled(comment != null && !comment.isEmpty());
		copyComment.addActionListener(e -> copyToClipboard(comment));
		menu.add(copyComment);

		menu.show(this, screenPoint.x, screenPoint.y);
	}

	/**
	 * 列行右键菜单（仅左半 = 列名+类型 区域触发）
	 *
	 * <p>右半（注释区域）不弹菜单，交给原连线流程（从行右边缘拖出连线）。</p>
	 */
	private void showColumnContextMenu(KanbanCard card, int rowIndex, java.awt.Point screenPoint) {
		if (card == null) {
			return;
		}
		TableInfo info = card.getTableInfo();
		if (info == null || rowIndex < 0 || rowIndex >= info.getColumns().size()) {
			return;
		}
		ColumnInfo col = info.getColumns().get(rowIndex);

		javax.swing.JPopupMenu menu = buildStyledPopupMenu();
		javax.swing.JMenuItem copyName = buildStyledMenuItem("复制列名");
		copyName.addActionListener(e -> copyToClipboard(col.getName()));
		menu.add(copyName);

		String comment = col.getComment();
		javax.swing.JMenuItem copyComment = buildStyledMenuItem("复制注释");
		copyComment.setEnabled(comment != null && !comment.isEmpty());
		copyComment.addActionListener(e -> copyToClipboard(comment));
		menu.add(copyComment);

		menu.show(this, screenPoint.x, screenPoint.y);
	}

	// ====================== 关联列高亮（需求 2） ======================

	/**
	 * 关联列的解析结果：哪个 card 的哪一行需要高亮
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
	 * 拼装关联列 key（用 cardId 而非 card 引用，避免删除卡片时 key 失效）
	 */
	private static String makeRelatedKey(KanbanCard card, int row) {
		return card.getId() + "#" + row;
	}

	/**
	 * 解析关联列 key（按 cardId 找到 card），用于设置临时行高亮
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
	 * 设置激活高亮（用户左键选中的列）
	 */
	private void setActiveHighlight(KanbanCard card, int row) {
		this.activeHighlightCard = card;
		this.activeHighlightRow = row;
		refreshRelatedRows();
	}

	/**
	 * 清空激活高亮（点击空白区域、取消选中、删除连线等）
	 */
	private void clearActiveHighlight() {
		this.activeHighlightCard = null;
		this.activeHighlightRow = -1;
		relatedRowKeys.clear();
	}

	/**
	 * 重新计算关联列集合
	 *
	 * <p>遍历所有连线，若一方命中 activeHighlight，则把另一方加入 relatedRowKeys。</p>
	 */
	private void refreshRelatedRows() {
		relatedRowKeys.clear();
		if (activeHighlightCard == null || activeHighlightRow < 0) {
			return;
		}
		for (Connection conn : connections) {
			if (conn.getSource() == activeHighlightCard
					&& conn.getSourceRow() == activeHighlightRow) {
				relatedRowKeys.add(makeRelatedKey(conn.getTarget(), conn.getTargetRow()));
			}
			if (conn.getTarget() == activeHighlightCard
					&& conn.getTargetRow() == activeHighlightRow) {
				relatedRowKeys.add(makeRelatedKey(conn.getSource(), conn.getSourceRow()));
			}
		}
	}

	// ====================== 磁吸 + 对齐辅助线（需求 6） ======================

	/**
	 * 拖拽过程中计算对齐 + 磁吸。
	 *
	 * <p>水平方向：把当前卡片的 左/中/右 与其它卡片的 左/中/右 对齐；</p>
	 * <p>垂直方向：把当前卡片的 上/中/下 与其它卡片的 上/中/下 对齐。</p>
	 *
	 * <p>关键：吸附时必须用"匹配的那条边"（如 sys_job 底部对齐 gen 顶部，就把 sys_job 的
	 * 底部写到 gen 顶部位置，而不是把 sys_job 的左上角写到那里）。</p>
	 *
	 * <p>距离小于 SNAP_THRESHOLD 时直接吸附；距离在 SNAP 与 ALIGN 之间时只显示辅助线，
	 * 不吸附（让用户看到对齐关系但不被强制锁定）。</p>
	 *
	 * @param moving 当前正在拖动的卡片
	 */
	private void applySnapAndGuides(KanbanCard moving) {
		if (moving == null) {
			activeSnapGuideV = null;
			activeSnapGuideH = null;
			return;
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
		List<Double> xCandidates = new ArrayList<>();
		List<Double> yCandidates = new ArrayList<>();
		for (KanbanCard other : cards) {
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

		// 没有候选 → 清掉辅助线
		if (xCandidates.isEmpty() && yCandidates.isEmpty()) {
			activeSnapGuideV = null;
			activeSnapGuideH = null;
			return;
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
		// 注意：先记下吸附前的边值，吸附后用于绘制辅助线位置（吸附后坐标 = bestSnap）
		if (hasSnapX && bestDx <= SNAP_THRESHOLD) {
			double newX;
			switch (bestMyXSide) {
				case 0: newX = bestSnapX; break;                              // 左对齐
				case 1: newX = bestSnapX - cardW / 2.0; break;                 // 中对齐
				case 2: newX = bestSnapX - cardW; break;                       // 右对齐
				default: newX = bestSnapX;
			}
			mb.setRect(newX, mb.getY(), cardW, cardH);
		}
		if (hasSnapY && bestDy <= SNAP_THRESHOLD) {
			double newY;
			switch (bestMyYSide) {
				case 0: newY = bestSnapY; break;                              // 上对齐
				case 1: newY = bestSnapY - cardH / 2.0; break;                 // 中对齐
				case 2: newY = bestSnapY - cardH; break;                       // 下对齐
				default: newY = bestSnapY;
			}
			mb.setRect(mb.getX(), newY, cardW, cardH);
		}

		// 对齐辅助线（在 ALIGN_THRESHOLD 内显示，画在吸附位置 = bestSnap）
		if (hasSnapX && bestDx <= ALIGN_THRESHOLD) {
			double screenX = bestSnapX * zoomFactor + transform.getTranslateX();
			Rectangle bounds = getBounds();
			activeSnapGuideV = new Line2D.Double(screenX, 0, screenX, bounds.height);
		} else {
			activeSnapGuideV = null;
		}
		if (hasSnapY && bestDy <= ALIGN_THRESHOLD) {
			double screenY = bestSnapY * zoomFactor + transform.getTranslateY();
			Rectangle bounds = getBounds();
			activeSnapGuideH = new Line2D.Double(0, screenY, bounds.width, screenY);
		} else {
			activeSnapGuideH = null;
		}
	}

	/**
	 * 绘制对齐辅助线（在屏幕坐标系下，覆盖在最上层）
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

	// ====================== 持久化：列连接键 ======================

	/**
	 * 将 KanbanCard 实例关联回"激活高亮"集合（已不需要；保留 placeholder）
	 */
	@SuppressWarnings("unused")
	private void ensureRelatedRowKeysResolved() {
		// 保留：未来若引入卡片删除时可能需要清理 relatedRowKeys
		// 当前实现下，refreshRelatedRows 每次重绘前都会重建，无需特殊处理
	}
}