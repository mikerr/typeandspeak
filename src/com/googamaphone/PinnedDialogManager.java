
package com.googamaphone;

import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;

public class PinnedDialogManager {
    private final SparseArray<PinnedDialog> mPinnedDialogs = new SparseArray<PinnedDialog>();

    protected PinnedDialog onCreatePinnedDialog(int id) {
        return null;
    }

    protected void onPreparePinnedDialog(int id, PinnedDialog dialog, Bundle arguments) {
        // Do nothing.
    }
    
    public void showPinnedDialog(int id, View pinnedView) {
        showPinnedDialog(id, pinnedView, null);
    }

    public void showPinnedDialog(int id, View pinnedView, Bundle arguments) {
        PinnedDialog dialog = mPinnedDialogs.get(id);

        if (dialog == null) {
            dialog = onCreatePinnedDialog(id);
            mPinnedDialogs.put(id, dialog);
        }
        
        onPreparePinnedDialog(id, dialog, arguments);

        dialog.show(pinnedView);
    }

    public void dismissPinnedDialog(int id) {
        PinnedDialog dialog = mPinnedDialogs.get(id);

        if (dialog == null) {
            return;
        }

        dialog.dismiss();
    }

    public View getPinnedView(int id) {
        PinnedDialog dialog = mPinnedDialogs.get(id);

        if (dialog == null) {
            return null;
        }

        return dialog.getPinnedView();
    }
}
