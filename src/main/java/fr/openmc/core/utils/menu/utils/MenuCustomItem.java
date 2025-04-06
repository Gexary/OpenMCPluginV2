package fr.openmc.core.utils.menu.utils;

public enum MenuCustomItem {
    ACCEPT(0),
    DECLINE(1),
    CANCEL(2),
    LIST(3),
    TRANSPARENT(4);

    private final int CMD;

    MenuCustomItem(int CMD) {
        this.CMD = CMD;
    }

    public int getCMD() {
        return CMD;
    }
}
