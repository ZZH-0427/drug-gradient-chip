package com.druggradientchip.android.reverse

import kotlin.math.abs

internal const val WIDTH_MIN_MM = 0.1f
internal const val WIDTH_MAX_MM = 0.3f
internal const val BEND_MIN = 1
internal const val BEND_MAX = 6

data class ReverseSearchConfig(
    val restartCount: Int = 3,
    val widthMinMm: Float = WIDTH_MIN_MM,
    val widthMaxMm: Float = WIDTH_MAX_MM,
    val bendClassMin: Int = BEND_MIN,
    val bendClassMax: Int = BEND_MAX,
    val widthStepsMm: FloatArray = floatArrayOf(0.04f, 0.02f, 0.01f, 0.005f, 0.0025f, 0.001f),
    val maeWeight: Double = 0.10,
    val seed: Int = 42,
    val deterministic: Boolean = true,
    val qualityThresholds: ReverseQualityThresholds = ReverseQualityThresholds(),
) {
    val searchEvaluationBudget: Int
        get() = restartCount * (
            1 + REVERSE_VARIABLE_COUNT * (bendClassMax - bendClassMin) * 2 +
                REVERSE_VARIABLE_COUNT * widthStepsMm.size * 2
            )
    val totalEvaluationBudget: Int get() = searchEvaluationBudget + 1

    fun validate() {
        if (restartCount < 1 || !deterministic || widthMinMm != WIDTH_MIN_MM || widthMaxMm != WIDTH_MAX_MM ||
            bendClassMin != BEND_MIN || bendClassMax != BEND_MAX ||
            widthStepsMm.isEmpty() || widthStepsMm.any { !it.isFinite() || it <= 0.0f } || maeWeight < 0.0
        ) {
            throw ReverseRuntimeException(ReverseErrorCode.INVALID_REQUEST, "Unsupported Reverse search configuration.")
        }
    }

    fun snapshot() = ReverseSearchConfigSnapshot(
        restartCount, widthMinMm, widthMaxMm, bendClassMin, bendClassMax,
        widthStepsMm.toList(), maeWeight, seed, deterministic, totalEvaluationBudget,
    )
}

internal data class SearchCandidate(val widths: FloatArray, val bends: IntArray) {
    fun validate() {
        if (widths.size != REVERSE_VARIABLE_COUNT || bends.size != REVERSE_VARIABLE_COUNT ||
            widths.any { !it.isFinite() || it !in WIDTH_MIN_MM..WIDTH_MAX_MM } ||
            bends.any { it !in BEND_MIN..BEND_MAX }
        ) {
            throw ReverseRuntimeException(ReverseErrorCode.INVALID_REQUEST, "Reverse candidate geometry is invalid.")
        }
    }

    fun copyCandidate() = SearchCandidate(widths.copyOf(), bends.copyOf())
}

internal fun interface ReverseEvaluator {
    fun predict(candidate: SearchCandidate, inlet: HighConcentrationInlet): FloatArray
}

internal data class RestartCandidate(
    val restartIndex: Int,
    val candidate: SearchCandidate,
    val predicted: FloatArray,
    val objective: Double,
)

internal data class ReverseSearchOutcome(
    val result: ReverseResult,
    val restartCandidates: List<RestartCandidate>,
)

