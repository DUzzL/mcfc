package dev.epicpix.annotations.instructions

import dev.epicpix.annotations.MinecraftVersion
import dev.epicpix.annotations.displayToInternalClassName
import java.util.concurrent.atomic.AtomicBoolean

interface ParsedToken
interface ParsedTokenStatement : ParsedToken
interface ParsedTokenExpression : ParsedToken

data class ParsedFile(val fileInfo: Map<String, String>, val definitions: List<ParsedTokenInstructionDefinition>)

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
data class ParsedTokenInstructionDefinition(val instructionFlags: List<String>, val name: String, val params: List<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>>, val code: List<ParsedTokenStatement>, val versionRange: Pair<MinecraftVersion?, MinecraftVersion?>?, var usesObf: Boolean = false) : ParsedToken


interface ParsedTokenInstanceType
@ConsistentCopyVisibility
data class ParsedTokenInstanceStatic private constructor(val displayName: String) : ParsedTokenInstanceType {
    companion object {
        fun fromInternalName(internalName: String): ParsedTokenInstanceStatic {
            return ParsedTokenInstanceStatic(internalName.replace('/', '.').replace('$', '.'))
        }

        fun fromDisplayName(displayName: String): ParsedTokenInstanceStatic {
            return ParsedTokenInstanceStatic(displayName)
        }
    }

    val className: String
        get() = displayToInternalClassName(displayName) ?: throw IllegalArgumentException("Class '$displayName' does not exist")
}
data class ParsedTokenInstanceValue(val expression: ParsedTokenExpression) : ParsedTokenInstanceType

data class ParsedTokenStatementIf(val expression: ParsedTokenExpression, val code: List<ParsedTokenStatement>, val elseCode: List<ParsedTokenStatement>?) : ParsedTokenStatement
data class ParsedTokenStatementCompileIf(val expression: ParsedTokenExpression, val code: List<ParsedTokenStatement>, val elseCode: List<ParsedTokenStatement>?) : ParsedTokenStatement
data class ParsedTokenStatementCompileMatch(val expression: ParsedTokenExpression, val matches: Map<String, List<ParsedTokenStatement>>) : ParsedTokenStatement
data class ParsedTokenStatementFunctionCall(val name: String, val instance: ParsedTokenInstanceType?, val arguments: List<ParsedTokenExpression>) : ParsedTokenStatement, ParsedTokenExpression
data class ParsedTokenStatementAssignVariable(val accessor: ParsedTokenValueAccessor, val expression: ParsedTokenExpression) : ParsedTokenStatement
data class ParsedTokenStatementReturn(val value: ParsedTokenExpression?) : ParsedTokenStatement

data class ParsedTokenExpressionField(val name: String, val instance: ParsedTokenInstanceType) : ParsedTokenExpression
data class ParsedTokenExpressionBoolean(val value: Boolean) : ParsedTokenExpression
data class ParsedTokenExpressionNumber(val number: Long) : ParsedTokenExpression
data class ParsedTokenExpressionString(val string: String) : ParsedTokenExpression
data class ParsedTokenExpressionTyped(val type: ParsedTokenExpressionType) : ParsedTokenExpression
data class ParsedTokenExpressionBinary(val a: ParsedTokenExpression, val b: ParsedTokenExpression, val op: ParsedTokenExpressionOperator) : ParsedTokenExpression

enum class ParsedTokenValueAccessorScope {
    CONTEXT, INSTRUCTION, LOCAL, STATIC, RETURN,
}

interface ParsedTokenExpressionType
@ConsistentCopyVisibility
data class ParsedTokenExpressionTypeComplex private constructor(val displayName: String, val generics: List<ParsedTokenExpressionTypeComplex>) : ParsedTokenExpressionType {
    companion object {
        fun fromInternalName(internalName: String): ParsedTokenExpressionTypeComplex {
            return fromInternalName(internalName.replace('/', '.').replace('$', '.'), listOf())
        }

        fun fromInternalName(internalName: String, generics: List<ParsedTokenExpressionTypeComplex>): ParsedTokenExpressionTypeComplex {
            return ParsedTokenExpressionTypeComplex(internalName.replace('/', '.').replace('$', '.'), generics)
        }

        fun fromDisplayName(displayName: String): ParsedTokenExpressionTypeComplex {
            return fromDisplayName(displayName, listOf())
        }

        fun fromDisplayName(displayName: String, generics: List<ParsedTokenExpressionTypeComplex>): ParsedTokenExpressionTypeComplex {
            return ParsedTokenExpressionTypeComplex(displayName, generics)
        }
    }

    val typeName: String
        get() = displayToInternalClassName(displayName) ?: throw IllegalArgumentException("Class '$displayName' does not exist")
}

