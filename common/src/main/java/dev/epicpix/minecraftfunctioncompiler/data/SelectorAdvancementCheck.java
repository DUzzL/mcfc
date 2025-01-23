package dev.epicpix.minecraftfunctioncompiler.data;

import java.util.Map;

public interface SelectorAdvancementCheck {

    record AdvancementCompleted(boolean done) implements SelectorAdvancementCheck {}
    record CriteriaCompleted(Map<String, Boolean> criteria) implements SelectorAdvancementCheck {}

}
