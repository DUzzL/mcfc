package dev.epicpix.minecraftfunctioncompiler.data.text;

public record TextHoverEvent<T>(Action<T> action, T value) {
    public static final class Action<T> {
        private final String name;
        private Action(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static final Action<Text> SHOW_TEXT = new Action<>("SHOW_TEXT");
    }
}
