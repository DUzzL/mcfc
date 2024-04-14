package dev.epicpix.annotations

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

private interface LexToken

private data class LexTokenFileHeader(val name: String) : LexToken
private data class LexTokenInstructionDefinition(val name: String) : LexToken
private data class LexTokenFunctionDefinition(val name: String) : LexToken
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
    OPEN_ANGLE_BRACKET,
    CLOSE_ANGLE_BRACKET,
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
    KW_ELSE,
    KW_WHILE,
    KW_NULL,
    KW_LET,
    KW_FALSE,
    KW_TRUE,
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
        while(res.first() == '/' && index < code.length && code[index] == '/') {
            index++
            while(index < code.length && !" \t\n".contains(code[index])) {
                index++
            }
            index++
            if(index >= code.length) return null
        }
        when(res.first()) {
            '{' -> return LexTokenSimple(LexTokenSimpleType.OPEN_BRACE)
            '}' -> return LexTokenSimple(LexTokenSimpleType.CLOSE_BRACE)
            '[' -> return LexTokenSimple(LexTokenSimpleType.OPEN_BRACKET)
            ']' -> return LexTokenSimple(LexTokenSimpleType.CLOSE_BRACKET)
            '(' -> return LexTokenSimple(LexTokenSimpleType.OPEN_PAREN)
            ')' -> return LexTokenSimple(LexTokenSimpleType.CLOSE_PAREN)
            '<' -> return LexTokenSimple(LexTokenSimpleType.OPEN_ANGLE_BRACKET)
            '>' -> return LexTokenSimple(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)
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
        if(res.first() == '$') {
            res = ""
            while(index < code.length && (code[index].isLetter() || code[index] == '_')) {
                res += code[index++]
            }
            return LexTokenFunctionDefinition(res)
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
            }else if(res == "else") {
                return LexTokenSimple(LexTokenSimpleType.KW_ELSE)
            }else if(res == "while") {
                return LexTokenSimple(LexTokenSimpleType.KW_WHILE)
            }else if(res == "null") {
                return LexTokenSimple(LexTokenSimpleType.KW_NULL)
            }else if(res == "let") {
                return LexTokenSimple(LexTokenSimpleType.KW_LET)
            }else if(res == "false") {
                return LexTokenSimple(LexTokenSimpleType.KW_FALSE)
            }else if(res == "true") {
                return LexTokenSimple(LexTokenSimpleType.KW_TRUE)
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

data class DefinitionInfo(val externalFunctionNames: List<String>, val functionMappings: Map<String, String>) {
    fun isObfuscated(key: String): Boolean {
        if(functionMappings[key] == null) return true
        val mapping = functionMappings[key]!!
        return mapping.split(" ")[4] == "true"
    }
}

interface ParsedToken
interface ParsedTokenStatement : ParsedToken
interface ParsedTokenExpression : ParsedToken

data class ParsedFile(val fileInfo: Map<String, String>, val definitions: List<ParsedTokenInstructionDefinition>, val functions: List<ParsedTokenFunctionDefinition>)

data class ParsedTokenValueAccessor(val type: ParsedTokenExpressionType, val scope: ParsedTokenValueAccessorScope, val name: String, var gets: Boolean = false, var sets: Boolean = false) : ParsedTokenExpression
data class ParsedTokenInstructionDefinition(val name: String, val params: List<ParsedTokenValueAccessor>, val code: List<ParsedTokenStatement>, val definitionInfo: DefinitionInfo, var usesObf: Boolean = false) : ParsedToken
data class ParsedTokenFunctionDefinition(val name: String, val params: List<ParsedTokenValueAccessor>, val code: List<ParsedTokenStatement>, val definitionInfo: DefinitionInfo, var usesObf: Boolean = false) : ParsedToken

data class ParsedTokenStatementIf(val expression: ParsedTokenExpression, val code: List<ParsedTokenStatement>, val elseCode: List<ParsedTokenStatement>?) : ParsedTokenStatement
data class ParsedTokenStatementWhile(val expression: ParsedTokenExpression, val code: List<ParsedTokenStatement>) : ParsedTokenStatement
data class ParsedTokenStatementFunctionCall(val name: String, val arguments: List<ParsedTokenExpression>, val code: List<ParsedTokenStatement>?, val isOutsideCall: Boolean) : ParsedTokenStatement, ParsedTokenExpression
data class ParsedTokenStatementDefineVariable(val accessor: ParsedTokenValueAccessor, val expression: ParsedTokenExpression?) : ParsedTokenStatement
data class ParsedTokenStatementAssignVariable(val accessor: ParsedTokenValueAccessor, val expression: ParsedTokenExpression) : ParsedTokenStatement

data class ParsedTokenExpressionNumber(val number: Long) : ParsedTokenExpression
data class ParsedTokenExpressionTyped(val type: ParsedTokenExpressionType) : ParsedTokenExpression
data class ParsedTokenExpressionBinary(val a: ParsedTokenExpression, val b: ParsedTokenExpression, val op: ParsedTokenExpressionOperator) : ParsedTokenExpression

enum class ParsedTokenValueAccessorScope {
    CONTEXT, INSTRUCTION, LOCAL, STATIC,
}

interface ParsedTokenExpressionType
data class ParsedTokenExpressionSimpleTyped(val type: ParsedTokenExpressionSimpleType) : ParsedTokenExpressionType
data class ParsedTokenExpressionTypeComplex(val typeName: String) : ParsedTokenExpressionType
data class ParsedTokenExpressionTypeYield(val yieldType: ParsedTokenExpressionType) : ParsedTokenExpressionType

enum class ParsedTokenExpressionSimpleType {
    NULL, BOOLEAN, INTEGER, SCOPE, OBJECT, VOID,
}

enum class ParsedTokenExpressionOperator {
    ADD, SUB, EQ, NE, GT, LT,
}

private fun parseExpressionTypeWord(iter: ListIterator<LexToken>): ParsedTokenExpressionType {
    if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
        var t = ""
        while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)) {
            if(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
                t += "."
            }else {
                t += iter.expectToken<LexTokenWord>().word
            }
        }
        return ParsedTokenExpressionTypeComplex(t)
    }
    val word = iter.expectToken<LexTokenWord>()
    return when(word.word) {
        "boolean" -> ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)
        "int" -> ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)
        "object" -> ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.OBJECT)
        "scope" -> ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.SCOPE)
        "yield" -> {
            iter.expectTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)
            var t = ""
            while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)) {
                if(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
                    t += "."
                }else {
                    t += iter.expectToken<LexTokenWord>().word
                }
            }
            ParsedTokenExpressionTypeYield(when(t) {
                "boolean" -> ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)
                "int" -> ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)
                "object" -> ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.OBJECT)
                "scope" -> ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.SCOPE)
                else -> ParsedTokenExpressionTypeComplex(t)
            })
        }
        else -> throw IllegalArgumentException("Unknown expression type: ${word.word}")
    }
}

