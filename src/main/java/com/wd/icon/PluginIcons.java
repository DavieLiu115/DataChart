package com.wd.icon;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

/**
 * 插件图标集合。
 *
 * <p>2026-08-03 优化：只保留实际被引用的图标，删除 50+ 个从未使用的字段，
 * 减少类加载时创建的延迟图标对象数量。如需新增图标，追加到下方并确认有引用。</p>
 *
 * <p>说明：{@code IconLoader.getIcon()} 返回的是延迟加载图标（首次 paint 时才解析 SVG），
 * 因此类加载本身不会触发大量 I/O；这里的优化目标是减少无用的延迟图标对象。</p>
 */
public class PluginIcons {

    public static final Icon dataSchema = load("/icons/dataSchema.svg");
    public static final Icon dataSchema_dark = load("/icons/dataSchema_dark.svg");

    // 表格列图标（KanbanCard 使用）
    public static final Icon colGoldKeyDotIndex = load("/icons/colGoldKeyDotIndex.svg");
    public static final Icon colGoldKeyDotIndex_dark = load("/icons/colGoldKeyDotIndex_dark.svg");
    public static final Icon dataColumn = load("/icons/dataColumn.svg");
    public static final Icon colDot = load("/icons/colDot.svg");
    public static final Icon colDot_dark = load("/icons/colDot_dark.svg");
    public static final Icon colIndex = load("/icons/colIndex.svg");
    public static final Icon colDotIndex = load("/icons/colDotIndex.svg");
    public static final Icon colDotIndex_dark = load("/icons/colDotIndex_dark.svg");

    // 工具栏按钮图标（DataChartView 使用）
    public static final Icon export = load("/icons/export.svg");
    public static final Icon export_dark = load("/icons/export_dark.svg");
    public static final Icon image = load("/icons/image.svg");
    public static final Icon image_dark = load("/icons/image_dark.svg");
    public static final Icon autoLayout = load("/icons/autoLayout.svg");
    public static final Icon autoLayout_dark = load("/icons/autoLayout_dark.svg");
    public static final Icon Donation = load("/icons/donation.svg");
    public static final Icon Donation_Enter = load("/icons/donation_enter.svg");

    public static final Icon oneOne = load("/icons/oneOne.svg");

    public static Icon load(String iconPath) {
        return IconLoader.getIcon(iconPath, PluginIcons.class);
    }

}
