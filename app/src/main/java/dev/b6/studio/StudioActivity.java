package dev.b6.studio;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

public abstract class StudioActivity extends AppCompatActivity {
  protected BleController ble;
  protected LinearLayout root, content;

  protected int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  protected int color(int attr) {
    return MaterialColors.getColor(root, attr);
  }

  @Override
  public void onCreate(Bundle state) {
    DynamicColors.applyToActivityIfAvailable(this);
    super.onCreate(state);
    ble = ((StudioApplication) getApplication()).controller();
    WindowCompat.enableEdgeToEdge(getWindow());
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
    setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          androidx.core.graphics.Insets b =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars()
                      | WindowInsetsCompat.Type.displayCutout()
                      | WindowInsetsCompat.Type.ime());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          return WindowInsetsCompat.CONSUMED;
        });
  }

  protected MaterialToolbar toolbar(String title) {
    MaterialToolbar bar = new MaterialToolbar(this);
    bar.setTitle(title);
    bar.setNavigationIcon(R.drawable.ic_arrow_back);
    bar.setNavigationContentDescription("返回");
    bar.setNavigationOnClickListener(v -> finish());
    root.addView(bar, new LinearLayout.LayoutParams(-1, dp(64)));
    return bar;
  }

  protected void scrollContent() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(20), dp(8), dp(20), dp(24));
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  protected TextView text(String value, int size, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return t;
  }

  protected void space(LinearLayout parent, int n) {
    parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(n)));
  }

  protected LinearLayout card(String title) {
    MaterialCardView card = new MaterialCardView(this);
    card.setRadius(dp(24));
    card.setCardElevation(0);
    card.setStrokeWidth(0);
    card.setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainerLow));
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(20), dp(18), dp(20), dp(18));
    card.addView(box);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(14);
    content.addView(card, p);
    box.addView(text(title, 18, true));
    space(box, 12);
    return box;
  }

  protected MaterialButton button(String label, int icon) {
    MaterialButton b = new MaterialButton(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setMinHeight(dp(48));
    b.setCornerRadius(dp(24));
    if (icon != 0) {
      b.setIconResource(icon);
      b.setIconSize(dp(20));
    }
    return b;
  }

  protected void finishInsets() {
    UiSpacing.apply(root);
    ViewCompat.requestApplyInsets(root);
  }
}