private fun parseExpressionAccessorScopeWord(iter: ListIterator<LexToken>): ParsedTokenValueAccessorScope {
    val word = iter.expectToken<LexTokenWord>()
    return when(word.word) {
        "ctx" -> ParsedTokenValueAccessorScope.CONTEXT
        "instr" -> ParsedTokenValueAccessorScope.INSTRUCTION
        "local" -> ParsedTokenValueAccessorScope.LOCAL
        "static" -> ParsedTokenValueAccessorScope.STATIC
        else -> throw IllegalArgumentException("Unknown value accessor type: ${word.word}")
    }
}

private fun parseFunctionDefinitionParams(iter: ListIterator<LexToken>): List<ParsedTokenValueAccessor> {
    val res = mutableListOf<ParsedTokenValueAccessor>()
    while(true) {
        if(iter.tryNextToken<LexTokenWord>() == null && !iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
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

private fun parseFunctionCallParams(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, definitions: DefinitionInfo, usesObf: AtomicBoolean): List<ParsedTokenExpression> {
    val res = mutableListOf<ParsedTokenExpression>()
    while(true) {
        if(iter.tryNextTokenType(LexTokenSimpleType.CLOSE_PAREN)) {
            iter.previous()
            break
        }
        res.add(parseExpression(iter, valueDefinitions, definitions, usesObf))
        if(!iter.tryNextTokenType(LexTokenSimpleType.COMMA)) {
            break
        }
    }
    return res
}

private fun parseExpression(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, definitions: DefinitionInfo, usesObf: AtomicBoolean): ParsedTokenExpression {
    val exp1 = parseExpression1(iter, valueDefinitions, definitions, usesObf)
    if(!iter.hasNext()) return exp1
    if(iter.tryNextTokenType(LexTokenSimpleType.EQ)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression(iter, valueDefinitions, definitions, usesObf), ParsedTokenExpressionOperator.EQ)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.NE)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression(iter, valueDefinitions, definitions, usesObf), ParsedTokenExpressionOperator.NE)
    }
    return exp1
}

private fun parseExpression1(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, definitions: DefinitionInfo, usesObf: AtomicBoolean): ParsedTokenExpression {
    val exp1 = parseExpression2(iter, valueDefinitions, definitions, usesObf)
    if(!iter.hasNext()) return exp1
    if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression1(iter, valueDefinitions, definitions, usesObf), ParsedTokenExpressionOperator.LT)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression1(iter, valueDefinitions, definitions, usesObf), ParsedTokenExpressionOperator.GT)
    }
    return exp1
}

private fun parseExpression2(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, definitions: DefinitionInfo, usesObf: AtomicBoolean): ParsedTokenExpression {
    val exp1 = parseExpressionBase(iter, valueDefinitions, definitions, usesObf)
    if(!iter.hasNext()) return exp1
    if(iter.tryNextTokenType(LexTokenSimpleType.PLUS)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression2(iter, valueDefinitions, definitions, usesObf), ParsedTokenExpressionOperator.ADD)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.MINUS)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression2(iter, valueDefinitions, definitions, usesObf), ParsedTokenExpressionOperator.SUB)
    }
    return exp1
}

