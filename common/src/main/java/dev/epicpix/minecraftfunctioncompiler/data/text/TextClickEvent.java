package dev.epicpix.minecraftfunctioncompiler.data.text;

public record TextClickEvent(Action action, String value) {

    enum Action {
        OPEN_URL,
        RUN_COMMAND,
        SUGGEST_COMMAND,
        CHANGE_PAGE,
        COPY_TO_CLIPBOARD,
    }
}