data class ParsedTokenExpressionTypeGeneric(val generic: ParsedTokenGenericData, val lowerBound: ParsedTokenExpressionTypeComplex) : ParsedTokenExpressionType

data class ParsedTokenGenericData(val name: String, val allowExtends: Boolean)

enum class ParsedTokenExpressionSimpleType : ParsedTokenExpressionType {
    NULL, BOOLEAN, INTEGER, LONG, FLOAT, DOUBLE, LABEL, OBJECT, NUMBER,
}

enum class ParsedTokenExpressionOperator {
    ADD, SUB, MUL, DIV, MOD, EQ, NE, GT, LT, GE, LE, SHR, SHL,
}

private fun parseExpressionTypeWord(iter: ListIterator<LexToken>, complexTypeOpened: Boolean = false): ParsedTokenExpressionType {
    if(complexTypeOpened || iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
        var t = ""
        while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)) {
            if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
                val generic = parseExpressionTypeWord(iter, true) as ParsedTokenExpressionTypeComplex
                iter.expectTokenType(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)
                return ParsedTokenExpressionTypeComplex.fromDisplayName(t, listOf(generic))
            }
            if(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
                t += "."
            }else {
                t += iter.expectToken<LexTokenWord>().word
            }
        }
        return ParsedTokenExpressionTypeComplex.fromDisplayName(t, emptyList())
    }
    val word = iter.expectToken<LexTokenWord>()
    return when(word.word) {
        "boolean" -> ParsedTokenExpressionSimpleType.BOOLEAN
        "int" -> ParsedTokenExpressionSimpleType.INTEGER
        "double" -> ParsedTokenExpressionSimpleType.DOUBLE
        "float" -> ParsedTokenExpressionSimpleType.FLOAT
        "long" -> ParsedTokenExpressionSimpleType.LONG
        "label" -> ParsedTokenExpressionSimpleType.LABEL
        "generic" -> {
            iter.expectTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)
            val name = iter.expectToken<LexTokenWord>()
            iter.expectTokenType(LexTokenSimpleType.COLON)
            val allowExtends = iter.tryNextTokenType(LexTokenSimpleType.PERCENT)
            var lowerBound = ""
            while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)) {
                if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
                    val generic = parseExpressionTypeWord(iter, true) as ParsedTokenExpressionTypeComplex
                    iter.expectTokenType(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)
                    return ParsedTokenExpressionTypeGeneric(ParsedTokenGenericData(name.word, allowExtends), ParsedTokenExpressionTypeComplex.fromDisplayName(lowerBound, listOf(generic)))
                }
                if(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
                    lowerBound += "."
                }else {
                    lowerBound += iter.expectToken<LexTokenWord>().word
                }
            }
            return ParsedTokenExpressionTypeGeneric(ParsedTokenGenericData(name.word, allowExtends), ParsedTokenExpressionTypeComplex.fromDisplayName(lowerBound))
        }
        "number" -> {
            iter.expectTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)
            val name = iter.expectToken<LexTokenWord>()
            iter.expectTokenType(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)
            return ParsedTokenExpressionTypeGeneric(ParsedTokenGenericData(name.word, false), ParsedTokenExpressionTypeComplex.fromInternalName("java/lang/Number"))
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

