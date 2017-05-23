package com.googamaphone;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.LinearLayout;

public class DispatchingLinearLayout extends LinearLayout {
    private OnKeyListener mOnKeyListener;

    public DispatchingLinearLayout(Context context) {
        super(context);
    }

    public DispatchingLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if ((mOnKeyListener != null) && mOnKeyListener.onKey(this, event.getKeyCode(), event)) {
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void setOnKeyListener(OnKeyListener listener) {
        mOnKeyListener = listener;
    }
}