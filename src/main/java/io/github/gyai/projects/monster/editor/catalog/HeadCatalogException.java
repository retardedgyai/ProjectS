package io.github.gyai.projects.monster.editor.catalog;

public final class HeadCatalogException extends RuntimeException {
    public HeadCatalogException(String safeMessage) {
        super(safeMessage == null || safeMessage.isBlank()
                ? "Head Catalogを利用できません" : safeMessage);
    }
}