private fun parseExpressionBase(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, definitions: DefinitionInfo, usesObf: AtomicBoolean): ParsedTokenExpression {
    if(!iter.hasNext()) throw IllegalStateException("Expected expression base, got EOF")
    iter.tryNextToken<LexTokenNumber> {
        return ParsedTokenExpressionNumber(it.number.toLong())
    }
    iter.tryNextTokenType(LexTokenSimpleType.KW_NULL) {
        return ParsedTokenExpressionTyped(ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.NULL))
    }
    iter.tryNextTokenType(LexTokenSimpleType.KW_FALSE) {
        return ParsedTokenExpressionNumber(0)
    }
    iter.tryNextTokenType(LexTokenSimpleType.KW_TRUE) {
        return ParsedTokenExpressionNumber(1)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_BRACKET)) {
        iter.previous()
        return parseValueAccessor(iter, valueDefinitions)
    }
    val name = iter.expectToken<LexTokenWord>()
    iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
    val params = parseFunctionCallParams(iter, valueDefinitions, definitions, usesObf)
    iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
    if(definitions.externalFunctionNames.contains(name.word) && definitions.isObfuscated(name.word)) {
        usesObf.set(true)
    }
    return ParsedTokenStatementFunctionCall(name.word, params, null, definitions.externalFunctionNames.contains(name.word))
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

private fun parseCode(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, definitions: DefinitionInfo, usesObf: AtomicBoolean): List<ParsedTokenStatement> {
    val res = mutableListOf<ParsedTokenStatement>()
    iter.expectTokenType(LexTokenSimpleType.OPEN_BRACE)
    while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_BRACE)) {
        if(iter.tryNextTokenType(LexTokenSimpleType.KW_IF)) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val expression = parseExpression(iter, valueDefinitions, definitions, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = parseCode(iter, valueDefinitions, definitions, usesObf)
            if(iter.tryNextTokenType(LexTokenSimpleType.KW_ELSE)) {
                val elseCode = parseCode(iter, valueDefinitions, definitions, usesObf)
                res.add(ParsedTokenStatementIf(expression, code, elseCode))
            } else {
                res.add(ParsedTokenStatementIf(expression, code, null))
            }
        }else if(iter.tryNextTokenType(LexTokenSimpleType.KW_WHILE)) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val expression = parseExpression(iter, valueDefinitions, definitions, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = parseCode(iter, valueDefinitions, definitions, usesObf)
            res.add(ParsedTokenStatementWhile(expression, code))
        }else if(iter.tryNextTokenType(LexTokenSimpleType.KW_LET)) {
            val accessor = parseValueAccessor(iter, valueDefinitions)
            if(iter.tryNextTokenType(LexTokenSimpleType.SEMICOLON)) {
                res.add(ParsedTokenStatementDefineVariable(accessor, null))
            }else {
                iter.expectTokenType(LexTokenSimpleType.ASSIGN)
                val expression = parseExpression(iter, valueDefinitions, definitions, usesObf)
                iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
                res.add(ParsedTokenStatementDefineVariable(accessor, expression))
            }
        }else if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_BRACKET)) {
            iter.previous()
            val accessor = parseValueAccessor(iter, valueDefinitions)
            iter.expectTokenType(LexTokenSimpleType.ASSIGN)
            val expression = parseExpression(iter, valueDefinitions, definitions, usesObf)
            iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
            res.add(ParsedTokenStatementAssignVariable(accessor, expression))
        }else {
            val name = iter.expectToken<LexTokenWord>()
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val params = parseFunctionCallParams(iter, valueDefinitions, definitions, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = if(!iter.tryNextTokenType(LexTokenSimpleType.SEMICOLON)) parseCode(iter, valueDefinitions, definitions, usesObf) else null
            if(definitions.externalFunctionNames.contains(name.word) && definitions.isObfuscated(name.word)) {
                usesObf.set(true)
            }
            res.add(ParsedTokenStatementFunctionCall(name.word, params, code, code == null && definitions.externalFunctionNames.contains(name.word)))
        }
    }
    return res
}

private fun parseTokens(iter: ListIterator<LexToken>, loadDefinitionCallback: (Any?) -> String?): ParsedFile {
    val fileInfo = mutableMapOf<String, String>()
    val instrDefs = mutableListOf<ParsedTokenInstructionDefinition>()
    val funcDefs = mutableListOf<ParsedTokenFunctionDefinition>()
    val allFunctionNames = mutableListOf<String>()
    val functionsDefinitions = mutableMapOf<String, String>()
    while(iter.hasNext()) {
        val next = iter.next()
        if(next is LexTokenFileHeader) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val content = iter.expectToken<LexTokenString>()
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            fileInfo[next.name] = content.content
            if(next.name == "definitions") {
                val files = content.content.split(',')
                for(file in files) {
                    val toml = loadToml(file)
                    (toml["definitions_functions"] as? Map<*, *>)?.forEach { (k, v) ->
                        allFunctionNames.add(k.toString())
                        val def = loadDefinitionCallback(v)
                        if(def != null) {
                            functionsDefinitions[k.toString()] = def
                        }
                    }
                }
            }
        }else if(next is LexTokenInstructionDefinition) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val params = parseFunctionDefinitionParams(iter)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val defInfo = DefinitionInfo(allFunctionNames, functionsDefinitions)
            val usesObf = AtomicBoolean()
            val code = parseCode(iter, params, defInfo, usesObf)
            instrDefs.add(ParsedTokenInstructionDefinition(next.name, params, code, defInfo, usesObf.get()))
        }else if(next is LexTokenFunctionDefinition) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val params = parseFunctionDefinitionParams(iter)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val defInfo = DefinitionInfo(allFunctionNames, functionsDefinitions)
            val usesObf = AtomicBoolean()
            val code = parseCode(iter, params, defInfo, usesObf)
            funcDefs.add(ParsedTokenFunctionDefinition(next.name, params, code, defInfo, usesObf.get()))
        }
    }
    return ParsedFile(fileInfo, instrDefs, funcDefs)
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

