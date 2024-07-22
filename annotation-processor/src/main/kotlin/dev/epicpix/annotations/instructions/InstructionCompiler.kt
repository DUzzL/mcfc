package dev.epicpix.annotations.instructions

import dev.epicpix.annotations.*
import dev.epicpix.annotations.enums.EnumList
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

private enum class LexTokenSimpleType : LexToken {
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
            '{' -> return LexTokenSimpleType.OPEN_BRACE
            '}' -> return LexTokenSimpleType.CLOSE_BRACE
            '[' -> return LexTokenSimpleType.OPEN_BRACKET
            ']' -> return LexTokenSimpleType.CLOSE_BRACKET
            '(' -> return LexTokenSimpleType.OPEN_PAREN
            ')' -> return LexTokenSimpleType.CLOSE_PAREN
            '<' -> return LexTokenSimpleType.OPEN_ANGLE_BRACKET
            '>' -> return LexTokenSimpleType.CLOSE_ANGLE_BRACKET
            '.' -> return LexTokenSimpleType.DOT
            ',' -> return LexTokenSimpleType.COMMA
            '+' -> return LexTokenSimpleType.PLUS
            '-' -> return LexTokenSimpleType.MINUS
            '/' -> return LexTokenSimpleType.SLASH
            '*' -> return LexTokenSimpleType.STAR
            ';' -> return LexTokenSimpleType.SEMICOLON
            ':' -> return LexTokenSimpleType.COLON
        }
        if(res == "=" || res == "!") {
            if(index < code.length && code[index] == '=') {
                index++
                if(res == "=") {
                    return LexTokenSimpleType.EQ
                }else {
                    return LexTokenSimpleType.NE
                }
            }
        }
        if(res == "=") {
            if(index < code.length && code[index] == '>') {
                index++
                return LexTokenSimpleType.ARROW
            }
            return LexTokenSimpleType.ASSIGN
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
                "if" -> LexTokenSimpleType.KW_IF
                "else" -> LexTokenSimpleType.KW_ELSE
                "while" -> LexTokenSimpleType.KW_WHILE
                "null" -> LexTokenSimpleType.KW_NULL
                "let" -> LexTokenSimpleType.KW_LET
                "false" -> LexTokenSimpleType.KW_FALSE
                "true" -> LexTokenSimpleType.KW_TRUE
                "return" -> LexTokenSimpleType.KW_RETURN
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
data class ParsedTokenInstructionDefinition(val name: String, val params: List<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>>, val code: List<ParsedTokenStatement>, val versionRange: Pair<String?, String?>?, var usesObf: Boolean = false) : ParsedToken
data class ParsedTokenFunctionDefinition(val name: String, val params: List<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>>, val code: List<ParsedTokenStatement>, var usesObf: Boolean = false) : ParsedToken
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

data class ParsedTokenExpressionField(val name: String, val instance: ParsedTokenInstanceType) : ParsedTokenExpression
data class ParsedTokenExpressionBoolean(val value: Boolean) : ParsedTokenExpression
data class ParsedTokenExpressionNumber(val number: Long) : ParsedTokenExpression
data class ParsedTokenExpressionString(val string: String) : ParsedTokenExpression
data class ParsedTokenExpressionTyped(val type: ParsedTokenExpressionType) : ParsedTokenExpression
data class ParsedTokenExpressionBinary(val a: ParsedTokenExpression, val b: ParsedTokenExpression, val op: ParsedTokenExpressionOperator) : ParsedTokenExpression

enum class ParsedTokenValueAccessorScope {
    CONTEXT, INSTRUCTION, LOCAL, STATIC,
}

interface ParsedTokenExpressionType
data class ParsedTokenExpressionTypeComplex(val typeName: String) : ParsedTokenExpressionType
data class ParsedTokenExpressionTypeYield(val yieldType: ParsedTokenExpressionType) : ParsedTokenExpressionType

enum class ParsedTokenExpressionSimpleType : ParsedTokenExpressionType {
    NULL, BOOLEAN, INTEGER, LONG, FLOAT, DOUBLE, SCOPE, OBJECT,
}

enum class ParsedTokenExpressionOperator {
    ADD, SUB, MUL, EQ, NE, GT, LT,
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
        "boolean" -> ParsedTokenExpressionSimpleType.BOOLEAN
        "int" -> ParsedTokenExpressionSimpleType.INTEGER
        "double" -> ParsedTokenExpressionSimpleType.DOUBLE
        "scope" -> ParsedTokenExpressionSimpleType.SCOPE
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
                "boolean" -> ParsedTokenExpressionSimpleType.BOOLEAN
                "int" -> ParsedTokenExpressionSimpleType.INTEGER
                "double" -> ParsedTokenExpressionSimpleType.DOUBLE
                "scope" -> ParsedTokenExpressionSimpleType.SCOPE
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

private fun parseFunctionCallParams(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): List<ParsedTokenExpression> {
    val res = mutableListOf<ParsedTokenExpression>()
    while(true) {
        if(iter.tryNextTokenType(LexTokenSimpleType.CLOSE_PAREN)) {
            iter.previous()
            break
        }
        res.add(parseExpression(iter, valueDefinitions, usesObf))
        if(!iter.tryNextTokenType(LexTokenSimpleType.COMMA)) {
            break
        }
    }
    return res
}

private fun parseExpression(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): ParsedTokenExpression {
    val exp1 = parseExpression1(iter, valueDefinitions, usesObf)
    if(!iter.hasNext()) return exp1
    if(iter.tryNextTokenType(LexTokenSimpleType.EQ)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.EQ)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.NE)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.NE)
    }
    return exp1
}

