package dev.epicpix.annotations

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
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
    ARROW,
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
    KW_RETURN,
    KW_NEW,
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
            if(index < code.length && code[index] == '>') {
                index++
                return LexTokenSimple(LexTokenSimpleType.ARROW)
            }
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
            return when(res) {
                "if" -> LexTokenSimple(LexTokenSimpleType.KW_IF)
                "else" -> LexTokenSimple(LexTokenSimpleType.KW_ELSE)
                "while" -> LexTokenSimple(LexTokenSimpleType.KW_WHILE)
                "null" -> LexTokenSimple(LexTokenSimpleType.KW_NULL)
                "let" -> LexTokenSimple(LexTokenSimpleType.KW_LET)
                "false" -> LexTokenSimple(LexTokenSimpleType.KW_FALSE)
                "true" -> LexTokenSimple(LexTokenSimpleType.KW_TRUE)
                "return" -> LexTokenSimple(LexTokenSimpleType.KW_RETURN)
                "new" -> LexTokenSimple(LexTokenSimpleType.KW_NEW)
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

data class ParsedFile(val fileInfo: Map<String, String>, val definitions: List<ParsedTokenInstructionDefinition>, val functions: List<ParsedTokenFunctionDefinition>, val collapsableDefinitions: List<ParsedTokenCollapsableInstructionDefinition>)

data class ParsedTokenValueAccessor(val scope: ParsedTokenValueAccessorScope, val name: String) : ParsedTokenExpression {
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(javaClass != other?.javaClass) return false

        other as ParsedTokenValueAccessor

        if(scope != other.scope) return false
        if(name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = scope.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}
data class ParsedTokenInstructionDefinition(val name: String, val params: List<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>>, val code: List<ParsedTokenStatement>, val definitionInfo: DefinitionInfo, var usesObf: Boolean = false) : ParsedToken
data class ParsedTokenFunctionDefinition(val name: String, val params: List<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>>, val code: List<ParsedTokenStatement>, val definitionInfo: DefinitionInfo, var usesObf: Boolean = false) : ParsedToken
data class ParsedTokenCollapsableInstructionDefinition(val name: String, val params: List<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>>, val conditions: List<ParsedTokenInstructionCondition>, var usesObf: Boolean = false) : ParsedToken

data class ParsedTokenInstructionCondition(val checks: List<ParsedTokenInstructionConditionComparison>, val instruction: String) : ParsedTokenStatement
data class ParsedTokenInstructionConditionComparison(val param: String, val operator: ParsedTokenInstructionConditionOperator, val value: Long) : ParsedTokenStatement


interface ParsedTokenInstanceType
data class ParsedTokenInstanceStatic(val className: String) : ParsedTokenInstanceType
data class ParsedTokenInstanceValue(val expression: ParsedTokenExpression) : ParsedTokenInstanceType

data object ParsedTokenStatementReturn : ParsedTokenStatement
data class ParsedTokenStatementIf(val expression: ParsedTokenExpression, val code: List<ParsedTokenStatement>, val elseCode: List<ParsedTokenStatement>?) : ParsedTokenStatement
data class ParsedTokenStatementWhile(val expression: ParsedTokenExpression, val code: List<ParsedTokenStatement>) : ParsedTokenStatement
data class ParsedTokenStatementFunctionCall(val name: String, val instance: ParsedTokenInstanceType?, val arguments: List<ParsedTokenExpression>, val code: List<ParsedTokenStatement>?) : ParsedTokenStatement, ParsedTokenExpression
data class ParsedTokenStatementDefineVariable(val type: ParsedTokenExpressionType?, val accessor: ParsedTokenValueAccessor, val expression: ParsedTokenExpression?) : ParsedTokenStatement
data class ParsedTokenStatementAssignVariable(val accessor: ParsedTokenValueAccessor, val expression: ParsedTokenExpression) : ParsedTokenStatement

data class ParsedTokenExoressionField(val name: String, val instance: ParsedTokenInstanceType) : ParsedTokenExpression
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
    NULL, BOOLEAN, INTEGER, SCOPE, OBJECT,
}

enum class ParsedTokenExpressionOperator {
    ADD, SUB, EQ, NE, GT, LT,
}

enum class ParsedTokenInstructionConditionOperator {
    EQ, NE
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

private fun parseFunctionDefinitionParams(iter: ListIterator<LexToken>, usesObf: AtomicBoolean): List<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>> {
    val res = mutableListOf<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>>()
    while(true) {
        if(iter.tryNextToken<LexTokenWord>() == null && !iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
            break
        }
        iter.previous()
        val type = parseExpressionTypeWord(iter)
        if(type is ParsedTokenExpressionTypeComplex) {
            if(type.typeName.startsWith("net.minecraft")) {
                usesObf.set(true)
            }
        }
        iter.expectTokenType(LexTokenSimpleType.OPEN_BRACKET)
        val scope = parseExpressionAccessorScopeWord(iter)
        iter.expectTokenType(LexTokenSimpleType.COLON)
        val name = iter.expectToken<LexTokenWord>()
        iter.expectTokenType(LexTokenSimpleType.CLOSE_BRACKET)
        res.add(ParsedTokenValueAccessor(scope, name.word) to type)
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
    var exp1 = parseExpressionLowerBase(iter, valueDefinitions, definitions, usesObf)
    if(!iter.hasNext()) return exp1
    while(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
        val name = iter.expectToken<LexTokenWord>()
        if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_PAREN)) {
            val params = parseFunctionCallParams(iter, valueDefinitions, definitions, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            if(definitions.externalFunctionNames.contains(name.word) && definitions.isObfuscated(name.word)) {
                usesObf.set(true)
            }
            exp1 = ParsedTokenStatementFunctionCall(name.word, ParsedTokenInstanceValue(exp1), params, null)
        }else {
            exp1 = ParsedTokenExoressionField(name.word, ParsedTokenInstanceValue(exp1))
        }
    }
    return exp1
}

private fun parseExpressionLowerBase(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, definitions: DefinitionInfo, usesObf: AtomicBoolean): ParsedTokenExpression {
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
    iter.tryNextTokenType(LexTokenSimpleType.KW_NEW) {
        val instanceOf = ParsedTokenInstanceStatic((parseExpressionTypeWord(iter) as ParsedTokenExpressionTypeComplex).typeName)
        iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
        val params = parseFunctionCallParams(iter, valueDefinitions, definitions, usesObf)
        iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
        return ParsedTokenStatementFunctionCall("<init>", instanceOf, params, null)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_BRACKET)) {
        iter.previous()
        return parseValueAccessor(iter, valueDefinitions)
    }
    val name = iter.expectToken<LexTokenWord>()
    iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
    val params = parseFunctionCallParams(iter, valueDefinitions, definitions, usesObf)
    iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
    if(definitions.isObfuscated(name.word)) {
        usesObf.set(true)
    }
    return ParsedTokenStatementFunctionCall(name.word, null, params, null)
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
    if(scope == ParsedTokenValueAccessorScope.LOCAL) {
        return ParsedTokenValueAccessor(scope, name.word);
    }
    throw IllegalArgumentException("Undefined value accessor: [$scope:${name.word}]")
}

private fun parseCode(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, definitions: DefinitionInfo, usesObf: AtomicBoolean): List<ParsedTokenStatement> {
    val res = mutableListOf<ParsedTokenStatement>()
    iter.expectTokenType(LexTokenSimpleType.OPEN_BRACE)
    while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_BRACE)) {
        if(iter.tryNextTokenType(LexTokenSimpleType.KW_RETURN)) {
            iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
            res.add(ParsedTokenStatementReturn)
        }else if(iter.tryNextTokenType(LexTokenSimpleType.KW_IF)) {
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
            val type = if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
                var t = ""
                while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)) {
                    if(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
                        t += "."
                    }else {
                        t += iter.expectToken<LexTokenWord>().word
                    }
                }
                when(t) {
                    "boolean" -> ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)
                    "int" -> ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)
                    "scope" -> ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.SCOPE)
                    else -> ParsedTokenExpressionTypeComplex(t)
                }
            }else null
            if(iter.tryNextTokenType(LexTokenSimpleType.SEMICOLON)) {
                res.add(ParsedTokenStatementDefineVariable(type, accessor, null))
            }else {
                iter.expectTokenType(LexTokenSimpleType.ASSIGN)
                val expression = parseExpression(iter, valueDefinitions, definitions, usesObf)
                iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
                res.add(ParsedTokenStatementDefineVariable(type, accessor, expression))
            }
        }else if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_BRACKET)) {
            iter.previous()
            val accessor = parseValueAccessor(iter, valueDefinitions)
            if(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
                iter.previous()
                iter.previous()
                iter.previous()
                iter.previous()
                iter.previous()
                iter.previous()
                val expression = parseExpression(iter, valueDefinitions, definitions, usesObf)
                iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
                res.add(expression as ParsedTokenStatementFunctionCall)
            }else {
                iter.expectTokenType(LexTokenSimpleType.ASSIGN)
                val expression = parseExpression(iter, valueDefinitions, definitions, usesObf)
                iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
                res.add(ParsedTokenStatementAssignVariable(accessor, expression))
            }
        }else {
            val name = iter.expectToken<LexTokenWord>()
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val params = parseFunctionCallParams(iter, valueDefinitions, definitions, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = if(!iter.tryNextTokenType(LexTokenSimpleType.SEMICOLON)) parseCode(iter, valueDefinitions, definitions, usesObf) else null
            if(definitions.externalFunctionNames.contains(name.word) && definitions.isObfuscated(name.word)) {
                usesObf.set(true)
            }
            res.add(ParsedTokenStatementFunctionCall(name.word, null, params, code))
        }
    }
    return res
}

