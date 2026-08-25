package it.nexus.friendstickers;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private static final int PICK_PACK = 101;
    private TextView status;
    private Button addButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Friend Reactions 😂");
        title.setTextSize(30f);
        title.setTextColor(Color.BLACK);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Importa il pacchetto privato e aggiungilo a WhatsApp.");
        subtitle.setTextSize(17f);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, dp(10), 0, dp(22));
        root.addView(subtitle, matchWrap());

        Button importButton = new Button(this);
        importButton.setText("1. Importa pacchetto ZIP");
        importButton.setOnClickListener(v -> choosePack());
        root.addView(importButton, matchWrap());

        addButton = new Button(this);
        addButton.setText("2. Aggiungi a WhatsApp");
        addButton.setOnClickListener(v -> addToWhatsApp());
        root.addView(addButton, matchWrap());

        status = new TextView(this);
        status.setTextSize(16f);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(0, dp(20), 0, 0);
        root.addView(status, matchWrap());

        refreshStatus();
        setContentView(root);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void choosePack() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream"});
        try {
            startActivityForResult(intent, PICK_PACK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Selettore file non disponibile", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PACK || resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        status.setText("Importazione in corso…");
        addButton.setEnabled(false);

        new Thread(() -> {
            try {
                List<StickerPack> packs = PackStore.importZip(this, uri);
                getContentResolver().notifyChange(
                        Uri.parse("content://" + BuildConfig.CONTENT_PROVIDER_AUTHORITY + "/metadata"),
                        null
                );
                runOnUiThread(() -> {
                    StickerPack first = packs.get(0);
                    status.setText("✅ Importato: " + first.name + " — " + first.stickers.size() + " sticker");
                    addButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("❌ Pacchetto non valido: " + e.getMessage());
                    addButton.setEnabled(PackStore.hasActivePack(this));
                });
            }
        }).start();
    }

    private void refreshStatus() {
        try {
            if (!PackStore.hasActivePack(this)) {
                status.setText("Nessun pacchetto importato.");
                addButton.setEnabled(false);
                return;
            }
            List<StickerPack> packs = PackStore.loadActive(this);
            StickerPack first = packs.get(0);
            status.setText("✅ Pronto: " + first.name + " — " + first.stickers.size() + " sticker");
            addButton.setEnabled(true);
        } catch (Exception e) {
            status.setText("Pacchetto da reimportare.");
            addButton.setEnabled(false);
        }
    }

    private void addToWhatsApp() {
        try {
            List<StickerPack> packs = PackStore.loadActive(this);
            StickerPack pack = packs.get(0);
            Intent intent = new Intent();
            intent.setAction("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
            intent.putExtra("sticker_pack_id", pack.identifier);
            intent.putExtra("sticker_pack_authority", BuildConfig.CONTENT_PROVIDER_AUTHORITY);
            intent.putExtra("sticker_pack_name", pack.name);
            startActivityForResult(intent, 200);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "WhatsApp non trovato o non compatibile", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Reimporta il pacchetto: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