private fun parseExpression1(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): ParsedTokenExpression {
    val exp1 = parseExpression2(iter, valueDefinitions, usesObf)
    if(!iter.hasNext()) return exp1
    if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression1(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.LT)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression1(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.GT)
    }
    return exp1
}

private fun parseExpression2(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): ParsedTokenExpression {
    val exp1 = parseExpression3(iter, valueDefinitions, usesObf)
    if(!iter.hasNext()) return exp1
    if(iter.tryNextTokenType(LexTokenSimpleType.PLUS)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression2(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.ADD)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.MINUS)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression2(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.SUB)
    }
    return exp1
}

private fun parseExpression3(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): ParsedTokenExpression {
    val exp1 = parseExpressionBase(iter, valueDefinitions, usesObf)
    if(!iter.hasNext()) return exp1
    if(iter.tryNextTokenType(LexTokenSimpleType.STAR)) {
        return ParsedTokenExpressionBinary(exp1, parseExpression3(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.MUL)
    }
    return exp1
}

private fun parseExpressionBase(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): ParsedTokenExpression {
    var exp1 = parseExpressionLowerBase(iter, valueDefinitions, usesObf)
    if(!iter.hasNext()) return exp1
    while(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
        val name = iter.expectToken<LexTokenWord>()
        if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_PAREN)) {
            val params = parseFunctionCallParams(iter, valueDefinitions, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            exp1 = ParsedTokenStatementFunctionCall(name.word, ParsedTokenInstanceValue(exp1), params, null)
        }else {
            exp1 = ParsedTokenExpressionField(name.word, ParsedTokenInstanceValue(exp1))
        }
    }
    return exp1
}

private fun parseExpressionLowerBase(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): ParsedTokenExpression {
    if(!iter.hasNext()) throw IllegalStateException("Expected expression base, got EOF")
    iter.tryNextToken<LexTokenNumber> {
        return ParsedTokenExpressionNumber(it.number.toLong())
    }
    iter.tryNextTokenType(LexTokenSimpleType.KW_NULL) {
        return ParsedTokenExpressionTyped(ParsedTokenExpressionSimpleType.NULL)
    }
    iter.tryNextTokenType(LexTokenSimpleType.KW_FALSE) {
        return ParsedTokenExpressionBoolean(false)
    }
    iter.tryNextTokenType(LexTokenSimpleType.KW_TRUE) {
        return ParsedTokenExpressionBoolean(true)
    }
    iter.tryNextTokenType(LexTokenSimpleType.KW_NEW) {
        val instanceOf = ParsedTokenInstanceStatic((parseExpressionTypeWord(iter) as ParsedTokenExpressionTypeComplex).typeName)
        iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
        val params = parseFunctionCallParams(iter, valueDefinitions, usesObf)
        iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
        return ParsedTokenStatementFunctionCall("<init>", instanceOf, params, null)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_BRACKET)) {
        iter.previous()
        return parseValueAccessor(iter, valueDefinitions)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
        iter.previous()
        val type = parseExpressionTypeWord(iter)
        if(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
            val classOf = ParsedTokenInstanceStatic((type as ParsedTokenExpressionTypeComplex).typeName)
            val name = iter.expectToken<LexTokenWord>()
            if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_PAREN)) {
                val params = parseFunctionCallParams(iter, valueDefinitions, usesObf)
                iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
                return ParsedTokenStatementFunctionCall(name.word, classOf, params, null)
            }else {
                return ParsedTokenExpressionField(name.word, classOf)
            }
        }else {
            return ParsedTokenExpressionTyped(type)
        }
    }
    iter.tryNextToken<LexTokenString>()?.also {
        return ParsedTokenExpressionString(it.content)
    }
    val name = iter.expectToken<LexTokenWord>()
    iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
    val params = parseFunctionCallParams(iter, valueDefinitions, usesObf)
    iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
    usesObf.set(true)
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

