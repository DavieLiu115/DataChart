package com.wd.icon;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

public class PluginIcons {

    public static final Icon dataSchema = load("/icons/dataSchema.svg");
    public static final Icon dataSchema_dark = load("/icons/dataSchema_dark.svg");
    public static final Icon clearCash = load("/icons/clearCash.svg");
    public static final Icon clearCash_dark = load("/icons/clearCash_dark.svg");
    public static final Icon reset = load("/icons/reset.svg");
    public static final Icon reset_dark = load("/icons/reset_dark.svg");
    public static final Icon dataStores = load("/icons/dataStores.svg");
    public static final Icon dataStores_dark = load("/icons/dataStores_dark.svg");
    public static final Icon testCustom = load("/icons/testCustom.svg");
    public static final Icon testCustom_dark = load("/icons/testCustom_dark.svg");
    public static final Icon warningDialog = load("/icons/warningDialog.svg");
    public static final Icon warningDialog_dark = load("/icons/warningDialog_dark.svg");
    public static final Icon diagram = load("/icons/diagram.svg");
    public static final Icon diagram_dark = load("/icons/diagram_dark.svg");
    public static final Icon download = load("/icons/download.svg");
    public static final Icon download_dark = load("/icons/download_dark.svg");
    public static final Icon image = load("/icons/image.svg");
    public static final Icon image_dark = load("/icons/image_dark.svg");
    public static final Icon lightning = load("/icons/lightning.svg");
    public static final Icon lightning_dark = load("/icons/lightning_dark.svg");
    public static final Icon colGoldKeyDotIndex = load("/icons/colGoldKeyDotIndex.svg");
    public static final Icon colGoldKeyDotIndex_dark = load("/icons/colGoldKeyDotIndex_dark.svg");
    public static final Icon DataTables = load("/icons/DataTables.svg");
    public static final Icon dataColumn = load("/icons/dataColumn.svg");
    public static final Icon colDot = load("/icons/colDot.svg");
    public static final Icon colDot_dark = load("/icons/colDot_dark.svg");
    public static final Icon colIndex = load("/icons/colIndex.svg");
    public static final Icon colDotIndex = load("/icons/colDotIndex.svg");
    public static final Icon colDotIndex_dark = load("/icons/colDotIndex_dark.svg");
    public static final Icon export = load("/icons/export.svg");
    public static final Icon export_dark = load("/icons/export_dark.svg");
    public static final Icon import_light = load("/icons/import.svg");
    public static final Icon import_dark = load("/icons/import_dark.svg");
    public static final Icon deletedTable = load("/icons/deletedTable.svg");
    public static final Icon deletedTable_dark = load("/icons/deletedTable_dark.svg");
    public static final Icon link = load("/icons/link.svg");
    public static final Icon link_dark = load("/icons/link_dark.svg");
    public static final Icon refresh = load("/icons/refresh.svg");
    public static final Icon refresh_dark = load("/icons/refresh_dark.svg");
    public static final Icon collapse = load("/icons/collapse.svg");
    public static final Icon collapse_dark = load("/icons/collapse_dark.svg");
    public static final Icon expand = load("/icons/expand.svg");
    public static final Icon expand_dark = load("/icons/expand_dark.svg");
    public static final Icon grid = load("/icons/grid.svg");
    public static final Icon grid_dark = load("/icons/grid_dark.svg");
    public static final Icon magicResolve = load("/icons/magicResolve.svg");
    public static final Icon magicResolve_dark = load("/icons/magicResolve_dark.svg");
    public static final Icon createTable = load("/icons/createTable.svg");
    public static final Icon createTable_dark = load("/icons/createTable_dark.svg");
    public static final Icon dropTable = load("/icons/dropTable.svg");
    public static final Icon dropTable_dark = load("/icons/dropTable_dark.svg");

    public static final Icon success = load("/icons/success.svg");
    public static final Icon success_dark = load("/icons/success_dark.svg");
    public static final Icon testFailed = load("/icons/testFailed.svg");
    public static final Icon testFailed_dark = load("/icons/testFailed_dark.svg");
    public static final Icon search = load("/icons/search.svg");
    public static final Icon search_dark = load("/icons/search_dark.svg");
    public static final Icon fullScream = load("/icons/fullScream.svg");
    public static final Icon exit_fullScream = load("/icons/exit_fullScream.svg");
    public static final Icon columnFilter = load("/icons/columnFilter.svg");

    public static final Icon extension = load("/icons/extension.svg");
    public static final Icon extension_dark = load("/icons/extension_dark.svg");
    public static final Icon preview = load("/icons/preview.svg");
    public static final Icon preview_dark = load("/icons/preview_dark.svg");
    public static final Icon INTELLIJ_COLLAPSE_ALL = load("/icons/intellij_collapseAll.svg");
    public static final Icon INTELLIJ_COLLAPSE_ALL_DARK = load("/icons/intellij_collapseAll_dark.svg");
    public static final Icon INTELLIJ_EXPAND_ALL = load("/icons/intellij_expandAll.svg");
    public static final Icon INTELLIJ_EXPAND_ALL_DARK = load("/icons/intellij_expandAll_dark.svg");
    public static final Icon Donation = load("/icons/donation.svg");
    public static final Icon Donation_Enter = load("/icons/donation_enter.svg");

    public static Icon load(String iconPath) {
        return IconLoader.getIcon(iconPath, PluginIcons.class);
    }

}
