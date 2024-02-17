package dev.epicpix.annotations

import java.io.OutputStream

private fun splitTokens(code: String): List<String> {
    var index = 0
    fun readToken(): String? {
        if(index >= code.length) return null
        while(index < code.length && " \t\n".contains(code[index])) {
            index++
        }
        if(index >= code.length) return null
        var res = code[index++].toString()
        if("{}[]().,+-/*".contains(res)) {
            return res
        }
        if(res == "=" || res == "!") {
            if(index < code.length && code[index] == '=') {
                return res + code[index++]
            }else {
                return res
            }
        }
        if(res.first().isLetter() || res.first() == '@') {
            while(index < code.length && code[index].isLetterOrDigit()) {
                res += code[index++]
            }
            return res
        }
        if(res.first().isDigit()) {
            while(index < code.length && code[index].isDigit()) {
                res += code[index++]
            }
            return res
        }
        throw NotImplementedError("Failed to read token\ncode: $code\nvalue: $res")
    }
    val res = mutableListOf<String>()
    while(true) {
        val token = readToken() ?: break
        res.add(token)
    }
    return res
}

private enum class TokenType {
    IF,
    CMP_EQ,
    CMP_NE,
    NULL,
    WORD,
    NUMBER,
    RUN,
    CODE,
}

private open class ParsedTokens(val type: TokenType, val children: List<ParsedTokens>) {
    override fun toString(): String {
        return "ParsedTokens(type=$type, children=$children)"
    }
}
private class ParsedTokensWord(val word: String) : ParsedTokens(TokenType.WORD, listOf()) {
    override fun toString(): String {
        return "ParsedTokensWord(word='$word')"
    }
}
private class ParsedTokensNumber(val number: String) : ParsedTokens(TokenType.NUMBER, listOf()) {
    override fun toString(): String {
        return "ParsedTokensNumber(number='$number')"
    }
}

private fun Iterator<String>.expectToken(token: String) {
    if(!hasNext()) throw IllegalStateException("Expected token '$token', got EOF")
    val got = next()
    if(got != token) {
        throw IllegalStateException("Expected token '$token', got '$got'")
    }
}

private fun Iterator<String>.readExpression(): ParsedTokens {
    val exp1 = readExpressionBase()
    if(!hasNext()) throw IllegalStateException("Expected expression operator, got EOF")
    val op = next()
    val exp2 = readExpressionBase()
    if(op == "==") {
        return ParsedTokens(TokenType.CMP_EQ, listOf(exp1, exp2))
    }else if(op == "!=") {
        return ParsedTokens(TokenType.CMP_NE, listOf(exp1, exp2))
    }else {
        throw IllegalStateException("Expected expression operator, got '$op'")
    }
}

private fun Iterator<String>.readExpressionBase(): ParsedTokens {
    if(!hasNext()) throw IllegalStateException("Expected expression base, got EOF")
    val value = next()
    if(value.matches(Regex("[0-9]+"))) {
        return ParsedTokensNumber(value)
    }else if(value == "null") {
        return ParsedTokens(TokenType.NULL, listOf())
    }else if(value == "run") {
        return ParsedTokens(TokenType.RUN, listOf())
    }else {
        return ParsedTokensWord(value)
    }
}

private fun parseCode(iter: Iterator<String>): ParsedTokens {
    val res = mutableListOf<ParsedTokens>()
    iter.expectToken("{")
    while(iter.hasNext()) {
        val token = iter.next()
        if(token == "if") {
            iter.expectToken("(")
            val expression = iter.readExpression()
            iter.expectToken(")")
            val code = parseCode(iter)
            res.add(ParsedTokens(TokenType.IF, listOf(expression, code)))
        }else if(token == "}") {
            return ParsedTokens(TokenType.CODE, res)
        }else if(token == "run") {
            res.add(ParsedTokens(TokenType.RUN, listOf()))
        }else {
            throw IllegalStateException("Expected code token, got '$token'")
        }
    }
    throw IllegalStateException("Expected '}', got EOF")
}

private enum class LoweredTokenType {
    IF_NULL,
    IF_NOT_NULL,
    IF_EQ,
    IF_NE,
    LOCAL,
    NULL,
    NUMBER,
    RUN,
    CODE,
}

private open class LoweredTokens(val type: LoweredTokenType, val children: List<LoweredTokens>) {
    override fun toString(): String {
        return "ParsedTokens(type=$type, children=$children)"
    }
}
private class LoweredTokensLocal(val local: String) : LoweredTokens(LoweredTokenType.LOCAL, listOf()) {
    override fun toString(): String {
        return "ParsedTokensWord(local='$local')"
    }
}
private class LoweredTokensNumber(val number: String) : LoweredTokens(LoweredTokenType.NUMBER, listOf()) {
    override fun toString(): String {
        return "ParsedTokensNumber(number='$number')"
    }
}