private fun parseCode(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): List<ParsedTokenStatement> {
    val res = mutableListOf<ParsedTokenStatement>()
    if(iter.peekNextTokenType(LexTokenSimpleType.SEMICOLON)) {
        return res
    }
    iter.expectTokenType(LexTokenSimpleType.OPEN_BRACE)
    while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_BRACE)) {
        if(iter.tryNextTokenType(LexTokenSimpleType.KW_RETURN)) {
            iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
            res.add(ParsedTokenStatementReturn)
        }else if(iter.tryNextTokenType(LexTokenSimpleType.KW_IF)) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val expression = parseExpression(iter, valueDefinitions, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = parseCode(iter, valueDefinitions, usesObf)
            if(iter.tryNextTokenType(LexTokenSimpleType.KW_ELSE)) {
                val elseCode = parseCode(iter, valueDefinitions, usesObf)
                res.add(ParsedTokenStatementIf(expression, code, elseCode))
            } else {
                res.add(ParsedTokenStatementIf(expression, code, null))
            }
        }else if(iter.tryNextTokenType(LexTokenSimpleType.KW_WHILE)) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val expression = parseExpression(iter, valueDefinitions, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = parseCode(iter, valueDefinitions, usesObf)
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
                    "boolean" -> ParsedTokenExpressionSimpleType.BOOLEAN
                    "int" -> ParsedTokenExpressionSimpleType.INTEGER
                    "double" -> ParsedTokenExpressionSimpleType.DOUBLE
                    "scope" -> ParsedTokenExpressionSimpleType.SCOPE
                    else -> ParsedTokenExpressionTypeComplex(t)
                }
            }else null
            if(iter.tryNextTokenType(LexTokenSimpleType.SEMICOLON)) {
                res.add(ParsedTokenStatementDefineVariable(type, accessor, null))
            }else {
                iter.expectTokenType(LexTokenSimpleType.ASSIGN)
                val expression = parseExpression(iter, valueDefinitions, usesObf)
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
                val expression = parseExpression(iter, valueDefinitions, usesObf)
                iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
                res.add(expression as ParsedTokenStatementFunctionCall)
            }else {
                iter.expectTokenType(LexTokenSimpleType.ASSIGN)
                val expression = parseExpression(iter, valueDefinitions, usesObf)
                iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
                res.add(ParsedTokenStatementAssignVariable(accessor, expression))
            }
        }else {
            if(iter.peekNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
                val expression = parseExpression(iter, valueDefinitions, usesObf)
                iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
                res.add(expression as ParsedTokenStatementFunctionCall)
            }else {
                val name = iter.expectToken<LexTokenWord>()
                iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
                val params = parseFunctionCallParams(iter, valueDefinitions, usesObf)
                iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
                val code = if(iter.peekNextTokenType(LexTokenSimpleType.SEMICOLON) || iter.peekNextTokenType(LexTokenSimpleType.DOT)) null else parseCode(iter, valueDefinitions, usesObf)
                var expr = ParsedTokenStatementFunctionCall(name.word, null, params, code)
                var readMore = false
                while(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
                    val name2 = iter.expectToken<LexTokenWord>()
                    iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
                    val params2 = parseFunctionCallParams(iter, valueDefinitions, usesObf)
                    iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
                    expr = ParsedTokenStatementFunctionCall(name2.word, ParsedTokenInstanceValue(expr), params2, null)
                    readMore = true
                }
                if(code == null || readMore) {
                    iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
                }
                res.add(expr)
            }
        }
    }
    return res
}

private fun parseVersionNumber(iter: ListIterator<LexToken>): String {
    val res = StringBuilder()
    while(iter.hasNext()) {
        val token = iter.next()
        if(token == LexTokenSimpleType.DOT) {
            res.append(".")
        } else if(token is LexTokenNumber) {
            res.append(token.number)
        } else {
            iter.previous()
            break
        }
    }
    return res.toString()
}

