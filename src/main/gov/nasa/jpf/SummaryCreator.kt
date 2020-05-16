package gov.nasa.jpf

import gov.nasa.jpf.jvm.bytecode.*
import gov.nasa.jpf.search.Search
import gov.nasa.jpf.vm.*
import gov.nasa.jpf.vm.bytecode.FieldInstruction
import java.io.PrintWriter
import java.util.*

/**
 * Listener implementing a method-summary utility.
 */
class SummaryCreator(config: Config) : RecordingListener() {
    private var skipInit = false
    private var skipped = false
    private val out: PrintWriter
    private var skip = false

    // just to make init skipping more efficient
    private var miMain: MethodInfo? = null

    // necessary for tests that re-run searches
    private fun reinitialise() {
        recorded = HashSet()
        recording = HashSet()
        blackList = HashSet()
        nativeWhiteList = HashSet()
        container = SummaryContainer()
        contextMap = HashMap()
        counterContainer = CounterContainer()
        modificationMap = HashMap()


        // Test gov.nasa.jpf.test.mc.basic.AttrsTest
        // This might actually be "OK",
        // if breaking attributes only affects other extensions, not core?
        blackList.add("java.lang.Integer.intValue()I")
        nativeWhiteList.add("matches")
        nativeWhiteList.add("desiredAssertionStatus")
        nativeWhiteList.add("print")
        nativeWhiteList.add("println")
        nativeWhiteList.add("min")
        nativeWhiteList.add("max")
    }

    private fun blacklistAndResetRecording(reason: String) {
        for (methodName in recording) {
            assert(!recorded.contains(methodName))
            counterContainer.countInterruptedRecording(methodName, reason)
            blackList.add(methodName)
        }
        recording = HashSet()
    }

    override fun executeInstruction(vm: VM, ti: ThreadInfo, instructionToExecute: Instruction) {
        var mi = instructionToExecute.methodInfo
        if (skip || mi == null) {
            return
        }
        if (instructionToExecute is JVMInvokeInstruction) {
            mi = instructionToExecute.invokedMethod
            if (mi == null) {
                return
            }
            val methodName = mi.fullName
            if (container.hasSummariesForMethod(methodName)) {
                counterContainer.countAttemptedSummaryMatch(methodName)
                val (context, mods) = getApplicableSummary(vm, ti, mi, instructionToExecute) ?: return
                counterContainer.addMatchedArgumentsCount(methodName)


                // ideally none of the targets should have been frozen
                // but it seems like they are in log4j1 - fixed
                if (mods.anyTargetsAreFrozen()) {
                    return
                }

                // TODO: Get class in a different way that doesn't break in edge-cases
                if (context.hasDependentStaticFields()) {
                    return
                }

                // We need to ensure that context and modification information
                // propagates down to other methods that might be recording
                for (r in recording) {
                    contextMap[r]!!.addContextFields(context)
                    modificationMap[r]!!.addModificationFields(mods)
                }
                counterContainer.addTotalCalls(methodName)
                if(logSummaryApplication) {
                    out.println("applied summary for $methodName")
                    out.println(context)
                    out.println(mods)
                }
                mods.applyModifications()

                // at this point we want to make sure that we don't create another summary
                // like the one we just applied
                stopRecording(methodName)
                skipped = true
                val nextInstruction = instructionToExecute.next
                val frame = ti.modifiableTopFrame
                frame.removeArguments(mi)
                val returnType = mi.returnType
                if (returnType == "V") {
                    ti.skipInstruction(nextInstruction)
                    return
                }
                val returnValue = mods.returnValue
                putReturnValueOnStackFrame(returnType, returnValue, frame, vm)
                ti.skipInstruction(nextInstruction)
            }
        }
    }

    private fun getApplicableSummary(vm: VM, ti: ThreadInfo, mi: MethodInfo, call: JVMInvokeInstruction): MethodSummary? {
        val summary: MethodSummary?
        val methodName = mi.fullName
        val runningThreads = vm.threadList.count.alive
        summary = if (call is INVOKESTATIC) {
            container.hasMatchingContext(methodName, call.getArgumentValues(ti), runningThreads == 1)
        } else {
            val top = ti.topFrame
            val argTypes = mi.argumentTypes
            val args = top.getArgumentsValues(ti, argTypes)
            val calleeObject = ti.getElementInfo(top.getCalleeThis(mi))
            // call.getArgumentValues() throws NPE here in log4j2 orig
            // at line 890 of StackFrame, which is strange cause this is executing the same code
            container.hasMatchingContext(methodName, calleeObject, args, runningThreads == 1)
        }
        if (summary == null) {
            counterContainer.addFailedMatchCount(methodName)
        }
        return summary
    }

    private fun stopRecording(methodName: String) {
        contextMap.remove(methodName)
        modificationMap.remove(methodName)
        recording.remove(methodName)
    }

