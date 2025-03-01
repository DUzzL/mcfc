package dev.epicpix.minecraftfunctioncompiler.data;

import dev.epicpix.minecraftfunctioncompiler.emitter.types.NumberComparisonType;

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

    public NumberComparisonType toNumberComparisonJump() {
        return switch(this) {
            case LT -> NumberComparisonType.GREATER_THAN_OR_EQUAL;
            case LE -> NumberComparisonType.GREATER_THAN;
            case EQ -> NumberComparisonType.NOT_EQUAL;
            case NE -> NumberComparisonType.EQUAL;
            case GE -> NumberComparisonType.LESS_THAN;
            case GT -> NumberComparisonType.LESS_THAN_OR_EQUAL;
        };
    }
}
