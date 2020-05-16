package gov.nasa.jpf

import java.util.*

/**
 * Wrapper class for maintaining statistics about the methods that have/haven't been recorded.
 */
class CounterContainer {
    private val counterMap: MutableMap<String, MethodCounter> = HashMap()
    fun getAttemptedMatchCount(methodName: String): Int {
        return counterMap[methodName]!!.attemptedMatchCount
    }

    val methodStatistics = "{\"methodStats\":${counterMap.values.joinToString(",","[","]")}}"


    val numberOfRecordedMethods = counterMap.values.stream().filter { obj: MethodCounter -> obj.isRecorded }.count().toInt()

    val numberOfUniqueMethods = counterMap.size

    fun countInterruptedRecording(methodName: String, reason: String) {
        val counter = counterMap[methodName]
        if (!counterMap[methodName]?.reasonForInterruption.isNullOrBlank())
            counter?.reasonForInterruption = reason
    }

    fun overrideReasonForInterruption(methodName: String) {
        counterMap[methodName]!!.reasonForInterruption = "transition or lock"
    }

    fun countAttemptedSummaryMatch(methodName: String) {
        counterMap[methodName]!!.attemptedMatchCount++
    }

    fun addFailedMatchCount(methodName: String) {
        counterMap[methodName]!!.failedMatchCount++
    }

    fun addMatchedArgumentsCount(methodName: String) {
        counterMap[methodName]!!.attemptedMatchCount = 0
        counterMap[methodName]!!.argsMatchCount++
    }

    fun addTotalCalls(methodName: String) {
        counterMap[methodName]!!.totalCalls++
    }

    fun addMethodInvocation(methodName: String, numberOfInstructions: Int) {
        if (!counterMap.containsKey(methodName)) {
            counterMap[methodName] = MethodCounter(methodName)
            counterMap[methodName]!!.instructionCount = numberOfInstructions
        }
        counterMap[methodName]!!.totalCalls++
    }

    fun addWriteCount(methodName: String) {
        counterMap[methodName]!!.writeCount++
    }

    fun addReadCount(methodName: String) {
        counterMap[methodName]!!.readCount++
    }

    fun addRecordedMethod(methodName: String) {
        counterMap[methodName]!!.isRecorded = true
    }
}