private fun evaluateExpressionType(mappingData: MappingData?, functionDefinitions: Map<String, String>, expression: ParsedTokenExpression): ParsedTokenExpressionType {
    if(expression is ParsedTokenExpressionTyped) {
        return expression.type
    }else if(expression is ParsedTokenExpressionBinary) {
        val a = evaluateExpressionType(mappingData, functionDefinitions, expression.a)
        val b = evaluateExpressionType(mappingData, functionDefinitions, expression.b)
        if(expression.op == ParsedTokenExpressionOperator.ADD || expression.op == ParsedTokenExpressionOperator.SUB) {
            if(a.isSimpleType(ParsedTokenExpressionSimpleType.INTEGER) && b.isSimpleType(ParsedTokenExpressionSimpleType.INTEGER)) {
                return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)
            }
        }else if(expression.op == ParsedTokenExpressionOperator.NE || expression.op == ParsedTokenExpressionOperator.EQ) {
            if(a.isIntegerLikeType() && b.isIntegerLikeType()) {
                return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)
            }else if(a.isObjectLike() && b.isObjectLike()) {
                return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)
            }
        }else if(expression.op == ParsedTokenExpressionOperator.LT || expression.op == ParsedTokenExpressionOperator.GT) {
            if(a.isIntegerLikeType() && b.isIntegerLikeType()) {
                return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)
            }
        }
    }else if(expression is ParsedTokenValueAccessor) {
        return expression.type
    }else if(expression is ParsedTokenExpressionNumber) {
        return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)
    }else if(expression is ParsedTokenStatementFunctionCall) {
        val decl = functionDefinitions[expression.name]!!
        val def = decl.split(" ")[2].run { substring(lastIndexOf(')')+1) }
        if(def == "Z") {
            return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)
        }
        if(def == "I") {
            return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)
        }
        if(def.startsWith("L")) {
            return ParsedTokenExpressionTypeComplex(def.substring(1, def.length - 1).replace('/', '.'))
        }
        TODO(decl)
    }
    TODO("$expression")
}

private fun ParsedTokenExpressionType.isObjectLike(): Boolean {
    return this is ParsedTokenExpressionTypeComplex || isSimpleType(ParsedTokenExpressionSimpleType.NULL) || isSimpleType(ParsedTokenExpressionSimpleType.OBJECT)
}

private fun ParsedTokenExpressionType.isSimpleType(type: ParsedTokenExpressionSimpleType): Boolean {
    return this is ParsedTokenExpressionSimpleTyped && this.type == type
}

private fun ParsedTokenExpressionType.isIntegerLikeType(): Boolean {
    return this is ParsedTokenExpressionSimpleTyped && (this.type == ParsedTokenExpressionSimpleType.INTEGER || this.type == ParsedTokenExpressionSimpleType.BOOLEAN)
}

private fun isExpressionSimpleType(mappingData: MappingData?, functionDefinitions: Map<String, String>, expression: ParsedTokenExpression, type: ParsedTokenExpressionSimpleType): Boolean {
    return evaluateExpressionType(mappingData, functionDefinitions, expression).isSimpleType(type)
}

