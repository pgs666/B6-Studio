package dev.b6.studio;

import android.app.Application;

/** A single BLE session shared by all activities; holds only the application context. */
public final class StudioApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
        getSharedPreferences("appearance", 0)
            .getInt("night", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
  }

  private BleController controller;

  public BleController controller() {
    if (controller == null) controller = new BleController(this);
    return controller;
  }
}
