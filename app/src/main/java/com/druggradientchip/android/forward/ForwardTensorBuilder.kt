package com.druggradientchip.android.forward

import kotlin.math.ln
import kotlin.math.sqrt

data class FloatTensorInput(val values: FloatArray, val shape: LongArray)
data class LongTensorInput(val values: LongArray, val shape: LongArray)

data class ForwardTensorInputs(
    val x: FloatTensorInput,
    val edgeIndex: LongTensorInput,
    val edgeAttr: FloatTensorInput,
    val u: FloatTensorInput,
    val chipPhysics: FloatTensorInput,
    val chipPhysicsZ: FloatTensorInput,
    val nodeType: LongTensorInput,
)

class ForwardTensorBuilder {
    fun build(request: ForwardRequest, artifact: ForwardModelArtifact): ForwardTensorInputs = try {
        ForwardValidation.validate(request)
        val chips = request.chips.sortedBy { it.position }
        val dynamicPositions = artifact.family != "fixed-six"
        val rawX = buildRawX(request, chips, dynamicPositions)
        val rawEdges = buildRawEdges(request, chips, dynamicPositions)
        val rawU = floatArrayOf(request.inlet1, request.inlet2)
        val rawChipPhysics = buildChipPhysics(chips)
        val nodeType = LongArray(request.chipCount + 2) { index ->
            when (index) {
                0 -> 0L
                1 -> 1L
                else -> 2L
            }
        }
        ForwardTensorInputs(
            x = FloatTensorInput(normalizeRows(rawX, Normalizers.X_MEAN, Normalizers.X_STD), longArrayOf((request.chipCount + 2).toLong(), 15L)),
            edgeIndex = LongTensorInput(rawEdges.first, longArrayOf(2L, (4 * request.chipCount).toLong())),
            edgeAttr = FloatTensorInput(normalizeRows(rawEdges.second, Normalizers.EDGE_MEAN, Normalizers.EDGE_STD), longArrayOf((4 * request.chipCount).toLong(), 9L)),
            u = FloatTensorInput(normalizeRows(rawU, Normalizers.U_MEAN, Normalizers.U_STD), longArrayOf(1L, 2L)),
            chipPhysics = FloatTensorInput(rawChipPhysics, longArrayOf(request.chipCount.toLong(), 7L)),
            chipPhysicsZ = FloatTensorInput(normalizeRows(rawChipPhysics, Normalizers.CHIP_PHYSICS_MEAN, Normalizers.CHIP_PHYSICS_STD), longArrayOf(request.chipCount.toLong(), 7L)),
            nodeType = LongTensorInput(nodeType, longArrayOf((request.chipCount + 2).toLong())),
        )
    } catch (error: ForwardRuntimeException) {
        throw error
    } catch (error: Throwable) {
        throw ForwardRuntimeException(ForwardErrorCode.TENSOR_CONSTRUCTION_FAILURE, "Failed to build forward tensors.", error)
    }

    private fun buildRawX(request: ForwardRequest, chips: List<ForwardChipInput>, dynamicPositions: Boolean): FloatArray {
        val rows = mutableListOf<Float>()
        rows += inletNode(1, request.inlet1).toList()
        rows += inletNode(2, request.inlet2).toList()
        chips.forEachIndexed { index, chip ->
            rows += chipNode(chip, index, request.chipCount, dynamicPositions).toList()
        }
        return rows.toFloatArray()
    }

    private fun inletNode(inletIndex: Int, concentration: Float): FloatArray {
        val row = FloatArray(15)
        if (inletIndex == 1) row[0] = 1.0f else row[1] = 1.0f
        row[3] = concentration
        return row
    }

    private fun chipNode(chip: ForwardChipInput, chipIndex: Int, chipCount: Int, dynamicPositions: Boolean): FloatArray {
        val structure = parseChipStructure(chip.chipType)
        val positionValue = if (dynamicPositions) encodePositionForSixChipCheckpoint(chip.position.toFloat(), chipCount) else chip.position.toFloat()
        return floatArrayOf(
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            chip.leftWidthMm,
            chip.rightWidthMm,
            chip.leftBendStraightDistanceMm,
            chip.rightBendStraightDistanceMm,
            positionValue,
            positionValue,
            assemblyPositionNorm(chipIndex, if (dynamicPositions) chipCount else 6),
            structure.startNorm,
            structure.endNorm,
            structure.signedSpanNorm,
            structure.absoluteSpanNorm,
        )
    }

