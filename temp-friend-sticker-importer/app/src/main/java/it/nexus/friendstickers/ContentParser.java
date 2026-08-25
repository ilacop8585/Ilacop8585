package it.nexus.friendstickers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ContentParser {
    private ContentParser() {}

    static List<StickerPack> load(File root) throws Exception {
        File jsonFile = new File(root, "contents.json");
        if (!jsonFile.isFile()) {
            throw new IllegalStateException("contents.json non trovato");
        }
        String json;
        try (InputStream input = new FileInputStream(jsonFile);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            json = new String(output.toByteArray(), StandardCharsets.UTF_8);
        }

        JSONObject rootJson = new JSONObject(json);
        JSONArray packsJson = rootJson.getJSONArray("sticker_packs");
        List<StickerPack> packs = new ArrayList<>();

        for (int i = 0; i < packsJson.length(); i++) {
            JSONObject packJson = packsJson.getJSONObject(i);
            JSONArray stickersJson = packJson.getJSONArray("stickers");
            List<Sticker> stickers = new ArrayList<>();

            for (int j = 0; j < stickersJson.length(); j++) {
                JSONObject stickerJson = stickersJson.getJSONObject(j);
                JSONArray emojiJson = stickerJson.optJSONArray("emojis");
                List<String> emojis = new ArrayList<>();
                if (emojiJson != null) {
                    for (int k = 0; k < emojiJson.length(); k++) {
                        emojis.add(emojiJson.getString(k));
                    }
                }
                stickers.add(new Sticker(
                        stickerJson.getString("image_file"),
                        emojis,
                        stickerJson.optString("accessibility_text", "")
                ));
            }

            packs.add(new StickerPack(
                    packJson.getString("identifier"),
                    packJson.getString("name"),
                    packJson.getString("publisher"),
                    packJson.getString("tray_image_file"),
                    packJson.optString("image_data_version", "1"),
                    packJson.optBoolean("animated_sticker_pack", false),
                    stickers
            ));
        }
        return packs;
    }
}