private fun parseTokens(iter: ListIterator<LexToken>, loadDefinitionCallback: (Any?) -> String?): ParsedFile {
    val fileInfo = mutableMapOf<String, String>()
    val instrDefs = mutableListOf<ParsedTokenInstructionDefinition>()
    val funcDefs = mutableListOf<ParsedTokenFunctionDefinition>()
    val collapsableInstrDefs = mutableListOf<ParsedTokenCollapsableInstructionDefinition>()
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
            val usesObf = AtomicBoolean()
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val params = parseFunctionDefinitionParams(iter, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val defInfo = DefinitionInfo(allFunctionNames, functionsDefinitions)
            val code = parseCode(iter, params.map { it.first }, defInfo, usesObf)
            instrDefs.add(ParsedTokenInstructionDefinition(next.name, params, code, defInfo, usesObf.get()))
        }else if(next is LexTokenFunctionDefinition) {
            val usesObf = AtomicBoolean()
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val params = parseFunctionDefinitionParams(iter, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val defInfo = DefinitionInfo(allFunctionNames, functionsDefinitions)
            val code = parseCode(iter, params.map { it.first }, defInfo, usesObf)
            funcDefs.add(ParsedTokenFunctionDefinition(next.name, params, code, defInfo, usesObf.get()))
        }else if(next is LexTokenSimple) {
            if(next.type == LexTokenSimpleType.ASSIGN) {
                val name = iter.expectToken<LexTokenWord>()
                val usesObf = AtomicBoolean()
                iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
                val params = parseFunctionDefinitionParams(iter, usesObf)
                iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
                iter.expectTokenType(LexTokenSimpleType.OPEN_BRACE)
                val conditions = mutableListOf<ParsedTokenInstructionCondition>()
                while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_BRACE)) {
                    val checks = mutableListOf<ParsedTokenInstructionConditionComparison>()
                    if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_PAREN)) {
                        while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_PAREN)) {
                            val instrInput = iter.expectToken<LexTokenWord>().word
                            val op = when(iter.expectToken<LexTokenSimple>().type) {
                                LexTokenSimpleType.EQ -> ParsedTokenInstructionConditionOperator.EQ
                                LexTokenSimpleType.NE -> ParsedTokenInstructionConditionOperator.NE
                                else -> throw IllegalStateException("Invalid operator")
                            }
                            val value = if(iter.tryNextTokenType(LexTokenSimpleType.KW_TRUE)) {
                                1
                            } else if(iter.tryNextTokenType(LexTokenSimpleType.KW_FALSE)) {
                                0
                            } else {
                                iter.expectToken<LexTokenNumber>().number.toLong()
                            }
                            checks.add(ParsedTokenInstructionConditionComparison(instrInput, op, value))
                        }
                    }
                    iter.expectTokenType(LexTokenSimpleType.ARROW)
                    val instr = iter.expectToken<LexTokenWord>().word
                    iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
                    conditions.add(ParsedTokenInstructionCondition(checks, instr))
                }
                collapsableInstrDefs.add(ParsedTokenCollapsableInstructionDefinition(name.word, params, conditions, usesObf.get()))
            }
        }
    }
    return ParsedFile(fileInfo, instrDefs, funcDefs, collapsableInstrDefs)
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

