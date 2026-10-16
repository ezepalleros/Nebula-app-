package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.navigation.Navigation;

import com.appincreible.musicplayer.BuildConfig;
import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.themes.ListAppearance;
import com.google.android.material.card.MaterialCardView;

/** Static entry for the Information screen, kept outside SettingsFragment's category builder. */
public final class SettingsInfoEntryView extends FrameLayout implements ListAppearance.Listener {

    private MaterialCardView card;
    private ImageView icon;
    private TextView title;
    private TextView summary;
    private TextView arrow;

    public SettingsInfoEntryView(@NonNull Context context) {
        super(context);
        init();
    }

    public SettingsInfoEntryView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SettingsInfoEntryView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.item_settings_category, this, true);
        card = findViewById(R.id.categoryCard);
        icon = findViewById(R.id.categoryIcon);
        title = findViewById(R.id.categoryTitle);
        summary = findViewById(R.id.categorySummary);
        arrow = findViewById(R.id.categoryArrow);

        icon.setImageResource(R.drawable.ic_info);
        title.setText("Información");
        summary.setText("Versión " + BuildConfig.VERSION_NAME + " · GitHub · Donaciones");
        card.setOnClickListener(v -> Navigation.findNavController(this).navigate(R.id.settingsInfoFragment));
        applyAppearance();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ListAppearance.addListener(this);
        applyAppearance();
    }

    @Override
    protected void onDetachedFromWindow() {
        ListAppearance.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onListAppearanceChanged() {
        applyAppearance();
    }

    private void applyAppearance() {
        if (card == null) return;
        ListAppearance.Appearance appearance = ListAppearance.resolve(getContext());
        // Información uses its own ninth accent (white). Keep it visible in light mode.
        int accent = ContextCompat.getColor(getContext(), R.color.settings_category_information);
        if (ColorUtils.calculateContrast(accent, appearance.effectiveSurface) < 3.0d) {
            accent = 0xFF45506A;
        }
        card.setCardBackgroundColor(appearance.containerColor);
        card.setStrokeColor(ColorUtils.setAlphaComponent(accent, appearance.transparent ? 210 : 120));
        icon.setImageTintList(ColorStateList.valueOf(accent));
        icon.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 36)));
        title.setTextColor(appearance.primaryText);
        summary.setTextColor(appearance.secondaryText);
        arrow.setTextColor(accent);
    }
}