private fun lowerExpression(parsed: ParsedTokens): LoweredTokens {
    if(parsed is ParsedTokensWord) {
        if(parsed.word.startsWith("@")) {
            return LoweredTokensLocal(parsed.word.substring(1))
        }else {
            throw IllegalStateException("Unknown expression word: ${parsed.word}")
        }
    }else if(parsed.type == TokenType.NULL) {
        return LoweredTokens(LoweredTokenType.NULL, listOf())
    }
    throw IllegalStateException("Unexpected expression token: $parsed")
}

private fun lowerCode(parsed: ParsedTokens): LoweredTokens {
    val res = mutableListOf<LoweredTokens>()
    for(token in parsed.children) {
        if(token.type == TokenType.IF) {
            val expression = token.children[0]
            val code = lowerCode(token.children[1])
            if(expression.type == TokenType.CMP_EQ) {
                val exp1 = lowerExpression(expression.children[0])
                val exp2 = lowerExpression(expression.children[1])
                val isNullCheck = (exp1.type == LoweredTokenType.NULL) xor (exp2.type == LoweredTokenType.NULL)
                if(isNullCheck) {
                    val nonNull = if(exp1.type == LoweredTokenType.NULL) exp2 else exp1
                    res.add(LoweredTokens(LoweredTokenType.IF_NULL, listOf(nonNull, code)))
                }else {
                    res.add(LoweredTokens(LoweredTokenType.IF_EQ, listOf(exp1, exp2, code)))
                }
            }else if(expression.type == TokenType.CMP_NE) {
                val exp1 = lowerExpression(expression.children[0])
                val exp2 = lowerExpression(expression.children[1])
                val isNullCheck = (exp1.type == LoweredTokenType.NULL) xor (exp2.type == LoweredTokenType.NULL)
                if(isNullCheck) {
                    val nonNull = if(exp1.type == LoweredTokenType.NULL) exp2 else exp1
                    res.add(LoweredTokens(LoweredTokenType.IF_NOT_NULL, listOf(nonNull, code)))
                }else {
                    res.add(LoweredTokens(LoweredTokenType.IF_NE, listOf(exp1, exp2, code)))
                }
            }else {
                throw IllegalStateException("Unsupported if expression: $expression")
            }
        }else if(token.type == TokenType.RUN) {
            res.add(LoweredTokens(LoweredTokenType.RUN, listOf()))
        } else {
            throw IllegalStateException("Unexpected code token: $token")
        }
    }
    return LoweredTokens(LoweredTokenType.CODE, res)
}

private fun writeExpression(mappingData: MappingData?, expression: LoweredTokens, output: OutputStream, dropResult: Boolean) {
    if(expression is LoweredTokensLocal) {
        if(!dropResult) {
            output.appendText("context.load(\"${expression.local}\");\n")
        }
    }else {
        throw IllegalStateException("Unexpected expression token: $expression")
    }
}

private fun writeCode(mappingData: MappingData?, code: LoweredTokens, output: OutputStream, uniqId: Int): Int {
    if(code.type == LoweredTokenType.CODE) {
        var uid = uniqId
        code.children.forEach {
            uid = writeCode(mappingData, it, output, uid)
        }
        return uid
    }else if(code.type == LoweredTokenType.IF_NOT_NULL) {
        var uid = uniqId
        val jumpLabel = "j${uid++}"
        output.appendText("org.objectweb.asm.Label $jumpLabel = new org.objectweb.asm.Label();\n")
        writeExpression(mappingData, code.children[0], output, false)
        output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.IFNULL, $jumpLabel);\n")
        uid = writeCode(mappingData, code.children[1], output, uid)
        output.appendText("context.getCodeWriter().getMethodVisitor().visitLabel($jumpLabel);\n")
        return uid
    }else if(code.type == LoweredTokenType.RUN) {
        output.appendText("context.run();\n")
        return uniqId
    }else {
        throw IllegalStateException("Unexpected code token: $code")
    }
}

fun generateCode(mappingData: MappingData?, inputCode: String, output: OutputStream) {
    output.appendText("// $inputCode\n")
    val tokens = splitTokens(inputCode)
    output.appendText("// ${tokens.joinToString(" ")}\n")
    val code = parseCode(listOf("{", *tokens.toTypedArray(), "}").iterator())
    output.appendText("// $code\n")
    val lowered = lowerCode(code)
    output.appendText("// $lowered\n")
    writeCode(mappingData, lowered, output, 0)
}