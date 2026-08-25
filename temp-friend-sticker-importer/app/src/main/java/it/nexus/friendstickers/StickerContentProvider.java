package it.nexus.friendstickers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;

public class StickerContentProvider extends ContentProvider {
    private static final String METADATA = "metadata";
    private static final String STICKERS = "stickers";
    private static final String STICKERS_ASSET = "stickers_asset";
    private static final int METADATA_ALL = 1;
    private static final int METADATA_ONE = 2;
    private static final int STICKER_LIST = 3;
    private static final int STICKER_FILE = 4;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    @Override
    public boolean onCreate() {
        String authority = BuildConfig.CONTENT_PROVIDER_AUTHORITY;
        MATCHER.addURI(authority, METADATA, METADATA_ALL);
        MATCHER.addURI(authority, METADATA + "/*", METADATA_ONE);
        MATCHER.addURI(authority, STICKERS + "/*", STICKER_LIST);
        MATCHER.addURI(authority, STICKERS_ASSET + "/*/*", STICKER_FILE);
        return true;
    }

    private List<StickerPack> packs() {
        try {
            return PackStore.loadActive(getContext());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        int code = MATCHER.match(uri);
        if (code == METADATA_ALL) return metadataCursor(uri, packs());
        if (code == METADATA_ONE) {
            String id = uri.getLastPathSegment();
            for (StickerPack pack : packs()) {
                if (pack.identifier.equals(id)) return metadataCursor(uri, Collections.singletonList(pack));
            }
            return metadataCursor(uri, Collections.emptyList());
        }
        if (code == STICKER_LIST) return stickerCursor(uri, uri.getLastPathSegment());
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    private Cursor metadataCursor(Uri uri, List<StickerPack> packs) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher",
                "sticker_pack_icon", "android_play_store_link", "ios_app_store_link",
                "sticker_pack_publisher_email", "sticker_pack_publisher_website",
                "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
                "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack"
        });
        for (StickerPack pack : packs) {
            cursor.addRow(new Object[]{
                    pack.identifier, pack.name, pack.publisher, pack.trayImageFile,
                    null, null, null, null, null, null,
                    pack.imageDataVersion, 0, pack.animated ? 1 : 0
            });
        }
        if (getContext() != null) cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    private Cursor stickerCursor(Uri uri, String identifier) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                "sticker_file_name", "sticker_emoji", "sticker_accessibility_text"
        });
        for (StickerPack pack : packs()) {
            if (!pack.identifier.equals(identifier)) continue;
            for (Sticker sticker : pack.stickers) {
                cursor.addRow(new Object[]{
                        sticker.fileName, TextUtils.join(",", sticker.emojis), sticker.accessibilityText
                });
            }
        }
        if (getContext() != null) cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        if (MATCHER.match(uri) != STICKER_FILE) throw new FileNotFoundException(uri.toString());
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 3) throw new FileNotFoundException(uri.toString());
        String identifier = segments.get(1);
        String fileName = segments.get(2);
        if (!identifier.matches("[A-Za-z0-9_.-]{1,64}") || fileName.contains("/") || fileName.contains("..")) {
            throw new FileNotFoundException("Invalid path");
        }
        File root = PackStore.activeRoot(getContext());
        File file = new File(new File(root, identifier), fileName);
        try {
            String rootCanonical = root.getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(rootCanonical) || !file.isFile()) {
                throw new FileNotFoundException(fileName);
            }
        } catch (java.io.IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(descriptor, 0, file.length());
    }

    @Override
    public String getType(Uri uri) {
        int code = MATCHER.match(uri);
        if (code == STICKER_FILE) {
            String name = uri.getLastPathSegment();
            return name != null && name.toLowerCase().endsWith(".png") ? "image/png" : "image/webp";
        }
        if (code == METADATA_ALL || code == STICKER_LIST) {
            return "vnd.android.cursor.dir/vnd." + BuildConfig.CONTENT_PROVIDER_AUTHORITY;
        }
        if (code == METADATA_ONE) {
            return "vnd.android.cursor.item/vnd." + BuildConfig.CONTENT_PROVIDER_AUTHORITY;
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