data class AccessorStatistics(val accessor: ParsedTokenValueAccessor, var gets: Boolean = false, var sets: Boolean = false)
data class LocalInfo(val mapping: String, val type: ParsedTokenExpressionType)

data class WriterData(
    val mappingData: MappingData?,
    val functionDefinitions: Map<String, String>,
    val accessorMappings: Map<ParsedTokenValueAccessor, ParsedTokenValueAccessor> = mapOf(),
    val localTypes: MutableMap<String, LocalInfo> = mutableMapOf(),
    val file: ParsedFile,
    val params: List<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>>,
    val paramStatistics: List<AccessorStatistics> = params.map { AccessorStatistics(it.first) },
    val localReturn: String = "er",
) {
    fun copyLocalTypes() = copy(localTypes = localTypes.toMutableMap())

    fun getAccessorType(accessor: ParsedTokenValueAccessor): ParsedTokenExpressionType {
        if(accessor.scope == ParsedTokenValueAccessorScope.LOCAL) {
            return localTypes.entries.find { it.key == accessor.name }?.value?.type ?: throw NullPointerException("Failed to find type of $accessor")
        }
        return params.find { (it, _) -> it.name == accessor.name && it.scope == accessor.scope }?.second ?: throw NullPointerException("Failed to find type of $accessor")
    }
    fun markGet(accessor: ParsedTokenValueAccessor) {
        paramStatistics.find { it.accessor == accessor }?.gets = true
    }
    fun markSet(accessor: ParsedTokenValueAccessor) {
        paramStatistics.find { it.accessor == accessor }?.sets = true
    }
}

private fun evaluateExpressionType(writerData: WriterData, expression: ParsedTokenExpression): ParsedTokenExpressionType {
    if(expression is ParsedTokenExpressionTyped) {
        return expression.type
    }else if(expression is ParsedTokenExpressionBinary) {
        val a = evaluateExpressionType(writerData, expression.a)
        val b = evaluateExpressionType(writerData, expression.b)
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
        return writerData.getAccessorType(expression)
    }else if(expression is ParsedTokenExpressionNumber) {
        return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)
    }else if(expression is ParsedTokenStatementFunctionCall) {
        val paramTypes = expression.arguments.map { evaluateExpressionType(writerData, it) }
        val instance = expression.instance
        val functionMatches = if(instance is ParsedTokenInstanceValue) {
            val instanceType = evaluateExpressionType(writerData, instance.expression)
            if(instanceType !is ParsedTokenExpressionTypeComplex) {
                throw IllegalArgumentException("Cannot run functions on a primitive type: $instanceType")
            }
            val matches = getClassAccessibleMethods(instanceType.typeName).filter { !it.isStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }
            if(matches.size < 1) {
                throw IllegalArgumentException("Failed to find function [${instanceType.typeName}].${expression.name}(${paramTypes.joinToString()})")
            }
            if(matches.size > 1) {
                throw IllegalArgumentException("Found too many functions matching [${instanceType.typeName}].${expression.name}(${paramTypes.joinToString()}): $matches")
            }
            matches
        }else if(instance is ParsedTokenInstanceStatic) {
            val checkStatic = expression.name != "<init>"
            val matches = getClassAccessibleMethods(instance.className).filter { it.isStatic == checkStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }
            if(matches.size < 1) {
                throw IllegalArgumentException("Failed to find function <${instance.className}>.${expression.name}(${paramTypes.joinToString()})")
            }
            if(matches.size > 1) {
                throw IllegalArgumentException("Found too many functions matching <${instance.className}>.${expression.name}(${paramTypes.joinToString()}): $matches")
            }
            matches
        }else {
            throw IllegalArgumentException("Cannot run function on unknown instance: $instance")
        }
        val def = Type.getMethodType(functionMatches[0].descriptor).returnType
        if(def == Type.BOOLEAN_TYPE) {
            return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)
        }
        if(def == Type.INT_TYPE) {
            return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)
        }
        if(def.sort == Type.OBJECT) {
            return ParsedTokenExpressionTypeComplex(def.className)
        }
        TODO(def.descriptor)
    }else if(expression is ParsedTokenExoressionField) {
        val instance = expression.instance
        val fieldMatch = if(instance is ParsedTokenInstanceValue) {
            val instanceType = evaluateExpressionType(writerData, instance.expression)
            if(instanceType !is ParsedTokenExpressionTypeComplex) {
                throw IllegalArgumentException("Cannot access fields on a primitive type: $instanceType")
            }
            val matches = getClassAccessibleFields(instanceType.typeName).filter { !it.isStatic && it.name == expression.name }
            if(matches.size != 1) {
                throw IllegalArgumentException("Failed to find field [${instanceType.typeName}].${expression.name}")
            }
            matches[0]
        }else if(instance is ParsedTokenInstanceStatic) {
            val matches = getClassAccessibleFields(instance.className).filter { it.isStatic && it.name == expression.name }
            if(matches.size != 1) {
                throw IllegalArgumentException("Failed to find field <${instance.className}>.${expression.name}")
            }
            matches[0]
        }else {
            throw IllegalArgumentException("Cannot access field on unknown instance: $instance")
        }
        val def = Type.getType(fieldMatch.descriptor)
        if(def == Type.BOOLEAN_TYPE) {
            return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)
        }
        if(def == Type.INT_TYPE) {
            return ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)
        }
        if(def.sort == Type.OBJECT) {
            return ParsedTokenExpressionTypeComplex(def.className)
        }
        TODO(def.descriptor)
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