private fun parseTokens(iter: ListIterator<LexToken>): ParsedFile {
    val fileInfo = mutableMapOf<String, String>()
    val instrDefs = mutableListOf<ParsedTokenInstructionDefinition>()
    val funcDefs = mutableListOf<ParsedTokenFunctionDefinition>()
    val collapsableInstrDefs = mutableListOf<ParsedTokenCollapsableInstructionDefinition>()
    while(iter.hasNext()) {
        val next = iter.next()
        if(next is LexTokenFileHeader) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val content = iter.expectToken<LexTokenString>()
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            fileInfo[next.name] = content.content
        }else if(next is LexTokenInstructionDefinition) {
            val range = if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_BRACKET)) {
                val range = if(iter.tryNextTokenType(LexTokenSimpleType.MINUS)) {
                    val high = parseVersionNumber(iter)
                    null to high
                }else {
                    val low = parseVersionNumber(iter)
                    val high = if(iter.tryNextTokenType(LexTokenSimpleType.MINUS)) {
                        if(iter.tryNextTokenType(LexTokenSimpleType.CLOSE_BRACKET)) {
                            iter.previous()
                            null
                        }else {
                            parseVersionNumber(iter)
                        }
                    } else low
                    low to high
                }
                iter.expectTokenType(LexTokenSimpleType.CLOSE_BRACKET)
                range
            }else null
            val usesObf = AtomicBoolean()
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val params = parseFunctionDefinitionParams(iter, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = parseCode(iter, params.map { it.first }, usesObf)
            instrDefs.add(ParsedTokenInstructionDefinition(next.name, params, code, range, usesObf.get()))
        }else if(next is LexTokenFunctionDefinition) {
            val usesObf = AtomicBoolean()
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val params = parseFunctionDefinitionParams(iter, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = parseCode(iter, params.map { it.first }, usesObf)
            funcDefs.add(ParsedTokenFunctionDefinition(next.name, params, code, usesObf.get()))
        }else if(next == LexTokenSimpleType.ASSIGN) {
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
                        val op = when(iter.expectToken<LexTokenSimpleType>()) {
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
    return ParsedFile(fileInfo, instrDefs, funcDefs, collapsableInstrDefs)
}

private fun ListIterator<LexToken>.expectTokenType(type: LexTokenSimpleType) {
    if(!hasNext()) throw IllegalStateException("Expected token type '$type', got EOF")
    val got = next()
    if(got != type) {
        throw IllegalStateException("Expected token type '$type', got '$got'")
    }
}

private fun ListIterator<LexToken>.peekNextTokenType(type: LexTokenSimpleType): Boolean {
    if(!hasNext()) return false
    val got = next()
    previous()
    return got == type
}

private fun ListIterator<LexToken>.tryNextTokenType(type: LexTokenSimpleType): Boolean {
    if(!hasNext()) return false
    val got = next()
    if(got != type) {
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
    val accessorMappings: Map<ParsedTokenValueAccessor, ParsedTokenValueAccessor> = mapOf(),
    val localTypes: MutableMap<String, LocalInfo> = mutableMapOf(),
    val enums: EnumList,
    val file: ParsedFile,
    val params: List<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>>,
    val paramStatistics: List<AccessorStatistics> = params.map { AccessorStatistics(it.first) },
    val localReturn: String = "er",
    val majorVersion: Int,
    val minorVersion: Int,
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
    fun isAboveVersion(major: Int, minor: Int): Boolean {
        return majorVersion > major || (majorVersion == major && minorVersion > minor)
    }
    fun isBelowVersion(major: Int, minor: Int): Boolean {
        return majorVersion < major || (majorVersion == major && minorVersion < minor)
    }
}

private fun evaluateExpressionType(writerData: WriterData, expression: ParsedTokenExpression): ParsedTokenExpressionType {
    if(expression is ParsedTokenExpressionTyped) {
        return expression.type
    }else if(expression is ParsedTokenExpressionBinary) {
        val a = evaluateExpressionType(writerData, expression.a)
        val b = evaluateExpressionType(writerData, expression.b)
        if(expression.op == ParsedTokenExpressionOperator.ADD || expression.op == ParsedTokenExpressionOperator.SUB || expression.op == ParsedTokenExpressionOperator.MUL) {
            if(a == ParsedTokenExpressionSimpleType.INTEGER && b == ParsedTokenExpressionSimpleType.INTEGER) {
                return ParsedTokenExpressionSimpleType.INTEGER
            }
            if(a == ParsedTokenExpressionSimpleType.DOUBLE && b == ParsedTokenExpressionSimpleType.DOUBLE) {
                return ParsedTokenExpressionSimpleType.DOUBLE
            }
        }else if(expression.op == ParsedTokenExpressionOperator.NE || expression.op == ParsedTokenExpressionOperator.EQ) {
            if(a.isIntegerLikeType() && b.isIntegerLikeType()) {
                return ParsedTokenExpressionSimpleType.BOOLEAN
            }else if(a.isObjectLike() && b.isObjectLike()) {
                return ParsedTokenExpressionSimpleType.BOOLEAN
            }
        }else if(expression.op == ParsedTokenExpressionOperator.LT || expression.op == ParsedTokenExpressionOperator.GT) {
            if(a.isIntegerLikeType() && b.isIntegerLikeType()) {
                return ParsedTokenExpressionSimpleType.BOOLEAN
            }
        }
    }else if(expression is ParsedTokenValueAccessor) {
        return writerData.getAccessorType(expression)
    }else if(expression is ParsedTokenExpressionNumber) {
        return ParsedTokenExpressionSimpleType.INTEGER
    }else if(expression is ParsedTokenExpressionBoolean) {
        return ParsedTokenExpressionSimpleType.BOOLEAN
    }else if(expression is ParsedTokenStatementFunctionCall) {
        if(expression.instance == null) {
            if(expression.name == "cast" || expression.name == "map_enum") {
                return (expression.arguments[1] as ParsedTokenExpressionTyped).type as ParsedTokenExpressionTypeComplex
            }
            if(expression.name == "is") {
                return ParsedTokenExpressionSimpleType.BOOLEAN
            }
            if(expression.name == "map_attribute") {
                if(writerData.isAboveVersion(20, 4)) {
                    return ParsedTokenExpressionTypeComplex("net.minecraft.core.Holder")
                }else {
                    return ParsedTokenExpressionTypeComplex("net.minecraft.world.entity.ai.attributes.Attribute")
                }
            }
            if(expression.name == "map_mob_effect") {
                if(writerData.isAboveVersion(20, 4)) {
                    return ParsedTokenExpressionTypeComplex("net.minecraft.core.Holder")
                }else {
                    return ParsedTokenExpressionTypeComplex("net.minecraft.world.effect.MobEffect")
                }
            }
            if(expression.name == "map_resource_location") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.resources.ResourceLocation")
            }
            if(expression.name == "map_nbt_value") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.nbt.Tag")
            }
            if(expression.name == "map_scoreboard_criterion") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.world.scores.criteria.ObjectiveCriteria")
            }
            if(expression.name == "map_text") {
                return ParsedTokenExpressionTypeComplex("net.minecraft.network.chat.Component")
            }
            if(expression.name == "to_double") {
                return ParsedTokenExpressionSimpleType.DOUBLE
            }
        }
        val paramTypes = expression.arguments.map { evaluateExpressionType(writerData, it) }
        val instance = expression.instance
        val functionMatches = if(instance is ParsedTokenInstanceValue) {
            val instanceType = evaluateExpressionType(writerData, instance.expression)
            if(instanceType !is ParsedTokenExpressionTypeComplex) {
                throw IllegalArgumentException("Cannot run functions on a primitive type: $instanceType")
            }
            val matches = getClassAccessibleMethods(instanceType.typeName).filter { !it.isStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }.toMutableList()
            if(matches.size < 1) {
                throw IllegalArgumentException("Failed to find function [${instanceType.typeName}].${expression.name}(${paramTypes.joinToString()})")
            }
            if(matches.size > 1) {
                val closest = matches.map { it to checkClassDistance(it.owner.replace('/', '.').replace('$', '.'), instanceType.typeName) }.reduce { a, b -> if(a.second == null || a.second!! > b.second!!) b else a }
                matches.clear()
                matches.add(closest.first)
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
            return ParsedTokenExpressionSimpleType.BOOLEAN
        }
        if(def == Type.INT_TYPE) {
            return ParsedTokenExpressionSimpleType.INTEGER
        }
        if(def == Type.FLOAT_TYPE) {
            return ParsedTokenExpressionSimpleType.FLOAT
        }
        if(def == Type.DOUBLE_TYPE) {
            return ParsedTokenExpressionSimpleType.DOUBLE
        }
        if(def.sort == Type.OBJECT) {
            return ParsedTokenExpressionTypeComplex(def.className)
        }
        if(expression.name == "<init>") {
            if(functionMatches[0].owner == "java/lang/Object" && functionMatches[0].name == "<init>" && functionMatches[0].descriptor == "()V") {
                return ParsedTokenExpressionTypeComplex((instance as ParsedTokenInstanceStatic).className.replace('/', '.').replace('$', '.'))
            }
            return ParsedTokenExpressionTypeComplex(functionMatches[0].owner.replace('/', '.').replace('$', '.'))
        }
        throw IllegalArgumentException(def.descriptor)
    }else if(expression is ParsedTokenExpressionField) {
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
            return ParsedTokenExpressionSimpleType.BOOLEAN
        }
        if(def == Type.INT_TYPE) {
            return ParsedTokenExpressionSimpleType.INTEGER
        }
        if(def.sort == Type.OBJECT) {
            return ParsedTokenExpressionTypeComplex(def.className)
        }
        TODO(def.descriptor)
    }
    TODO("$expression")
}

