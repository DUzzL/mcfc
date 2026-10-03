package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

/** A constant (score holder name, objective name) pair: one link-time score cache per pair. */
public record ScoreRefKey(String holder, String objective) {}
