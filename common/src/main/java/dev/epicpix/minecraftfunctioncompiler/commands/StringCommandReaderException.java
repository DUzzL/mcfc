package dev.epicpix.minecraftfunctioncompiler.commands;

public class StringCommandReaderException extends RuntimeException {
    private final StringCommandReaderExceptionType type;
    private final String string;
    private final int index;
    public StringCommandReaderException(StringCommandReader reader, StringCommandReaderExceptionType type) {
        super(type.name());
        this.type = type;
        this.string = reader.getString();
        this.index = reader.getIndex();
    }

    public StringCommandReaderExceptionType getType() {
        return type;
    }

    public String getString() {
        return string;
    }

    public int getIndex() {
        return index;
    }
}
