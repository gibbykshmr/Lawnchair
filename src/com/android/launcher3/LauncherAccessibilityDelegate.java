package com.android.launcher3;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import com.android.launcher3.LauncherModel.ScreenPosProvider;
import com.android.launcher3.util.Thunk;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class LauncherAccessibilityDelegate extends AccessibilityDelegate {

    public static final int REMOVE = R.id.action_remove;
    public static final int INFO = R.id.action_info;
    public static final int UNINSTALL = R.id.action_uninstall;
    public static final int ADD_TO_WORKSPACE = R.id.action_add_to_workspace;
    public static final int MOVE = R.id.action_move;

    enum DragType {
        ICON,
        FOLDER,
        WIDGET
    }

    public static class DragInfo {
        DragType dragType;
        ItemInfo info;
        View item;
    }

    private DragInfo mDragInfo = null;

    private final SparseArray<AccessibilityAction> mActions =
            new SparseArray<AccessibilityAction>();
    @Thunk final Launcher mLauncher;

    public LauncherAccessibilityDelegate(Launcher launcher) {
        mLauncher = launcher;

        mActions.put(REMOVE, new AccessibilityAction(REMOVE,
                launcher.getText(R.string.delete_target_label)));
        mActions.put(INFO, new AccessibilityAction(INFO,
                launcher.getText(R.string.info_target_label)));
        mActions.put(UNINSTALL, new AccessibilityAction(UNINSTALL,
                launcher.getText(R.string.delete_target_uninstall_label)));
        mActions.put(ADD_TO_WORKSPACE, new AccessibilityAction(ADD_TO_WORKSPACE,
                launcher.getText(R.string.action_add_to_workspace)));
        mActions.put(MOVE, new AccessibilityAction(MOVE,
                launcher.getText(R.string.action_move)));

    }

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        if (!(host.getTag() instanceof ItemInfo)) return;
        ItemInfo item = (ItemInfo) host.getTag();

        if (DeleteDropTarget.supportsDrop(item)) {
            info.addAction(mActions.get(REMOVE));
        }
        if (UninstallDropTarget.supportsDrop(host.getContext(), item)) {
            info.addAction(mActions.get(UNINSTALL));
        }
        if (InfoDropTarget.supportsDrop(host.getContext(), item)) {
            info.addAction(mActions.get(INFO));
        }

        if ((item instanceof ShortcutInfo)
                || (item instanceof LauncherAppWidgetInfo)
                || (item instanceof FolderInfo)) {
            info.addAction(mActions.get(MOVE));
        } if ((item instanceof AppInfo) || (item instanceof PendingAddItemInfo)) {
            info.addAction(mActions.get(ADD_TO_WORKSPACE));
        }
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        if ((host.getTag() instanceof ItemInfo)
                && performAction(host, (ItemInfo) host.getTag(), action)) {
            return true;
        }
        return super.performAccessibilityAction(host, action, args);
    }

    public boolean performAction(View host, ItemInfo item, int action) {
        if (action == REMOVE) {
            if (DeleteDropTarget.removeWorkspaceOrFolderItem(mLauncher, item, host)) {
                announceConfirmation(R.string.item_removed);
                return true;
            }
            return false;
        } else if (action == INFO) {
            InfoDropTarget.startDetailsActivityForInfo(item, mLauncher);
            return true;
        } else if (action == UNINSTALL) {
            return UninstallDropTarget.startUninstallActivity(mLauncher, item);
        } else if (action == MOVE) {
            beginAccessibleDrag(host, item);
        } else if (action == ADD_TO_WORKSPACE) {
            final int preferredPage = mLauncher.getWorkspace().getCurrentPage();
            final ScreenPosProvider screenProvider = new ScreenPosProvider() {

                @Override
                public int getScreenIndex(ArrayList<Long> screenIDs) {
                    return preferredPage;
                }
            };
            if (item instanceof AppInfo) {
                final ArrayList<ItemInfo> addShortcuts = new ArrayList<ItemInfo>();
                addShortcuts.add(((AppInfo) item).makeShortcut());
                mLauncher.showWorkspace(true, new Runnable() {
                    @Override
                    public void run() {
                        mLauncher.getModel().addAndBindAddedWorkspaceItems(
                                mLauncher, addShortcuts, screenProvider, 0, true);
                        announceConfirmation(R.string.item_added_to_workspace);
                    }
                });
                return true;
            } else if (item instanceof PendingAddItemInfo) {
                mLauncher.getModel().addAndBindPendingItem(
                        mLauncher, (PendingAddItemInfo) item, screenProvider, 0);
                announceConfirmation(R.string.item_added_to_workspace);
                return true;
            }
        }
        return false;
    }

    @Thunk void announceConfirmation(int resId) {
        announceConfirmation(mLauncher.getResources().getString(resId));
    }

    @Thunk void announceConfirmation(String confirmation) {
        mLauncher.getDragLayer().announceForAccessibility(confirmation);

    }

    public boolean isInAccessibleDrag() {
        return mDragInfo != null;
    }

    public DragInfo getDragInfo() {
        return mDragInfo;
    }

    /**
     * @param clickedTarget the actual view that was clicked
     * @param dropLocation relative to {@param clickedTarget}. If provided, its center is used
     * as the actual drop location otherwise the views center is used.
     */
    public void handleAccessibleDrop(View clickedTarget, Rect dropLocation,
            String confirmation) {
        if (!isInAccessibleDrag()) return;

        int[] loc = new int[2];
        if (dropLocation == null) {
            loc[0] = clickedTarget.getWidth() / 2;
            loc[1] = clickedTarget.getHeight() / 2;
        } else {
            loc[0] = dropLocation.centerX();
            loc[1] = dropLocation.centerY();
        }

        mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(clickedTarget, loc);
        mLauncher.getDragController().completeAccessibleDrag(loc);

        endAccessibleDrag();
        if (!TextUtils.isEmpty(confirmation)) {
            announceConfirmation(confirmation);
        }
    }

    public void beginAccessibleDrag(View item, ItemInfo info) {
        mDragInfo = new DragInfo();
        mDragInfo.info = info;
        mDragInfo.item = item;
        mDragInfo.dragType = DragType.ICON;
        if (info instanceof FolderInfo) {
            mDragInfo.dragType = DragType.FOLDER;
        } else if (info instanceof LauncherAppWidgetInfo) {
            mDragInfo.dragType = DragType.WIDGET;
        }

        CellLayout.CellInfo cellInfo = new CellLayout.CellInfo(item, info);

        Rect pos = new Rect();
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(item, pos);

        mLauncher.getDragController().prepareAccessibleDrag(pos.centerX(), pos.centerY());
        mLauncher.getWorkspace().enableAccessibleDrag(true);
        mLauncher.getWorkspace().startDrag(cellInfo, true);
    }

    public boolean onBackPressed() {
        if (isInAccessibleDrag()) {
            cancelAccessibleDrag();
            return true;
        }
        return false;
    }

    private void cancelAccessibleDrag() {
        mLauncher.getDragController().cancelDrag();
        endAccessibleDrag();
    }

    private void endAccessibleDrag() {
        mDragInfo = null;
        mLauncher.getWorkspace().enableAccessibleDrag(false);
    }
}