private fun writeExpression(mappingData: MappingData?, functionDefinitions: Map<String, String>, accessorMappings: Map<ParsedTokenValueAccessor, ParsedTokenValueAccessor>, localMappings: MutableMap<String, String>, expression: ParsedTokenExpression, file: ParsedFile, output: OutputStream, uniqId: Int, keepResult: Boolean = true): Int {
    var expression = expression
    if(expression is ParsedTokenValueAccessor) {
        expression.gets = true
        while(true) {
            val mapping = accessorMappings.entries.find {
                val expression = expression as ParsedTokenValueAccessor
                it.key.name == expression.name && it.key.type == expression.type && it.key.scope == expression.scope
            }?.value
            if(mapping != null) {
                expression = mapping
            }else {
                break
            }
        }
        expression as ParsedTokenValueAccessor
        if(expression.scope == ParsedTokenValueAccessorScope.CONTEXT) {
            if(keepResult) {
                output.appendText("context.load(\"${expression.name}\");\n")
            }
            return uniqId
        }else if(expression.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
            if(expression.type.isSimpleType(ParsedTokenExpressionSimpleType.INTEGER)) {
                if(keepResult) {
                    output.appendText("context.getCodeWriter().writeInt(context.getInstruction().<Integer>get(\"${expression.name}\"));\n")
                }
                return uniqId
            }else if(expression.type.isSimpleType(ParsedTokenExpressionSimpleType.BOOLEAN)) {
                if(keepResult) {
                    output.appendText("context.getCodeWriter().writeInt(context.getInstruction().<Boolean>get(\"${expression.name}\") ? 1 : 0);\n")
                }
                return uniqId
            }else if(expression.type is ParsedTokenExpressionTypeComplex && (expression.type as ParsedTokenExpressionTypeComplex).typeName == "java.lang.String") {
                if(keepResult) {
                    output.appendText("context.getCodeWriter().writeString(context.getInstruction().<java.lang.String>get(\"${expression.name}\"));\n")
                }
                return uniqId
            }
        }else if(expression.scope == ParsedTokenValueAccessorScope.LOCAL) {
            if(localMappings[expression.name] == null) {
                throw IllegalStateException("Mappings for local variable '${expression.name}' is null")
            }
            if(keepResult) {
                if(expression.type.isIntegerLikeType()) {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, ${localMappings[expression.name]}.getIndex());\n")
                    return uniqId
                }else {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, ${localMappings[expression.name]}.getIndex());\n")
                    return uniqId
                }
            }
            return uniqId
        }else if(expression.scope == ParsedTokenValueAccessorScope.STATIC) {
            if(keepResult) {
                output.appendText("context.getCodeWriter().loadLocal(\"static\$${expression.name}\");\n")
            }
            return uniqId
        }
    }else if(expression is ParsedTokenExpressionNumber) {
        if(keepResult) {
            val value = expression.number.toInt()
            if(value >= -1 && value <= 5) {
                if(value == -1) {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.ICONST_M1);\n")
                } else {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.ICONST_$value);\n")
                }
            } else if(value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitIntInsn(org.objectweb.asm.Opcodes.BIPUSH, $value);\n")
            } else if(value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitIntInsn(org.objectweb.asm.Opcodes.SIPUSH, $value);\n")
            } else {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitLdcInsn($value);\n")
            }
        }
        return uniqId
    }else if(expression is ParsedTokenExpressionBinary) {
        if(expression.op == ParsedTokenExpressionOperator.ADD) {
            if(isExpressionSimpleType(mappingData, functionDefinitions, expression, ParsedTokenExpressionSimpleType.INTEGER)) {
                var uid = uniqId
                uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), expression.a, file, output, uid)
                uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), expression.b, file, output, uid)
                if(keepResult) {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.IADD);\n")
                }else {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.POP2);\n")
                }
                return uid
            }
        }else if(expression.op == ParsedTokenExpressionOperator.SUB) {
            if(isExpressionSimpleType(mappingData, functionDefinitions, expression, ParsedTokenExpressionSimpleType.INTEGER)) {
                var uid = uniqId
                uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), expression.a, file, output, uid)
                uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), expression.b, file, output, uid)
                if(keepResult) {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.ISUB);\n")
                }else {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.POP2);\n")
                }
                return uid
            }
        }
    }else if(expression is ParsedTokenStatementFunctionCall) {
        if(expression.isOutsideCall) {
            val def = functionDefinitions[expression.name]!!
            val className = def.split(" ")[0]
            val methodName = def.split(" ")[1]
            val methodDescriptor = def.split(" ")[2]
            val opcode = def.split(" ")[3]
            val isInterface = def.split(" ")[5]
            var uid = uniqId
            for(arg in expression.arguments) {
                uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), arg, file, output, uid)
            }
            output.appendText("context.getCodeWriter().getMethodVisitor().visitMethodInsn($opcode, \"$className\", \"$methodName\", \"$methodDescriptor\", $isInterface);\n")
            if(!keepResult && !methodDescriptor.endsWith(")V")) {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitInsn(org.objectweb.asm.Opcodes.POP);\n")
            }
            return uid
        }else {
            val func = file.functions.find { it.name == expression.name }
            if(func != null) {
                val requiredArgs = func.params.filter { it.scope != ParsedTokenValueAccessorScope.STATIC && it.scope != ParsedTokenValueAccessorScope.LOCAL }
                if(requiredArgs.size != expression.arguments.size) {
                    throw UnsupportedOperationException("Wrong function call to '${expression.name}' argument count: expected ${requiredArgs.size}, got ${expression.arguments.size}")
                }

                val yields = mutableMapOf<String, (ParsedTokenExpression, OutputStream, MutableMap<String, String>, Int) -> Int>()
                val newMappings = mutableMapOf<ParsedTokenValueAccessor, ParsedTokenValueAccessor>()
                for(argIndex in requiredArgs.indices) {
                    val arg = requiredArgs[argIndex]
                    if(arg.scope == ParsedTokenValueAccessorScope.CONTEXT && arg.type !is ParsedTokenExpressionTypeYield) {
                        newMappings[arg] = expression.arguments[argIndex] as ParsedTokenValueAccessor
                    }
                }

                if(requiredArgs.isNotEmpty()) {
                    val isYieldFunc = requiredArgs.last().type is ParsedTokenExpressionTypeYield
                    if(isYieldFunc) {
                        if(expression.code == null) {
                            throw UnsupportedOperationException("Wrong function call to '${expression.name}': expected function code for yield, got none")
                        }
                        val lastArg = requiredArgs.last()
                        yields[lastArg.name] = { param, output, localMappings, uniqueId ->
                            val expectedType = evaluateExpressionType(mappingData, functionDefinitions, expression.arguments.last())
                            var uid = uniqueId
                            uid = writeExpression(mappingData, functionDefinitions, accessorMappings + newMappings, localMappings.toMutableMap(), param, file, output, uid)
                            if(evaluateExpressionType(mappingData, functionDefinitions, param) != expectedType) {
                                output.appendText("context.getCodeWriter().getMethodVisitor().visitTypeInsn(org.objectweb.asm.Opcodes.CHECKCAST, \"${(expectedType as ParsedTokenExpressionTypeComplex).typeName.replace('.', '/')}\");\n")
                            }
                            val yieldFuncAccessor = expression.arguments.last() as ParsedTokenValueAccessor
                            yieldFuncAccessor.sets = true
                            output.appendText("context.save(\"${yieldFuncAccessor.name}\");\n")
                            output.appendText("{ // ${func.name} - yield([${lastArg.scope}:${lastArg.name}])\n")
                            val output2 = ByteArrayOutputStream()
                            uid = writeCode(mappingData, functionDefinitions, accessorMappings + newMappings, localMappings.toMutableMap(), expression.code!!, file, output2, mutableListOf(), uid)
                            output.appendText(output2.toByteArray().decodeToString().prependIndent("    ").run { substring(0, length - 4) })
                            output.appendText("}\n")
                            uid
                        }
                    }
                }

                output.appendText("{ // ${func.name}\n")
                val output2 = ByteArrayOutputStream()
                val uid = writeCode(mappingData, functionDefinitions, accessorMappings + newMappings, localMappings.toMutableMap(), func.code, file, output2, mutableListOf(), uniqId, yields)
                output.appendText(output2.toByteArray().decodeToString().prependIndent("    ").run { substring(0, length - 4) })
                output.appendText("}\n")
                return uid
            }
            throw UnsupportedOperationException("Unknown function call: ${expression.name}")
        }
    }
    throw UnsupportedOperationException("Unsupported expression: $expression")
}

