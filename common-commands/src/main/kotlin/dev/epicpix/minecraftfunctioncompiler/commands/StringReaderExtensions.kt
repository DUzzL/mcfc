package dev.epicpix.minecraftfunctioncompiler.commands

import com.mojang.brigadier.StringReader

fun StringReader.readRawInt(): Int {
    var num = 0
    val negative: Boolean = peek() == '-'
    if(negative) read()
    while(canRead()) {
        if(peek().isDigit()) {
            num = num * 10 + (read() - '0')
        }else break
    }
    return if(negative) -num else num
}

fun StringReader.optRead(char: Char): Boolean {
    return if(peek() == char) {
        read()
        true
    } else false
}