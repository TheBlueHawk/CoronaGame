package ch.epfl.sdp.tabActivity;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

/**
 * Special viewPager that disable swipe motion to navigate between tabs
 * Especially useful for the map as it uses swipe motion to navigate
 */
public class SwipeViewPager extends ViewPager {

    private boolean swipeEnabled;

    public SwipeViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.swipeEnabled = true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!this.swipeEnabled) return false;
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!this.swipeEnabled) return false;
        return super.onInterceptTouchEvent(event);
    }

    public void setSwipeEnabled(boolean enabled) {
        this.swipeEnabled = enabled;
    }
}