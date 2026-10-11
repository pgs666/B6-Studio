package dev.b6.studio;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

/** Shared spacing between standalone pills; segmented buttons keep their shared shape. */
final class UiSpacing {
  private UiSpacing() {}

  static void apply(ViewGroup parent) {
    int gap = Math.round(8 * parent.getResources().getDisplayMetrics().density);
    for (int i = 0; i < parent.getChildCount(); i++) {
      View child = parent.getChildAt(i);
      if (child instanceof ViewGroup) apply((ViewGroup) child);
      if (i > 0
          && parent instanceof LinearLayout
          && !(parent instanceof MaterialButtonToggleGroup)
          && child instanceof MaterialButton
          && parent.getChildAt(i - 1) instanceof MaterialButton) {
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) child.getLayoutParams();
        if (((LinearLayout) parent).getOrientation() == LinearLayout.VERTICAL) p.topMargin = gap;
        else p.setMarginStart(gap);
        child.setLayoutParams(p);
      }
    }
  }
}
