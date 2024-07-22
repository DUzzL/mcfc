package dev.epicpix.minecraftfunctioncompiler.data.text;

import java.util.List;
import java.util.function.Consumer;

public record Text(List<Text> siblings, TextStyle style, TextContent content) {

    public void forEachComponent(Consumer<Text> consumer) {
        for(Text sibling : siblings) {
            sibling.forEachComponent(consumer);
        }
        consumer.accept(this);
    }

}
