package dev.epicpix.annotations

import java.io.ByteArrayOutputStream
import java.io.OutputStream

private interface LexToken

private data class LexTokenFileHeader(val name: String) : LexToken
private data class LexTokenInstructionDefinition(val name: String) : LexToken
private data class LexTokenNumber(val number: String) : LexToken
private data class LexTokenWord(val word: String) : LexToken
private data class LexTokenString(val content: String) : LexToken
private data class LexTokenSimple(val type: LexTokenSimpleType) : LexToken

private enum class LexTokenSimpleType {
    OPEN_BRACE,
    CLOSE_BRACE,
    OPEN_BRACKET,
    CLOSE_BRACKET,
    OPEN_PAREN,
    CLOSE_PAREN,
    DOT,
    COMMA,
    PLUS,
    MINUS,
    SLASH,
    STAR,
    SEMICOLON,
    COLON,
    EQ,
    NE,
    ASSIGN,
    KW_IF,
    KW_NULL,
    KW_LET,
}

private fun lexTokens(code: String): List<LexToken> {
    var index = 0
    fun readToken(): LexToken? {
        if(index >= code.length) return null
        while(index < code.length && " \t\n".contains(code[index])) {
            index++
        }
        if(index >= code.length) return null
        var res = code[index++].toString()
        when(res.first()) {
            '{' -> return LexTokenSimple(LexTokenSimpleType.OPEN_BRACE)
            '}' -> return LexTokenSimple(LexTokenSimpleType.CLOSE_BRACE)
            '[' -> return LexTokenSimple(LexTokenSimpleType.OPEN_BRACKET)
            ']' -> return LexTokenSimple(LexTokenSimpleType.CLOSE_BRACKET)
            '(' -> return LexTokenSimple(LexTokenSimpleType.OPEN_PAREN)
            ')' -> return LexTokenSimple(LexTokenSimpleType.CLOSE_PAREN)
            '.' -> return LexTokenSimple(LexTokenSimpleType.DOT)
            ',' -> return LexTokenSimple(LexTokenSimpleType.COMMA)
            '+' -> return LexTokenSimple(LexTokenSimpleType.PLUS)
            '-' -> return LexTokenSimple(LexTokenSimpleType.MINUS)
            '/' -> return LexTokenSimple(LexTokenSimpleType.SLASH)
            '*' -> return LexTokenSimple(LexTokenSimpleType.STAR)
            ';' -> return LexTokenSimple(LexTokenSimpleType.SEMICOLON)
            ':' -> return LexTokenSimple(LexTokenSimpleType.COLON)
        }
        if(res == "=" || res == "!") {
            if(index < code.length && code[index] == '=') {
                index++
                if(res == "=") {
                    return LexTokenSimple(LexTokenSimpleType.EQ)
                }else {
                    return LexTokenSimple(LexTokenSimpleType.NE)
                }
            }
        }
        if(res == "=") {
            return LexTokenSimple(LexTokenSimpleType.ASSIGN)
        }
        if(res.first() == '@') {
            res = ""
            while(index < code.length && (code[index].isLetter() || code[index] == '_')) {
                res += code[index++]
            }
            return LexTokenInstructionDefinition(res)
        }
        if(res.first() == '#') {
            res = ""
            while(index < code.length && (code[index].isLetter() || code[index] == '_')) {
                res += code[index++]
            }
            return LexTokenFileHeader(res)
        }
        if(res.first().isLetter()) {
            while(index < code.length && (code[index].isLetterOrDigit() || code[index] == '_')) {
                res += code[index++]
            }
            if(res == "if") {
                return LexTokenSimple(LexTokenSimpleType.KW_IF)
            }else if(res == "null") {
                return LexTokenSimple(LexTokenSimpleType.KW_NULL)
            }else if(res == "let") {
                return LexTokenSimple(LexTokenSimpleType.KW_LET)
            }
            return LexTokenWord(res)
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
        throw NotImplementedError("Failed to lex token\ncode: $code\nvalue: $res")
    }
    val res = mutableListOf<LexToken>()
    while(true) {
        val token = readToken() ?: break
        res.add(token)
    }
    return res
}

interface ParsedToken
interface ParsedTokenStatement : ParsedToken
interface ParsedTokenExpression : ParsedToken {
    fun type(): ParsedTokenExpressionType {
        return when(this) {
            is ParsedTokenExpressionBinary -> type
            is ParsedTokenExpressionTyped -> type
            is ParsedTokenValueAccessor -> type
            is ParsedTokenExpressionNumber -> ParsedTokenExpressionType.INTEGER
            else -> throw IllegalArgumentException("Unknown expression: $this")
        }
    }
}

data class ParsedFile(val fileInfo: Map<String, String>, val definitions: List<ParsedTokenInstructionDefinition>)

data class ParsedTokenValueAccessor(val type: ParsedTokenExpressionType, val scope: ParsedTokenValueAccessorScope, val name: String, var gets: Boolean = false, var sets: Boolean = false) : ParsedTokenExpression
data class ParsedTokenInstructionDefinition(val name: String, val params: List<ParsedTokenValueAccessor>, val code: List<ParsedTokenStatement>, var usesObf: Boolean = false) : ParsedToken

data class ParsedTokenStatementIf(val expression: ParsedTokenExpression, val code: List<ParsedTokenStatement>) : ParsedTokenStatement
data class ParsedTokenStatementFunctionCall(val name: String, val arguments: List<ParsedTokenExpression>, val code: List<ParsedTokenStatement>?) : ParsedTokenStatement
data class ParsedTokenStatementDefineVariable(val accessor: ParsedTokenValueAccessor, val expression: ParsedTokenExpression) : ParsedTokenStatement
data class ParsedTokenStatementAssignVariable(val accessor: ParsedTokenValueAccessor, val expression: ParsedTokenExpression) : ParsedTokenStatement

data class ParsedTokenExpressionNumber(val number: Long) : ParsedTokenExpression
data class ParsedTokenExpressionTyped(val type: ParsedTokenExpressionType) : ParsedTokenExpression
data class ParsedTokenExpressionBinary(val a: ParsedTokenExpression, val b: ParsedTokenExpression, val op: ParsedTokenExpressionOperator, val type: ParsedTokenExpressionType) : ParsedTokenExpression

enum class ParsedTokenValueAccessorScope {
    CONTEXT, INSTRUCTION,
}

enum class ParsedTokenExpressionType {
    NULL, BOOLEAN, INTEGER, SCOPE, OBJECT,
}

enum class ParsedTokenExpressionOperator {
    ADD, SUB, EQ, NE
}

private fun parseExpressionTypeWord(iter: ListIterator<LexToken>): ParsedTokenExpressionType {
    val word = iter.expectToken<LexTokenWord>()
    return when(word.word) {
        "boolean" -> ParsedTokenExpressionType.BOOLEAN
        "int" -> ParsedTokenExpressionType.INTEGER
        "object" -> ParsedTokenExpressionType.OBJECT
        "scope" -> ParsedTokenExpressionType.SCOPE
        else -> throw IllegalArgumentException("Unknown expression type: ${word.word}")
    }
}

private fun parseExpressionAccessorScopeWord(iter: ListIterator<LexToken>): ParsedTokenValueAccessorScope {
    val word = iter.expectToken<LexTokenWord>()
    return when(word.word) {
        "ctx" -> ParsedTokenValueAccessorScope.CONTEXT
        "instr" -> ParsedTokenValueAccessorScope.INSTRUCTION
        else -> throw IllegalArgumentException("Unknown value accessor type: ${word.word}")
    }
}

private fun parseFunctionDefinitionParams(iter: ListIterator<LexToken>): List<ParsedTokenValueAccessor> {
    val res = mutableListOf<ParsedTokenValueAccessor>()
    while(true) {
        if(iter.tryNextToken<LexTokenWord>() == null) {
            break
        }
        iter.previous()
        val type = parseExpressionTypeWord(iter)
        iter.expectTokenType(LexTokenSimpleType.OPEN_BRACKET)
        val scope = parseExpressionAccessorScopeWord(iter)
        iter.expectTokenType(LexTokenSimpleType.COLON)
        val name = iter.expectToken<LexTokenWord>()
        iter.expectTokenType(LexTokenSimpleType.CLOSE_BRACKET)
        res.add(ParsedTokenValueAccessor(type, scope, name.word))
        if(!iter.tryNextTokenType(LexTokenSimpleType.COMMA)) {
            break
        }
    }
    return res
}

private fun parseFunctionCallParams(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>): List<ParsedTokenExpression> {
    val res = mutableListOf<ParsedTokenExpression>()
    while(true) {
        if(iter.tryNextTokenType(LexTokenSimpleType.CLOSE_PAREN)) {
            iter.previous()
            break
        }
        res.add(parseExpression(iter, valueDefinitions))
        if(!iter.tryNextTokenType(LexTokenSimpleType.COMMA)) {
            break
        }
    }
    return res
}

private fun parseExpression(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>): ParsedTokenExpression {
    val exp1 = parseExpression1(iter, valueDefinitions)
    if(!iter.hasNext()) return exp1
    if(iter.tryNextTokenType(LexTokenSimpleType.EQ)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression(iter, valueDefinitions), ParsedTokenExpressionOperator.EQ, ParsedTokenExpressionType.BOOLEAN)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.NE)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression(iter, valueDefinitions), ParsedTokenExpressionOperator.NE, ParsedTokenExpressionType.BOOLEAN)
    }
    return exp1
}

