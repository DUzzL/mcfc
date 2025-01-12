package dev.epicpix.minecraftfunctioncompiler.il;

public record LabelData(int id) {
    public static LabelData ofId(int id) {
        return new LabelData(id);
    }

    public String toString() {
        return "label<" + id + ">";
    }
}
