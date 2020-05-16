package gov.nasa.jpf

import gov.nasa.jpf.vm.ClassInfo
import gov.nasa.jpf.vm.ElementInfo

class MethodModifications(private val params: Array<Any>) {
    private val modifiedFields: MutableMap<Int, ModifiedFieldData> = mutableMapOf()
    private val modifiedStaticFields: MutableMap<Int, ModifiedFieldData> = mutableMapOf()
    // TODO: Distinguish between actually returning null, and void method
    var returnValue: Any? = null

    private data class ModifiedFieldData(
            val fieldName: String,
            val type: String,
            val newValue: Any,
            // for non-static fields
            val targetObject: ElementInfo? = null,
            // for static fields
            val classInfo: ClassInfo? = null
    )

    override fun toString(): String {
        if (params.isEmpty() && modifiedFields.isEmpty() && modifiedStaticFields.isEmpty() && returnValue == null) {
            return "{}"
        }
        val sb = StringBuilder()
        sb.append("{\"modsSize\":").append(1 + params.size + modifiedFields.size + modifiedStaticFields.size)
        sb.append(", \"returnValue\":\"").append(returnValue).append("\"")
        sb.append(", \"args\":[")
        for (arg in params) {
            if (arg !== params[params.size - 1]) {
                sb.append("\"").append(arg).append("\",")
            } else {
                sb.append("\"").append(arg).append("\"")
            }
        }
        sb.append("], \"fields\":[ ")
        for (fieldData in modifiedFields.values) {
            sb.append("{\"fieldName\":\"").append(fieldData.fieldName).append("\", \"targetObject\":\"").append(fieldData.targetObject).append("\", \"value\":\"").append(fieldData.newValue).append("\"},")
        }
        sb.deleteCharAt(sb.length - 1)
        sb.append("], \"staticFields\":[ ")
        for (fieldData in modifiedStaticFields.values) {
            sb.append("{\"fieldName\":\"").append(fieldData.fieldName).append("\", \"classInfo\":\"").append(fieldData.classInfo).append("\", \"value\":\"").append(fieldData.newValue).append("\"},")
        }
        sb.deleteCharAt(sb.length - 1)
        sb.append("]}")
        return sb.toString()
    }

    fun anyTargetsAreFrozen(): Boolean {
        for (fieldData in modifiedFields.values) {
            if (fieldData.targetObject!!.isFrozen) return true
        }
        for (staticFieldData in modifiedStaticFields.values) {
            val targetClassObject: ElementInfo = staticFieldData.classInfo!!.modifiableStaticElementInfo
            if (targetClassObject.isFrozen) return true
        }
        return false
    }

    fun applyModifications() {
        applyFieldUpdates()
        applyStaticFieldUpdates()
    }

    private fun applyStaticFieldUpdates() {
        for (staticFieldData in modifiedStaticFields.values) {
            assert(staticFieldData.classInfo != null)
            val targetClassObject: ElementInfo = staticFieldData.classInfo!!.modifiableStaticElementInfo!!
            applyFieldUpdate(staticFieldData.fieldName, staticFieldData.type, staticFieldData.newValue, targetClassObject)
        }
    }

    private fun applyFieldUpdates() {
        for (fieldData in modifiedFields.values) {
            assert(fieldData.targetObject != null)
            assert(!fieldData.targetObject!!.isShared)
            applyFieldUpdate(fieldData.fieldName, fieldData.type, fieldData.newValue, fieldData.targetObject)
        }
    }

    private fun applyFieldUpdate(fieldName: String, type: String, newValue: Any, ei: ElementInfo) {
        when (type) {
            "int" -> ei.setIntField(fieldName, newValue as Int)
            "float" -> ei.setFloatField(fieldName, newValue as Float)
            "char" -> ei.setCharField(fieldName, newValue as Char)
            "byte" -> ei.setByteField(fieldName, newValue as Byte)
            "double" -> ei.setDoubleField(fieldName, newValue as Double)
            "long" -> ei.setLongField(fieldName, newValue as Long)
            "short" -> ei.setShortField(fieldName, newValue as Short)
            "boolean" -> ei.setBooleanField(fieldName, newValue as Boolean)
            "#objectReference" -> ei.setReferenceField(fieldName, newValue as Int)
        }
    }

    /**
     * Takes another set of modifications and adds them to itself.
     * Needed when a summary is applied during recording.
     */
    fun addModificationFields(innerMods: MethodModifications) {
        modifiedFields.putAll(innerMods.modifiedFields)
        modifiedStaticFields.putAll(innerMods.modifiedStaticFields)
    }

    fun addField(fieldName: String, type: String, newValue: Any, ei: ElementInfo) {
        modifiedFields[fieldName.hashCode() + ei.hashCode()] = ModifiedFieldData(fieldName, type, newValue, targetObject = ei)
    }

    fun addStaticField(fieldName: String, type: String, newValue: Any, ci: ClassInfo) {
        modifiedStaticFields[fieldName.hashCode() + ci.hashCode()] = ModifiedFieldData(fieldName, type, newValue, classInfo = ci)
    }

}