private fun parseExpression1(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>): ParsedTokenExpression {
    val exp1 = parseExpressionBase(iter, valueDefinitions)
    if(!iter.hasNext()) return exp1
    if(iter.tryNextTokenType(LexTokenSimpleType.PLUS)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression1(iter, valueDefinitions), ParsedTokenExpressionOperator.ADD, ParsedTokenExpressionType.INTEGER)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.MINUS)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression1(iter, valueDefinitions), ParsedTokenExpressionOperator.SUB, ParsedTokenExpressionType.INTEGER)
    }
    return exp1
}

private fun parseExpressionBase(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>): ParsedTokenExpression {
    if(!iter.hasNext()) throw IllegalStateException("Expected expression base, got EOF")
    iter.tryNextToken<LexTokenNumber> {
        return ParsedTokenExpressionNumber(it.number.toLong())
    }
    iter.tryNextTokenType(LexTokenSimpleType.KW_NULL) {
        return ParsedTokenExpressionTyped(ParsedTokenExpressionType.NULL)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_BRACKET)) {
        iter.previous()
        return parseValueAccessor(iter, valueDefinitions)
    }
    throw IllegalStateException("Invalid expression base: ${iter.next()}")
}

private fun parseValueAccessor(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>): ParsedTokenValueAccessor {
    iter.expectTokenType(LexTokenSimpleType.OPEN_BRACKET)
    val scope = parseExpressionAccessorScopeWord(iter)
    iter.expectTokenType(LexTokenSimpleType.COLON)
    val name = iter.expectToken<LexTokenWord>()
    iter.expectTokenType(LexTokenSimpleType.CLOSE_BRACKET)
    for(definition in valueDefinitions) {
        if(definition.scope == scope && definition.name == name.word) {
            return definition
        }
    }
    throw IllegalArgumentException("Undefined value accessor: [$scope:${name.word}]")
}

