package org.levimc.launcher.nemesis;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** In-activity overlay: no SYSTEM_ALERT_WINDOW permission and no external WebView navigation. */
public final class NemesisGameOverlay {
    private final Activity activity;
    private final Button handle;
    private Dialog dialog;
    private NemesisWebController controller;
    public NemesisGameOverlay(Activity activity) {
        this.activity = activity;
        handle = new Button(activity);
        handle.setText("N");
        handle.setTextColor(0xff1a1423);
        handle.setTextSize(18);
        handle.setContentDescription("Открыть Nemesis");
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xffc0afff);
        background.setCornerRadius(dp(14));
        handle.setBackground(background);
        handle.setMinWidth(0);
        handle.setMinHeight(0);
        handle.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
        position.setMargins(dp(12), dp(12), dp(12), 0);
        activity.addContentView(handle, position);
        handle.setOnClickListener(view -> show());
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    public void show() {
        if (activity.isFinishing() || activity.isDestroyed() || dialog != null) return;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xff14151b);
        Button close = new Button(activity);
        close.setText("Закрыть Nemesis  ×");
        close.setTextColor(0xfff4f3fa);
        close.setBackgroundColor(0xff242630);
        close.setOnClickListener(view -> hide());
        controller = new NemesisWebController(activity, true, this::hide);
        layout.addView(close, new LinearLayout.LayoutParams(-1, dp(48)));
        layout.addView(controller.view(), new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(layout);
        dialog.setOnDismissListener(ignored -> {
            if (controller != null) { controller.destroy(); controller = null; }
            dialog = null;
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setDimAmount(0.15f);
        }
        controller.load(true);
    }
    public void hide() { if (dialog != null) dialog.dismiss(); }
    public void destroy() {
        hide();
        if (handle.getParent() instanceof ViewGroup) ((ViewGroup) handle.getParent()).removeView(handle);
    }
}
