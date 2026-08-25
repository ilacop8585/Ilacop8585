package it.nexus.friendstickers;

import java.util.List;

final class StickerPack {
    final String identifier;
    final String name;
    final String publisher;
    final String trayImageFile;
    final String imageDataVersion;
    final boolean animated;
    final List<Sticker> stickers;

    StickerPack(String identifier, String name, String publisher, String trayImageFile,
                String imageDataVersion, boolean animated, List<Sticker> stickers) {
        this.identifier = identifier;
        this.name = name;
        this.publisher = publisher;
        this.trayImageFile = trayImageFile;
        this.imageDataVersion = imageDataVersion;
        this.animated = animated;
        this.stickers = stickers;
    }
}