    private fun putReturnValueOnStackFrame(returnType: String, returnValue: Any?, frame: StackFrame, vm: VM) {
        if (returnValue == null) {
            frame.pushRef(MJIEnv.NULL)
            return
        }

        when (returnType) {
            "J" -> frame.pushLong((returnValue as Long))
            "D" -> frame.pushDouble((returnValue as Double))
            "F" -> frame.pushFloat((returnValue as Float))
            "S" -> frame.push((returnValue as Int))
            "I" -> frame.push((returnValue as Int))
            "Z" -> if (returnValue is Boolean) {
                if (returnValue) {
                    frame.push(0)
                } else {
                    frame.push(1)
                }
            } else {
                frame.push((returnValue as Int))
            }
            else -> {
                if (returnValue is ElementInfo) {
                    frame.pushRef(returnValue.objectRef)
                } else {
                    if (returnValue as Int == MJIEnv.NULL) {
                        return
                    }
                    if (vm.getElementInfo(returnValue) == null) {
                        return
                    }
                    frame.pushRef(returnValue)
                }
            }
        }
    }

    override fun instructionExecuted(
            vm: VM,
            ti: ThreadInfo,
            nextInsn: Instruction,
            executedInstruction: Instruction
    ) {
        var mi = executedInstruction.methodInfo
        if (skip) {
            skip = if (mi === miMain) {
                false
            } else {
                return
            }
        }

        //out.println(executedInsn);
        if (executedInstruction is JVMInvokeInstruction) {
            // skipping in executeInstruction still results in
            // this instructionExecuted notification surprisingly
            if (skipped) {
                skipped = false
                return
            }
            val calledMethod = executedInstruction.getInvokedMethod(ti)
                    ?: return

            // if the invocation is blocked, do nothing
            if (ti.nextPC === executedInstruction) return
            val methodName = calledMethod.fullName
            val numberOfInstructions = calledMethod.numberOfInstructions
            counterContainer.addMethodInvocation(methodName, numberOfInstructions)
            if (!recorded.contains(methodName)) {
                recording.add(methodName)
            }
            if (methodStopsRecording(calledMethod, methodName)) {
                return
            }
            val runningThreads = vm.threadList.count.alive
            val args = executedInstruction.getArgumentValues(ti)
            if (!contextMap.containsKey(methodName)) {
                val isStatic = executedInstruction is INVOKESTATIC
                val types = calledMethod.argumentTypes
                for (type in types) {
                    if (type == Types.T_ARRAY) {
                        blacklistAndResetRecording("array argument")
                        return
                    }
                }
                if (isStatic) {
                    contextMap[methodName] = MethodContext(args, runningThreads == 1, null)
                } else {
                    if (ti.getElementInfo(executedInstruction.lastObjRef) == null) {
                        blacklistAndResetRecording("faulty this")
                        return
                    }
                    contextMap[methodName] = MethodContext(args, runningThreads == 1, ti.getElementInfo(executedInstruction.lastObjRef))
                }
            }
            if (!modificationMap.containsKey(methodName)) {
                modificationMap[methodName] = MethodModifications(args)
            }
        } else if (executedInstruction is JVMReturnInstruction) {
            val returningMethod = executedInstruction.methodInfo
            val methodName = returningMethod.fullName
            if (recording.contains(methodName)) {
                val returnValue = executedInstruction.getReturnValue(ti)
                completeRecording(methodName, returnValue)
            }
        } else if (executedInstruction is EXECUTENATIVE) {
            if (nativeWhiteList.contains(mi!!.name)) {
                return
            }
            blacklistAndResetRecording("native method")
        } else if (executedInstruction is FieldInstruction) {
            val methodName = mi!!.fullName
            if (!recording.contains(methodName)) return
            if (executedInstruction.isRead) {
                handleReadInstruction(methodName, executedInstruction)
            } else {
                handleWriteInstruction(methodName, executedInstruction)
            }
        }
    }

    private fun handleWriteInstruction(methodName: String, fieldInstruction: FieldInstruction) {
        counterContainer.addWriteCount(methodName)
        val ei = fieldInstruction.lastElementInfo
        val fi = fieldInstruction.fieldInfo
        val storageOffset = fi.storageOffset
        assert(storageOffset != -1)
        if (ei.isShared) {
            blacklistAndResetRecording("shared field write")
            return
        }
        if (fi.typeCode == Types.T_ARRAY) {
            blacklistAndResetRecording("array field")
            return
        }
        var type = fi.type
        var valueObject = ei.getFieldValueObject(fi.name)
        if (fi.isReference) {
            type = "#objectReference"
            valueObject = ei.getReferenceField(fi.name)
        }
        if (fieldInstruction is PUTFIELD) {
            for (stackMethodName in recording) {
                modificationMap[stackMethodName]!!.addField(fieldInstruction.getFieldName(), type!!, valueObject!!, ei)
            }
        } else if (fieldInstruction is PUTSTATIC) {
            for (stackMethodName in recording) {
                modificationMap[stackMethodName]!!.addStaticField(fieldInstruction.getFieldName(), type!!, valueObject!!, fi.classInfo)
            }
        }
    }

