package com.packstudio.model;

/**
 * Physical container type of a discovered resource pack.
 */
public enum PackSourceKind {
    ZIP_ARCHIVE("ZIP Pack"),
    FOLDER("Folder Pack"),
    BUILTIN("Built-in");

    private final String label;

    PackSourceKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
