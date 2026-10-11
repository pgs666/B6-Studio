package dev.b6.studio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;

/** Independently navigable screen. Updates only while STARTED; the BLE session stays shared. */
public final class LogsActivity extends StudioActivity {
  private RecyclerView list;
  private LogAdapter adapter;
  private TextView count;
  private boolean paused;
  private long shownRevision = -1;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    toolbar("通信日志");
    LinearLayout actions = new LinearLayout(this);
    actions.setOrientation(LinearLayout.VERTICAL);
    actions.setPadding(dp(20), 0, dp(20), dp(8));
    count = text("", 12, false);
    actions.addView(count);
    space(actions, 8);
    LinearLayout row = new LinearLayout(this);
    MaterialButton pause = button("暂停", R.drawable.ic_pause);
    pause.setOnClickListener(
        v -> {
          paused = !paused;
          pause.setText(paused ? "继续" : "暂停");
          if (!paused) refresh();
          else updateCount();
        });
    MaterialButton copy = button("复制", R.drawable.ic_content_copy);
    copy.setOnClickListener(
        v -> {
          ClipboardManager cb = getSystemService(ClipboardManager.class);
          StringBuilder s = new StringBuilder();
          for (LogStore.Entry e : adapter.rows) {
            if (s.length() > 0) s.append('\n');
            s.append(e.text);
          }
          cb.setPrimaryClip(ClipData.newPlainText("B6 Studio log", s));
          Snackbar.make(root, "当前列表已复制", Snackbar.LENGTH_SHORT).show();
        });
    MaterialButton clear = button("清空", R.drawable.ic_delete);
    clear.setOnClickListener(
        v -> {
          ble.clearLogs();
          shownRevision = -1;
          adapter.replace(new ArrayList<>());
          if (!paused) refresh();
          else updateCount();
        });
    for (MaterialButton b : new MaterialButton[] {pause, copy, clear}) {
      b.setMinWidth(0);
      b.setPadding(dp(8), 0, dp(8), 0);
      b.setIconPadding(dp(4));
      row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    actions.addView(row);
    MaterialSwitch raw = new MaterialSwitch(this);
    raw.setText("记录原始通知");
    raw.setChecked(ble.rawNotifications);
    raw.setOnCheckedChangeListener((b, checked) -> ble.rawNotifications = checked);
    actions.addView(raw);
    root.addView(actions);
    list = new RecyclerView(this);
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setItemAnimator(null);
    list.setPadding(dp(20), dp(4), dp(20), dp(12));
    list.setClipToPadding(false);
    adapter = new LogAdapter();
    list.setAdapter(adapter);
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    ble.logChanges.observe(
        this,
        value -> {
          if (!paused) refresh();
        });
    refresh();
    finishInsets();
  }

  private void updateCount() {
    count.setText((paused ? "显示已暂停 · " : "") + adapter.rows.size() + " 条 · 缓冲上限 500 条 / 64K 字符");
  }

  private void refresh() {
    long revision = ble.logs.revision();
    if (revision == shownRevision) return;
    shownRevision = revision;
    boolean follow = adapter.rows.isEmpty() || !list.canScrollVertically(1);
    adapter.replace(ble.logs.snapshot());
    updateCount();
    if (follow && adapter.getItemCount() > 0) list.scrollToPosition(adapter.getItemCount() - 1);
  }

  private final class Holder extends RecyclerView.ViewHolder {
    final TextView text;

    Holder(TextView t) {
      super(t);
      text = t;
    }
  }

  private final class LogAdapter extends RecyclerView.Adapter<Holder> {
    final List<LogStore.Entry> rows = new ArrayList<>();

    LogAdapter() {
      setHasStableIds(true);
    }

    @Override
    public long getItemId(int p) {
      return rows.get(p).id;
    }

    @Override
    public int getItemCount() {
      return rows.size();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int type) {
      TextView t = text("", 12, false);
      t.setTypeface(Typeface.MONOSPACE);
      t.setPadding(0, dp(5), 0, dp(5));
      t.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
      return new Holder(t);
    }

    @Override
    public void onBindViewHolder(Holder h, int p) {
      h.text.setText(rows.get(p).text);
    }

    void replace(List<LogStore.Entry> next) {
      if (next.isEmpty()) {
        int old = rows.size();
        rows.clear();
        if (old > 0) notifyItemRangeRemoved(0, old);
        return;
      }
      int remove = 0;
      long first = next.get(0).id;
      while (remove < rows.size() && rows.get(remove).id < first) remove++;
      if (remove > 0) {
        rows.subList(0, remove).clear();
        notifyItemRangeRemoved(0, remove);
      }
      int kept = rows.size();
      if (kept > next.size() || (kept > 0 && rows.get(0).id != first)) {
        int old = rows.size();
        rows.clear();
        if (old > 0) notifyItemRangeRemoved(0, old);
        rows.addAll(next);
        notifyItemRangeInserted(0, next.size());
        return;
      }
      int add = next.size() - kept;
      if (add > 0) {
        rows.addAll(next.subList(kept, next.size()));
        notifyItemRangeInserted(kept, add);
      }
    }
  }
}