private fun isExpressionSimpleType(writerData: WriterData, expression: ParsedTokenExpression, type: ParsedTokenExpressionSimpleType): Boolean {
    return evaluateExpressionType(writerData, expression).isSimpleType(type)
}

private fun doesArgumentTypeMatch(matchType: ParsedTokenExpressionType, expectedType: Type): Boolean {
    if(expectedType == Type.BOOLEAN_TYPE) {
        return matchType.isSimpleType(ParsedTokenExpressionSimpleType.BOOLEAN)
    }else if(expectedType == Type.INT_TYPE) {
        return matchType.isSimpleType(ParsedTokenExpressionSimpleType.INTEGER)
    }else if(expectedType.sort == Type.OBJECT) {
        return matchType is ParsedTokenExpressionTypeComplex && (getClassLowerBounds(expectedType.className).any { matchType.typeName == it } || getClassLowerBounds(matchType.typeName).any { expectedType.className == it })
    }else {
        TODO(expectedType.descriptor)
    }
}

private fun writeExpression(writerData: WriterData, expression: ParsedTokenExpression, output: OutputStream, uniqId: Int, keepResult: Boolean = true): Int {
    var expression = expression
    if(expression is ParsedTokenValueAccessor) {
        writerData.markGet(expression)
        while(true) {
            val mapping = writerData.accessorMappings.entries.find {
                val expression = expression as ParsedTokenValueAccessor
                it.key.name == expression.name && writerData.getAccessorType(it.key) == writerData.getAccessorType(expression) && it.key.scope == expression.scope
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
            val type = writerData.getAccessorType(expression)
            if(type.isSimpleType(ParsedTokenExpressionSimpleType.INTEGER)) {
                if(keepResult) {
                    output.appendText("cw.writeInt(context.getInstruction().<Integer>get(\"${expression.name}\"));\n")
                }
                return uniqId
            }else if(type.isSimpleType(ParsedTokenExpressionSimpleType.BOOLEAN)) {
                if(keepResult) {
                    output.appendText("cw.writeInt(context.getInstruction().<Boolean>get(\"${expression.name}\") ? 1 : 0);\n")
                }
                return uniqId
            }else if(type is ParsedTokenExpressionTypeComplex && type.typeName == "java.lang.String") {
                if(keepResult) {
                    output.appendText("cw.writeString(context.getInstruction().<java.lang.String>get(\"${expression.name}\"));\n")
                }
                return uniqId
            }else if(type is ParsedTokenExpressionTypeComplex && type.typeName == "java.util.UUID") {
                if(keepResult) {
                    output.appendText("context.getFieldStorage().loadUUID(cw, context.getInstruction().<java.util.UUID>get(\"${expression.name}\"));\n")
                }
                return uniqId
            }
        }else if(expression.scope == ParsedTokenValueAccessorScope.LOCAL) {
            if(writerData.localTypes[expression.name] == null) {
                throw IllegalStateException("Local variable '${expression.name}' not found")
            }
            if(keepResult) {
                if(writerData.getAccessorType(expression).isIntegerLikeType()) {
                    output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, ${writerData.localTypes[expression.name]!!.mapping}.getIndex());\n")
                    return uniqId
                }else {
                    output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, ${writerData.localTypes[expression.name]!!.mapping}.getIndex());\n")
                    return uniqId
                }
            }
            return uniqId
        }else if(expression.scope == ParsedTokenValueAccessorScope.STATIC) {
            if(keepResult) {
                output.appendText("cw.loadLocal(\"static\$${expression.name}\");\n")
            }
            return uniqId
        }
    }else if(expression is ParsedTokenExpressionNumber) {
        if(keepResult) {
            val value = expression.number.toInt()
            if(value >= -1 && value <= 5) {
                if(value == -1) {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.ICONST_M1);\n")
                } else {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.ICONST_$value);\n")
                }
            } else if(value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                output.appendText("mv.visitIntInsn(org.objectweb.asm.Opcodes.BIPUSH, $value);\n")
            } else if(value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                output.appendText("mv.visitIntInsn(org.objectweb.asm.Opcodes.SIPUSH, $value);\n")
            } else {
                output.appendText("mv.visitLdcInsn($value);\n")
            }
        }
        return uniqId
    }else if(expression is ParsedTokenExpressionBinary) {
        if(expression.op == ParsedTokenExpressionOperator.ADD) {
            if(isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.INTEGER)) {
                var uid = uniqId
                uid = writeExpression(writerData.copyLocalTypes(), expression.a, output, uid)
                uid = writeExpression(writerData.copyLocalTypes(), expression.b, output, uid)
                if(keepResult) {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.IADD);\n")
                }else {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.POP2);\n")
                }
                return uid
            }
        }else if(expression.op == ParsedTokenExpressionOperator.SUB) {
            if(isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.INTEGER)) {
                var uid = uniqId
                uid = writeExpression(writerData.copyLocalTypes(), expression.a, output, uid)
                uid = writeExpression(writerData.copyLocalTypes(), expression.b, output, uid)
                if(keepResult) {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.ISUB);\n")
                }else {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.POP2);\n")
                }
                return uid
            }
        }
    }else if(expression is ParsedTokenStatementFunctionCall) {
        if(expression.instance != null) {
            val paramTypes = expression.arguments.map { evaluateExpressionType(writerData, it) }
            val instance = expression.instance
            val functionMatches = if(instance is ParsedTokenInstanceValue) {
                val instanceType = evaluateExpressionType(writerData, instance.expression)
                if(instanceType !is ParsedTokenExpressionTypeComplex) {
                    throw IllegalArgumentException("Cannot run functions on a primitive type: $instanceType")
                }
                val matches = getClassAccessibleMethods(instanceType.typeName).filter { !it.isStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }
                if(matches.size < 1) {
                    throw IllegalArgumentException("Failed to find function [${instanceType.typeName}].${expression.name}(${paramTypes.joinToString()})")
                }
                if(matches.size > 1) {
                    throw IllegalArgumentException("Found too many functions matching [${instanceType.typeName}].${expression.name}(${paramTypes.joinToString()}): $matches")
                }
                matches
            }else if(instance is ParsedTokenInstanceStatic) {
                val checkStatic = expression.name != "<init>"
                val matches = getClassAccessibleMethods(instance.className).filter { it.isStatic == checkStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }
                if(matches.size < 1) {
                    throw IllegalArgumentException("Failed to find function <${instance.className}>.${expression.name}(${paramTypes.joinToString()}): ${checkStatic} ${getClassAccessibleMethods(instance.className)}")
                }
                if(matches.size > 1) {
                    throw IllegalArgumentException("Found too many functions matching <${instance.className}>.${expression.name}(${paramTypes.joinToString()}): $matches")
                }
                matches
            }else {
                throw IllegalArgumentException("Cannot run function on unknown instance: $instance")
            }
            val def = functionMatches[0]
            val classOwner = loadClassInfo(def.owner.replace('/', '.').replace('$', '.'))
            val className = writerData.mappingData.getClassName(def.owner)
            val methodName = writerData.mappingData.getMethodName(def.owner, def.name, def.descriptor)
            val methodDescriptor = remapType(Type.getMethodType(def.descriptor), writerData.mappingData).descriptor
            val opcode = if(def.isStatic) Opcodes.INVOKESTATIC else if(classOwner.isInterface) Opcodes.INVOKEINTERFACE else if(expression.name == "<init>") Opcodes.INVOKESPECIAL else Opcodes.INVOKEVIRTUAL
            var uid = uniqId
            if(expression.name == "<init>") {
                output.appendText("mv.visitTypeInsn(org.objectweb.asm.Opcodes.NEW, \"$className\");\n")
                output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.DUP);\n")
            }
            if(instance is ParsedTokenInstanceValue) {
                uid = writeExpression(writerData.copyLocalTypes(), instance.expression, output, uid)
            }
            for(arg in expression.arguments) {
                uid = writeExpression(writerData.copyLocalTypes(), arg, output, uid)
            }
            output.appendText("mv.visitMethodInsn($opcode, \"$className\", \"$methodName\", \"$methodDescriptor\", ${classOwner.isInterface});\n")
            if(!keepResult && Type.getReturnType(def.descriptor) == Type.VOID_TYPE) {
                output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.POP);\n")
            }
            return uid
        }else {
            val func = writerData.file.functions.find { it.name == expression.name }
            if(func != null) {
                val requiredArgs = func.params.filter { (it, _) -> it.scope != ParsedTokenValueAccessorScope.STATIC && it.scope != ParsedTokenValueAccessorScope.LOCAL }
                if(requiredArgs.size != expression.arguments.size) {
                    throw UnsupportedOperationException("Wrong function call to '${expression.name}' argument count: expected ${requiredArgs.size}, got ${expression.arguments.size}")
                }

                val yields = mutableMapOf<String, (ParsedTokenExpression, OutputStream, WriterData, Int) -> Int>()
                val newMappings = mutableMapOf<ParsedTokenValueAccessor, ParsedTokenValueAccessor>()
                for(argIndex in requiredArgs.indices) {
                    val (arg, type) = requiredArgs[argIndex]
                    if(arg.scope == ParsedTokenValueAccessorScope.CONTEXT && type !is ParsedTokenExpressionTypeYield) {
                        newMappings[arg] = expression.arguments[argIndex] as ParsedTokenValueAccessor
                    }
                }

                if(requiredArgs.isNotEmpty()) {
                    val isYieldFunc = requiredArgs.last().second is ParsedTokenExpressionTypeYield
                    if(isYieldFunc) {
                        if(expression.code == null) {
                            throw UnsupportedOperationException("Wrong function call to '${expression.name}': expected function code for yield, got none")
                        }
                        val lastArg = requiredArgs.last()
                        yields[lastArg.first.name] = { param, output, writerData, uniqueId ->
                            val expectedType = evaluateExpressionType(writerData, expression.arguments.last())
                            var uid = uniqueId
                            uid = writeExpression(writerData.copyLocalTypes().copy(accessorMappings = writerData.accessorMappings + newMappings, params = writerData.params + func.params), param, output, uid)
                            if(evaluateExpressionType(writerData, param) != expectedType) {
                                output.appendText("mv.visitTypeInsn(org.objectweb.asm.Opcodes.CHECKCAST, \"${(expectedType as ParsedTokenExpressionTypeComplex).typeName.replace('.', '/')}\");\n")
                            }
                            val yieldFuncAccessor = expression.arguments.last() as ParsedTokenValueAccessor
                            writerData.markSet(yieldFuncAccessor)
                            when(yieldFuncAccessor.scope) {
                                ParsedTokenValueAccessorScope.CONTEXT, ParsedTokenValueAccessorScope.INSTRUCTION -> {
                                    output.appendText("context.save(\"${yieldFuncAccessor.name}\");\n") }
                                ParsedTokenValueAccessorScope.LOCAL -> {
                                    val type = writerData.getAccessorType(yieldFuncAccessor)
                                    if(type.isIntegerLikeType()) {
                                        output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, ${writerData.localTypes[yieldFuncAccessor.name]!!.mapping}.getIndex());\n")
                                    }else if(type.isObjectLike()) {
                                        output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, ${writerData.localTypes[yieldFuncAccessor.name]!!.mapping}.getIndex());\n")
                                    }else {
                                        throw IllegalStateException("Unknown assign type: $type")
                                    }
                                }
                                ParsedTokenValueAccessorScope.STATIC -> {}
                            }
                            output.appendText("{ // ${func.name} - yield([${lastArg.first.scope}:${lastArg.first.name}])\n")
                            val output2 = ByteArrayOutputStream()
                            uid = writeCode(writerData.copyLocalTypes().copy(accessorMappings = writerData.accessorMappings + newMappings, params = writerData.params + func.params), expression.code!!, output2, uid)
                            output.appendText(output2.toByteArray().decodeToString().prependIndent("    ").run { substring(0, length - 4) })
                            output.appendText("}\n")
                            uid
                        }
                    }
                }

                output.appendText("{ // ${func.name}\n")
                val output2 = ByteArrayOutputStream()
                val uid = writeCode(writerData.copyLocalTypes().copy(accessorMappings = writerData.accessorMappings + newMappings, params = writerData.params + func.params), func.code, output2, uniqId, yields)
                output.appendText(output2.toByteArray().decodeToString().prependIndent("    ").run { substring(0, length - 4) })
                output.appendText("}\n")
                return uid
            }
            throw UnsupportedOperationException("Unknown function call: ${expression.name}")
        }
    }else if(expression is ParsedTokenExoressionField) {
        val instance = expression.instance
        val fieldMatch = if(instance is ParsedTokenInstanceValue) {
            val instanceType = evaluateExpressionType(writerData, instance.expression)
            if(instanceType !is ParsedTokenExpressionTypeComplex) {
                throw IllegalArgumentException("Cannot access fields on a primitive type: $instanceType")
            }
            val matches = getClassAccessibleFields(instanceType.typeName).filter { !it.isStatic && it.name == expression.name }
            if(matches.size != 1) {
                throw IllegalArgumentException("Failed to find field [${instanceType.typeName}].${expression.name}")
            }
            matches[0]
        }else if(instance is ParsedTokenInstanceStatic) {
            val matches = getClassAccessibleFields(instance.className).filter { it.isStatic && it.name == expression.name }
            if(matches.size != 1) {
                throw IllegalArgumentException("Failed to find field <${instance.className}>.${expression.name}")
            }
            matches[0]
        }else {
            throw IllegalArgumentException("Cannot access field on unknown instance: $instance")
        }
        val className = writerData.mappingData.getClassName(fieldMatch.owner)
        val fieldName = writerData.mappingData.getFieldName(fieldMatch.owner, fieldMatch.name, fieldMatch.descriptor)
        val fieldDescriptor = remapType(Type.getType(fieldMatch.descriptor), writerData.mappingData).descriptor
        val opcode = if(fieldMatch.isStatic) Opcodes.GETSTATIC else Opcodes.GETFIELD
        var uid = uniqId
        if(instance is ParsedTokenInstanceValue) {
            uid = writeExpression(writerData.copyLocalTypes(), instance.expression, output, uid)
        }
        output.appendText("mv.visitFieldInsn($opcode, \"$className\", \"$fieldName\", \"$fieldDescriptor\");\n")
        if(!keepResult) {
            output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.POP);\n")
        }
        return uid
    }
    throw UnsupportedOperationException("Unsupported expression: $expression")
}