    private fun buildRawEdges(request: ForwardRequest, chips: List<ForwardChipInput>, dynamicPositions: Boolean): Pair<LongArray, FloatArray> {
        val sources = mutableListOf<Long>()
        val targets = mutableListOf<Long>()
        val attrs = mutableListOf<Float>()
        chips.forEachIndexed { index, chip ->
            val chipNodeIndex = 2 + index
            val structure = parseChipStructure(chip.chipType)
            val positionNorm = assemblyPositionNorm(index, if (dynamicPositions) request.chipCount else 6)
            fun append(source: Int, target: Int, width: Float, distance: Float, side1: Float, side2: Float, toChip: Float, fromChip: Float) {
                sources += source.toLong()
                targets += target.toLong()
                attrs += listOf(width, distance, positionNorm, structure.signedSpanNorm, structure.absoluteSpanNorm, side1, side2, toChip, fromChip)
            }
            append(0, chipNodeIndex, chip.leftWidthMm, chip.leftBendStraightDistanceMm, 1.0f, 0.0f, 1.0f, 0.0f)
            append(1, chipNodeIndex, chip.rightWidthMm, chip.rightBendStraightDistanceMm, 0.0f, 1.0f, 1.0f, 0.0f)
            append(chipNodeIndex, 0, chip.leftWidthMm, chip.leftBendStraightDistanceMm, 1.0f, 0.0f, 0.0f, 1.0f)
            append(chipNodeIndex, 1, chip.rightWidthMm, chip.rightBendStraightDistanceMm, 0.0f, 1.0f, 0.0f, 1.0f)
        }
        return Pair(longArrayOf(*sources.toLongArray(), *targets.toLongArray()), attrs.toFloatArray())
    }

    private fun buildChipPhysics(chips: List<ForwardChipInput>): FloatArray {
        val values = mutableListOf<Float>()
        val eps = 1.0e-12f
        chips.forEach { chip ->
            val leftWidth = chip.leftWidthMm
            val rightWidth = chip.rightWidthMm
            val leftDistance = chip.leftBendStraightDistanceMm
            val rightDistance = chip.rightBendStraightDistanceMm
            val widthSum = leftWidth + rightWidth
            val distanceSum = leftDistance + rightDistance
            values += listOf(
                ln(((leftWidth + eps) / (rightWidth + eps)).toDouble()).toFloat(),
                (leftWidth - rightWidth) / (widthSum + eps),
                sqrt((leftWidth * rightWidth).toDouble()).toFloat(),
                widthSum,
                ln(((rightDistance + eps) / (leftDistance + eps)).toDouble()).toFloat(),
                (rightDistance - leftDistance) / (distanceSum + eps),
                sqrt((leftDistance * rightDistance).toDouble()).toFloat(),
            )
        }
        return values.toFloatArray()
    }

    private fun parseChipStructure(chipType: String): ChipStructure {
        val match = Regex("""(\d+)\s*to\s*(\d+)""", RegexOption.IGNORE_CASE).find(chipType)
            ?: throw ForwardRuntimeException(ForwardErrorCode.INVALID_REQUEST, "Invalid chip_type: $chipType")
        val start = match.groupValues[1].toFloat()
        val end = match.groupValues[2].toFloat()
        if (start !in 1.0f..6.0f || end !in 1.0f..6.0f) {
            throw ForwardRuntimeException(ForwardErrorCode.INVALID_REQUEST, "chip_type endpoints must be 1..6: $chipType")
        }
        val signedSpan = end - start
        return ChipStructure(start / 6.0f, end / 6.0f, signedSpan / 5.0f, kotlin.math.abs(signedSpan) / 5.0f)
    }

    private fun assemblyPositionNorm(chipIndex: Int, chipCount: Int): Float =
        chipIndex.toFloat() / (chipCount - 1).toFloat()

    private fun encodePositionForSixChipCheckpoint(position: Float, chipCount: Int): Float =
        1.0f + 5.0f * ((position - 1.0f) / (chipCount - 1).toFloat())

    private fun normalizeRows(values: FloatArray, mean: FloatArray, std: FloatArray): FloatArray =
        FloatArray(values.size) { index ->
            val feature = index % mean.size
            (values[index] - mean[feature]) / if (kotlin.math.abs(std[feature]) < 1.0e-12f) 1.0f else std[feature]
        }

    private data class ChipStructure(
        val startNorm: Float,
        val endNorm: Float,
        val signedSpanNorm: Float,
        val absoluteSpanNorm: Float,
    )
}

private object Normalizers {
    val X_MEAN = floatArrayOf(0.125f, 0.125f, 0.75f, 0.11138691f, 0.13581643f, 0.13635391f, 15.487744f, 17.090599f, 2.512721f, 2.5576327f, 0.375f, 0.40938178f, 0.45390552f, 0.053428486f, 0.28108904f)
    val X_STD = floatArrayOf(0.3307189f, 0.3307189f, 0.4330127f, 0.31461066f, 0.10845458f, 0.10202935f, 13.20107f, 13.205832f, 2.103565f, 2.0813463f, 0.36657196f, 0.3586582f, 0.35793063f, 0.39765432f, 0.2863084f)
    val EDGE_MEAN = floatArrayOf(0.18144687f, 21.718895f, 0.5f, 0.07123798f, 0.37478536f, 0.5f, 0.5f, 0.5f, 0.5f)
    val EDGE_STD = floatArrayOf(0.0809379f, 10.741055f, 0.341565f, 0.45778805f, 0.27236125f, 0.5f, 0.5f, 0.5f, 0.5f)
    val U_MEAN = floatArrayOf(0.443686f, 0.44740924f)
    val U_STD = floatArrayOf(0.4968186f, 0.4972265f)
    val CHIP_PHYSICS_MEAN = floatArrayOf(-0.06640396f, -0.015710587f, 0.16668354f, 0.36289376f, 0.1566649f, 0.075220995f, 20.47938f)
    val CHIP_PHYSICS_STD = floatArrayOf(0.9065948f, 0.39248f, 0.046402242f, 0.084433906f, 0.74397117f, 0.3315558f, 8.431229f)
}
