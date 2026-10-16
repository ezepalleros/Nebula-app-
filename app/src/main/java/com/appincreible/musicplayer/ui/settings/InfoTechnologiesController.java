package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.appincreible.musicplayer.themes.ListAppearance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Créditos de integraciones verificadas en el código: no se cargan servicios externos al mostrar la pantalla. */
final class InfoTechnologiesController {

    private static final class Credit {
        final String name;
        final String description;
        final String url;

        Credit(String name, String description, String url) {
            this.name = name;
            this.description = description;
            this.url = url;
        }
    }

    // Nombres y usos comprobados en los clientes, repositorios y dependencias de Nebula.
    private static final Credit[] SERVICES = {
            new Credit("LRCLIB", "Búsqueda y descarga de letras de canciones.", "https://lrclib.net"),
            new Credit("Deezer", "Búsqueda de portadas desde el editor de canciones.", "https://www.deezer.com"),
            new Credit("iTunes Search API", "Búsqueda alternativa de portadas de canciones.", "https://www.apple.com/itunes/"),
            new Credit("Radio Browser", "Directorio de estaciones de radio por Internet.", "https://www.radio-browser.info"),
            new Credit("GitHub Releases", "Comprobación de actualizaciones de Nebula.", "https://github.com/ezepalleros/Nebula-app-/releases")
    };

    private static final Credit[] LIBRARIES = {
            new Credit("AndroidX Media3 · ExoPlayer", "Reproducción de música y transmisiones.", "https://developer.android.com/media/media3"),
            new Credit("Glide", "Carga y caché de portadas e imágenes.", "https://github.com/bumptech/glide"),
            new Credit("jaudiotagger", "Lectura y escritura de metadatos de audio.", "https://github.com/ijabz/jaudiotagger"),
            new Credit("Retrofit · OkHttp · Gson", "Conexiones con servicios y procesamiento de respuestas.", "https://square.github.io/retrofit/"),
            new Credit("Room · WorkManager", "Datos locales y tareas en segundo plano.", "https://developer.android.com/jetpack/androidx/releases/room"),
            new Credit("DejaVu Sans Condensed", "Fuente libre incluida para el estilo Teléfono clásico.", "https://dejavu-fonts.github.io/")
    };

    private final Context context;
    private final LinearLayout container;
    private final List<TextView> headings = new ArrayList<>();
    private final List<TextView> names = new ArrayList<>();
    private final List<TextView> descriptions = new ArrayList<>();
    private final Consumer<String> openLink;

    InfoTechnologiesController(@NonNull LinearLayout container, @NonNull Consumer<String> openLink) {
        this.context = container.getContext();
        this.container = container;
        this.openLink = openLink;
        section("SERVICIOS EN LÍNEA", SERVICES);
        section("BIBLIOTECAS Y COMPONENTES", LIBRARIES);
        TextView note = text("Servicios y bibliotecas de terceros; su inclusión no implica afiliación.", 12, false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(12);
        container.addView(note, noteParams);
        descriptions.add(note);
    }

    private void section(String label, Credit[] credits) {
        TextView heading = text(label, 12, true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(headings.isEmpty() ? 4 : 20);
        headingParams.bottomMargin = dp(4);
        container.addView(heading, headingParams);
        headings.add(heading);

        for (Credit credit : credits) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(9), 0, dp(9));

            TextView name = text(credit.name + "  ↗", 15, true);
            row.addView(name, new LinearLayout.LayoutParams(-1, -2));
            names.add(name);

            TextView description = text(credit.description, 13, false);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
            descParams.topMargin = dp(3);
            row.addView(description, descParams);
            descriptions.add(description);

            row.setClickable(true);
            row.setFocusable(true);
            TypedValue out = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                    && out.resourceId != 0) {
                row.setBackgroundResource(out.resourceId);
            }
            row.setOnClickListener(v -> openLink.accept(credit.url));
            row.setContentDescription(credit.name + ". " + credit.description + " Abrir sitio web.");
            container.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    void applyAppearance(@NonNull ListAppearance.Appearance appearance) {
        for (TextView heading : headings) heading.setTextColor(appearance.secondaryText);
        for (TextView name : names) name.setTextColor(appearance.primaryText);
        for (TextView description : descriptions) description.setTextColor(appearance.secondaryText);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private int dp(int amount) {
        return Math.round(amount * context.getResources().getDisplayMetrics().density);
    }
}