private fun parseCode(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>): List<ParsedTokenStatement> {
    val res = mutableListOf<ParsedTokenStatement>()
    iter.expectTokenType(LexTokenSimpleType.OPEN_BRACE)
    while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_BRACE)) {
        if(iter.tryNextTokenType(LexTokenSimpleType.KW_IF)) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val expression = parseExpression(iter, valueDefinitions)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = parseCode(iter, valueDefinitions)
            res.add(ParsedTokenStatementIf(expression, code))
        }else if(iter.tryNextTokenType(LexTokenSimpleType.KW_LET)) {
            val accessor = parseValueAccessor(iter, valueDefinitions)
            iter.expectTokenType(LexTokenSimpleType.ASSIGN)
            val expression = parseExpression(iter, valueDefinitions)
            iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
            res.add(ParsedTokenStatementDefineVariable(accessor, expression))
        }else if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_BRACKET)) {
            iter.previous()
            val accessor = parseValueAccessor(iter, valueDefinitions)
            iter.expectTokenType(LexTokenSimpleType.ASSIGN)
            val expression = parseExpression(iter, valueDefinitions)
            iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
            res.add(ParsedTokenStatementAssignVariable(accessor, expression))
        }else {
            val name = iter.expectToken<LexTokenWord>()
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val params = parseFunctionCallParams(iter, valueDefinitions)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = if(!iter.tryNextTokenType(LexTokenSimpleType.SEMICOLON)) parseCode(iter, valueDefinitions) else null
            res.add(ParsedTokenStatementFunctionCall(name.word, params, code))
        }
    }
    return res
}

private fun parseTokens(iter: ListIterator<LexToken>): ParsedFile {
    val fileInfo = mutableMapOf<String, String>()
    val instrDefs = mutableListOf<ParsedTokenInstructionDefinition>()
    while(iter.hasNext()) {
        val next = iter.next()
        if(next is LexTokenFileHeader) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val content = iter.expectToken<LexTokenString>()
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            fileInfo[next.name] = content.content
        }else if(next is LexTokenInstructionDefinition) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val params = parseFunctionDefinitionParams(iter)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = parseCode(iter, params)
            instrDefs.add(ParsedTokenInstructionDefinition(next.name, params, code))
        }
    }
    return ParsedFile(fileInfo, instrDefs)
}

