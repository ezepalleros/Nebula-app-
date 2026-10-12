package com.appincreible.musicplayer.lyrics;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Editor de frases LRC: las marcas de tiempo son de solo lectura y se conservan al guardar. */
final class LyricsLineEditorDialog {
    private LyricsLineEditorDialog() { }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static String timeLabel(long ms) {
        long t = Math.max(0, ms);
        return String.format(Locale.US, "%02d:%02d.%02d", t / 60000, (t / 1000) % 60, (t % 1000) / 10);
    }

    static void show(Context context, @Nullable LrcLyrics original, Consumer<String> onSave) {
        boolean timed = original != null && original.synchronizedLines;
        List<LrcLyrics.Line> lines = original == null ? java.util.Collections.emptyList() : original.lines;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 4));

        TextView hint = new TextView(context);
        hint.setText(timed ? "Editá las frases. Los tiempos se conservan automáticamente." :
                "Escribí una frase por renglón.");
        hint.setTextSize(13);
        hint.setAlpha(0.8f);
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(false);
        scroller.setVerticalScrollBarEnabled(true);
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(rows, new ScrollView.LayoutParams(-1, -2));
        int available = context.getResources().getDisplayMetrics().heightPixels;
        root.addView(scroller, new LinearLayout.LayoutParams(-1, Math.min(dp(context, 470), available * 55 / 100)));

        List<EditText> editors = new ArrayList<>();
        if (timed) {
            for (LrcLyrics.Line line : lines) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, dp(context, 12), 0, dp(context, 8));

                TextView timestamp = new TextView(context);
                timestamp.setText(timeLabel(line.timeMs));
                timestamp.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                timestamp.setTextSize(12);
                timestamp.setContentDescription("Tiempo fijo " + timeLabel(line.timeMs));
                row.addView(timestamp, new LinearLayout.LayoutParams(-1, -2));

                EditText phrase = field(context, line.text, "Frase");
                phrase.setSingleLine(false);
                phrase.setMaxLines(3);
                phrase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                row.addView(phrase, new LinearLayout.LayoutParams(-1, -2));
                editors.add(phrase);
                rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
        } else {
            EditText plain = field(context, plainText(lines), "Escribí o pegá la letra");
            plain.setGravity(Gravity.TOP);
            plain.setMinLines(10);
            plain.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            rows.addView(plain, new LinearLayout.LayoutParams(-1, -2));
            editors.add(plain);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle("Editar letra")
                .setView(root)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    StringBuilder result = new StringBuilder();
                    if (timed) {
                        for (int i = 0; i < editors.size(); i++) {
                            String phrase = editors.get(i).getText().toString()
                                    .replace('\r', ' ').replace('\n', ' ').trim();
                            result.append('[').append(timeLabel(lines.get(i).timeMs))
                                    .append(']').append(phrase).append('\n');
                        }
                    } else {
                        result.append(editors.get(0).getText());
                    }
                    onSave.accept(result.toString());
                }).show();
    }

    private static EditText field(Context context, String value, String hint) {
        EditText edit = new EditText(context);
        edit.setText(value);
        edit.setHint(hint);
        edit.setTextSize(16);
        edit.setSelectAllOnFocus(false);
        return edit;
    }

    private static String plainText(List<LrcLyrics.Line> lines) {
        StringBuilder text = new StringBuilder();
        for (LrcLyrics.Line line : lines) text.append(line.text).append('\n');
        return text.toString();
    }
}
