package org.levimc.launcher.nemesis;

import android.app.Activity;
import android.os.Bundle;

/** Native host for the packaged Nemesis login/launcher and client settings. */
public final class NemesisActivity extends Activity {
    private NemesisWebController controller;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff0d0e12);
        getWindow().setNavigationBarColor(0xff0d0e12);
        controller = new NemesisWebController(this, false, this::finish);
        setContentView(controller.view());
        controller.load(false);
    }
    @Override protected void onResume() {
        super.onResume();
        if (controller != null) controller.refresh();
    }
    @Override public void onBackPressed() {
        if (controller != null && controller.view().getUrl() != null
                && controller.view().getUrl().contains("/client.html")) controller.load(false);
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (controller != null) { controller.destroy(); controller = null; }
        super.onDestroy();
    }
}
