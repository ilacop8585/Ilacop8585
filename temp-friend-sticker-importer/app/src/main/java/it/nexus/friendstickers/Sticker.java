package it.nexus.friendstickers;

import java.util.List;

final class Sticker {
    final String fileName;
    final List<String> emojis;
    final String accessibilityText;

    Sticker(String fileName, List<String> emojis, String accessibilityText) {
        this.fileName = fileName;
        this.emojis = emojis;
        this.accessibilityText = accessibilityText;
    }
}
