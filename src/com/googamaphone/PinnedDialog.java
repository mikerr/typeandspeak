
package com.googamaphone;

import com.googamaphone.typeandspeak.R;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class PinnedDialog {
    public static final int ABOVE = 0x1;
    public static final int BELOW = 0x2;

    private final Rect mAnchorRect = new Rect();
    private final Rect mBoundsRect = new Rect();
    private final Rect mScreenRect = new Rect();

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final ViewGroup mWindowView;
    private final ViewGroup mContentView;
    private final ImageView mTickAbove;
    private final ImageView mTickBelow;
    private final View mTickAbovePadding;
    private final View mTickBelowPadding;
    private final LayoutParams mParams;

    private View mAnchorView;

    private boolean mVisible = false;

    /**
     * Creates a new simple overlay.
     *
     * @param context The parent context.
     */
    public PinnedDialog(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        mWindowView = new PinnedLayout(context);
        mWindowView.setOnTouchListener(mOnTouchListener);
        mWindowView.setOnKeyListener(mOnKeyListener);

        LayoutInflater.from(context).inflate(R.layout.pinned_dialog, mWindowView);

        mContentView = (ViewGroup) mWindowView.findViewById(R.id.content);
        mTickBelow = (ImageView) mWindowView.findViewById(R.id.tick_below);
        mTickAbove = (ImageView) mWindowView.findViewById(R.id.tick_above);
        mTickAbovePadding = mWindowView.findViewById(R.id.tick_above_padding);
        mTickBelowPadding = mWindowView.findViewById(R.id.tick_below_padding);

        mParams = new WindowManager.LayoutParams();
        mParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        mParams.format = PixelFormat.TRANSLUCENT;
        mParams.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        mParams.width = LayoutParams.WRAP_CONTENT;
        mParams.height = LayoutParams.WRAP_CONTENT;
        mParams.windowAnimations = R.style.fade_dialog;

        mVisible = false;
    }

    public final void cancel() {
        dismiss();
    }

    /**
     * Shows the overlay. Calls the listener's
     * {@link SimpleOverlayListener#onHide(SimpleOverlay)} if available.
     */
    public final void show(View pinnedView) {
        if (isVisible()) {
            return;
        }

        mAnchorView = pinnedView;

        mWindowManager.addView(mWindowView, mParams);
        mVisible = true;

        final ViewTreeObserver observer = pinnedView.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(mOnGlobalLayoutListener);
    }

    /**
     * Hides the overlay. Calls the listener's
     * {@link SimpleOverlayListener#onHide(SimpleOverlay)} if available.
     */
    @SuppressWarnings("deprecation")
    public final void dismiss() {
        if (!isVisible()) {
            return;
        }

        mWindowManager.removeView(mWindowView);
        mVisible = false;

        final ViewTreeObserver observer = mAnchorView.getViewTreeObserver();
        observer.removeGlobalOnLayoutListener(mOnGlobalLayoutListener);
    }

    /**
     * @return The overlay context.
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * Finds and returns the view within the overlay content.
     *
     * @param id The ID of the view to return.
     * @return The view with the specified ID, or {@code null} if not found.
     */
    public View findViewById(int id) {
        return mWindowView.findViewById(id);
    }

    /**
     * @return A copy of the current layout parameters.
     */
    public LayoutParams getParams() {
        final LayoutParams copy = new LayoutParams();
        copy.copyFrom(mParams);
        return copy;
    }

    /**
     * Sets the current layout parameters and applies them immediately.
     *
     * @param params The layout parameters to use.
     */
    public void setParams(LayoutParams params) {
        mParams.copyFrom(params);

        if (!isVisible()) {
            return;
        }

        mWindowManager.updateViewLayout(mWindowView, mParams);
    }

    /**
     * @return {@code true} if this overlay is visible.
     */
    public boolean isVisible() {
        return (mVisible && mWindowView.isShown());
    }

    /**
     * Inflates the specified resource ID and sets it as the content view.
     *
     * @param layoutResId The layout ID of the view to set as the content view.
     */
    public PinnedDialog setContentView(int layoutResId) {
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        inflater.inflate(layoutResId, mContentView);
        return this;
    }

    public View getPinnedView() {
        return mAnchorView;
    }

    private void updatePinningOffset() {
        final int width = mWindowView.getWidth();
        final int height = mWindowView.getHeight();
        final LayoutParams params = getParams();
        final View rootView = mAnchorView.getRootView();
        final View parentContent = rootView.findViewById(android.R.id.content);

        rootView.getGlobalVisibleRect(mScreenRect);
        parentContent.getGlobalVisibleRect(mBoundsRect);
        mAnchorView.getGlobalVisibleRect(mAnchorRect);

        if ((mAnchorRect.bottom + height) <= mBoundsRect.bottom) {
            // Place below.
            params.y = (mAnchorRect.bottom);
            mTickBelow.setVisibility(View.VISIBLE);
            mTickAbove.setVisibility(View.GONE);
        } else if ((mAnchorRect.top - height) >= mScreenRect.top) {
            // Place above.
            params.y = (mAnchorRect.top - height);
            mTickBelow.setVisibility(View.GONE);
            mTickAbove.setVisibility(View.VISIBLE);
        } else {
            // Center on screen.
            params.y = (mScreenRect.centerY() - (height / 2));
            mTickBelow.setVisibility(View.GONE);
            mTickAbove.setVisibility(View.GONE);
        }

        // First, attempt to center on the pinned view.
        params.x = (mAnchorRect.centerX() - (width / 2));

        if (params.x < mBoundsRect.left) {
            // Align to left of parent.
            params.x = mBoundsRect.left;
        } else if ((params.x + width) > mBoundsRect.right) {
            // Align to right of parent.
            params.x = (mBoundsRect.right - width);
        }

        final int tickLeft = (mAnchorRect.centerX() - params.x) - (mTickAbove.getWidth() / 2);
        mTickAbovePadding.getLayoutParams().width = tickLeft;
        mTickBelowPadding.getLayoutParams().width = tickLeft;

        params.gravity = Gravity.LEFT | Gravity.TOP;

        setParams(params);
    }

    private final OnGlobalLayoutListener mOnGlobalLayoutListener = new OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            if (!isVisible()) {
                // This dialog is not showing.
                return;
            }

            if (mWindowView.getWindowToken() == null) {
                // This dialog was removed by the system.
                mVisible = false;
                return;
            }

            updatePinningOffset();
        }
    };

    private final OnKeyListener mOnKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    cancel();
                }
                return true;
            }

            return false;
        }
    };

    private final OnTouchListener mOnTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.getHitRect(mAnchorRect);

                if (!mAnchorRect.contains((int) event.getX(), (int) event.getY())) {
                    cancel();
                }
            }

            return false;
        }
    };

    private class PinnedLayout extends FrameLayout {
        public PinnedLayout(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);

            updatePinningOffset();
        }
    }
}
