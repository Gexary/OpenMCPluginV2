package fr.openmc.core.utils.menu.utils;

public class InventoryRectangle {
    private final int topRow, bottomRow, leftCol, rightCol;

    public InventoryRectangle(int startSlot, int endSlot) {
        int startRow = startSlot / 9;
        int startCol = startSlot % 9;
        int endRow = endSlot / 9;
        int endCol = endSlot % 9;

        this.topRow = Math.min(startRow, endRow);
        this.bottomRow = Math.max(startRow, endRow);
        this.leftCol = Math.min(startCol, endCol);
        this.rightCol = Math.max(startCol, endCol);
    }

    public void forEachSlot(IntConsumer action) {
        for (int row = topRow; row <= bottomRow; row++) {
            int base = row * 9;
            for (int col = leftCol; col <= rightCol; col++) {
                action.accept(base + col);
            }
        }
    }

    @FunctionalInterface
    public interface IntConsumer {
        void accept(int value);
    }
}
