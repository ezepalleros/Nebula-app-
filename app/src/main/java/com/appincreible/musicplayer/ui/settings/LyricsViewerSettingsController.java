package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.google.android.material.card.MaterialCardView;
import android.view.Gravity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Preferencias visuales del visor; se mantienen fuera de SettingsFragment. */
public final class LyricsViewerSettingsController {
    private LyricsViewerSettingsController() { }
    public static void attach(Context c, View header) {
        SharedPreferences p = AppPreferences.prefs(c);
        LinearLayout parent = (LinearLayout) header.getParent();
        MaterialCardView card = new MaterialCardView(c);
        card.setRadius(dp(c, 18));
        card.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(card,
                com.google.android.material.R.attr.colorSurfaceContainerLow));
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(c,18),dp(c,16),dp(c,18),dp(c,16));
        TextView title = new TextView(c);
        title.setText("VISUALIZACIÓN");
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        body.addView(title);
        String[] modes={"Mostrar portada", "Solo frase actual (karaoke)", "Letra completa con resaltado"};
        choice(c,body,"Qué mostrar al tocar la portada", "Qué aparece mientras se reproduce.",
                modes,new String[]{"cover","single","full"},p,"lyrics_view_mode","full");
        choice(c,body,"Ocupar pantalla completa", "Solo letras, lápiz y cerrar; oculta el skin y los controles.",
                new String[]{"Desactivado", "Activado"},new int[]{0,1},p,"lyrics_view_fullscreen",0);
        choice(c,body,"Tamaño de letra", "Tamaño de las frases.",
                new String[]{"Chico", "Mediano", "Grande", "Enorme"},new int[]{17,22,27,33},p,"lyrics_view_font",22);
        choice(c,body,"Color de la línea activa", "Destaca la frase que está sonando.",
                new String[]{"Blanco", "Dorado", "Violeta", "Turquesa"},new int[]{0,1,2,3},p,"lyrics_view_color",0);
        choice(c,body,"Atenuación de las otras frases", "Visibilidad de las frases anteriores y siguientes.",
                new String[]{"Suave", "Media", "Fuerte"},new int[]{40,55,65},p,"lyrics_view_dim",65);
        choice(c,body,"Alineación", "Posición de las frases.",
                new String[]{"Centrada", "Izquierda"},new int[]{1,0},p,"lyrics_view_center",true);
        choice(c,body,"Tipografía", "Fuente usada para mostrar la letra.",
                new String[]{"Predeterminada", "Serif", "Monoespaciada", "Ligera"},
                new String[]{"default","serif","mono","light"},p,"lyrics_view_typeface","default");
        choice(c,body,"Fondo del visor", "Elegí el fondo detrás de las letras.",
                new String[]{"Portada atenuada", "Fondo liso", "Transparente (sin fondo)"},
                new String[]{"artwork","solid","transparent"},p,"lyrics_view_background","artwork");
        card.addView(body);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(dp(c,14),dp(c,12),dp(c,14),dp(c,12));
        parent.addView(card,1,lp);
    }
    private interface Selection { int selected(); void save(int which); }

    private static void addChoice(Context c, LinearLayout body, String label, String desc,
                                  String[] items, Selection selection) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c,6),dp(c,14),dp(c,6),dp(c,14));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(c);
        heading.setText(label);
        heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        words.addView(heading);
        TextView hint = new TextView(c);
        hint.setText(desc);
        hint.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        words.addView(hint);
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView value = new TextView(c);
        value.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setMaxWidth(dp(c,132));
        value.setPadding(dp(c,10),0,0,0);
        value.setTextColor(com.google.android.material.color.MaterialColors.getColor(value,
                com.google.android.material.R.attr.colorPrimary));
        row.addView(value);
        Runnable refresh=()->value.setText(items[Math.max(0,Math.min(items.length-1,selection.selected()))]+"  ›");
        refresh.run();
        row.setClickable(true);
        row.setFocusable(true);
        android.util.TypedValue typed = new android.util.TypedValue();
        if(c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,typed,true))
            row.setBackgroundResource(typed.resourceId);
        row.setOnClickListener(v->new MaterialAlertDialogBuilder(c).setTitle(label)
                .setSingleChoiceItems(items,selection.selected(),(dialog,which)->{
                    selection.save(which);refresh.run();dialog.dismiss();
                }).setNegativeButton("Cancelar",null).show());
        body.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private static void choice(Context c, LinearLayout body, String label, String desc, String[] items,
            int[] values, SharedPreferences p, String key, int defaultValue) {
        addChoice(c,body,label,desc,items,new Selection(){
            public int selected(){return index(values,p.getInt(key,defaultValue));}
            public void save(int which){p.edit().putInt(key,values[which]).apply();}
        });
    }
    private static void choice(Context c, LinearLayout body, String label, String desc, String[] items,
            int[] values, SharedPreferences p, String key, boolean defaultValue) {
        addChoice(c,body,label,desc,items,new Selection(){
            public int selected(){return p.getBoolean(key,defaultValue)?0:1;}
            public void save(int which){p.edit().putBoolean(key,which==0).apply();}
        });
    }
    private static void choice(Context c, LinearLayout body, String label, String desc, String[] items,
            String[] keys, SharedPreferences p, String key, String fallback) {
        addChoice(c,body,label,desc,items,new Selection(){
            public int selected(){return index(keys,p.getString(key,fallback));}
            public void save(int which){p.edit().putString(key,keys[which]).apply();}
        });
    }
    private static int index(int[] values,int chosen){for(int i=0;i<values.length;i++)if(values[i]==chosen)return i;return 0;}
    private static int index(String[] values,String chosen){for(int i=0;i<values.length;i++)if(values[i].equals(chosen))return i;return 0;}
    private static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
}