private fun ListIterator<LexToken>.expectTokenType(type: LexTokenSimpleType) {
    if(!hasNext()) throw IllegalStateException("Expected token type '$type', got EOF")
    val got = next()
    if(!(got is LexTokenSimple && got.type == type)) {
        throw IllegalStateException("Expected token type '$type', got '$got'")
    }
}

private fun ListIterator<LexToken>.tryNextTokenType(type: LexTokenSimpleType): Boolean {
    if(!hasNext()) return false
    val got = next()
    if(!(got is LexTokenSimple && got.type == type)) {
        previous()
        return false
    }
    return true
}

private inline fun ListIterator<LexToken>.tryNextTokenType(type: LexTokenSimpleType, code: () -> Unit) {
    if(tryNextTokenType(type)) {
        code()
    }
}

private inline fun <reified T: LexToken> ListIterator<LexToken>.expectToken(): T {
    if(!hasNext()) throw IllegalStateException("Expected token '${T::class.java}', got EOF")
    val got = next()
    if(got !is T) {
        throw IllegalStateException("Expected token '${T::class.java}', got '$got'")
    }
    return got
}

private inline fun <reified T: LexToken> ListIterator<LexToken>.tryNextToken(): T? {
    if(!hasNext()) return null
    val got = next()
    if(got !is T) {
        previous()
        return null
    }
    return got
}

private inline fun <reified T: LexToken> ListIterator<LexToken>.tryNextToken(code: (T) -> Unit) {
    val value = tryNextToken<T>()
    if(value != null) {
        code(value)
    }
}

private fun writeExpression(mappingData: MappingData?, expression: ParsedTokenExpression, output: OutputStream) {
    if(expression is ParsedTokenValueAccessor) {
        expression.gets = true
        if(expression.scope == ParsedTokenValueAccessorScope.CONTEXT) {
            output.appendText("context.load(\"${expression.name}\");\n")
            return
        }else if(expression.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
            if(expression.type == ParsedTokenExpressionType.INTEGER) {
                output.appendText("context.getCodeWriter().writeInt(context.getInstruction().<Integer>get(\"${expression.name}\"));\n")
                return
            }
        }
    }else if(expression is ParsedTokenExpressionNumber) {
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
        return
    }else if(expression is ParsedTokenExpressionBinary) {
        if(expression.op == ParsedTokenExpressionOperator.ADD) {
            if(expression.type == ParsedTokenExpressionType.INTEGER) {
                writeExpression(mappingData, expression.a, output)
                writeExpression(mappingData, expression.b, output)
                output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.IADD);\n")
                return
            }
        }else if(expression.op == ParsedTokenExpressionOperator.SUB) {
            if(expression.type == ParsedTokenExpressionType.INTEGER) {
                writeExpression(mappingData, expression.a, output)
                writeExpression(mappingData, expression.b, output)
                output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.ISUB);\n")
                return
            }
        }
    }
    throw UnsupportedOperationException("Unsupported expression: $expression")
}