private fun writeComparison(writerData: WriterData, expression: ParsedTokenExpression, output: OutputStream, failureLabel: String, uniqId: Int): Int {
    var uid = uniqId
    if(!isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.BOOLEAN)) {
        throw IllegalStateException("comparison <expr> must return a boolean")
    }
    if(expression !is ParsedTokenExpressionBinary) {
        throw IllegalStateException("comparison must use a binary operator")
    }
    if(expression.op == ParsedTokenExpressionOperator.NE || expression.op == ParsedTokenExpressionOperator.EQ) {
        if(isExpressionSimpleType(writerData, expression.a, ParsedTokenExpressionSimpleType.NULL) xor isExpressionSimpleType(writerData, expression.b, ParsedTokenExpressionSimpleType.NULL)) {
            val nonNull = if(isExpressionSimpleType(writerData, expression.a, ParsedTokenExpressionSimpleType.NULL)) expression.b else expression.a
            uid = writeExpression(writerData.copyLocalTypes(), nonNull, output, uid)
            if(expression.op == ParsedTokenExpressionOperator.NE) {
                output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.IFNULL, $failureLabel);\n")
            }else {
                output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.IFNONNULL, $failureLabel);\n")
            }
        }else {
            val typeA = evaluateExpressionType(writerData, expression.a)
            val typeB = evaluateExpressionType(writerData, expression.b)
            if(typeA != typeB && (!typeA.isIntegerLikeType() || !typeB.isIntegerLikeType())) {
                throw IllegalStateException("if(<expr> ==/!= <expr>) only works if both <expr> have the same type")
            }
            if(isExpressionSimpleType(writerData, expression.a, ParsedTokenExpressionSimpleType.INTEGER) || isExpressionSimpleType(writerData, expression.a, ParsedTokenExpressionSimpleType.BOOLEAN)) {
                uid = writeExpression(writerData.copyLocalTypes(), expression.a, output, uid)
                uid = writeExpression(writerData.copyLocalTypes(), expression.b, output, uid)
                if(expression.op == ParsedTokenExpressionOperator.NE) {
                    output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPEQ, $failureLabel);\n")
                }else {
                    output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPNE, $failureLabel);\n")
                }
            }else {
                throw IllegalArgumentException("Unsupported expression comparison type: ${evaluateExpressionType(writerData, expression.a)}")
            }
        }
        return uid
    }else if(expression.op == ParsedTokenExpressionOperator.LT || expression.op == ParsedTokenExpressionOperator.GT) {
        if(isExpressionSimpleType(writerData, expression.a, ParsedTokenExpressionSimpleType.INTEGER) && isExpressionSimpleType(writerData, expression.b, ParsedTokenExpressionSimpleType.INTEGER)) {
            uid = writeExpression(writerData.copyLocalTypes(), expression.a, output, uid)
            uid = writeExpression(writerData.copyLocalTypes(), expression.b, output, uid)
            if(expression.op == ParsedTokenExpressionOperator.LT) {
                output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPGE, $failureLabel);\n")
            }else {
                output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPLE, $failureLabel);\n")
            }
            return uid
        }else {
            throw IllegalArgumentException("Unsupported expression comparison type: ${evaluateExpressionType(writerData, expression.a)} ${expression.op} ${evaluateExpressionType(writerData, expression.b)}")
        }
    }else {
        throw IllegalArgumentException("Unsupported comparison binary operator: ${expression.op}")
    }
}

