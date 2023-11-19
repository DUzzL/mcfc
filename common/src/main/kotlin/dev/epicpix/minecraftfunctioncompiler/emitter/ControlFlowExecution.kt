package dev.epicpix.minecraftfunctioncompiler.emitter

import org.objectweb.asm.Label

data class ControlFlowExecution(val continueJump: Label, val breakJump: Label)