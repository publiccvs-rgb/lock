package dev.lock.core.module;

public enum Category {
    VISUALS("Visuals"),
    HUD("HUD"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    WORLD("World"),
    UTILITY("Utility"),
    INVENTORY("Inventory"),
    INTERFACE("Interface"),
    PERFORMANCE("Performance"),
    RECORDING("Recording & Visibility");

    public final String label;

    Category(String label) { this.label = label; }
}