private fun writeCode(writerData: WriterData, code: List<ParsedTokenStatement>, output: OutputStream, uniqId: Int, yieldCallbacks: Map<String, (ParsedTokenExpression, OutputStream, WriterData, Int) -> Int> = mapOf()): Int {
    val onScopeEnd = mutableListOf<() -> Unit>()
    var uid = uniqId
    for(el in code) {
        if(el is ParsedTokenStatementReturn) {
            output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, ${writerData.localReturn});\n")
            continue
        }else if(el is ParsedTokenStatementIf) {
            val failureLabel = "j${uid++}"
            output.appendText("org.objectweb.asm.Label $failureLabel = new org.objectweb.asm.Label();\n")
            uid = writeComparison(writerData.copyLocalTypes(), el.expression, output, failureLabel, uid)
            uid = writeCode(writerData.copyLocalTypes(), el.code, output, uid, yieldCallbacks)
            if(el.elseCode != null) {
                val jumpLabel = "j${uid++}"
                output.appendText("org.objectweb.asm.Label $jumpLabel = new org.objectweb.asm.Label();\n")
                output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, $jumpLabel);\n")
                output.appendText("mv.visitLabel($failureLabel);\n")
                uid = writeCode(writerData.copyLocalTypes(), el.elseCode, output, uid, yieldCallbacks)
                output.appendText("mv.visitLabel($jumpLabel);\n")
            }else {
                output.appendText("mv.visitLabel($failureLabel);\n")
            }
            continue
        }else if(el is ParsedTokenStatementWhile) {
            val failureLabel = "j${uid++}"
            output.appendText("org.objectweb.asm.Label $failureLabel = new org.objectweb.asm.Label();\n")
            val startLoopLabel = "j${uid++}"
            output.appendText("org.objectweb.asm.Label $startLoopLabel = new org.objectweb.asm.Label();\n")
            output.appendText("mv.visitLabel($startLoopLabel);\n")
            uid = writeComparison(writerData.copyLocalTypes(), el.expression, output, failureLabel, uid)
            uid = writeCode(writerData.copyLocalTypes(), el.code, output, uid, yieldCallbacks)
            output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, $startLoopLabel);\n")
            output.appendText("mv.visitLabel($failureLabel);\n")
            continue
        }else if(el is ParsedTokenStatementFunctionCall) {
            if(el.name == "run") {
                output.appendText("context.run();\n")
            }else if(el.name == "scope") {
                val scopeAccessor = el.arguments[0] as ParsedTokenValueAccessor
                writerData.markSet(scopeAccessor)
                val jumpLabel = "j${uid++}"
                output.appendText("org.objectweb.asm.Label $jumpLabel = new org.objectweb.asm.Label();\n")
                output.appendText("context.getNewScopes().put(context.getInstruction().<dev.epicpix.minecraftfunctioncompiler.il.ScopeData>get(\"${scopeAccessor.name}\").getId(), $jumpLabel);\n")
                uid = writeCode(writerData.copyLocalTypes(), el.code!!, output, uid, yieldCallbacks)
                output.appendText("mv.visitLabel($jumpLabel);\n")
            }else if(el.name == "exit_scope") {
                val scopeAccessor = el.arguments[0] as ParsedTokenValueAccessor
                writerData.markGet(scopeAccessor)
                output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, context.getNewScopes().get(context.getInstruction().<dev.epicpix.minecraftfunctioncompiler.il.ScopeData>get(\"${scopeAccessor.name}\").getId()));\n")
            }else if(el.name == "yield") {
                val yieldObj = el.arguments[0] as ParsedTokenValueAccessor
                writerData.markGet(yieldObj)
                if(writerData.getAccessorType(yieldObj) !is ParsedTokenExpressionTypeYield) {
                    throw IllegalArgumentException("Tried to yield() on a non-yield argument (${yieldObj.name})")
                }
                val value = el.arguments[1]
                val cb = yieldCallbacks[yieldObj.name] ?: throw IllegalArgumentException("yield(${yieldObj.name}) did not have a callback")
                uid = cb(value, output, writerData.copyLocalTypes(), uid)
            }else {
                uid = writeExpression(writerData.copyLocalTypes(), el, output, uid, false)
            }
            continue
        }else if(el is ParsedTokenStatementDefineVariable) {
            if(el.expression != null) {
                uid = writeExpression(writerData.copyLocalTypes(), el.expression, output, uid)
            }
            val type = if(el.accessor.scope == ParsedTokenValueAccessorScope.LOCAL) el.type!! else writerData.getAccessorType(el.accessor)
            val assignType = if(type.isSimpleType(ParsedTokenExpressionSimpleType.INTEGER)) {
                "I"
            }else if(type is ParsedTokenExpressionTypeComplex) {
                "L${writerData.mappingData.getClassName(type.typeName.replace('.', '/'))};"
            }else throw UnsupportedOperationException("Unknown type: ${el.accessor} : $type")
            writerData.markSet(el.accessor)
            val storageLocal = "l${uid++}"
            when(el.accessor.scope) {
                ParsedTokenValueAccessorScope.CONTEXT,
                ParsedTokenValueAccessorScope.INSTRUCTION -> {
                    val storageLocalUid = "s${uid++}"
                    output.appendText("String $storageLocalUid = cw.generateUniqueLocal();\n")
                    output.appendText("context.store(\"${el.accessor.name}\", $storageLocalUid);\n")
                    output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.impl.Local $storageLocal = cw.createLocal($storageLocalUid, \"$assignType\");\n")
                }
                ParsedTokenValueAccessorScope.LOCAL -> {
                    output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.impl.Local $storageLocal = cw.createLocal(cw.generateUniqueLocal(), \"$assignType\");\n")
                    writerData.localTypes[el.accessor.name] = LocalInfo(storageLocal, el.type!!)
                }
                ParsedTokenValueAccessorScope.STATIC -> {
                    output.appendText("dev.epicpix.minecraftfunctioncompiler.emitter.impl.Local $storageLocal = cw.createLocal(\"static\$${el.accessor.name}\", \"$assignType\");\n")
                }
            }
            if(el.expression != null) {
                if(assignType.first() == 'I') {
                    output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, $storageLocal.getIndex());\n")
                } else if(assignType.first() == 'L') {
                    output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, $storageLocal.getIndex());\n")
                } else {
                    throw IllegalStateException("Unknown assign type: $assignType")
                }
            }
            onScopeEnd.add(0) {
                output.appendText("cw.destroyLocal($storageLocal);\n")
            }
            continue
        }else if(el is ParsedTokenStatementAssignVariable) {
            uid = writeExpression(writerData.copyLocalTypes(), el.expression, output, uid)
            writerData.markSet(el.accessor)
            when(el.accessor.scope) {
                ParsedTokenValueAccessorScope.CONTEXT, ParsedTokenValueAccessorScope.INSTRUCTION -> {
                    output.appendText("context.save(\"${el.accessor.name}\");\n") }
                ParsedTokenValueAccessorScope.LOCAL -> {
                    val type = writerData.getAccessorType(el.accessor)
                    if(type.isIntegerLikeType()) {
                        output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, ${writerData.localTypes[el.accessor.name]!!.mapping}.getIndex());\n")
                    }else if(type.isObjectLike()) {
                        output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, ${writerData.localTypes[el.accessor.name]!!.mapping}.getIndex());\n")
                    }else {
                        throw IllegalStateException("Unknown assign type: $type")
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

fun generateCode(mappingData: MappingData?, definition: ParsedTokenInstructionDefinition, file: ParsedFile, output: OutputStream): List<AccessorStatistics> {
    val bout = ByteArrayOutputStream()
    val writerData = WriterData(mappingData = mappingData, functionDefinitions = definition.definitionInfo.functionMappings, file = file, params = definition.params)
    try {
        bout.appendText("org.objectweb.asm.Label ${writerData.localReturn} = new org.objectweb.asm.Label();\n")
        writeCode(writerData, definition.code, bout, 0)
        bout.appendText("mv.visitLabel(${writerData.localReturn});\n")
    }catch(e: Exception) {
        throw RuntimeException("Failed to write code for instruction definition ${definition.name}", e)
    }
    output.appendText("// ${definition.name}\n")
    output.write(bout.toByteArray())
    return writerData.paramStatistics
}
