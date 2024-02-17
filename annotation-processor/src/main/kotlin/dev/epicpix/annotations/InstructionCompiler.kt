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
        if("{}[]().,+-/*;".contains(res)) {
            return res
        }
        if(res == "=" || res == "!") {
            if(index < code.length && code[index] == '=') {
                return res + code[index++]
            }else {
                return res
            }
        }
        if(res.first().isLetter() || res.first() == '@' || res.first() == '#' || res.first() == ':') {
            while(index < code.length && (code[index].isLetterOrDigit() || code[index] == '_' || (res.first() == ':' && (code[index] == '/' || code[index] == '$')))) {
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
    SCOPE,
    EXIT_SCOPE,
    CREATE_LOCAL,
    IF,
    CMP_EQ,
    CMP_NE,
    ASSIGN,
    NULL,
    WORD,
    NUMBER,
    RUN,
    CODE,
    PLUS,
    MINUS,
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

private fun ListIterator<String>.expectToken(token: String) {
    if(!hasNext()) throw IllegalStateException("Expected token '$token', got EOF")
    val got = next()
    if(got != token) {
        throw IllegalStateException("Expected token '$token', got '$got'")
    }
}

private fun ListIterator<String>.readExpression(): ParsedTokens {
    val exp1 = readExpression1()
    if(!hasNext()) return exp1
    val op = next()
    if(op != "==" && op != "!=") {
        previous()
        return exp1
    }
    val exp2 = readExpression1()
    if(op == "==") {
        return ParsedTokens(TokenType.CMP_EQ, listOf(exp1, exp2))
    }else {
        return ParsedTokens(TokenType.CMP_NE, listOf(exp1, exp2))
    }
}

private fun ListIterator<String>.readExpression1(): ParsedTokens {
    val exp1 = readExpressionBase()
    if(!hasNext()) return exp1
    val op = next()
    if(op != "+" && op != "-") {
        previous()
        return exp1
    }
    val exp2 = readExpressionBase()
    if(op == "+") {
        return ParsedTokens(TokenType.PLUS, listOf(exp1, exp2))
    }else {
        return ParsedTokens(TokenType.MINUS, listOf(exp1, exp2))
    }
}

private fun ListIterator<String>.readExpressionBase(): ParsedTokens {
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

private fun parseCode(iter: ListIterator<String>): ParsedTokens {
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
        }else if(token == "scope") {
            iter.expectToken("(")
            val scope = iter.next()
            if(!scope.startsWith("#")) {
                throw IllegalStateException("Expected scope token, got '$scope'")
            }
            iter.expectToken(")")
            val code = parseCode(iter)
            res.add(ParsedTokens(TokenType.SCOPE, listOf(ParsedTokensWord(scope), code)))
        }else if(token == "}") {
            return ParsedTokens(TokenType.CODE, res)
        }else if(token == "run") {
            iter.expectToken(";")
            res.add(ParsedTokens(TokenType.RUN, listOf()))
        }else if(token.startsWith("@")) {
            iter.expectToken("=")
            val expression = iter.readExpression()
            iter.expectToken(";")
            res.add(ParsedTokens(TokenType.ASSIGN, listOf(ParsedTokensWord(token), expression)))
        }else if(token == "exit_scope") {
            iter.expectToken("(")
            val scope = iter.next()
            if(!scope.startsWith("#")) {
                throw IllegalStateException("Expected scope token, got '$scope'")
            }
            iter.expectToken(")")
            iter.expectToken(";")
            res.add(ParsedTokens(TokenType.EXIT_SCOPE, listOf(ParsedTokensWord(scope))))
        }else if(token == "create_local") {
            iter.expectToken("(")
            val type = iter.next()
            if(!type.startsWith(":")) {
                throw IllegalStateException("Expected type token, got '$type'")
            }
            val local = iter.next()
            if(!local.startsWith("@")) {
                throw IllegalStateException("Expected local token, got '$local'")
            }
            iter.expectToken("=")
            val value = iter.readExpression()
            iter.expectToken(")")
            val code = parseCode(iter)
            res.add(ParsedTokens(TokenType.CREATE_LOCAL, listOf(ParsedTokensWord(type), ParsedTokensWord(local), value, code)))
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
    SCOPE,
    SCOPE_BLOCK,
    EXIT_SCOPE,
    ASSIGN,
    CREATE_LOCAL,
    TYPE,
    NULL,
    PLUS,
    MINUS,
    NUMBER,
    RUN,
    CODE,
}

private open class LoweredTokens(val type: LoweredTokenType, val children: List<LoweredTokens>) {
    override fun toString(): String {
        return "LoweredTokens(type=$type, children=$children)"
    }
}
private class LoweredTokensLocal(val local: String) : LoweredTokens(LoweredTokenType.LOCAL, listOf()) {
    override fun toString(): String {
        return "LoweredTokensLocal(local='$local')"
    }
}
private class LoweredTokensScope(val scope: String) : LoweredTokens(LoweredTokenType.SCOPE, listOf()) {
    override fun toString(): String {
        return "LoweredTokensScope(scope='$scope')"
    }
}
private class LoweredTokensType(val typeName: String) : LoweredTokens(LoweredTokenType.TYPE, listOf()) {
    override fun toString(): String {
        return "LoweredTokensType(typeName='$typeName')"
    }
}
private class LoweredTokensNumber(val number: String) : LoweredTokens(LoweredTokenType.NUMBER, listOf()) {
    override fun toString(): String {
        return "LoweredTokensNumber(number='$number')"
    }
}

private fun lowerExpression(parsed: ParsedTokens): LoweredTokens {
    if(parsed is ParsedTokensWord) {
        if(parsed.word.startsWith("@")) {
            return LoweredTokensLocal(parsed.word.substring(1))
        }else {
            throw IllegalStateException("Unknown expression word: ${parsed.word}")
        }
    }else if(parsed is ParsedTokensNumber) {
        return LoweredTokensNumber(parsed.number)
    }else if(parsed.type == TokenType.NULL) {
        return LoweredTokens(LoweredTokenType.NULL, listOf())
    }else if(parsed.type == TokenType.PLUS) {
        return LoweredTokens(LoweredTokenType.PLUS, listOf(lowerExpression(parsed.children[0]), lowerExpression(parsed.children[1])))
    }else if(parsed.type == TokenType.MINUS) {
        return LoweredTokens(LoweredTokenType.MINUS, listOf(lowerExpression(parsed.children[0]), lowerExpression(parsed.children[1])))
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
        }else if(token.type == TokenType.SCOPE) {
            val scope = (token.children[0] as ParsedTokensWord).word.substring(1)
            val code = lowerCode(token.children[1])
            res.add(LoweredTokens(LoweredTokenType.SCOPE_BLOCK, listOf(LoweredTokensScope(scope), code)))
        }else if(token.type == TokenType.EXIT_SCOPE) {
            val scope = (token.children[0] as ParsedTokensWord).word.substring(1)
            res.add(LoweredTokens(LoweredTokenType.EXIT_SCOPE, listOf(LoweredTokensScope(scope))))
        }else if(token.type == TokenType.RUN) {
            res.add(LoweredTokens(LoweredTokenType.RUN, listOf()))
        }else if(token.type == TokenType.ASSIGN) {
            val exp1 = lowerExpression(token.children[0])
            val exp2 = lowerExpression(token.children[1])
            res.add(LoweredTokens(LoweredTokenType.ASSIGN, listOf(exp1, exp2)))
        }else if(token.type == TokenType.CREATE_LOCAL) {
            val type = (token.children[0] as ParsedTokensWord).word.substring(1)
            val local = (token.children[1] as ParsedTokensWord).word.substring(1)
            val expression = lowerExpression(token.children[2])
            val code = lowerCode(token.children[3])
            res.add(LoweredTokens(LoweredTokenType.CREATE_LOCAL, listOf(LoweredTokensType(type), LoweredTokensLocal(local), expression, code)))
        }else {
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
    }else if(expression is LoweredTokensNumber) {
        if(!dropResult) {
            val value = expression.number.toInt()
            if (value >= -1 && value <= 5) {
                if(value == -1) {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.ICONST_M1);\n")
                }else {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.ICONST_$value);\n")
                }
            } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitIntInsn(org.objectweb.asm.Opcodes.BIPUSH, $value);\n")
            } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitIntInsn(org.objectweb.asm.Opcodes.SIPUSH, $value);\n")
            } else {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitLdcInsn($value);\n")
            }
        }
    }else if(expression.type == LoweredTokenType.PLUS) {
        writeExpression(mappingData, expression.children[0], output, false)
        writeExpression(mappingData, expression.children[1], output, false)
        output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.IADD);\n")
        if(dropResult) {
            output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.POP);\n")
        }
    }else if(expression.type == LoweredTokenType.MINUS) {
        writeExpression(mappingData, expression.children[0], output, false)
        writeExpression(mappingData, expression.children[1], output, false)
        output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.ISUB);\n")
        if(dropResult) {
            output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.POP);\n")
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
    }else if(code.type == LoweredTokenType.SCOPE_BLOCK) {
        var uid = uniqId
        val jumpLabel = "j${uid++}"
        val scope = (code.children[0] as LoweredTokensScope).scope
        output.appendText("org.objectweb.asm.Label $jumpLabel = new org.objectweb.asm.Label();\n")
        output.appendText("context.getNewScopes().put(context.getInstruction().<dev.epicpix.minecraftfunctioncompiler.il.ScopeData>get(\"$scope\").getId(), $jumpLabel);\n")
        uid = writeCode(mappingData, code.children[1], output, uid)
        output.appendText("context.getCodeWriter().getMethodVisitor().visitLabel($jumpLabel);\n")
        return uid
    }else if(code.type == LoweredTokenType.EXIT_SCOPE) {
        val scope = (code.children[0] as LoweredTokensScope).scope
        output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, context.getNewScopes().get(context.getInstruction().<dev.epicpix.minecraftfunctioncompiler.il.ScopeData>get(\"$scope\").getId()));\n")
        return uniqId
    }else if(code.type == LoweredTokenType.RUN) {
        output.appendText("context.run();\n")
        return uniqId
    }else if(code.type == LoweredTokenType.ASSIGN) {
        writeExpression(mappingData, code.children[1], output, false)
        val assignTo = code.children[0]
        if(assignTo is LoweredTokensLocal) {
            output.appendText("context.save(\"${assignTo.local}\");\n")
        }else {
            throw IllegalStateException("Cannot assign to non-local variable: $assignTo")
        }
        return uniqId
    }else if(code.type == LoweredTokenType.CREATE_LOCAL) {
        writeExpression(mappingData, code.children[2], output, false)
        val assignType = (code.children[0] as LoweredTokensType).typeName.let {
            if(it == "int") return@let "I"
            "L${mappingData?.getClassName(it) ?: it};"
        }
        val assignTo = code.children[1] as LoweredTokensLocal
        var uid = uniqId
        val storageLocalUid = "s${uid++}"
        val storageLocal = "l${uid++}"
        output.appendText("String $storageLocalUid = context.getCodeWriter().generateUniqueLocal();\n")
        output.appendText("context.store(\"${assignTo.local}\", $storageLocalUid);\n")
        output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.impl.Local $storageLocal = context.getCodeWriter().createLocal($storageLocalUid, \"$assignType\");\n")
        if(assignType.first() == 'I') {
            output.appendText("context.getCodeWriter().getMethodVisitor().visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, $storageLocal.getIndex());\n")
        }else {
            throw IllegalStateException("Unknown assign type: $assignType")
        }
        writeCode(mappingData, code.children[3], output, uid)
        output.appendText("context.getCodeWriter().destroyLocal($storageLocal);\n")
        return uid
    }else {
        throw IllegalStateException("Unexpected code token: $code")
    }
}

fun generateCode(mappingData: MappingData?, inputCode: String, output: OutputStream) {
    output.appendText("// $inputCode\n")
    val tokens = splitTokens(inputCode)
    output.appendText("// ${tokens.joinToString(" ")}\n")
    val code = parseCode(listOf("{", *tokens.toTypedArray(), "}").listIterator())
    output.appendText("// $code\n")
    val lowered = lowerCode(code)
    output.appendText("// $lowered\n")
    writeCode(mappingData, lowered, output, 0)
}