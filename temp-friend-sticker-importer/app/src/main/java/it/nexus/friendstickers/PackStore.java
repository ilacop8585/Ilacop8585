package it.nexus.friendstickers;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class PackStore {
    private static final String ACTIVE_DIR = "active_pack";
    private static final String INCOMING_DIR = "incoming_pack";

    private PackStore() {}

    static File activeRoot(Context context) {
        return new File(context.getFilesDir(), ACTIVE_DIR);
    }

    static boolean hasActivePack(Context context) {
        return new File(activeRoot(context), "contents.json").isFile();
    }

    static List<StickerPack> loadActive(Context context) throws Exception {
        return ContentParser.load(activeRoot(context));
    }

    static List<StickerPack> importZip(Context context, Uri uri) throws Exception {
        File incoming = new File(context.getFilesDir(), INCOMING_DIR);
        deleteRecursively(incoming);
        if (!incoming.mkdirs() && !incoming.isDirectory()) {
            throw new IllegalStateException("Impossibile creare la cartella temporanea");
        }

        String incomingCanonical = incoming.getCanonicalPath() + File.separator;
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IllegalStateException("File non leggibile");
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry entry;
                byte[] buffer = new byte[16384];
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name == null || name.isEmpty()) continue;
                    File target = new File(incoming, name);
                    String targetCanonical = target.getCanonicalPath();
                    if (!targetCanonical.startsWith(incomingCanonical)) {
                        throw new SecurityException("Percorso ZIP non valido");
                    }
                    if (entry.isDirectory()) {
                        if (!target.mkdirs() && !target.isDirectory()) {
                            throw new IllegalStateException("Impossibile creare " + name);
                        }
                    } else {
                        File parent = target.getParentFile();
                        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                            throw new IllegalStateException("Impossibile creare cartella");
                        }
                        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                            int read;
                            long total = 0;
                            while ((read = zip.read(buffer)) != -1) {
                                total += read;
                                if (total > 2L * 1024L * 1024L) {
                                    throw new IllegalStateException("File nel pacchetto troppo grande");
                                }
                                output.write(buffer, 0, read);
                            }
                        }
                    }
                    zip.closeEntry();
                }
            }
        }

        List<StickerPack> packs = ContentParser.load(incoming);
        validate(incoming, packs);

        File active = activeRoot(context);
        deleteRecursively(active);
        if (!incoming.renameTo(active)) {
            throw new IllegalStateException("Impossibile attivare il pacchetto");
        }
        return ContentParser.load(active);
    }

    private static void validate(File root, List<StickerPack> packs) throws Exception {
        if (packs.isEmpty() || packs.size() > 10) {
            throw new IllegalStateException("Numero pacchetti non valido");
        }
        for (StickerPack pack : packs) {
            if (!pack.identifier.matches("[A-Za-z0-9_.-]{1,64}")) {
                throw new IllegalStateException("Identificatore pacchetto non valido");
            }
            if (pack.stickers.size() < 3 || pack.stickers.size() > 30) {
                throw new IllegalStateException("Ogni pacchetto deve avere da 3 a 30 sticker");
            }
            File packDir = new File(root, pack.identifier);
            File tray = new File(packDir, pack.trayImageFile);
            if (!tray.isFile() || tray.length() > 50L * 1024L) {
                throw new IllegalStateException("Icona tray non valida");
            }
            for (Sticker sticker : pack.stickers) {
                File file = new File(packDir, sticker.fileName);
                if (!file.isFile()) {
                    throw new IllegalStateException("Sticker mancante: " + sticker.fileName);
                }
                if (!sticker.fileName.toLowerCase().endsWith(".webp")) {
                    throw new IllegalStateException("Formato sticker non valido");
                }
                if (pack.animated && file.length() > 500L * 1024L) {
                    throw new IllegalStateException("Sticker animato troppo grande: " + sticker.fileName);
                }
            }
        }
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }
}