private fun writeComparison(mappingData: MappingData?, functionDefinitions: Map<String, String>, accessorMappings: Map<ParsedTokenValueAccessor, ParsedTokenValueAccessor>, localMappings: MutableMap<String, String>, expression: ParsedTokenExpression, file: ParsedFile, output: OutputStream, failureLabel: String, uniqId: Int): Int {
    var uid = uniqId
    if(!isExpressionSimpleType(mappingData, functionDefinitions, expression, ParsedTokenExpressionSimpleType.BOOLEAN)) {
        throw IllegalStateException("comparison <expr> must return a boolean")
    }
    if(expression !is ParsedTokenExpressionBinary) {
        throw IllegalStateException("comparison must use a binary operator")
    }
    if(expression.op == ParsedTokenExpressionOperator.NE || expression.op == ParsedTokenExpressionOperator.EQ) {
        if(isExpressionSimpleType(mappingData, functionDefinitions, expression.a, ParsedTokenExpressionSimpleType.NULL) xor isExpressionSimpleType(mappingData, functionDefinitions, expression.b, ParsedTokenExpressionSimpleType.NULL)) {
            val nonNull = if(isExpressionSimpleType(mappingData, functionDefinitions, expression.a, ParsedTokenExpressionSimpleType.NULL)) expression.b else expression.a
            uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), nonNull, file, output, uid)
            if(expression.op == ParsedTokenExpressionOperator.NE) {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.IFNULL, $failureLabel);\n")
            }else {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.IFNONNULL, $failureLabel);\n")
            }
        }else {
            val typeA = evaluateExpressionType(mappingData, functionDefinitions, expression.a)
            val typeB = evaluateExpressionType(mappingData, functionDefinitions, expression.b)
            if(typeA != typeB && (!typeA.isIntegerLikeType() || !typeB.isIntegerLikeType())) {
                throw IllegalStateException("if(<expr> ==/!= <expr>) only works if both <expr> have the same type")
            }
            if(isExpressionSimpleType(mappingData, functionDefinitions, expression.a, ParsedTokenExpressionSimpleType.INTEGER) || isExpressionSimpleType(mappingData, functionDefinitions, expression.a, ParsedTokenExpressionSimpleType.BOOLEAN)) {
                uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), expression.a, file, output, uid)
                uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), expression.b, file, output, uid)
                if(expression.op == ParsedTokenExpressionOperator.NE) {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPEQ, $failureLabel);\n")
                }else {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPNE, $failureLabel);\n")
                }
            }else {
                throw IllegalArgumentException("Unsupported expression comparison type: ${evaluateExpressionType(mappingData, functionDefinitions, expression.a)}")
            }
        }
        return uid
    }else if(expression.op == ParsedTokenExpressionOperator.LT || expression.op == ParsedTokenExpressionOperator.GT) {
        if(isExpressionSimpleType(mappingData, functionDefinitions, expression.a, ParsedTokenExpressionSimpleType.INTEGER) && isExpressionSimpleType(mappingData, functionDefinitions, expression.b, ParsedTokenExpressionSimpleType.INTEGER)) {
            uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), expression.a, file, output, uid)
            uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), expression.b, file, output, uid)
            if(expression.op == ParsedTokenExpressionOperator.LT) {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPGE, $failureLabel);\n")
            }else {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPLE, $failureLabel);\n")
            }
            return uid
        }else {
            throw IllegalArgumentException("Unsupported expression comparison type: ${evaluateExpressionType(mappingData, functionDefinitions, expression.a)} ${expression.op} ${evaluateExpressionType(mappingData, functionDefinitions, expression.b)}")
        }
    }else {
        throw IllegalArgumentException("Unsupported comparison binary operator: ${expression.op}")
    }
}