internal class ReverseSearchEngine(
    private val evaluator: ReverseEvaluator,
    private val config: ReverseSearchConfig = ReverseSearchConfig(),
) {
    fun optimize(
        request: ReverseRequest,
        cancellation: ReverseCancellationToken,
        onProgress: (ReverseProgress) -> Unit,
    ): ReverseSearchOutcome {
        ReverseRequestValidator.validate(request)
        config.validate()
        val started = System.nanoTime()
        var evaluations = 0
        val restarts = mutableListOf<RestartCandidate>()

        fun progress(restart: Int, state: ReverseProgressState, complete: Boolean = false) {
            onProgress(
                ReverseProgress(
                    completedEvaluations = evaluations,
                    totalEvaluationBudget = config.totalEvaluationBudget,
                    fraction = if (complete) 1.0 else evaluations.toDouble() / config.totalEvaluationBudget,
                    restartIndex = restart,
                    state = state,
                )
            )
        }

        fun evaluate(candidate: SearchCandidate, restart: Int): Pair<FloatArray, Double> {
            cancellation.throwIfCanceled()
            candidate.validate()
            val predicted = evaluator.predict(candidate, request.selectedHighInlet)
            validateOutput(predicted)
            evaluations += 1
            progress(restart, ReverseProgressState.SEARCHING)
            return predicted to ReverseMetricsCalculator.objective(predicted, request.targetConcentrations, config.maeWeight)
        }

        progress(0, ReverseProgressState.SEARCHING)
        repeat(config.restartCount) { restart ->
            var candidate = initialCandidate(request, restart)
            var evaluated = evaluate(candidate, restart)
            discreteSweep(candidate, evaluated.first, evaluated.second) { evaluate(it, restart) }.also {
                candidate = it.first
                evaluated = it.second to it.third
            }
            widthSweeps(candidate, evaluated.first, evaluated.second) { evaluate(it, restart) }.also {
                candidate = it.first
                evaluated = it.second to it.third
            }
            discreteSweep(candidate, evaluated.first, evaluated.second) { evaluate(it, restart) }.also {
                candidate = it.first
                evaluated = it.second to it.third
            }
            restarts += RestartCandidate(restart, candidate.copyCandidate(), evaluated.first.copyOf(), evaluated.second)
        }

        val best = restarts.minWith(compareBy<RestartCandidate> { it.objective }.thenBy { it.restartIndex })
        cancellation.throwIfCanceled()
        progress(best.restartIndex, ReverseProgressState.VERIFYING)
        val verified = evaluator.predict(best.candidate, request.selectedHighInlet)
        validateOutput(verified)
        evaluations += 1
        val metrics = ReverseMetricsCalculator.calculate(verified, request.targetConcentrations, config.qualityThresholds)
        progress(best.restartIndex, ReverseProgressState.COMPLETED, complete = true)
        val publicCandidate = mapCandidate(best.candidate)
        return ReverseSearchOutcome(
            result = ReverseResult(
                selectedHighInlet = request.selectedHighInlet,
                chipCount = REVERSE_CHIP_COUNT,
                candidate = publicCandidate,
                recomputedConcentrations = verified.copyOf(),
                perPositionErrors = FloatArray(REVERSE_CHIP_COUNT) {
                    abs(verified[it] - request.targetConcentrations[it])
                },
                metrics = metrics,
                evaluationCount = evaluations,
                runtimeMs = (System.nanoTime() - started).toDouble() / 1_000_000.0,
                searchConfig = config.snapshot(),
                warnings = warnings(request),
                modelIdentifier = FixedSixReverseEvaluator.MODEL_IDENTIFIER,
            ),
            restartCandidates = restarts.toList(),
        )
    }

    private fun initialCandidate(request: ReverseRequest, restart: Int): SearchCandidate {
        val widths = FloatArray(REVERSE_VARIABLE_COUNT)
        for (chip in 0 until REVERSE_CHIP_COUNT) {
            val alpha = ((request.targetConcentrations[chip] - request.selectedHighInlet.inlet2) /
                (request.selectedHighInlet.inlet1 - request.selectedHighInlet.inlet2)).coerceIn(0.0f, 1.0f)
            var left = WIDTH_MIN_MM + (WIDTH_MAX_MM - WIDTH_MIN_MM) * alpha
            var right = WIDTH_MAX_MM - (WIDTH_MAX_MM - WIDTH_MIN_MM) * alpha
            if (restart % 3 == 1) {
                left = 0.2f
                right = 0.2f
            } else if (restart % 3 == 2) {
                val swap = left
                left = right
                right = swap
            }
            widths[chip * 2] = left
            widths[chip * 2 + 1] = right
        }
        val preferred = 3 + restart % 3
        return SearchCandidate(
            widths,
            IntArray(REVERSE_VARIABLE_COUNT) {
                if (it % 2 == 0) (preferred - 1).coerceAtLeast(BEND_MIN)
                else (preferred + 1).coerceAtMost(BEND_MAX)
            },
        ).also(SearchCandidate::validate)
    }

    private fun discreteSweep(
        initial: SearchCandidate,
        initialPrediction: FloatArray,
        initialScore: Double,
        evaluate: (SearchCandidate) -> Pair<FloatArray, Double>,
    ): Triple<SearchCandidate, FloatArray, Double> {
        var candidate = initial
        var prediction = initialPrediction
        var score = initialScore
        for (variable in 0 until REVERSE_VARIABLE_COUNT) {
            var localBest = candidate
            var localPrediction = prediction
            var localScore = score
            for (bend in BEND_MIN..BEND_MAX) {
                if (bend == candidate.bends[variable]) continue
                val proposal = candidate.copyCandidate().also { it.bends[variable] = bend }
                val proposed = evaluate(proposal)
                if (proposed.second < localScore) {
                    localBest = proposal
                    localPrediction = proposed.first
                    localScore = proposed.second
                }
            }
            candidate = localBest
            prediction = localPrediction
            score = localScore
        }
        return Triple(candidate, prediction, score)
    }

    private fun widthSweeps(
        initial: SearchCandidate,
        initialPrediction: FloatArray,
        initialScore: Double,
        evaluate: (SearchCandidate) -> Pair<FloatArray, Double>,
    ): Triple<SearchCandidate, FloatArray, Double> {
        var candidate = initial
        var prediction = initialPrediction
        var score = initialScore
        config.widthStepsMm.forEach { step ->
            for (variable in 0 until REVERSE_VARIABLE_COUNT) {
                var localBest = candidate
                var localPrediction = prediction
                var localScore = score
                for (direction in floatArrayOf(-1.0f, 1.0f)) {
                    val value = (candidate.widths[variable] + direction * step).coerceIn(WIDTH_MIN_MM, WIDTH_MAX_MM)
                    if (value == candidate.widths[variable]) continue
                    val proposal = candidate.copyCandidate().also { it.widths[variable] = value }
                    val proposed = evaluate(proposal)
                    if (proposed.second < localScore) {
                        localBest = proposal
                        localPrediction = proposed.first
                        localScore = proposed.second
                    }
                }
                candidate = localBest
                prediction = localPrediction
                score = localScore
            }
        }
        return Triple(candidate, prediction, score)
    }

    private fun mapCandidate(candidate: SearchCandidate): ReverseCandidate = ReverseCandidate(
        (0 until REVERSE_CHIP_COUNT).map { chip ->
            val leftIndex = chip * 2
            val rightIndex = leftIndex + 1
            val leftBend = candidate.bends[leftIndex]
            val rightBend = candidate.bends[rightIndex]
            ReverseChipCandidate(
                position = chip + 1,
                chipType = "${leftBend}to${rightBend}",
                leftWidthMm = candidate.widths[leftIndex],
                rightWidthMm = candidate.widths[rightIndex],
                leftBendClass = leftBend,
                rightBendClass = rightBend,
                leftDistanceMm = distance(leftBend),
                rightDistanceMm = distance(rightBend),
            )
        },
    )

    private fun distance(bend: Int): Float = 1.0f + 6.0f * bend

    private fun validateOutput(output: FloatArray) {
        if (output.size != REVERSE_CHIP_COUNT || !output.all(Float::isFinite)) {
            throw ReverseRuntimeException(ReverseErrorCode.UNEXPECTED_OUTPUT, "Fixed-six evaluator must return six finite values.")
        }
    }

    private fun warnings(request: ReverseRequest): List<String> =
        if (request.targetConcentrations.any { it < 0.10f || it > 0.95f }) {
            listOf("TARGET_OUTSIDE_RECOMMENDED_RANGE")
        } else {
            emptyList()
        }
}
