package gov.nasa.jpf

import gov.nasa.jpf.search.Search
import gov.nasa.jpf.vm.*
import java.util.*

open class RecordingListener : ListenerAdapter() {
    private fun stopRecording() {
        for (methodName in recording) {
            assert(!recorded.contains(methodName))
            // not conditional, as these interruptions will  override any others
            counterContainer.overrideReasonForInterruption(methodName)
            blackList.add(methodName)
        }
        recording = HashSet()
    }

    override fun threadInterrupted(vm: VM, interruptedThread: ThreadInfo) {
        stopRecording()
    }

    override fun objectLocked(vm: VM, currentThread: ThreadInfo, lockedObject: ElementInfo) {
        stopRecording()
    }

    override fun objectUnlocked(vm: VM, currentThread: ThreadInfo, unlockedObject: ElementInfo) {
        stopRecording()
    }

    override fun objectWait(vm: VM, currentThread: ThreadInfo, waitingObject: ElementInfo) {
        stopRecording()
    }

    override fun objectNotify(vm: VM, currentThread: ThreadInfo, notifyingObject: ElementInfo) {
        stopRecording()
    }

    override fun objectNotifyAll(vm: VM, currentThread: ThreadInfo, notifyingObject: ElementInfo) {
        stopRecording()
    }

    override fun objectExposed(vm: VM, currentThread: ThreadInfo, fieldOwnerObject: ElementInfo, exposedObject: ElementInfo) {
        stopRecording()
    }

    override fun objectShared(vm: VM, currentThread: ThreadInfo, sharedObject: ElementInfo) {
        stopRecording()
    }

    override fun choiceGeneratorRegistered(vm: VM, nextCG: ChoiceGenerator<*>?, currentThread: ThreadInfo, executedInstruction: Instruction) {
        stopRecording()
    }

    override fun choiceGeneratorSet(vm: VM, newCG: ChoiceGenerator<*>?) {
        stopRecording()
    }

    override fun choiceGeneratorAdvanced(vm: VM, currentCG: ChoiceGenerator<*>?) {
        stopRecording()
    }

    override fun stateAdvanced(search: Search) {
        stopRecording()
    }

    override fun stateBacktracked(search: Search) {
        stopRecording()
    }

    companion object {
        @JvmField
        var counterContainer = CounterContainer()

        // contains the names of the methods that should never be recorded
        // this could be because they are interrupted by a transition
        // or because they call native methods that we can't track
        @JvmField
        var blackList = HashSet<String>()

        // contains the names of the methods that have been recorded as
        // doing a complete call-return cycle within a single transition
        @JvmField
        var recorded = HashSet<String>()

        // contains the names of the methods currently being recorded
        @JvmField
        var recording = HashSet<String>()
    }
}