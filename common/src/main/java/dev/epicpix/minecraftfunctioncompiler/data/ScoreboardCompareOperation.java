package dev.epicpix.minecraftfunctioncompiler.data;

public enum ScoreboardCompareOperation {
    LT, LE, EQ, NE, GE, GT;

    public ScoreboardCompareOperation negate() {
        return switch(this) {
            case LT -> GE;
            case LE -> GT;
            case EQ -> NE;
            case NE -> EQ;
            case GE -> LT;
            case GT -> LE;
        };
    }
}
