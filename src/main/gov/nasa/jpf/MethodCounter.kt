package gov.nasa.jpf

internal class MethodCounter(private val methodName: String) {
    override fun toString(): String {
        var str = "{\"methodName\":\"$methodName\""
        str += ",\"totalCalls\":$totalCalls"
        str += ",\"argsMatchCount\":$argsMatchCount"
        str += ",\"instructionCount\":$instructionCount"
        str += ",\"recorded\":" + isRecorded
        str += ",\"interruption\":\"$reasonForInterruption\""
        str += "}"
        return str
    }

    fun toReadableString(): String {
        var str = "called $totalCalls times"
        if (readCount > 0) str += "\nreadCount=$readCount"
        if (writeCount > 0) str += "\nwriteCount=$writeCount"
        str += "\ninstructionCount=$instructionCount"
        if (argsMatchCount != 0) {
            str += "\nArgs matched $argsMatchCount times"
        }
        return str
    }

    var totalCalls = 0

    // true if it ever finishes a call-return
    // withing the same transition
    var isRecorded = false
    var reasonForInterruption = ""
    var readCount = 0
    var writeCount = 0
    var instructionCount = 0
    var argsMatchCount = 0
    var attemptedMatchCount = 0
    var failedMatchCount = 0

}