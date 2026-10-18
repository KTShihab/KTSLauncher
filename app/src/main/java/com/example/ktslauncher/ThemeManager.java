package com.example.ktslauncher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThemeManager {

    private static final String PREF_THEME_ID = "theme_preset_id";
    private static final String PREF_CUSTOM_BG = "theme_custom_bg";
    private static final String PREF_CUSTOM_CELL = "theme_custom_cell";
    private static final String PREF_CUSTOM_DOCK = "theme_custom_dock";
    private static final String PREF_CUSTOM_ACCENT = "theme_custom_accent";
    private static final String PREF_IS_CUSTOM = "theme_is_custom";

    public static class ThemePreset {
        public final String id;
        public final String nameEn;
        public final String nameMl;
        public final int bgColor;
        public final int cellColor;
        public final int cellStrokeColor;
        public final int dockColor;
        public final int accentColor;
        public final int textColor;
        public final int dockTextColor;
        public final int topBarBtnColor;
        public final int topBarBtnTextColor;
        public final int drawerBgColor;
        public final int drawerSearchBgColor;
        public final int drawerSearchTextColor;
        public final int drawerItemTextColor;
        public final boolean isLight;

        public ThemePreset(String id, String nameEn, String nameMl,
                           int bgColor, int cellColor, int cellStrokeColor,
                           int dockColor, int accentColor, int textColor, int dockTextColor,
                           int topBarBtnColor, int topBarBtnTextColor,
                           int drawerBgColor, int drawerSearchBgColor,
                           int drawerSearchTextColor, int drawerItemTextColor,
                           boolean isLight) {
            this.id = id;
            this.nameEn = nameEn;
            this.nameMl = nameMl;
            this.bgColor = bgColor;
            this.cellColor = cellColor;
            this.cellStrokeColor = cellStrokeColor;
            this.dockColor = dockColor;
            this.accentColor = accentColor;
            this.textColor = textColor;
            this.dockTextColor = dockTextColor;
            this.topBarBtnColor = topBarBtnColor;
            this.topBarBtnTextColor = topBarBtnTextColor;
            this.drawerBgColor = drawerBgColor;
            this.drawerSearchBgColor = drawerSearchBgColor;
            this.drawerSearchTextColor = drawerSearchTextColor;
            this.drawerItemTextColor = drawerItemTextColor;
            this.isLight = isLight;
        }
    }

    private static final List<ThemePreset> PRESETS = new ArrayList<>();

    static {
        // 1. Classic Dark
        PRESETS.add(new ThemePreset(
                "classic_dark", "Classic Dark", "ക്ലാസിക് ഡാർക്ക്",
                0xFF121212, 0xFF2C2C35, 0x00000000,
                0xFF5E35B1, 0xFF5E35B1, 0xFFFFFFFF, 0xFFFFFFFF,
                0xFF333333, 0xFFFFFFFF,
                0xFF16161E, 0xFF23232C, 0xFFFFFFFF, 0xFFFFFFFF,
                false
        ));

        // 2. Modern Bright (Clean Light)
        PRESETS.add(new ThemePreset(
                "modern_bright", "Modern Bright", "മോഡേൺ ലൈറ്റ്",
                0xFFF4F5F9, 0xFFFFFFFF, 0xFFE2E8F0,
                0xFF4F46E5, 0xFF4F46E5, 0xFF1E293B, 0xFFFFFFFF,
                0xFFE2E8F0, 0xFF1E293B,
                0xFFF8FAFC, 0xFFFFFFFF, 0xFF1E293B, 0xFF1E293B,
                true
        ));

        // 3. Ocean Blue
        PRESETS.add(new ThemePreset(
                "ocean_blue", "Ocean Blue", "ഓഷ്യൻ ബ്ലൂ",
                0xFF0A192F, 0xFF172A45, 0x3364FFDA,
                0xFF0077B6, 0xFF00B4D8, 0xFFE6F1FF, 0xFFFFFFFF,
                0xFF172A45, 0xFFE6F1FF,
                0xFF0D1B2A, 0xFF1B263B, 0xFFE6F1FF, 0xFFE6F1FF,
                false
        ));

        // 4. Pure Daylight
        PRESETS.add(new ThemePreset(
                "pure_daylight", "Pure Daylight", "പൂർണ്ണ പ്രഭാതം",
                0xFFFFFFFF, 0xFFF1F5F9, 0xFFCBD5E1,
                0xFF0284C7, 0xFF0284C7, 0xFF0F172A, 0xFFFFFFFF,
                0xFFE2E8F0, 0xFF0F172A,
                0xFFFFFFFF, 0xFFF1F5F9, 0xFF0F172A, 0xFF0F172A,
                true
        ));

        // 5. Sunset Glow
        PRESETS.add(new ThemePreset(
                "sunset_glow", "Sunset Glow", "സൺസെറ്റ് ഗോൾഡ്",
                0xFF1A1218, 0xFF2D1B24, 0x44FF8A65,
                0xFFE65100, 0xFFFF9800, 0xFFFFF3E0, 0xFFFFFFFF,
                0xFF2D1B24, 0xFFFFF3E0,
                0xFF1E141C, 0xFF33202C, 0xFFFFF3E0, 0xFFFFF3E0,
                false
        ));

        // 6. Emerald Forest
        PRESETS.add(new ThemePreset(
                "emerald_forest", "Emerald Forest", "എമറാൾഡ് ഫോറസ്റ്റ്",
                0xFF091811, 0xFF132B1F, 0x3381C784,
                0xFF1B5E20, 0xFF2E7D32, 0xFFE8F5E9, 0xFFFFFFFF,
                0xFF132B1F, 0xFFE8F5E9,
                0xFF0C1F16, 0xFF193829, 0xFFE8F5E9, 0xFFE8F5E9,
                false
        ));

        // 7. Pastel Lavender
        PRESETS.add(new ThemePreset(
                "pastel_lavender", "Pastel Lavender", "പാസ്റ്റൽ ലാവെൻഡർ",
                0xFFF5F3FF, 0xFFFFFFFF, 0xFFDDD6FE,
                0xFF7C3AED, 0xFF8B5CF6, 0xFF2E1065, 0xFFFFFFFF,
                0xFFEDE9FE, 0xFF2E1065,
                0xFFF5F3FF, 0xFFFFFFFF, 0xFF2E1065, 0xFF2E1065,
                true
        ));

        // 8. High Contrast Yellow (Accessibility)
        PRESETS.add(new ThemePreset(
                "high_contrast_yellow", "High Contrast Yellow", "ഹൈ കോൺട്രാസ്റ്റ് (മഞ്ഞ & കറുപ്പ്)",
                0xFF000000, 0xFF181818, 0xFFFFD600,
                0xFFFFD600, 0xFFFFD600, 0xFFFFD600, 0xFF000000,
                0xFF222222, 0xFFFFD600,
                0xFF000000, 0xFF181818, 0xFFFFD600, 0xFFFFD600,
                false
        ));

        // 9. High Contrast Blue (Accessibility Light)
        PRESETS.add(new ThemePreset(
                "high_contrast_blue", "High Contrast Blue", "ഹൈ കോൺട്രാസ്റ്റ് (നീല & വെളുപ്പ്)",
                0xFFFFFFFF, 0xFFFFFFFF, 0xFF002171,
                0xFF002171, 0xFF002171, 0xFF000000, 0xFFFFFFFF,
                0xFFE8EAF6, 0xFF002171,
                0xFFFFFFFF, 0xFFE8EAF6, 0xFF000000, 0xFF000000,
                true
        ));

        // 10. AMOLED Pitch Black
        PRESETS.add(new ThemePreset(
                "amoled_black", "AMOLED Pitch Black", "അമോലെഡ് പിച്ച് ബ്ലാക്ക്",
                0xFF000000, 0xFF111111, 0xFF27272A,
                0xFF6D28D9, 0xFF8B5CF6, 0xFFFFFFFF, 0xFFFFFFFF,
                0xFF18181B, 0xFFFFFFFF,
                0xFF000000, 0xFF111111, 0xFFFFFFFF, 0xFFFFFFFF,
                false
        ));
    }

    public static List<ThemePreset> getPresets() {
        return Collections.unmodifiableList(PRESETS);
    }

    public static ThemePreset getPresetById(String id) {
        for (ThemePreset p : PRESETS) {
            if (p.id.equalsIgnoreCase(id)) return p;
        }
        return PRESETS.get(0); // Default classic dark
    }

    public static boolean isColorLight(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return darkness < 0.45;
    }

    public static int getContrastingTextColor(int backgroundColor) {
        return isColorLight(backgroundColor) ? 0xFF0F172A : 0xFFFFFFFF;
    }

    // Active theme values (considering custom overrides)
    public static class ActiveTheme {
        public final String presetId;
        public final boolean isCustom;
        public final int bgColor;
        public final int cellColor;
        public final int cellStrokeColor;
        public final int dockColor;
        public final int accentColor;
        public final int textColor;
        public final int dockTextColor;
        public final int topBarBtnColor;
        public final int topBarBtnTextColor;
        public final int drawerBgColor;
        public final int drawerSearchBgColor;
        public final int drawerSearchTextColor;
        public final int drawerItemTextColor;
        public final boolean isLight;

        public ActiveTheme(String presetId, boolean isCustom,
                           int bgColor, int cellColor, int cellStrokeColor,
                           int dockColor, int accentColor, int textColor, int dockTextColor,
                           int topBarBtnColor, int topBarBtnTextColor,
                           int drawerBgColor, int drawerSearchBgColor,
                           int drawerSearchTextColor, int drawerItemTextColor,
                           boolean isLight) {
            this.presetId = presetId;
            this.isCustom = isCustom;
            this.bgColor = bgColor;
            this.cellColor = cellColor;
            this.cellStrokeColor = cellStrokeColor;
            this.dockColor = dockColor;
            this.accentColor = accentColor;
            this.textColor = textColor;
            this.dockTextColor = dockTextColor;
            this.topBarBtnColor = topBarBtnColor;
            this.topBarBtnTextColor = topBarBtnTextColor;
            this.drawerBgColor = drawerBgColor;
            this.drawerSearchBgColor = drawerSearchBgColor;
            this.drawerSearchTextColor = drawerSearchTextColor;
            this.drawerItemTextColor = drawerItemTextColor;
            this.isLight = isLight;
        }
    }

    public static ActiveTheme getActiveTheme(SharedPreferences prefs) {
        String presetId = prefs.getString(PREF_THEME_ID, "classic_dark");
        ThemePreset base = getPresetById(presetId);
        boolean isCustom = prefs.getBoolean(PREF_IS_CUSTOM, false);

        if (!isCustom) {
            return new ActiveTheme(
                    base.id, false,
                    base.bgColor, base.cellColor, base.cellStrokeColor,
                    base.dockColor, base.accentColor, base.textColor, base.dockTextColor,
                    base.topBarBtnColor, base.topBarBtnTextColor,
                    base.drawerBgColor, base.drawerSearchBgColor,
                    base.drawerSearchTextColor, base.drawerItemTextColor,
                    base.isLight
            );
        }

        int bg = prefs.getInt(PREF_CUSTOM_BG, base.bgColor);
        int cell = prefs.getInt(PREF_CUSTOM_CELL, base.cellColor);
        int dock = prefs.getInt(PREF_CUSTOM_DOCK, base.dockColor);
        int accent = prefs.getInt(PREF_CUSTOM_ACCENT, base.accentColor);

        boolean light = isColorLight(bg);
        int text = getContrastingTextColor(cell);
        int dockText = getContrastingTextColor(dock);
        int topBarBtn = light ? 0xFFE2E8F0 : 0xFF2C2C35;
        int topBarBtnText = getContrastingTextColor(topBarBtn);
        int drawerBg = bg;
        int drawerSearchBg = cell;
        int drawerSearchText = text;
        int drawerItemText = getContrastingTextColor(drawerBg);
        int cellStroke = light ? 0xFFCBD5E1 : (base.cellStrokeColor != 0 ? base.cellStrokeColor : 0x00000000);

        return new ActiveTheme(
                presetId, true,
                bg, cell, cellStroke,
                dock, accent, text, dockText,
                topBarBtn, topBarBtnText,
                drawerBg, drawerSearchBg,
                drawerSearchText, drawerItemText,
                light
        );
    }

    public static void setPreset(SharedPreferences prefs, String presetId) {
        prefs.edit()
                .putString(PREF_THEME_ID, presetId)
                .putBoolean(PREF_IS_CUSTOM, false)
                .apply();
    }

    public static void setCustomBgColor(SharedPreferences prefs, int color) {
        prefs.edit()
                .putInt(PREF_CUSTOM_BG, color)
                .putBoolean(PREF_IS_CUSTOM, true)
                .apply();
    }

    public static void setCustomCellColor(SharedPreferences prefs, int color) {
        prefs.edit()
                .putInt(PREF_CUSTOM_CELL, color)
                .putBoolean(PREF_IS_CUSTOM, true)
                .apply();
    }

    public static void setCustomDockColor(SharedPreferences prefs, int color) {
        prefs.edit()
                .putInt(PREF_CUSTOM_DOCK, color)
                .putInt(PREF_CUSTOM_ACCENT, color)
                .putBoolean(PREF_IS_CUSTOM, true)
                .apply();
    }

    public static void resetToPreset(SharedPreferences prefs) {
        prefs.edit()
                .putBoolean(PREF_IS_CUSTOM, false)
                .remove(PREF_CUSTOM_BG)
                .remove(PREF_CUSTOM_CELL)
                .remove(PREF_CUSTOM_DOCK)
                .remove(PREF_CUSTOM_ACCENT)
                .apply();
    }

    public static void applySystemBarColors(Activity activity, int color, boolean isLight) {
        if (activity == null) return;
        applySystemBarColors(activity.getWindow(), color, isLight);
    }

    public static void applySystemBarColors(Window window, int color, boolean isLight) {
        if (window == null) return;
        window.setStatusBarColor(color);
        window.setNavigationBarColor(color);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(isLight);
            controller.setAppearanceLightNavigationBars(isLight);
        }
    }
}
