package gov.nasa.jpf

import gov.nasa.jpf.vm.ElementInfo
import java.util.*

/**
 * Helper class that provides the ability to store multiple summaries for a single method.
 */
class SummaryContainer {
    companion object {
        // the maximum number of contexts which we capture
        private const val CAPACITY = 100
    }

    private val container: MutableMap<String, MutableList<MethodSummary>> = mutableMapOf()

    fun addSummary(methodName: String, context: MethodContext, mods: MethodModifications) {
        var summaries = container[methodName]
        if (summaries == null) {
            summaries = ArrayList()
            summaries.add(MethodSummary(context, mods))
            container[methodName] = summaries
            return
        }
        if (summaries.size < CAPACITY) {
            summaries.add(MethodSummary(context, mods))
            return
        }
        throw IndexOutOfBoundsException("Trying to add too many summaries for $methodName")
    }

    fun canStoreMoreSummaries(methodName: String): Boolean {
        val summaries = container[methodName]
        return summaries == null || summaries.size < CAPACITY
    }

    fun hasSummariesForMethod(methodName: String) = container[methodName].isNullOrEmpty().not()

    fun hasMatchingContext(methodName: String, calleeObject: ElementInfo, args: Array<Any?>, runningAlone: Boolean) =
            container[methodName]?.find { it.context.match(calleeObject, args, runningAlone) }

    fun hasMatchingContext(methodName: String, args: Array<Any?>, runningAlone: Boolean) =
            container[methodName]?.find { it.context.match(args, runningAlone) }

    override fun toString(): String {
        val json = container.map { (methodName, summaries) ->
            val summariesJson = summaries.joinToString(",", "[", "]")
            { (context, mods) -> "{\"context\":$context,\"modifications\":$mods}" }

            "{\"$methodName\":$summariesJson}"
        }.joinToString(",", "[", "]")
        return "{\"summaries\":$json}"
    }
}