private fun writeCode(mappingData: MappingData?, code: List<ParsedTokenStatement>, output: OutputStream, onScopeEnd: MutableList<() -> Unit>, uniqId: Int): Int {
    var uid = uniqId
    for(el in code) {
        if(el is ParsedTokenStatementIf) {
            if(el.expression.type() != ParsedTokenExpressionType.BOOLEAN) {
                throw IllegalStateException("in if(<expr>), <expr> must return a boolean")
            }
            if(el.expression !is ParsedTokenExpressionBinary || (el.expression.op != ParsedTokenExpressionOperator.NE && el.expression.op != ParsedTokenExpressionOperator.EQ)) {
                throw IllegalStateException("if(<expr>) only works with if(<expr> != <expr>) or if(<expr> == <expr>)")
            }
            val jumpLabel = "j${uid++}"
            output.appendText("org.objectweb.asm.Label $jumpLabel = new org.objectweb.asm.Label();\n")
            if((el.expression.a.type() == ParsedTokenExpressionType.NULL) xor (el.expression.b.type() == ParsedTokenExpressionType.NULL)) {
                val nonNull = if(el.expression.a.type() == ParsedTokenExpressionType.NULL) el.expression.b else el.expression.a
                writeExpression(mappingData, nonNull, output)
                if(el.expression.op == ParsedTokenExpressionOperator.NE) {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.IFNULL, $jumpLabel);\n")
                }else {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.IFNONNULL, $jumpLabel);\n")
                }
            }else {
                if(el.expression.a.type() != el.expression.b.type()) {
                    throw IllegalStateException("if(<expr> ==/!= <expr>) only works if both <expr> have the same type")
                }
                if(el.expression.a.type() == ParsedTokenExpressionType.INTEGER) {
                    writeExpression(mappingData, el.expression.a, output)
                    writeExpression(mappingData, el.expression.b, output)
                    if(el.expression.op == ParsedTokenExpressionOperator.NE) {
                        output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPEQ, $jumpLabel);\n")
                    }else {
                        output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPNE, $jumpLabel);\n")
                    }
                }else {
                    throw IllegalArgumentException("Unsupported expression comparison type: ${el.expression.a.type()}")
                }
            }
            uid = writeCode(mappingData, el.code, output, mutableListOf(), uid)
            output.appendText("context.getCodeWriter().getMethodVisitor().visitLabel($jumpLabel);\n")
            continue
        }else if(el is ParsedTokenStatementFunctionCall) {
            if(el.name == "run") {
                output.appendText("context.run();\n")
                continue
            }else if(el.name == "scope") {
                val scopeAccessor = el.arguments[0] as ParsedTokenValueAccessor
                scopeAccessor.sets = true
                val jumpLabel = "j${uid++}"
                output.appendText("org.objectweb.asm.Label $jumpLabel = new org.objectweb.asm.Label();\n")
                output.appendText("context.getNewScopes().put(context.getInstruction().<dev.epicpix.minecraftfunctioncompiler.il.ScopeData>get(\"${scopeAccessor.name}\").getId(), $jumpLabel);\n")
                uid = writeCode(mappingData, el.code!!, output, mutableListOf(), uid)
                output.appendText("context.getCodeWriter().getMethodVisitor().visitLabel($jumpLabel);\n")
                continue
            }else if(el.name == "exit_scope") {
                val scopeAccessor = el.arguments[0] as ParsedTokenValueAccessor
                scopeAccessor.gets = true
                output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, context.getNewScopes().get(context.getInstruction().<dev.epicpix.minecraftfunctioncompiler.il.ScopeData>get(\"${scopeAccessor.name}\").getId()));\n")
                continue
            }else {
                throw IllegalArgumentException("Unknown function call: ${el.name}")
            }
        }else if(el is ParsedTokenStatementDefineVariable) {
            writeExpression(mappingData, el.expression, output)
            val assignType = "I"
            val assignTo = el.accessor.name
            el.accessor.sets = true
            val storageLocalUid = "s${uid++}"
            val storageLocal = "l${uid++}"
            output.appendText("String $storageLocalUid = context.getCodeWriter().generateUniqueLocal();\n")
            output.appendText("context.store(\"$assignTo\", $storageLocalUid);\n")
            output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.impl.Local $storageLocal = context.getCodeWriter().createLocal($storageLocalUid, \"$assignType\");\n")
            if(assignType.first() == 'I') {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, $storageLocal.getIndex());\n")
            }else {
                throw IllegalStateException("Unknown assign type: $assignType")
            }
            onScopeEnd.add {
                output.appendText("context.getCodeWriter().destroyLocal($storageLocal);\n")
            }
            continue
        }else if(el is ParsedTokenStatementAssignVariable) {
            writeExpression(mappingData, el.expression, output)
            el.accessor.sets = true
            output.appendText("context.save(\"${el.accessor.name}\");\n")
            continue
        }
        throw Exception(el.toString())
    }
    onScopeEnd.forEach { it() }
    return uid
}

fun parseCode(inputCode: String) = parseTokens(lexTokens(inputCode).listIterator())

fun generateCode(mappingData: MappingData?, definition: ParsedTokenInstructionDefinition, output: OutputStream) {
    val bout = ByteArrayOutputStream()
    writeCode(mappingData, definition.code, bout, mutableListOf(), 0)
    output.appendText("// ${definition.name}\n")
    output.appendText("// ${definition.params.joinToString { 
        "${if(it.gets) "<gets> " else ""}${if(it.sets) "<sets> " else ""}${it.type.name.lowercase()} [${it.scope.name.lowercase()}:${it.name}]"
    }}\n")
    output.write(bout.toByteArray())
}
