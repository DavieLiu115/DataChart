package com.wd.ui;

import com.wd.db.ColumnInfo;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 看板搜索模型：负责搜索结果收集、焦点导航与滚动定位。
 *
 * <p>从 {@link KanbanBoard} 抽取，职责单一：</p>
 * <ul>
 *   <li>{@link #search} 遍历所有卡片匹配 表名/表注释/列名/列注释（不区分大小写）</li>
 *   <li>上下键导航（{@link #focusNext} / {@link #focusPrev}）</li>
 *   <li>滚动到焦点结果（{@link #scrollToFocus}）</li>
 *   <li>删除卡片时清理结果（{@link #removeResultsForCard}）</li>
 * </ul>
 *
 * <p>搜索结果按"卡片 + 行"为单位，{@code rowIndex = -1} 表示表头命中。</p>
 *
 * @author lww
 */
public class BoardSearchModel {

	/** 搜索结果列表：按"卡片 + 行"为单位的有序列表 */
	private final List<SearchResult> searchResults = new ArrayList<>();

	/** 当前"焦点"搜索结果在 {@link #searchResults} 中的下标，-1 表示无焦点 */
	private int searchFocusIndex = -1;

	/**
	 * 搜索结果单元：表示 (cardId, rowIndex) 形式的命中位置。
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

	/**
	 * 执行搜索。
	 *
	 * @param cards   全部卡片
	 * @param keyword 搜索关键词（null 或空表示清空）
	 * @return 命中总数
	 */
	public int search(List<KanbanCard> cards, String keyword) {
		clearSearchState(cards);

		if (keyword == null || keyword.trim().isEmpty()) {
			return 0;
		}

		String lower = keyword.trim().toLowerCase();
		for (KanbanCard card : cards) {
			// 1. 表名 / 表注释命中（表头行号约定 -1）
			String tableName = card.getName() == null ? "" : card.getName();
			String tableComment = card.getDescription() == null ? "" : card.getDescription();
			if (tableName.toLowerCase().contains(lower)
					|| tableComment.toLowerCase().contains(lower)) {
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
		}
		return searchResults.size();
	}

	/**
	 * 清空搜索状态（命中行 + 焦点 + 结果列表）。
	 */
	public void clearSearchState(List<KanbanCard> cards) {
		searchResults.clear();
		searchFocusIndex = -1;
		for (KanbanCard card : cards) {
			card.clearSearchMatchedRows();
		}
	}

	public int getResultCount() {
		return searchResults.size();
	}

	public int getFocusIndex() {
		return searchFocusIndex;
	}

	/**
	 * 切换到下一个搜索结果（下箭头）。
	 */
	public SearchResult focusNext() {
		if (searchResults.isEmpty()) {
			return null;
		}
		searchFocusIndex = (searchFocusIndex + 1) % searchResults.size();
		return searchResults.get(searchFocusIndex);
	}

	/**
	 * 切换到上一个搜索结果（上箭头）。
	 */
	public SearchResult focusPrev() {
		if (searchResults.isEmpty()) {
			return null;
		}
		searchFocusIndex = (searchFocusIndex - 1 + searchResults.size()) % searchResults.size();
		return searchResults.get(searchFocusIndex);
	}

	/**
	 * 把当前焦点写入对应卡片（焦点行 + 焦点卡片标志）。
	 *
	 * @param cards       全部卡片
	 * @param findById    按 ID 查找卡片的函数
	 */
	public void applyFocus(List<KanbanCard> cards, FindCard findById) {
		for (KanbanCard card : cards) {
			card.setSearchFocusRow(-1);
			card.setSearchFocusCard(false);
		}
		if (searchFocusIndex < 0 || searchFocusIndex >= searchResults.size()) {
			return;
		}
		SearchResult r = searchResults.get(searchFocusIndex);
		KanbanCard target = findById.find(r.getCardId());
		if (target != null) {
			target.setSearchFocusRow(r.getRowIndex());
			// 焦点卡片标志：用于表头命中（rowIndex=-1）时把卡片边框变黄
			target.setSearchFocusCard(true);
		}
	}

	/**
	 * 平移画板让当前焦点结果进入视口中心。
	 *
	 * @param cards       全部卡片
	 * @param findById    按 ID 查找卡片的函数
	 * @param viewport    视口几何
	 * @param viewWidth   视口宽
	 * @param viewHeight  视口高
	 */
	public void scrollToFocus(List<KanbanCard> cards, FindCard findById,
			BoardViewport viewport, int viewWidth, int viewHeight) {
		if (searchFocusIndex < 0 || searchFocusIndex >= searchResults.size()) {
			return;
		}
		SearchResult r = searchResults.get(searchFocusIndex);
		KanbanCard target = findById.find(r.getCardId());
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
		viewport.centerOn(cardCenterX, cardCenterY, viewWidth, viewHeight);
	}

	/**
	 * 从搜索结果中移除指定 cardId 的所有项，并修正焦点下标。
	 */
	public void removeResultsForCard(String cardId) {
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
	}

	/** 按卡片 ID 查找的函数式接口 */
	public interface FindCard {
		KanbanCard find(String id);
	}
}