private fun writeCode(mappingData: MappingData?, functionDefinitions: Map<String, String>, accessorMappings: Map<ParsedTokenValueAccessor, ParsedTokenValueAccessor>, localMappings: MutableMap<String, String>, code: List<ParsedTokenStatement>, file: ParsedFile, output: OutputStream, onScopeEnd: MutableList<() -> Unit>, uniqId: Int, yieldCallbacks: Map<String, (ParsedTokenExpression, OutputStream, MutableMap<String, String>, Int) -> Int> = mapOf()): Int {
    var uid = uniqId
    for(el in code) {
        if(el is ParsedTokenStatementIf) {
            val failureLabel = "j${uid++}"
            output.appendText("org.objectweb.asm.Label $failureLabel = new org.objectweb.asm.Label();\n")
            uid = writeComparison(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), el.expression, file, output, failureLabel, uid)
            uid = writeCode(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), el.code, file, output, mutableListOf(), uid, yieldCallbacks)
            if(el.elseCode != null) {
                val jumpLabel = "j${uid++}"
                output.appendText("org.objectweb.asm.Label $jumpLabel = new org.objectweb.asm.Label();\n")
                output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, $jumpLabel);\n")
                output.appendText("context.getCodeWriter().getMethodVisitor().visitLabel($failureLabel);\n")
                uid = writeCode(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), el.elseCode, file, output, mutableListOf(), uid, yieldCallbacks)
                output.appendText("context.getCodeWriter().getMethodVisitor().visitLabel($jumpLabel);\n")
            }else {
                output.appendText("context.getCodeWriter().getMethodVisitor().visitLabel($failureLabel);\n")
            }
            continue
        }else if(el is ParsedTokenStatementWhile) {
            val failureLabel = "j${uid++}"
            output.appendText("org.objectweb.asm.Label $failureLabel = new org.objectweb.asm.Label();\n")
            val startLoopLabel = "j${uid++}"
            output.appendText("org.objectweb.asm.Label $startLoopLabel = new org.objectweb.asm.Label();\n")
            output.appendText("context.getCodeWriter().getMethodVisitor().visitLabel($startLoopLabel);\n")
            uid = writeComparison(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), el.expression, file, output, failureLabel, uid)
            uid = writeCode(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), el.code, file, output, mutableListOf(), uid, yieldCallbacks)
            output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, $startLoopLabel);\n")
            output.appendText("context.getCodeWriter().getMethodVisitor().visitLabel($failureLabel);\n")
            continue
        }else if(el is ParsedTokenStatementFunctionCall) {
            if(el.name == "run") {
                output.appendText("context.run();\n")
            }else if(el.name == "scope") {
                val scopeAccessor = el.arguments[0] as ParsedTokenValueAccessor
                scopeAccessor.sets = true
                val jumpLabel = "j${uid++}"
                output.appendText("org.objectweb.asm.Label $jumpLabel = new org.objectweb.asm.Label();\n")
                output.appendText("context.getNewScopes().put(context.getInstruction().<dev.epicpix.minecraftfunctioncompiler.il.ScopeData>get(\"${scopeAccessor.name}\").getId(), $jumpLabel);\n")
                uid = writeCode(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), el.code!!, file, output, mutableListOf(), uid, yieldCallbacks)
                output.appendText("context.getCodeWriter().getMethodVisitor().visitLabel($jumpLabel);\n")
            }else if(el.name == "exit_scope") {
                val scopeAccessor = el.arguments[0] as ParsedTokenValueAccessor
                scopeAccessor.gets = true
                output.appendText("context.getCodeWriter().getMethodVisitor().visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, context.getNewScopes().get(context.getInstruction().<dev.epicpix.minecraftfunctioncompiler.il.ScopeData>get(\"${scopeAccessor.name}\").getId()));\n")
            }else if(el.name == "yield") {
                val yieldObj = el.arguments[0] as ParsedTokenValueAccessor
                yieldObj.gets = true
                if(yieldObj.type !is ParsedTokenExpressionTypeYield) {
                    throw IllegalArgumentException("Tried to yield() on a non-yield argument (${yieldObj.name})")
                }
                val value = el.arguments[1]
                val cb = yieldCallbacks[yieldObj.name] ?: throw IllegalArgumentException("yield(${yieldObj.name}) did not have a callback")
                uid = cb(value, output, localMappings.toMutableMap(), uid)
            }else {
                uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), el, file, output, uid, false)
            }
            continue
        }else if(el is ParsedTokenStatementDefineVariable) {
            if(el.expression != null) {
                uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), el.expression, file, output, uid)
            }
            val assignType = if(el.accessor.type.isSimpleType(ParsedTokenExpressionSimpleType.INTEGER)) {
                "I"
            }else if(el.accessor.type is ParsedTokenExpressionTypeComplex) {
                "L${mappingData.getClassName(el.accessor.type.typeName.replace('.', '/'))};"
            }else throw UnsupportedOperationException("Unknown type: ${el.accessor}")
            el.accessor.sets = true
            val storageLocal = "l${uid++}"
            when(el.accessor.scope) {
                ParsedTokenValueAccessorScope.CONTEXT,
                ParsedTokenValueAccessorScope.INSTRUCTION -> {
                    val storageLocalUid = "s${uid++}"
                    output.appendText("String $storageLocalUid = context.getCodeWriter().generateUniqueLocal();\n")
                    output.appendText("context.store(\"${el.accessor.name}\", $storageLocalUid);\n")
                    output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.impl.Local $storageLocal = context.getCodeWriter().createLocal($storageLocalUid, \"$assignType\");\n")
                }
                ParsedTokenValueAccessorScope.LOCAL -> {
                    output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.impl.Local $storageLocal = context.getCodeWriter().createLocal(context.getCodeWriter().generateUniqueLocal(), \"$assignType\");\n")
                    localMappings[el.accessor.name] = storageLocal
                }
                ParsedTokenValueAccessorScope.STATIC -> {
                    output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.impl.Local $storageLocal = context.getCodeWriter().createLocal(\"static\$${el.accessor.name}\", \"$assignType\");\n")
                }
            }
            if(el.expression != null) {
                if(assignType.first() == 'I') {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, $storageLocal.getIndex());\n")
                } else if(assignType.first() == 'L') {
                    output.appendText("context.getCodeWriter().getMethodVisitor().visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, $storageLocal.getIndex());\n")
                } else {
                    throw IllegalStateException("Unknown assign type: $assignType")
                }
            }
            onScopeEnd.add(0) {
                output.appendText("context.getCodeWriter().destroyLocal($storageLocal);\n")
            }
            continue
        }else if(el is ParsedTokenStatementAssignVariable) {
            uid = writeExpression(mappingData, functionDefinitions, accessorMappings, localMappings.toMutableMap(), el.expression, file, output, uid)
            el.accessor.sets = true
            when(el.accessor.scope) {
                ParsedTokenValueAccessorScope.CONTEXT, ParsedTokenValueAccessorScope.INSTRUCTION -> {
                    output.appendText("context.save(\"${el.accessor.name}\");\n") }
                ParsedTokenValueAccessorScope.LOCAL -> {
                    if(el.accessor.type.isIntegerLikeType()) {
                        output.appendText("context.getCodeWriter().getMethodVisitor().visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, ${localMappings[el.accessor.name]}.getIndex());\n")
                    }else if(el.accessor.type.isObjectLike()) {
                        output.appendText("context.getCodeWriter().getMethodVisitor().visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, ${localMappings[el.accessor.name]}.getIndex());\n")
                    }else {
                        throw IllegalStateException("Unknown assign type: ${el.accessor.type}")
                    }
                }
                ParsedTokenValueAccessorScope.STATIC -> {}
            }
            continue
        }
        throw Exception(el.toString())
    }
    onScopeEnd.forEach { it() }
    return uid
}

fun parseCode(inputCode: String, loadDefinitionCallback: (Any?) -> String?) = parseTokens(lexTokens(inputCode).listIterator(), loadDefinitionCallback)

fun generateCode(mappingData: MappingData?, definition: ParsedTokenInstructionDefinition, file: ParsedFile, output: OutputStream) {
    val bout = ByteArrayOutputStream()
    writeCode(mappingData, definition.definitionInfo.functionMappings, mapOf(), mutableMapOf(), definition.code, file, bout, mutableListOf(), 0)
    output.appendText("// ${definition.name}\n")
    output.appendText("// ${definition.params.joinToString { 
        "${if(it.gets) "<gets> " else ""}${if(it.sets) "<sets> " else ""}${
            if(it.type is ParsedTokenExpressionSimpleTyped) it.type.type.name.lowercase()
            else if(it.type is ParsedTokenExpressionTypeComplex) "<${it.type.typeName}>"
            else throw UnsupportedOperationException("Unknown expression type")
        } [${it.scope.name.lowercase()}:${it.name}]"
    }}\n")
    output.write(bout.toByteArray())
}
