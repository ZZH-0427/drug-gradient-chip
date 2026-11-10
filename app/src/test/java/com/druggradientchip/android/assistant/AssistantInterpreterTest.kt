package com.druggradientchip.android.assistant

import com.druggradientchip.android.reverse.HighConcentrationInlet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class AssistantInterpreterTest {
    private val interpreter = AssistantInterpreter()

    @Test
    fun `English and Chinese updates produce language neutral commands`() {
        val english = forward(interpreter.parse("Set the right width of chip 2 to 0.18").command)
        val chinese = forward(interpreter.parse("第二个芯片右侧宽度改成0.18").command)
        assertEquals(english.chips, chinese.chips)
        assertEquals(ChipPatch(position = 2, rightWidthMm = 0.18f), english.chips.single())
    }

    @Test
    fun `all supported position forms resolve without locale coupling`() {
        listOf("第一个芯片", "第二个芯片", "第3个", "1号芯片", "芯片1", "chip 1", "first chip", "position 1").forEach { form ->
            val expected = if (form.contains("二") || form.contains("3")) {
                if (form.contains("二")) 2 else 3
            } else {
                1
            }
            assertEquals(expected, forward(interpreter.parse("$form 左宽0.18").command).chips.single().position)
        }
    }

    @Test
    fun `structure aliases normalize to canonical value`() {
        listOf("4to2", "4 to 2", "4-2").forEach { alias ->
            val command = forward(interpreter.parse("chip 2 structure $alias").command)
            assertEquals("4to2", command.chips.single().chipType)
        }
    }

    @Test
    fun `mixed global and multi-chip Chinese command keeps every scope`() {
        val command = forward(
            interpreter.parse(
                "做6个芯片正向预测，入口1为高浓度；第一个用5to4，左宽0.261，右宽0.138，左距21，右距15；第二个用4-2，左宽0.089，右宽0.04，左距25，右距13",
            ).command,
        )
        assertEquals(6, command.chipCount)
        assertEquals(HighConcentrationInlet.INLET_1, command.highInlet)
        assertEquals(listOf(1, 2), command.chips.map(ChipPatch::position))
        assertEquals("4to2", command.chips[1].chipType)
    }

    @Test
    fun `context continuation uses only an unambiguous active chip`() {
        val state = AssistantSessionState(activeMode = AssistantMode.FORWARD, activeChipPosition = 2)
        val command = forward(interpreter.parse("左宽0.089，右宽0.04，左距25，右距13", state).command)
        assertEquals(listOf(2), command.chips.map(ChipPatch::position))

        val unclear = interpreter.parse("left width 0.1", AssistantSessionState(activeMode = AssistantMode.FORWARD)).command
        assertEquals(ClarificationReason.CHIP_POSITION_REQUIRED, clarify(unclear).reason)
    }

    @Test
    fun `signed numeric extraction preserves invalid negative values`() {
        val negativeWidth = forward(interpreter.parse("chip 1 left width -0.1").command)
        val negativeDistance = forward(interpreter.parse("chip 1 right distance -5").command)
        assertEquals(-0.1f, negativeWidth.chips.single().leftWidthMm)
        assertEquals(-5f, negativeDistance.chips.single().rightDistanceMm)
    }

    @Test
    fun `reverse supports inlet before and after six targets in both languages`() {
        val before = reverse(interpreter.parse("Inlet1 high, reverse target concentrations are .08 .14 .25 .38 .48 .58").command)
        val after = reverse(interpreter.parse("目标浓度分别是0.08、0.14、0.25、0.38、0.48、0.58，入口2为高浓度").command)
        assertEquals(HighConcentrationInlet.INLET_1, before.highInlet)
        assertEquals(HighConcentrationInlet.INLET_2, after.highInlet)
        assertEquals(listOf(.08f, .14f, .25f, .38f, .48f, .58f), after.targetConcentrations)
    }

    @Test
    fun `control intents are typed`() {
        assertTrue(interpreter.parse("confirm").command is AssistantCommand.ConfirmAndRun)
        assertTrue(interpreter.parse("cancel").command is AssistantCommand.Cancel)
        assertTrue(interpreter.parse("reset").command is AssistantCommand.Reset)
        val overview = interpreter.parse("help").command as AssistantCommand.Help
        val forwardHelp = interpreter.parse("forward help").command as AssistantCommand.Help
        assertEquals(HelpCategory.OVERVIEW, overview.category)
        assertEquals(HelpExample.entries, overview.examples)
        assertEquals(HelpCategory.FORWARD, forwardHelp.category)
        assertTrue(HelpExample.FORWARD_CHIP_PARAMETERS in forwardHelp.examples)
    }

    @Test
    fun `malformed ambiguous and conflicting input requests clarification`() {
        assertEquals(ClarificationReason.EMPTY_MESSAGE, clarify(interpreter.parse("   ").command).reason)
        assertEquals(ClarificationReason.MALFORMED_NUMBER, clarify(interpreter.parse("chip 1 left width .1.2").command).reason)
        assertEquals(ClarificationReason.MALFORMED_NUMBER, clarify(interpreter.parse("chip 1 left width 0..1").command).reason)
        assertEquals(ClarificationReason.CONFLICTING_CHIP_POSITIONS, clarify(interpreter.parse("chip 1 and chip 2 left width 0.18").command).reason)
        assertEquals(ClarificationReason.REPEATED_FIELD_CONFLICT, clarify(interpreter.parse("chip 1 left width 0.1 left width 0.2").command).reason)
        assertEquals(ClarificationReason.CONFLICTING_MODE, clarify(interpreter.parse("forward and reverse prediction").command).reason)
        assertEquals(ClarificationReason.UNSUPPORTED_OR_AMBIGUOUS, clarify(interpreter.parse("Inlet1 high").command).reason)
        assertEquals(ClarificationReason.UNSUPPORTED_OR_AMBIGUOUS, clarify(interpreter.parse("chip 2 diagonal width 0.18").command).reason)
        assertEquals(
            ClarificationReason.REPEATED_FIELD_CONFLICT,
            clarify(interpreter.parse("chip 1 structure 4to2 structure 5to3").command).reason,
        )
    }

    @Test
    fun `mixed separators and long whitespace remain deterministic`() {
        val mixed = forward(interpreter.parse("chip 1 left width .1; chip 2 right width .2，chip 3 left distance 5").command)
        assertEquals(listOf(1, 2, 3), mixed.chips.map(ChipPatch::position))
        val spaced = forward(interpreter.parse("forward${" ".repeat(2_000)}with 4 chips").command)
        assertEquals(4, spaced.chipCount)
    }

    @Test
    fun `parser is deterministic and lightweight`() {
        val message = "Forward with 6 chips and Inlet1 high; chip 2 uses 4-2, left width .089, right width .04, left distance 25, right distance 13"
        val expected = interpreter.parse(message)
        repeat(100) { assertEquals(expected, interpreter.parse(message)) }
        val runs = 10_000
        val elapsed = measureNanoTime { repeat(runs) { interpreter.parse(message) } }
        println("assistant_parser_mean_ms=${elapsed.toDouble() / runs / 1_000_000.0}")
    }

    private fun forward(value: AssistantCommand) = value as AssistantCommand.ForwardUpdate
    private fun reverse(value: AssistantCommand) = value as AssistantCommand.ReverseUpdate
    private fun clarify(value: AssistantCommand) = value as AssistantCommand.Clarify
}