private fun ParsedTokenExpressionType.isObjectLike(): Boolean {
    return this is ParsedTokenExpressionTypeComplex || this == ParsedTokenExpressionSimpleType.NULL || this == ParsedTokenExpressionSimpleType.OBJECT
}

private fun ParsedTokenExpressionType.isIntegerLikeType(): Boolean {
    return this == ParsedTokenExpressionSimpleType.INTEGER || this == ParsedTokenExpressionSimpleType.BOOLEAN
}

private fun isExpressionSimpleType(writerData: WriterData, expression: ParsedTokenExpression, type: ParsedTokenExpressionSimpleType): Boolean {
    return evaluateExpressionType(writerData, expression) == type
}

private fun doesArgumentTypeMatch(matchType: ParsedTokenExpressionType, expectedType: Type): Boolean {
    if(expectedType == Type.BOOLEAN_TYPE) {
        return matchType == ParsedTokenExpressionSimpleType.BOOLEAN
    }else if(expectedType == Type.INT_TYPE) {
        return matchType == ParsedTokenExpressionSimpleType.INTEGER
    }else if(expectedType == Type.LONG_TYPE) {
        return matchType == ParsedTokenExpressionSimpleType.LONG
    }else if(expectedType == Type.FLOAT_TYPE) {
        return matchType == ParsedTokenExpressionSimpleType.FLOAT
    }else if(expectedType == Type.DOUBLE_TYPE) {
        return matchType == ParsedTokenExpressionSimpleType.DOUBLE
    }else if(expectedType.sort == Type.OBJECT) {
        return matchType == ParsedTokenExpressionSimpleType.NULL || (matchType is ParsedTokenExpressionTypeComplex && (getClassLowerBounds(expectedType.className).any { matchType.typeName == it } || getClassLowerBounds(matchType.typeName).any { expectedType.className == it }))
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
            if(mapping != null && expression != mapping) {
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
            if(type == ParsedTokenExpressionSimpleType.INTEGER) {
                if(keepResult) {
                    output.appendText("cw.writeInt(context.instruction().<Integer>get(\"${expression.name}\"));\n")
                }
                return uniqId
            }else if(type == ParsedTokenExpressionSimpleType.BOOLEAN) {
                if(keepResult) {
                    output.appendText("cw.writeInt(context.instruction().<Boolean>get(\"${expression.name}\") ? 1 : 0);\n")
                }
                return uniqId
            }else if(type == ParsedTokenExpressionSimpleType.DOUBLE) {
                if(keepResult) {
                    output.appendText("cw.writeDouble(context.instruction().<Double>get(\"${expression.name}\"));\n")
                }
                return uniqId
            }else if(type is ParsedTokenExpressionTypeComplex && type.typeName == "java.lang.String") {
                if(keepResult) {
                    output.appendText("cw.writeString(context.instruction().<java.lang.String>get(\"${expression.name}\"));\n")
                }
                return uniqId
            }else if(type is ParsedTokenExpressionTypeComplex && type.typeName == "java.util.UUID") {
                if(keepResult) {
                    output.appendText("context.fieldStorage().loadUUID(cw, context.instruction().<java.util.UUID>get(\"${expression.name}\"));\n")
                }
                return uniqId
            }
        }else if(expression.scope == ParsedTokenValueAccessorScope.LOCAL) {
            if(writerData.localTypes[expression.name] == null) {
                throw IllegalStateException("Local variable '${expression.name}' not found")
            }
            if(keepResult) {
                if(writerData.getAccessorType(expression).isIntegerLikeType()) {
                    output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, ${writerData.localTypes[expression.name]!!.mapping}.index());\n")
                    return uniqId
                }else if(writerData.getAccessorType(expression) == ParsedTokenExpressionSimpleType.DOUBLE) {
                    output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.DLOAD, ${writerData.localTypes[expression.name]!!.mapping}.index());\n")
                    return uniqId
                }else {
                    output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, ${writerData.localTypes[expression.name]!!.mapping}.index());\n")
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
    }else if(expression is ParsedTokenExpressionBoolean) {
        if(keepResult) {
            if(expression.value) {
                output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.ICONST_1);\n")
            }else {
                output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.ICONST_0);\n")
            }
        }
        return uniqId
    }else if(expression is ParsedTokenExpressionTyped) {
        if(expression.type == ParsedTokenExpressionSimpleType.NULL) {
            output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);\n")
            return uniqId
        }
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
            if(isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.DOUBLE)) {
                var uid = uniqId
                uid = writeExpression(writerData.copyLocalTypes(), expression.a, output, uid)
                uid = writeExpression(writerData.copyLocalTypes(), expression.b, output, uid)
                if(keepResult) {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.DADD);\n")
                }else {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.POP2);\n")
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
            if(isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.DOUBLE)) {
                var uid = uniqId
                uid = writeExpression(writerData.copyLocalTypes(), expression.a, output, uid)
                uid = writeExpression(writerData.copyLocalTypes(), expression.b, output, uid)
                if(keepResult) {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.DSUB);\n")
                }else {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.POP2);\n")
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.POP2);\n")
                }
                return uid
            }
        }else if(expression.op == ParsedTokenExpressionOperator.MUL) {
            if(isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.INTEGER)) {
                var uid = uniqId
                uid = writeExpression(writerData.copyLocalTypes(), expression.a, output, uid)
                uid = writeExpression(writerData.copyLocalTypes(), expression.b, output, uid)
                if(keepResult) {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.IMUL);\n")
                }else {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.POP2);\n")
                }
                return uid
            }
            if(isExpressionSimpleType(writerData, expression, ParsedTokenExpressionSimpleType.DOUBLE)) {
                var uid = uniqId
                uid = writeExpression(writerData.copyLocalTypes(), expression.a, output, uid)
                uid = writeExpression(writerData.copyLocalTypes(), expression.b, output, uid)
                if(keepResult) {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.DMUL);\n")
                }else {
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.POP2);\n")
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
                val matches = getClassAccessibleMethods(instanceType.typeName).filter { !it.isStatic && it.name == expression.name && Type.getArgumentCount(it.descriptor) == paramTypes.size && Type.getArgumentTypes(it.descriptor).mapIndexed { index, type -> doesArgumentTypeMatch(paramTypes[index], type) }.none { !it } }.toMutableList()
                if(matches.size < 1) {
                    throw IllegalArgumentException("Failed to find function [${instanceType.typeName}].${expression.name}(${paramTypes.joinToString()})")
                }
                if(matches.size > 1) {
                    val closest = matches.map { it to checkClassDistance(it.owner.replace('/', '.').replace('$', '.'), instanceType.typeName) }.reduce { a, b -> if(a.second == null || a.second!! > b.second!!) b else a }
                    matches.clear()
                    matches.add(closest.first)
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
            val def = functionMatches[0].run { if(owner == "java/lang/Object" && name == "<init>" && descriptor == "()V") ClassMethodInfo((instance as ParsedTokenInstanceStatic).className.replace('.', '/'), "<init>", "()V", false) else this }
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
            if(!keepResult && Type.getReturnType(def.descriptor) != Type.VOID_TYPE) {
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
                                output.appendText("mv.visitTypeInsn(org.objectweb.asm.Opcodes.CHECKCAST, \"${writerData.mappingData.getClassName((expectedType as ParsedTokenExpressionTypeComplex).typeName.replace('.', '/'))}\");\n")
                            }
                            val yieldFuncAccessor = expression.arguments.last() as ParsedTokenValueAccessor
                            writerData.markSet(yieldFuncAccessor)
                            when(yieldFuncAccessor.scope) {
                                ParsedTokenValueAccessorScope.CONTEXT, ParsedTokenValueAccessorScope.INSTRUCTION -> {
                                    output.appendText("context.save(\"${yieldFuncAccessor.name}\");\n") }
                                ParsedTokenValueAccessorScope.LOCAL -> {
                                    val type = writerData.getAccessorType(yieldFuncAccessor)
                                    if(type.isIntegerLikeType()) {
                                        output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, ${writerData.localTypes[yieldFuncAccessor.name]!!.mapping}.index());\n")
                                    }else if(type.isObjectLike()) {
                                        output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, ${writerData.localTypes[yieldFuncAccessor.name]!!.mapping}.index());\n")
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
            if(expression.name == "cast") {
                val uid = writeExpression(writerData, expression.arguments[0], output, uniqId, true)
                val className = ((expression.arguments[1] as ParsedTokenExpressionTyped).type as ParsedTokenExpressionTypeComplex).typeName.replace('.', '/')
                val convertedClassName = writerData.mappingData.getClassName(className)
                output.appendText("mv.visitTypeInsn(org.objectweb.asm.Opcodes.CHECKCAST, \"$convertedClassName\");\n")
                return uid
            }
            if(expression.name == "is") {
                val uid = writeExpression(writerData, expression.arguments[0], output, uniqId, true)
                val className = ((expression.arguments[1] as ParsedTokenExpressionTyped).type as ParsedTokenExpressionTypeComplex).typeName.replace('.', '/')
                val convertedClassName = writerData.mappingData.getClassName(className)
                output.appendText("mv.visitTypeInsn(org.objectweb.asm.Opcodes.INSTANCEOF, \"$convertedClassName\");\n")
                return uid
            }
            if(expression.name == "map_enum") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    val destType = ((expression.arguments[1] as ParsedTokenExpressionTyped).type as ParsedTokenExpressionTypeComplex)
                    val enumMappings = writerData.enums.getEnumMappings(sourceType.typeName, destType.typeName)
                    if(enumMappings.isNotEmpty()) {
                        var uid = uniqId
                        if(keepResult) {
                            output.appendText("switch(context.instruction().<${sourceType.typeName}>get(\"${source.name}\")) {\n")
                            for((k, v) in enumMappings) {
                                output.appendText("    case $k -> ")
                                val packageName = v.substringBeforeLast('.')
                                val fieldName = v.substringAfterLast('.')
                                uid = writeExpression(writerData, ParsedTokenExpressionField(fieldName, ParsedTokenInstanceStatic(packageName)), output, uid, true)
                            }
                            output.appendText("}\n")
                        }
                        return uid
                    }
                }
            }
            if(expression.name == "map_attribute") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.DataLocation") {
                        if(keepResult) {
                            output.appendText("context.fieldStorage().loadAttribute(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.DataLocation>get(\"${source.name}\"));\n")
                        }
                        return uniqId
                    }
                }
            }
            if(expression.name == "map_mob_effect") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.DataLocation") {
                        if(keepResult) {
                            output.appendText("context.fieldStorage().loadEffect(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.DataLocation>get(\"${source.name}\"));\n")
                        }
                        return uniqId
                    }
                }
            }
            if(expression.name == "map_resource_location") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.DataLocation") {
                        if(keepResult) {
                            output.appendText("context.fieldStorage().loadResourceLocation(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.DataLocation>get(\"${source.name}\"));\n")
                        }
                        return uniqId
                    }
                }
            }
            if(expression.name == "map_nbt_value") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue") {
                        if(keepResult) {
                            output.appendText("context.fieldStorage().loadNbtValue(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue>get(\"${source.name}\"));\n")
                        }
                        return uniqId
                    }
                }
            }
            if(expression.name == "map_scoreboard_criterion") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCriterionData") {
                        if(keepResult) {
                            output.appendText("context.fieldStorage().loadScoreboardCriterion(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCriterionData>get(\"${source.name}\").name());\n")
                        }
                        return uniqId
                    }
                }
            }
            if(expression.name == "map_text") {
                val source = expression.arguments[0] as ParsedTokenValueAccessor
                if(source.scope == ParsedTokenValueAccessorScope.INSTRUCTION) {
                    val sourceType = writerData.getAccessorType(source) as ParsedTokenExpressionTypeComplex
                    if(sourceType.typeName == "dev.epicpix.minecraftfunctioncompiler.data.text.Text") {
                        if(keepResult) {
                            output.appendText("context.fieldStorage().loadText(cw, context.instruction().<dev.epicpix.minecraftfunctioncompiler.data.text.Text>get(\"${source.name}\"));\n")
                        }
                        return uniqId
                    }
                }
            }
            if(expression.name == "return_fail") {
                output.appendText("cw.writeLong(${1L shl 32}L);\n")
                output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.LRETURN);\n")
                return uniqId
            }
            if(expression.name == "return_success") {
                val sourceType = evaluateExpressionType(writerData, expression.arguments[0])
                if(sourceType == ParsedTokenExpressionSimpleType.INTEGER) {
                    val uid = writeExpression(writerData, expression.arguments[0], output, uniqId, true)
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.I2L);\n")
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.LRETURN);\n")
                    return uid
                }
            }
            if(expression.name == "to_double") {
                val sourceType = evaluateExpressionType(writerData, expression.arguments[0])
                if(sourceType == ParsedTokenExpressionSimpleType.INTEGER) {
                    val uid = writeExpression(writerData, expression.arguments[0], output, uniqId, true)
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.I2D);\n")
                    return uid
                }else if(sourceType == ParsedTokenExpressionSimpleType.FLOAT) {
                    val uid = writeExpression(writerData, expression.arguments[0], output, uniqId, true)
                    output.appendText("mv.visitInsn(org.objectweb.asm.Opcodes.F2D);\n")
                    return uid
                }
            }
            throw UnsupportedOperationException("Unknown function call: ${expression.name} / ${expression}")
        }
    }else if(expression is ParsedTokenExpressionField) {
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
            }else if(typeA is ParsedTokenExpressionTypeComplex && typeB is ParsedTokenExpressionTypeComplex) {
                uid = writeExpression(writerData.copyLocalTypes(), expression.a, output, uid)
                uid = writeExpression(writerData.copyLocalTypes(), expression.b, output, uid)
                if(expression.op == ParsedTokenExpressionOperator.NE) {
                    output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.IF_ACMPEQ, $failureLabel);\n")
                }else {
                    output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.IF_ACMPNE, $failureLabel);\n")
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
                output.appendText("context.newScopes().put(context.instruction().<dev.epicpix.minecraftfunctioncompiler.il.ScopeData>get(\"${scopeAccessor.name}\").id(), $jumpLabel);\n")
                uid = writeCode(writerData.copyLocalTypes(), el.code!!, output, uid, yieldCallbacks)
                output.appendText("mv.visitLabel($jumpLabel);\n")
            }else if(el.name == "exit_scope") {
                val scopeAccessor = el.arguments[0] as ParsedTokenValueAccessor
                writerData.markGet(scopeAccessor)
                output.appendText("mv.visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, context.newScopes().get(context.instruction().<dev.epicpix.minecraftfunctioncompiler.il.ScopeData>get(\"${scopeAccessor.name}\").id()));\n")
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
            val assignType = if(type == ParsedTokenExpressionSimpleType.INTEGER) {
                "I"
            }else if(type == ParsedTokenExpressionSimpleType.DOUBLE) {
                "D"
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
                    output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, $storageLocal.index());\n")
                } else if(assignType.first() == 'L') {
                    val expressionType = evaluateExpressionType(writerData, el.expression)
                    if(expressionType != ParsedTokenExpressionSimpleType.NULL) {
                        val expressionClasses = getClassLowerBounds((expressionType as ParsedTokenExpressionTypeComplex).typeName.replace('/', '.'))
                        if(!expressionClasses.contains((type as ParsedTokenExpressionTypeComplex).typeName)) {
                            throw IllegalArgumentException("Invalid assign, wrong type: $expressionType, expected $type")
                        }
                    }
                    output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, $storageLocal.index());\n")
                }else if(assignType.first() == 'D') {
                    output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.DSTORE, $storageLocal.index());\n")
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
                        output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, ${writerData.localTypes[el.accessor.name]!!.mapping}.index());\n")
                    }else if(type == ParsedTokenExpressionSimpleType.DOUBLE) {
                        output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.DSTORE, ${writerData.localTypes[el.accessor.name]!!.mapping}.index());\n")
                    }else if(type.isObjectLike()) {
                        output.appendText("mv.visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, ${writerData.localTypes[el.accessor.name]!!.mapping}.index());\n")
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

fun parseCode(inputCode: String) = parseTokens(lexTokens(inputCode).listIterator())

fun generateCode(mappingData: MappingData?, definition: ParsedTokenInstructionDefinition, file: ParsedFile, output: OutputStream, enums: EnumList, majorVersion: Int, minorVersion: Int): List<AccessorStatistics> {
    val bout = ByteArrayOutputStream()
    val writerData = WriterData(mappingData = mappingData, file = file, params = definition.params, enums = enums, majorVersion = majorVersion, minorVersion = minorVersion)
    if(definition.code.isNotEmpty()) {
        try {
            bout.appendText("org.objectweb.asm.Label ${writerData.localReturn} = new org.objectweb.asm.Label();\n")
            writeCode(writerData, definition.code, bout, 0)
            bout.appendText("mv.visitLabel(${writerData.localReturn});\n")
        }catch(e: Exception) {
            throw RuntimeException("Failed to write code for instruction definition ${definition.name}", e)
        }
    }
    output.appendText("// ${definition.name}\n")
    output.write(bout.toByteArray())
    return writerData.paramStatistics
}
