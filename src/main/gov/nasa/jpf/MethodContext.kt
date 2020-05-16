package gov.nasa.jpf

import gov.nasa.jpf.vm.ClassInfo
import gov.nasa.jpf.vm.ElementInfo
import java.util.*

class MethodContext(args: Array<Any>,
                             private val runningAlone: Boolean,
                             private val calleeObject: ElementInfo? = null) {
    private var params: Array<Any> = args.map {
        if (it is ElementInfo && it.isStringObject) {
            it.asString()
        } else {
            it
        }
    }.toTypedArray()
    private var dependentFields: MutableMap<Int, DependentFieldData?> = mutableMapOf()
    private var dependentStaticFields: MutableMap<String, DependentFieldData?> = mutableMapOf()


    fun hasDependentStaticFields() = dependentStaticFields.isNotEmpty()

    private data class DependentFieldData(
            val fieldName: String,
            val previousValue: Any,
            // for non-static fields
            val sourceObject: ElementInfo? = null,
            // for static fields
            val classInfo: ClassInfo? = null
    )


    /**
     * Takes another context and adds all fields from that to itself.
     * Needed when a summary is applied during recording.
     * TODO: Resolve conflicts between contexts.
     * TODO: Add *this* from inner as well, as a field?
     */
    fun addContextFields(innerContext: MethodContext) {
        val innerFields = innerContext.dependentFields
        val innerStaticFields = innerContext.dependentStaticFields
        for (fieldHash in innerFields.keys) {
            val fieldData = innerFields[fieldHash]
            dependentFields[fieldHash] = fieldData
        }
        for (fieldName in innerStaticFields.keys) {
            val fieldData = innerStaticFields[fieldName]
            dependentStaticFields[fieldName] = fieldData
        }
    }

    fun match(calleeObject: ElementInfo, args: Array<Any?>, runningAlone: Boolean): Boolean {
        assert(this.calleeObject != null)
        return if (this.calleeObject !== calleeObject) {
            false
        } else match(args, runningAlone)
    }

    fun match(args: Array<Any?>, runningAlone: Boolean): Boolean {
        if (this.runningAlone != runningAlone) return false
        if (!argumentsMatch(args)) {
            //System.out.println("args mismatch");
            return false
        }

        // at this point we know that the arguments match,
        // so any field operations that access fields
        // of arguments are safe
        return if (!staticFieldsMatch()) {
            false
        } else fieldsMatch()

        // now both args and static fields are guaranteed to match
    }

    private fun valuesDiffer(oldValue: Any?, currentValue: Any?): Boolean {
        if (oldValue == null) {
            return currentValue != null
        }
        if (oldValue is String) {
            val curr = currentValue as ElementInfo? ?: return true
            if (curr.isStringObject) {
                return !curr.equalsString(oldValue as String?)
            }
        }
        return oldValue != currentValue
    }

    private fun fieldsMatch(): Boolean {
        for (fieldData in dependentFields.values) {
            if (fieldData == null) return false
            val oldValue = fieldData.previousValue
            val currentValue = fieldData.sourceObject!!.getFieldValueObject(fieldData.fieldName)
            if (valuesDiffer(oldValue, currentValue)) {
                return false
            }
        }
        return true
    }

    private fun staticFieldsMatch(): Boolean {
        for (fieldName in dependentStaticFields.keys) {
            val fieldData = dependentStaticFields[fieldName] ?: return false
            val ci = fieldData.classInfo
            val oldValue = fieldData.previousValue
            // sometimes throws NPE, presumably the ci is not what we want here
            val currentValue = ci!!.getStaticFieldValueObject(fieldName)
            if (valuesDiffer(oldValue, currentValue)) return false
        }
        return true
    }

    private fun argumentsMatch(args: Array<Any?>): Boolean {
        require(args.size == params.size) { "Calling method with wrong number of arguments." }
        for (i in args.indices) {
            if (valuesDiffer(params[i], args[i])) return false
        }
        return true
    }

    fun containsField(fieldName: String, source: ElementInfo) =
            dependentFields.containsKey((fieldName + source.toString()).hashCode())

    fun containsStaticField(fieldName: String) = dependentStaticFields.containsKey(fieldName)

    fun addField(fieldName: String, source: ElementInfo, value: Any) {
        assert(!source.isShared)
        dependentFields[(fieldName + source.toString()).hashCode()] = DependentFieldData(fieldName, value, sourceObject = source)
    }

    fun addStaticField(fieldName: String, ci: ClassInfo?, value: Any) {
        dependentStaticFields[fieldName] = DependentFieldData(fieldName, value, classInfo = ci)
    }

    override fun toString(): String {
        if (params.isEmpty() && dependentFields.isEmpty() && dependentStaticFields.isEmpty() && calleeObject == null) {
            return "{}"
        }
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"contextSize\":${1 + params.size + dependentFields.size + dependentStaticFields.size})")
        sb.append(", \"this\":\"$calleeObject\"")
        sb.append(", \"args\":${params.joinToString(",", "[", "]") { "\"$it\"" }}")
        sb.append("\"fields\": ${dependentFields.values.joinToString(",", "[", "]") {
            "{\"sourceObject\": \"${it?.sourceObject}\", \"fieldName\":\"${it?.fieldName}\",\"value\":\"${it?.previousValue}\"}}"
        }}")
        sb.append("\"staticFields\": ${dependentStaticFields.values.joinToString(",", "[", "]") {
            "{\"fieldName\": \"${it?.fieldName}\", \"classInfo\":\"${it?.classInfo}\",\"value\":\"${it?.previousValue}\"}}"
        }}")
        sb.append("}")
        return sb.toString()
    }
}