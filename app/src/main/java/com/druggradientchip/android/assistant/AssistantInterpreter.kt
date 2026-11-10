package com.druggradientchip.android.assistant

import com.druggradientchip.android.reverse.HighConcentrationInlet

class AssistantInterpreter {
    fun parse(message: String, session: AssistantSessionState = AssistantSessionState()): ParseResult {
        val text = normalize(message)
        if (text.isEmpty()) return clarify(ClarificationReason.EMPTY_MESSAGE)
        if (MALFORMED_NUMBER.containsMatchIn(text)) return clarify(ClarificationReason.MALFORMED_NUMBER)

        val forwardIntent = containsAny(text, "forward", "正向", "前向")
        val reverseIntent = containsAny(text, "reverse", "反向")
        if (forwardIntent && reverseIntent) return clarify(ClarificationReason.CONFLICTING_MODE)

        parseControl(text, forwardIntent, reverseIntent)?.let { return ParseResult(it) }

        val inlet = parseInlet(text)
        if (inlet.conflict) return clarify(ClarificationReason.CONFLICTING_INLET)

        val targets = parseTargets(text)
        if (reverseIntent || targets != null || (session.activeMode == AssistantMode.REVERSE && !forwardIntent)) {
            return ParseResult(AssistantCommand.ReverseUpdate(inlet.value, targets))
        }

        val chipCount = parseChipCount(text, forwardIntent)
        val patches = mutableListOf<ChipPatch>()
        var activePosition = session.activeChipPosition
        var sawChipFieldWithoutPosition = false
        for (segment in text.split(SEGMENT_SEPARATOR).filter(String::isNotBlank)) {
            val positions = parsePositions(segment)
            if (positions.size > 1) return clarify(ClarificationReason.CONFLICTING_CHIP_POSITIONS)
            if (positions.size == 1) activePosition = positions.single()

            val extraction = parseChipPatch(segment, activePosition)
            if (extraction.conflict) return clarify(ClarificationReason.REPEATED_FIELD_CONFLICT)
            if (extraction.hasFields && activePosition == null) sawChipFieldWithoutPosition = true
            extraction.patch?.let(patches::add)
        }
        if (sawChipFieldWithoutPosition) return clarify(ClarificationReason.CHIP_POSITION_REQUIRED)

        val merged = mergePatches(patches) ?: return clarify(ClarificationReason.REPEATED_FIELD_CONFLICT)
        if (!forwardIntent && session.activeMode != AssistantMode.FORWARD && chipCount == null && merged.isEmpty() && inlet.value != null) {
            return clarify(ClarificationReason.UNSUPPORTED_OR_AMBIGUOUS)
        }
        if (merged.isEmpty() && chipCount == null && inlet.value == null && !forwardIntent) {
            return ParseResult(
                AssistantCommand.Clarify(ClarificationReason.UNSUPPORTED_OR_AMBIGUOUS),
                listOf(ParseWarning.UNRECOGNIZED_TEXT),
            )
        }
        return ParseResult(AssistantCommand.ForwardUpdate(chipCount, inlet.value, merged))
    }

    private data class InletParse(val value: HighConcentrationInlet?, val conflict: Boolean = false)
    private data class PatchExtraction(val patch: ChipPatch?, val hasFields: Boolean, val conflict: Boolean = false)

    private fun parseControl(text: String, forward: Boolean, reverse: Boolean): AssistantCommand? {
        if (HELP.containsMatchIn(text) && !hasDataExpression(text)) {
            val category = when {
                    forward -> HelpCategory.FORWARD
                    reverse -> HelpCategory.REVERSE
                    else -> HelpCategory.OVERVIEW
                }
            return AssistantCommand.Help(category, helpExamples(category))
        }
        if (forward || reverse || hasDataExpression(text)) return null
        return when {
            RESET.matches(text) -> AssistantCommand.Reset
            CANCEL.matches(text) -> AssistantCommand.Cancel
            CONFIRM.matches(text) -> AssistantCommand.ConfirmAndRun
            else -> null
        }
    }

    private fun hasDataExpression(text: String): Boolean =
        CHIP_COUNT.any { it.containsMatchIn(text) } || TARGET_PREFIX.containsMatchIn(text) ||
            FIELD_CONTEXT.containsMatchIn(text) || parsePositions(text).isNotEmpty()