    private fun handleReadInstruction(methodName: String, fieldInstruction: FieldInstruction) {
        counterContainer.addReadCount(methodName)
        val fi = try {
            fieldInstruction.fieldInfo
            // thrown if classloader requires a roundtrip
        } catch (loadOnJPFRequired: LoadOnJPFRequired) {
            blacklistAndResetRecording("static read")
            return
        }
        val ei = fieldInstruction.lastElementInfo
        val storageOffset = fi.storageOffset
        assert(storageOffset != -1)
        if (ei.isShared) {
            blacklistAndResetRecording("shared field read")
            return
        }
        if (fi.typeCode == Types.T_ARRAY) {
            blacklistAndResetRecording("array field")
            return
        }
        if (fieldInstruction is GETFIELD) {
            // propagate context to all recording methods
            for (stackMethodName in recording) {
                if (!contextMap[stackMethodName]!!.containsField(fieldInstruction.getFieldName(), ei)) {
                    contextMap[stackMethodName]!!.addField(fieldInstruction.getFieldName(), ei, ei.getFieldValueObject(fi.name))
                }
            }
        } else if (fieldInstruction is GETSTATIC) {
            for (stackMethodName in recording) {
                if (!contextMap[stackMethodName]!!.containsStaticField(fieldInstruction.getFieldName())) contextMap[stackMethodName]!!.addStaticField(fieldInstruction.getFieldName(), fi.classInfo, ei.getFieldValueObject(fi.name))
            }
        }
    }

    private fun completeRecording(methodName: String, returnValue: Any) {
        val methodModifications = modificationMap[methodName]
                ?: throw IllegalStateException("Finished recording without having initialised modifications for method $methodName")
        val context = contextMap[methodName]
                ?:throw IllegalStateException("Finished recording without having initialised context for method $methodName")

        methodModifications.returnValue = returnValue
        if (container.canStoreMoreSummaries(methodName)) {
            container.addSummary(methodName, context, methodModifications)
            contextMap.remove(methodName)
            modificationMap.remove(methodName)
        } else {
            // stop recording "methodName"
            recorded.add(methodName)
        }
        counterContainer.addRecordedMethod(methodName)
        recording.remove(methodName)
    }

    private fun methodStopsRecording(mi: MethodInfo, methodName: String): Boolean {
        if (mi.returnTypeCode == Types.T_ARRAY) {
            blacklistAndResetRecording("array type")
            return true
        }
        if (mi.name == "<init>") {
            blacklistAndResetRecording("<init>")
            return true
        }
        if (mi.name == "<clinit>") {
            blacklistAndResetRecording("<clinit>")
            return true
        }
        return blacklistedOrSynthetic(mi, methodName)
    }

    private fun blacklistedOrSynthetic(mi: MethodInfo, methodName: String): Boolean {
        // if a method is blacklisted, or is a synthetic method
        // methodName will match mi.getFullName,
        // getName() is used for the manually entered names
        if (blackList.contains(methodName)
                || blackList.contains(mi.name)
                || methodName.contains("$$")
                || methodName.contains("Verify")
                // gov.nasa.jpf.test.java.concurrent.ExecutorServiceTest and CountDownLatchTest
                || methodName.contains("java.util.concurrent.locks")
                || methodName.contains("reflect")) {
            blacklistAndResetRecording("blacklisted")
            return true
        }
        return false
    }

    // Search listener part
    override fun searchStarted(search: Search) {
        out.println("----------------------------------- search started")
        reinitialise()
        if (skipInit) {
            val tiCurrent = ThreadInfo.getCurrentThread()
            miMain = tiCurrent.entryMethod
            out.println("      [skipping static init instructions]")
        }
    }

    override fun searchFinished(search: Search) {
        out.println("----------------------------------- search finished")
        out.println()
        out.println(counterContainer.methodStatistics)
        out.println()
        out.println(container.toString())
    }

    companion object {
        // contains the names of native method calls that are known not to have
        // side-effects that can't be captured in the summary
        private var nativeWhiteList = HashSet<String>()
        private var container = SummaryContainer()
        private var contextMap = HashMap<String, MethodContext>()
        private var modificationMap = HashMap<String, MethodModifications>()
        private var logSummaryApplication = false
    }

    init {
        //  @jpfoption et.skip_init : boolean - do not log execution before entering main() (default=true).
        skipInit = config.getBoolean("et.skip_init", true)
        if (skipInit) {
            skip = true
        }
        reinitialise()
        out = PrintWriter(System.out, true)
        out.println("~Summaries active~")
    }
}