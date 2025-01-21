package dev.epicpix.annotations.instructions

interface LexToken

data class LexTokenFileHeader(val name: String) : LexToken
data class LexTokenInstructionFlag(val name: String) : LexToken
data class LexTokenInstructionDefinition(val name: String) : LexToken
data class LexTokenNumber(val number: String) : LexToken
data class LexTokenWord(val word: String) : LexToken
data class LexTokenString(val content: String) : LexToken

enum class LexTokenSimpleType : LexToken {
    OPEN_BRACE,
    CLOSE_BRACE,
    OPEN_BRACKET,
    CLOSE_BRACKET,
    OPEN_PAREN,
    CLOSE_PAREN,
    OPEN_ANGLE_BRACKET,
    CLOSE_ANGLE_BRACKET,
    LE,
    GE,
    EQEQ,
    NEEQ,
    AND,
    OR,
    XOR,
    NOT,
    DOT,
    COMMA,
    PLUS,
    MINUS,
    SLASH,
    STAR,
    SEMICOLON,
    COLON,
    ASSIGN,
    KW_IF,
    KW_ELSE,
    KW_NULL,
    KW_LET,
    KW_FALSE,
    KW_TRUE,
    KW_NEW,
}

fun lexTokens(code: String): List<LexToken> {
    var index = 0
    fun readToken(): LexToken? {
        if(index >= code.length) return null
        var prevIndex = -1
        while(prevIndex != index) {
            prevIndex = index
            while(index < code.length && " \t\n\r".contains(code[index])) {
                index++
            }
            while(code[index] == '/' && index + 1 < code.length && code[index + 1] == '/') {
                index += 2
                while(index < code.length && !"\n\r".contains(code[index])) {
                    index++
                }
                index++
                if(index >= code.length) return null
            }
        }
        var res = code[index++].toString()
        when(res.first()) {
            '{' -> return LexTokenSimpleType.OPEN_BRACE
            '}' -> return LexTokenSimpleType.CLOSE_BRACE
            '[' -> return LexTokenSimpleType.OPEN_BRACKET
            ']' -> return LexTokenSimpleType.CLOSE_BRACKET
            '(' -> return LexTokenSimpleType.OPEN_PAREN
            ')' -> return LexTokenSimpleType.CLOSE_PAREN
            '<' -> {
                if(index < code.length && code[index] == '=') {
                    index++
                    return LexTokenSimpleType.LE
                }
                return LexTokenSimpleType.OPEN_ANGLE_BRACKET
            }
            '>' -> {
                if(index < code.length && code[index] == '=') {
                    index++
                    return LexTokenSimpleType.GE
                }
                return LexTokenSimpleType.CLOSE_ANGLE_BRACKET
            }
            '.' -> return LexTokenSimpleType.DOT
            ',' -> return LexTokenSimpleType.COMMA
            '+' -> return LexTokenSimpleType.PLUS
            '-' -> return LexTokenSimpleType.MINUS
            '/' -> return LexTokenSimpleType.SLASH
            '*' -> return LexTokenSimpleType.STAR
            ';' -> return LexTokenSimpleType.SEMICOLON
            ':' -> return LexTokenSimpleType.COLON
            '^' -> return LexTokenSimpleType.XOR
            '&' -> return LexTokenSimpleType.AND
            '|' -> return LexTokenSimpleType.OR
        }
        if(res == "=") {
            if(index < code.length && code[index] == '=') {
                index++
                return LexTokenSimpleType.EQEQ
            }
            return LexTokenSimpleType.ASSIGN
        }
        if(res == "!") {
            if(index < code.length && code[index] == '=') {
                index++
                return LexTokenSimpleType.NEEQ
            }
            return LexTokenSimpleType.NOT
        }
        if(res.first() == '@') {
            res = ""
            while(index < code.length && (code[index].isLetterOrDigit() || code[index] == '_')) {
                res += code[index++]
            }
            return LexTokenInstructionDefinition(res)
        }
        if(res.first() == '#') {
            res = ""
            if(index < code.length && code[index] == '#') {
                index++
                while(index < code.length && (code[index].isLetter() || code[index] == '_')) {
                    res += code[index++]
                }
                return LexTokenInstructionFlag(res)
            }else {
                while(index < code.length && (code[index].isLetter() || code[index] == '_')) {
                    res += code[index++]
                }
                return LexTokenFileHeader(res)
            }
        }
        if(res.first().isLetter() || res.first() == '_') {
            while(index < code.length && (code[index].isLetterOrDigit() || code[index] == '_')) {
                res += code[index++]
            }
            return when(res) {
                "if" -> LexTokenSimpleType.KW_IF
                "else" -> LexTokenSimpleType.KW_ELSE
                "null" -> LexTokenSimpleType.KW_NULL
                "let" -> LexTokenSimpleType.KW_LET
                "false" -> LexTokenSimpleType.KW_FALSE
                "true" -> LexTokenSimpleType.KW_TRUE
                "new" -> LexTokenSimpleType.KW_NEW
                else -> LexTokenWord(res)
            }
        }
        if(res.first().isDigit()) {
            while(index < code.length && code[index].isDigit()) {
                res += code[index++]
            }
            return LexTokenNumber(res)
        }
        if(res.first() == '"') {
            while(index < code.length && code[index] != '"') {
                res += code[index++]
            }
            index++
            return LexTokenString(res.substring(1))
        }
        throw NotImplementedError("Failed to lex token\nvalue: $res")
    }
    val res = mutableListOf<LexToken>()
    while(true) {
        val token = readToken() ?: break
        res.add(token)
    }
    return res
}