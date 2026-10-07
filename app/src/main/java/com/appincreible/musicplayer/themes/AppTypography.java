package com.appincreible.musicplayer.themes;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for the user-selected app typography.
 *
 * It can be applied to a whole view tree and also installs a light RecyclerView hook so
 * recycled/new rows always receive the current family when they become visible.
 */
public final class AppTypography {

    private static final Map<String, Typeface> TYPEFACE_CACHE = new ConcurrentHashMap<>();
    private static final Map<RecyclerView, Boolean> INSTALLED_LISTS = new WeakHashMap<>();

    private AppTypography() { }

    @NonNull
    public static Typeface get(@NonNull Context context, int style) {
        return preview(context, AppPreferences.getPlayerTypography(context.getApplicationContext()), style);
    }

    /** Returns a typeface for previews without changing the selected app typography. */
    @NonNull
    public static Typeface preview(@NonNull Context context, @NonNull String typography, int style) {
        String family = familyFor(typography);
        String key = family + ':' + style;
        Typeface cached = TYPEFACE_CACHE.get(key);
        if (cached != null) return cached;
        Typeface created = Typeface.create(family, style);
        TYPEFACE_CACHE.put(key, created);
        return created;
    }

    public static void applyToViewTree(View root) {
        if (root == null) return;

        if (root instanceof TextView) {
            TextView textView = (TextView) root;
            Typeface current = textView.getTypeface();
            int style = current == null ? Typeface.NORMAL : current.getStyle();
            textView.setTypeface(get(textView.getContext(), style));
        }

        if (root instanceof RecyclerView) installRecyclerView((RecyclerView) root);

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToViewTree(group.getChildAt(i));
            }
        }
    }

    /** Use after KEY_PLAYER_TYPOGRAPHY changes. */
    public static void refresh(View root) {
        TYPEFACE_CACHE.clear();
        applyToViewTree(root);
        rebindRecyclerViews(root);
    }

    private static void installRecyclerView(RecyclerView recyclerView) {
        synchronized (INSTALLED_LISTS) {
            if (Boolean.TRUE.equals(INSTALLED_LISTS.get(recyclerView))) return;
            INSTALLED_LISTS.put(recyclerView, true);
        }
        recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(@NonNull View view) {
                applyToViewTree(view);
            }

            @Override public void onChildViewDetachedFromWindow(@NonNull View view) { }
        });
    }

    private static void rebindRecyclerViews(View root) {
        if (root == null) return;
        if (root instanceof RecyclerView) {
            RecyclerView.Adapter<?> adapter = ((RecyclerView) root).getAdapter();
            if (adapter != null) adapter.notifyDataSetChanged();
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                rebindRecyclerViews(group.getChildAt(i));
            }
        }
    }

    private static String familyFor(String typography) {
        if (AppPreferences.TYPOGRAPHY_ROUNDED.equals(typography)) return "sans-serif-rounded";
        if (AppPreferences.TYPOGRAPHY_SERIF.equals(typography)) return "serif";
        if (AppPreferences.TYPOGRAPHY_MONO.equals(typography)) return "monospace";
        if (AppPreferences.TYPOGRAPHY_CONDENSED.equals(typography)) return "sans-serif-condensed";
        return "sans-serif";
    }
}
