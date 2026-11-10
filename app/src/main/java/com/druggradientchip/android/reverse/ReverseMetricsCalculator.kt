package com.druggradientchip.android.reverse

import kotlin.math.abs
import kotlin.math.sqrt

data class ReverseQualityThresholds(
    val mae: Double = 0.02,
    val rmse: Double = 0.03,
    val maxAe: Double = 0.05,
    val spearman: Double = 0.95,
)

object ReverseMetricsCalculator {
    fun objective(predicted: FloatArray, target: FloatArray, maeWeight: Double = 0.10): Double {
        validatePair(predicted, target)
        val errors = errors(predicted, target)
        return errors.sumOf { it * it } / errors.size + maeWeight * errors.sumOf(::abs) / errors.size
    }

    fun calculate(
        predicted: FloatArray,
        target: FloatArray,
        thresholds: ReverseQualityThresholds = ReverseQualityThresholds(),
    ): ReverseMetrics {
        validatePair(predicted, target)
        val errors = errors(predicted, target)
        val mae = errors.sumOf(::abs) / errors.size
        val rmse = sqrt(errors.sumOf { it * it } / errors.size)
        val maxAe = errors.maxOf(::abs)
        val spearman = spearman(predicted, target)
        return ReverseMetrics(
            mae = mae,
            rmse = rmse,
            maxAe = maxAe,
            spearman = spearman,
            qualityPassed = mae <= thresholds.mae && rmse <= thresholds.rmse &&
                maxAe <= thresholds.maxAe && spearman >= thresholds.spearman,
        )
    }

    fun spearman(left: FloatArray, right: FloatArray): Double {
        validatePair(left, right)
        val leftRanks = averageRanks(left)
        val rightRanks = averageRanks(right)
        val leftMean = leftRanks.average()
        val rightMean = rightRanks.average()
        var covariance = 0.0
        var leftVariance = 0.0
        var rightVariance = 0.0
        for (index in leftRanks.indices) {
            val leftCentered = leftRanks[index] - leftMean
            val rightCentered = rightRanks[index] - rightMean
            covariance += leftCentered * rightCentered
            leftVariance += leftCentered * leftCentered
            rightVariance += rightCentered * rightCentered
        }
        if (leftVariance == 0.0 || rightVariance == 0.0) {
            return if (left.contentEquals(right)) 1.0 else 0.0
        }
        return covariance / sqrt(leftVariance * rightVariance)
    }

    private fun averageRanks(values: FloatArray): DoubleArray {
        val ordered = values.indices.sortedBy { values[it] }
        val ranks = DoubleArray(values.size)
        var start = 0
        while (start < ordered.size) {
            var end = start
            while (end + 1 < ordered.size && values[ordered[end + 1]] == values[ordered[start]]) end += 1
            val averageRank = (start + end + 2).toDouble() / 2.0
            for (offset in start..end) ranks[ordered[offset]] = averageRank
            start = end + 1
        }
        return ranks
    }

    private fun validatePair(left: FloatArray, right: FloatArray) {
        if (left.size != REVERSE_CHIP_COUNT || right.size != REVERSE_CHIP_COUNT) {
            throw ReverseRuntimeException(ReverseErrorCode.UNEXPECTED_OUTPUT, "Concentration arrays must contain six values.")
        }
        if (!left.all(Float::isFinite) || !right.all(Float::isFinite)) {
            throw ReverseRuntimeException(ReverseErrorCode.UNEXPECTED_OUTPUT, "Concentration arrays must be finite.")
        }
    }

    private fun errors(predicted: FloatArray, target: FloatArray) =
        DoubleArray(REVERSE_CHIP_COUNT) { predicted[it].toDouble() - target[it].toDouble() }
}
