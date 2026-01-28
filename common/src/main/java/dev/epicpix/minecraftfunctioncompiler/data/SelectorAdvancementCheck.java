package dev.epicpix.minecraftfunctioncompiler.data;

import java.util.Map;

public sealed interface SelectorAdvancementCheck permits SelectorAdvancementCheck.AdvancementCompleted, SelectorAdvancementCheck.CriteriaCompleted {

    record AdvancementCompleted(boolean done) implements SelectorAdvancementCheck {}
    record CriteriaCompleted(Map<String, Boolean> criteria) implements SelectorAdvancementCheck {}

}