private fun parseFunctionDefinitionParams(iter: ListIterator<LexToken>, usesObf: AtomicBoolean): MutableList<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>> {
    val res = mutableListOf<Pair<ParsedTokenValueAccessor, ParsedTokenExpressionType>>()
    while(true) {
        if(iter.tryNextToken<LexTokenWord>() == null && !iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
            break
        }
        iter.previous()
        val type = parseExpressionTypeWord(iter)
        if(type is ParsedTokenExpressionTypeComplex) {
            if(type.displayName.startsWith("net.minecraft")) {
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
    var exp1 = parseExpression1(iter, valueDefinitions, usesObf)
    if(!iter.hasNext()) return exp1
    while(true) {
        exp1 = if(iter.tryNextTokenType(LexTokenSimpleType.EQEQ)) {
            ParsedTokenExpressionBinary(exp1, parseExpression1(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.EQ)
        }else if(iter.tryNextTokenType(LexTokenSimpleType.NEEQ)) {
            ParsedTokenExpressionBinary(exp1, parseExpression1(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.NE)
        }else {
            break
        }
    }
    return exp1
}

private fun parseExpression1(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): ParsedTokenExpression {
    var exp1 = parseExpression2(iter, valueDefinitions, usesObf)
    if(!iter.hasNext()) return exp1
    while(true) {
        exp1 = if(iter.tryNextTokenType(LexTokenSimpleType.LE)) {
            ParsedTokenExpressionBinary(exp1, parseExpression2(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.LE)
        } else if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
            ParsedTokenExpressionBinary(exp1, parseExpression2(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.LT)
        } else if(iter.tryNextTokenType(LexTokenSimpleType.GE)) {
            ParsedTokenExpressionBinary(exp1, parseExpression2(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.GE)
        } else if(iter.tryNextTokenType(LexTokenSimpleType.CLOSE_ANGLE_BRACKET)) {
            ParsedTokenExpressionBinary(exp1, parseExpression2(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.GT)
        }else {
            break
        }
    }
    return exp1
}

private fun parseExpression2(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): ParsedTokenExpression {
    var exp1 = parseExpression3(iter, valueDefinitions, usesObf)
    if(!iter.hasNext()) return exp1
    while(true) {
        exp1 = if(iter.tryNextTokenType(LexTokenSimpleType.PLUS)) {
            ParsedTokenExpressionBinary(exp1, parseExpression3(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.ADD)
        }else if(iter.tryNextTokenType(LexTokenSimpleType.MINUS)) {
            ParsedTokenExpressionBinary(exp1, parseExpression3(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.SUB)
        }else {
            break
        }
    }
    return exp1
}

private fun parseExpression3(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): ParsedTokenExpression {
    var exp1 = parseExpression4(iter, valueDefinitions, usesObf)
    if(!iter.hasNext()) return exp1
    while(true) {
        exp1 = if(iter.tryNextTokenType(LexTokenSimpleType.STAR)) {
            ParsedTokenExpressionBinary(exp1, parseExpression4(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.MUL)
        }else if(iter.tryNextTokenType(LexTokenSimpleType.SLASH)) {
            ParsedTokenExpressionBinary(exp1, parseExpression4(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.DIV)
        }else if(iter.tryNextTokenType(LexTokenSimpleType.PERCENT)) {
            ParsedTokenExpressionBinary(exp1, parseExpression4(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.MOD)
        }else {
            break
        }
    }
    return exp1
}

private fun parseExpression4(iter: ListIterator<LexToken>, valueDefinitions: List<ParsedTokenValueAccessor>, usesObf: AtomicBoolean): ParsedTokenExpression {
    var exp1 = parseExpressionBase(iter, valueDefinitions, usesObf)
    if(!iter.hasNext()) return exp1
    while(true) {
        exp1 = if(iter.tryNextTokenType(LexTokenSimpleType.SHR)) {
            ParsedTokenExpressionBinary(exp1, parseExpressionBase(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.SHR)
        }else if(iter.tryNextTokenType(LexTokenSimpleType.SHL)) {
            ParsedTokenExpressionBinary(exp1, parseExpressionBase(iter, valueDefinitions, usesObf), ParsedTokenExpressionOperator.SHL)
        }else {
            break
        }
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
            exp1 = ParsedTokenStatementFunctionCall(name.word, ParsedTokenInstanceValue(exp1), params)
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
        val instanceOf = ParsedTokenInstanceStatic.fromDisplayName((parseExpressionTypeWord(iter) as ParsedTokenExpressionTypeComplex).displayName)
        iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
        val params = parseFunctionCallParams(iter, valueDefinitions, usesObf)
        iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
        return ParsedTokenStatementFunctionCall("<init>", instanceOf, params)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_BRACKET)) {
        iter.previous()
        return parseValueAccessor(iter, valueDefinitions)
    }
    if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_ANGLE_BRACKET)) {
        iter.previous()
        val type = parseExpressionTypeWord(iter)
        if(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
            val classOf = ParsedTokenInstanceStatic.fromDisplayName((type as ParsedTokenExpressionTypeComplex).displayName)
            val name = iter.expectToken<LexTokenWord>()
            if(iter.tryNextTokenType(LexTokenSimpleType.OPEN_PAREN)) {
                val params = parseFunctionCallParams(iter, valueDefinitions, usesObf)
                iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
                return ParsedTokenStatementFunctionCall(name.word, classOf, params)
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
    if(name.word != "_loadMacro" && name.word != "return_fail" && name.word != "return_success" && name.word != "to_float" && name.word != "to_integer" && name.word != "to_double") {
        usesObf.set(true)
    }
    return ParsedTokenStatementFunctionCall(name.word, null, params)
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
    val single = !iter.tryNextTokenType(LexTokenSimpleType.OPEN_BRACE)
    while(single || !iter.tryNextTokenType(LexTokenSimpleType.CLOSE_BRACE)) {
        if(iter.tryNextTokenType(LexTokenSimpleType.KW_IF)) {
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
        }else if(iter.tryNextTokenType(LexTokenSimpleType.KW_COMPILE_IF)) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val expression = parseExpression(iter, valueDefinitions, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            val code = parseCode(iter, valueDefinitions, usesObf)
            if(iter.tryNextTokenType(LexTokenSimpleType.KW_ELSE)) {
                val elseCode = parseCode(iter, valueDefinitions, usesObf)
                res.add(ParsedTokenStatementCompileIf(expression, code, elseCode))
            } else {
                res.add(ParsedTokenStatementCompileIf(expression, code, null))
            }
        }else if(iter.tryNextTokenType(LexTokenSimpleType.KW_COMPILE_MATCH)) {
            iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
            val expression = parseExpression(iter, valueDefinitions, usesObf)
            iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
            iter.expectTokenType(LexTokenSimpleType.OPEN_BRACE)
            val cases = mutableMapOf<String, List<ParsedTokenStatement>>()
            while(!iter.tryNextTokenType(LexTokenSimpleType.CLOSE_BRACE)) {
                val key = iter.expectToken<LexTokenWord>()
                val code = parseCode(iter, valueDefinitions, usesObf)
                cases[key.word] = code
            }
            res.add(ParsedTokenStatementCompileMatch(expression, cases))
        }else if(iter.tryNextTokenType(LexTokenSimpleType.KW_RETURN)) {
            if(iter.tryNextTokenType(LexTokenSimpleType.SEMICOLON)) {
                res.add(ParsedTokenStatementReturn(null))
            }else {
                val expression = parseExpression(iter, valueDefinitions, usesObf)
                res.add(ParsedTokenStatementReturn(expression))
                iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
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
                var expr = ParsedTokenStatementFunctionCall(name.word, null, params)
                var readMore = false
                while(iter.tryNextTokenType(LexTokenSimpleType.DOT)) {
                    val name2 = iter.expectToken<LexTokenWord>()
                    iter.expectTokenType(LexTokenSimpleType.OPEN_PAREN)
                    val params2 = parseFunctionCallParams(iter, valueDefinitions, usesObf)
                    iter.expectTokenType(LexTokenSimpleType.CLOSE_PAREN)
                    expr = ParsedTokenStatementFunctionCall(name2.word, ParsedTokenInstanceValue(expr), params2)
                    readMore = true
                }
                if(code == null || readMore) {
                    iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
                }
                res.add(expr)
            }
        }
        if(single) break
    }
    return res
}

private fun parseVersionNumber(iter: ListIterator<LexToken>): MinecraftVersion {
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
    return MinecraftVersion.parse(res.toString())
}

fun parseTokens(iter: ListIterator<LexToken>): ParsedFile {
    val fileInfo = mutableMapOf<String, String>()
    val instrDefs = mutableListOf<ParsedTokenInstructionDefinition>()
    val instructionFlags = mutableListOf<String>()
    while(iter.hasNext()) {
        val next = iter.next()
        if(next is LexTokenInstructionFlag) {
            instructionFlags.add(next.name)
        }else if(next is LexTokenFileHeader) {
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
            if(iter.tryNextTokenType(LexTokenSimpleType.ARROW)) {
                val type = parseExpressionTypeWord(iter)
                if(type is ParsedTokenExpressionTypeComplex) {
                    if(type.displayName.startsWith("net.minecraft")) {
                        usesObf.set(true)
                    }
                }
                var firstNonStatic = 0
                for(i in params.indices) {
                    if(params[i].first.scope == ParsedTokenValueAccessorScope.STATIC) {
                        firstNonStatic = i + 1
                    }else {
                        break
                    }
                }
                params.add(firstNonStatic, ParsedTokenValueAccessor(ParsedTokenValueAccessorScope.RETURN, "result") to type)
            }
            val code = if(iter.tryNextTokenType(LexTokenSimpleType.ASSIGN)) {
                val expression = parseExpression(iter, params.map { it.first }, usesObf)
                iter.expectTokenType(LexTokenSimpleType.SEMICOLON)
                listOf(ParsedTokenStatementReturn(expression))
            }else {
                parseCode(iter, params.map { it.first }, usesObf)
            }
            instrDefs.add(ParsedTokenInstructionDefinition(instructionFlags.toList(), next.name, params, code, range, usesObf.get()))
            instructionFlags.clear()
        }
    }
    return ParsedFile(fileInfo, instrDefs)
}


private fun ListIterator<LexToken>.expectTokenType(type: LexTokenSimpleType) {
    if(!hasNext()) throw IllegalStateException("Expected token type '$type', got EOF")
    val got = next()
    if(got != type) {
        previous()
        previous()
        previous()
        previous()
        throw IllegalStateException("Expected token type '$type', got '$got' /// (${next()} ${next()} ${next()} ${next()} ${next()} ${next()} ${next()})")
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
        previous()
        previous()
        previous()
        previous()
        throw IllegalStateException("Expected token '${T::class.java}', got '$got' /// (${next()} ${next()} ${next()} ${next()} ${next()} ${next()} ${next()})")
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