    private fun parseChipCount(text: String, forwardIntent: Boolean): Int? {
        CHIP_COUNT.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1)?.toIntOrNull() }?.let { return it }
        if (!forwardIntent) return null
        return Regex("(?:use|with|做|使用|设置)?\\s*(\\d{1,2})\\s*(?:-?\\s*chips?|个芯片)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseInlet(text: String): InletParse {
        val one = INLET_ONE.any { it.containsMatchIn(text) }
        val two = INLET_TWO.any { it.containsMatchIn(text) }
        return when {
            one && two -> InletParse(null, true)
            one -> InletParse(HighConcentrationInlet.INLET_1)
            two -> InletParse(HighConcentrationInlet.INLET_2)
            else -> InletParse(null)
        }
    }

    private fun parseTargets(text: String): List<Float>? {
        val match = TARGET_PREFIX.find(text) ?: return null
        var region = text.substring(match.range.last + 1)
        TAIL_INLET.find(region)?.let { region = region.substring(0, it.range.first) }
        return NUMBER.findAll(region).map { it.value.toFloat() }.toList()
    }

    private fun parsePositions(text: String): List<Int> {
        val found = linkedSetOf<Int>()
        POSITION_PATTERNS.forEach { regex ->
            regex.findAll(text).forEach { it.groupValues[1].toIntOrNull()?.let(found::add) }
        }
        CHINESE_ORDINAL.findAll(text).forEach { match -> chineseNumber(match.groupValues[1])?.let(found::add) }
        ENGLISH_ORDINALS.forEachIndexed { index, ordinal ->
            if (Regex("\\b$ordinal\\s+(?:chip|position)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) found += index + 1
        }
        return found.toList()
    }

    private fun parseChipPatch(text: String, position: Int?): PatchExtraction {
        val structures = parseStructures(text)
        val structure = structures.singleOrNull()
        val leftWidth = fieldValues(text, LEFT_WIDTH)
        val rightWidth = fieldValues(text, RIGHT_WIDTH)
        val leftDistance = fieldValues(text, LEFT_DISTANCE)
        val rightDistance = fieldValues(text, RIGHT_DISTANCE)
        val hasFields = structure != null || leftWidth.isNotEmpty() || rightWidth.isNotEmpty() ||
            leftDistance.isNotEmpty() || rightDistance.isNotEmpty()
        if (structures.distinct().size > 1 || listOf(leftWidth, rightWidth, leftDistance, rightDistance).any { it.distinct().size > 1 }) {
            return PatchExtraction(null, hasFields = true, conflict = true)
        }
        if (!hasFields || position == null) return PatchExtraction(null, hasFields)
        return PatchExtraction(
            ChipPatch(
                position = position,
                chipType = structure,
                leftWidthMm = leftWidth.singleOrNull(),
                rightWidthMm = rightWidth.singleOrNull(),
                leftDistanceMm = leftDistance.singleOrNull(),
                rightDistanceMm = rightDistance.singleOrNull(),
            ),
            true,
        )
    }

    private fun fieldValues(text: String, aliases: List<String>): List<Float> {
        val alias = aliases.joinToString("|") { Regex.escape(it) }
        val ordinal = "(?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth)"
        val scope = "(?:of\\s+(?:the\\s+)?(?:(?:chip|position)\\s*\\d{1,2}|$ordinal\\s+(?:chip|position))\\s*)?"
        val regex = Regex("(?:$alias)\\s*$scope(?:is|to|为|是|改成|改为|=|:)?\\s*($SIGNED_NUMBER)", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.groupValues[1].toFloat() }.toList()
    }

    private fun parseStructures(text: String): List<String> = STRUCTURE.findAll(text).filter { match ->
        val prefix = text.substring(0, match.range.first).takeLast(24)
        STRUCTURE_CONTEXT.containsMatchIn(prefix) || text.trim() == match.value
    }.map { "${it.groupValues[1]}to${it.groupValues[2]}" }.toList()

    private fun mergePatches(patches: List<ChipPatch>): List<ChipPatch>? {
        val result = mutableListOf<ChipPatch>()
        patches.groupBy(ChipPatch::position).toSortedMap().forEach { (position, values) ->
            fun <T> one(selector: (ChipPatch) -> T?): T? {
                val distinct = values.mapNotNull(selector).distinct()
                if (distinct.size > 1) throw FieldConflict()
                return distinct.singleOrNull()
            }
            try {
                result += ChipPatch(position, one { it.chipType }, one { it.leftWidthMm }, one { it.rightWidthMm }, one { it.leftDistanceMm }, one { it.rightDistanceMm })
            } catch (_: FieldConflict) {
                return null
            }
        }
        return result
    }

    private class FieldConflict : RuntimeException()

    private fun normalize(value: String): String = value.trim()
        .replace('：', ':').replace('＝', '=').replace('－', '-').replace(Regex("\\s+"), " ")

    private fun chineseNumber(value: String): Int? = when (value) {
        "一" -> 1; "二", "两" -> 2; "三" -> 3; "四" -> 4; "五" -> 5
        "六" -> 6; "七" -> 7; "八" -> 8; "九" -> 9; "十" -> 10
        else -> null
    }

    private fun clarify(reason: ClarificationReason) = ParseResult(AssistantCommand.Clarify(reason))
    private fun helpExamples(category: HelpCategory): List<HelpExample> = when (category) {
        HelpCategory.FORWARD -> listOf(
            HelpExample.FORWARD_GLOBAL_PARAMETERS,
            HelpExample.FORWARD_CHIP_PARAMETERS,
            HelpExample.FORWARD_CONTEXT_CONTINUATION,
        )
        HelpCategory.REVERSE -> listOf(HelpExample.REVERSE_TARGETS_AND_INLET)
        HelpCategory.OVERVIEW -> HelpExample.entries
    }
    private fun containsAny(text: String, vararg values: String) = values.any { text.contains(it, ignoreCase = true) }

    private companion object {
        const val SIGNED_NUMBER = "[+-]?(?:\\d+\\.\\d+|\\d+|\\.\\d+)"
        val NUMBER = Regex("(?<![0-9.])$SIGNED_NUMBER(?![0-9.])")
        val MALFORMED_NUMBER = Regex("(?<![0-9.])(?:[+-]?\\d*\\.){2,}\\d+")
        val SEGMENT_SEPARATOR = Regex("[\\n,;，；。]+")
        val TARGET_PREFIX = Regex("(?:target concentrations?|target values?|目标浓度|目标值)\\s*(?:依次|分别)?\\s*(?:are|is|为|是|:|=)?\\s*", RegexOption.IGNORE_CASE)
        val TAIL_INLET = Regex("(?:with\\s+|using\\s+)?inlet\\s*[12]|入口\\s*[一二12]|[一二]号入口", RegexOption.IGNORE_CASE)
        val CHIP_COUNT = listOf(
            Regex("(?:chip count|芯片数量|芯片数)\\s*(?:=|为|是|to)?\\s*(\\d{1,2})", RegexOption.IGNORE_CASE),
            Regex("(\\d{1,2})\\s*(?:-?\\s*chips?|个芯片)", RegexOption.IGNORE_CASE),
        )
        val INLET_ONE = listOf(
            Regex("inlet\\s*1.{0,16}high|high.{0,16}inlet\\s*1", RegexOption.IGNORE_CASE),
            Regex("(?:入口\\s*1|入口一|一号入口).{0,12}(?:高浓度|高)"),
            Regex("(?:高浓度|高).{0,12}(?:入口\\s*1|入口一|一号入口)"),
        )
        val INLET_TWO = listOf(
            Regex("inlet\\s*2.{0,16}high|high.{0,16}inlet\\s*2", RegexOption.IGNORE_CASE),
            Regex("(?:入口\\s*2|入口二|二号入口).{0,12}(?:高浓度|高)"),
            Regex("(?:高浓度|高).{0,12}(?:入口\\s*2|入口二|二号入口)"),
        )
        val POSITION_PATTERNS = listOf(
            Regex("(?:chip|position|芯片|位置)\\s*(\\d{1,2})", RegexOption.IGNORE_CASE),
            Regex("第\\s*(\\d{1,2})\\s*个?(?:芯片|位置)?"),
            Regex("(\\d{1,2})\\s*号(?:芯片)?"),
        )
        val CHINESE_ORDINAL = Regex("第\\s*([一二两三四五六七八九十])\\s*个?(?:芯片|位置)?")
        val ENGLISH_ORDINALS = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth")
        val STRUCTURE = Regex("(?<![A-Za-z0-9])([0-9]+)\\s*(?:to|-)\\s*([0-9]+)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
        val STRUCTURE_CONTEXT = Regex("(?:chip type|structure|type|uses?|using|用|结构|类型)\\s*(?:is|to|为|是|=|:)?\\s*$", RegexOption.IGNORE_CASE)
        val LEFT_WIDTH = listOf("left width", "左宽", "左侧宽度", "左边宽度")
        val RIGHT_WIDTH = listOf("right width", "右宽", "右侧宽度", "右边宽度")
        val LEFT_DISTANCE = listOf("left bend-straight distance", "left bend straight distance", "left distance", "左弯曲直道距离", "左弯直距离", "左侧距离", "左距离", "左距")
        val RIGHT_DISTANCE = listOf("right bend-straight distance", "right bend straight distance", "right distance", "右弯曲直道距离", "右弯直距离", "右侧距离", "右距离", "右距")
        val FIELD_CONTEXT = Regex("chip type|structure|left width|right width|left distance|right distance|结构|类型|左宽|右宽|左距|右距", RegexOption.IGNORE_CASE)
        val RESET = Regex("^(?:reset|start over|重置|重新开始)[.!。！]?$", RegexOption.IGNORE_CASE)
        val CANCEL = Regex("^(?:cancel|stop|取消|停止)[.!。！]?$", RegexOption.IGNORE_CASE)
        val CONFIRM = Regex("^(?:confirm|run|execute|确认|运行|执行|开始预测)[.!。！]?$", RegexOption.IGNORE_CASE)
        val HELP = Regex("\\bhelp\\b|帮助|怎么用", RegexOption.IGNORE_CASE)
    }
}
