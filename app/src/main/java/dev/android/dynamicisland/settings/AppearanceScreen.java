package dev.android.dynamicisland.settings;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import dev.android.dynamicisland.R;
import dev.android.dynamicisland.island.graphics.Colors;
import java.util.Locale;

/** Presets for quick choices, a real card preview, and a cancellable custom-colour editor. */
final class AppearanceScreen {
    private final SettingsActivity activity;
    private final OneUi ui;
    private final SharedPreferences prefs;

    AppearanceScreen(SettingsActivity activity, OneUi ui, SharedPreferences prefs) {
        this.activity = activity;
        this.ui = ui;
        this.prefs = prefs;
    }

    private int selected() { return prefs.getInt(ModuleSettings.KEY_ACCENT_COLOR, ModuleSettings.DEFAULT_ACCENT); }

    void open() {
        LinearLayout page = activity.page();
        activity.subPage(activity.getString(R.string.accent_page));
        LinearLayout mode = ui.card(page);
        boolean enabled = prefs.getBoolean(ModuleSettings.KEY_ACCENT_CUSTOM, false);
        ui.switchRow(mode, activity.getString(R.string.accent_enabled), activity.getString(R.string.accent_enabled_desc), enabled, on -> {
            prefs.edit().putBoolean(ModuleSettings.KEY_ACCENT_CUSTOM, on).apply();
            activity.refresh();
        });
        ui.category(page, activity.getString(R.string.accent_presets));
        LinearLayout choices = ui.card(page);
        LinearLayout swatches = new LinearLayout(activity);
        swatches.setGravity(Gravity.CENTER);
        swatches.setPadding(ui.dp(8), ui.dp(14), ui.dp(8), ui.dp(14));
        choices.addView(swatches);
        int[] colours = {0xFF3E91FF, 0xFF34C759, 0xFFFF9F0A, 0xFFFF6482, 0xFFBF75FF, 0xFF48C9BB};
        int[] names = {R.string.accent_blue, R.string.accent_green, R.string.accent_orange,
                R.string.accent_pink, R.string.accent_purple, R.string.accent_teal};
        for (int i = 0; i < colours.length; i++) {
            int colour = colours[i];
            TextView swatch = ui.text(selected() == colour ? "✓" : "", 22, Color.BLACK);
            swatch.setGravity(Gravity.CENTER);
            swatch.setContentDescription(activity.getString(names[i]));
            swatch.setBackground(ui.round(colour, ui.dp(24)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(44), 1);
            lp.setMargins(ui.dp(3), 0, ui.dp(3), 0);
            swatches.addView(swatch, lp);
            swatch.setOnClickListener(v -> save(colour));
        }
        ui.valueRow(choices, activity.getString(R.string.accent_custom), hex(selected()), this::edit);
        LinearLayout example = ui.card(page);
        example.addView(new Preview(enabled ? selected() : ModuleSettings.DEFAULT_ACCENT), new LinearLayout.LayoutParams(-1, ui.dp(164)));
        ui.note(page, activity.getString(R.string.accent_note));
        LinearLayout reset = ui.card(page);
        ui.actionRow(reset, activity.getString(R.string.accent_reset), activity.getString(R.string.accent_reset_desc), () -> {
            prefs.edit().putBoolean(ModuleSettings.KEY_ACCENT_CUSTOM, false)
                    .remove(ModuleSettings.KEY_ACCENT_COLOR).apply();
            activity.refresh();
        });
    }

    private void save(int colour) {
        prefs.edit().putInt(ModuleSettings.KEY_ACCENT_COLOR, colour).putBoolean(ModuleSettings.KEY_ACCENT_CUSTOM, true).apply();
        activity.refresh();
    }

    static Integer parse(String text) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}")) return null;
        return (int) (Long.parseLong(value, 16) | 0xFF000000L);
    }

    private static String hex(int colour) { return String.format(Locale.ROOT, "#%06X", colour & 0xFFFFFF); }

    private void edit() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(8));
        Preview preview = new Preview(selected());
        body.addView(preview, new LinearLayout.LayoutParams(-1, ui.dp(140)));
        EditText hex = new EditText(activity);
        hex.setSingleLine(true);
        hex.setHint(activity.getString(R.string.accent_hex));
        hex.setContentDescription(activity.getString(R.string.accent_hex));
        hex.setText(hex(selected()));
        body.addView(hex);
        float[] hsv = new float[3];
        Color.colorToHSV(selected(), hsv);
        SeekBar[] sliders = new SeekBar[3];
        int[] labels = {R.string.accent_hue, R.string.accent_saturation, R.string.accent_brightness};
        boolean[] syncing = {false};
        for (int i = 0; i < 3; i++) {
            int axis = i;
            body.addView(ui.text(activity.getString(labels[i]), 15, ui.textColor));
            SeekBar slider = new SeekBar(activity);
            sliders[i] = slider;
            slider.setContentDescription(activity.getString(labels[i]));
            slider.setMax(i == 0 ? 359 : 100);
            slider.setProgress(Math.round(i == 0 ? hsv[i] : hsv[i] * 100));
            body.addView(slider, new LinearLayout.LayoutParams(-1, ui.dp(40)));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                    if (!fromUser || syncing[0]) return;
                    hsv[axis] = axis == 0 ? value : value / 100f;
                    int colour = Color.HSVToColor(hsv);
                    syncing[0] = true;
                    hex.setText(hex(colour));
                    hex.setError(null);
                    syncing[0] = false;
                    preview.colour = colour;
                    preview.invalidate();
                }
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
        }
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (syncing[0]) return;
                Integer colour = parse(s.toString());
                if (colour == null) return;
                Color.colorToHSV(colour, hsv);
                syncing[0] = true;
                for (int i = 0; i < 3; i++) sliders[i].setProgress(Math.round(i == 0 ? hsv[i] : hsv[i] * 100));
                syncing[0] = false;
                preview.colour = colour;
                preview.invalidate();
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.accent_custom).setView(scroll)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Integer colour = parse(hex.getText().toString());
            if (colour == null) { hex.setError(activity.getString(R.string.accent_invalid)); return; }
            save(colour);
            dialog.dismiss();
        });
    }

    private final class Preview extends View {
        int colour;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();
        Preview(int colour) {
            super(activity);
            this.colour = colour;
            setContentDescription(activity.getString(R.string.accent_preview_title));
        }
        @Override protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density;
            canvas.save();
            canvas.scale(d, d);
            float w = getWidth() / d;
            paint.setColor(Colors.CARD_SOLID);
            rect.set(12, 8, w - 12, getHeight() / d - 8);
            canvas.drawRoundRect(rect, 24, 24, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(16);
            canvas.drawText(activity.getString(R.string.accent_preview_title) + " · 65%", 28, 40, paint);
            paint.setColor(Colors.TRACK);
            rect.set(28, 58, w - 28, 64);
            canvas.drawRoundRect(rect, 3, 3, paint);
            int visible = Colors.readableOnBlack(colour);
            paint.setColor(visible);
            rect.right = 28 + (w - 56) * 0.65f;
            canvas.drawRoundRect(rect, 3, 3, paint);
            paint.setColor(Colors.withAlpha(visible, 0.18f));
            rect.set(28, 82, w - 28, 118);
            canvas.drawRoundRect(rect, 18, 18, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(14);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(activity.getString(R.string.accent_preview_button), w / 2, 105, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.restore();
        }
    }
}
