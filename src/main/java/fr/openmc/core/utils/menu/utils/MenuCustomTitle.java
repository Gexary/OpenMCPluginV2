package fr.openmc.core.utils.menu.utils;

public enum MenuCustomTitle {
    TRADE("5"),
    LIST("1");

    private final String title;

    MenuCustomTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }
}
