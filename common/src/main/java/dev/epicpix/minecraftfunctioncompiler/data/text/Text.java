package dev.epicpix.minecraftfunctioncompiler.data.text;

import java.util.List;
import java.util.function.Consumer;

public record Text(List<Text> siblings, TextStyle style, TextContent content) {

    public void forEachComponent(Consumer<Text> consumer) {
        for(Text sibling : siblings) {
            sibling.forEachComponent(consumer);
        }
        if(content instanceof TextContent.TranslatableText translatable) {
            for(Object arg : translatable.args()) {
                if(arg instanceof Text text) {
                    text.forEachComponent(consumer);
                }
            }
        }
        // Recurse into hover event text so nested TextStyles are pre-registered
        if(style.hoverEvent() != null && style.hoverEvent().action() == TextHoverEvent.Action.SHOW_TEXT) {
            var hoverText = (Text) style.hoverEvent().value();
            hoverText.forEachComponent(consumer);
        }
        consumer.accept(this);
    }

    public boolean isCompilationSupported() {
        for(var sibling : siblings) if(!sibling.isCompilationSupported()) return false;
        if(!style.isCompilationSupported()) return false;
        return content.isCompilationSupported();
    }

    public boolean isConstant() {
        for(var sibling : siblings) if(!sibling.isConstant()) return false;
        if(!style.isConstant()) return false;
        return content.isConstant();
    }

}
