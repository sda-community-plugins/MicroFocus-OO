import com.serena.air.StepFailedException
import com.serena.air.StepPropertiesHelper
import com.serena.air.oo.OOHelper
import com.urbancode.air.AirPluginTool

def apTool = new AirPluginTool(args[0], args[1])
def props = new StepPropertiesHelper(apTool.stepProperties, true)

try {
    String oServerUrl = props.notNull('serverUrl')
    String oUsername = props.notNull('username')
    String oPassword = props.notNull('password')
    String executionId = props.notNull('executionId')
    boolean waitForCompletion = props.optionalBoolean("waitForCompletion", false)
    String outputProp = props.optional("outputProp", "flowPrimaryResult")
    long delay = props.optionalInt("delay", 6000)
    boolean debugMode = props.optionalBoolean("debugMode", false)

    OOHelper oClient = new OOHelper(oServerUrl, oUsername, oPassword)

    oClient.setPreemptiveAuth()
    oClient.setSSL()
    oClient.validate()
    oClient.setDebug(debugMode)

    json = oClient.getExecutionLog(executionId)
    json.each { data ->
        String path = data.stepInfo.path
        String stepName = data.stepInfo.stepName
        String responseType = (data.stepInfo.responseType ? "\t - ${data.stepInfo.responseType}" : "")
        String stepPrimaryResult = (data.stepPrimaryResult ? "\t - Result: ${data.stepPrimaryResult}" : "")
        println("Step [${path}]\t${stepName}${responseType}${stepPrimaryResult}")
    }

    long startTime = new Date().getTime();
    oClient.debug("Current UNIX time is: ${startTime}")

    def status = "UNKNOWN"
    def result = "UNKNOWN"
    if (waitForCompletion) {
        def running = true
        while (running) {
            sleep(delay)
            json = oClient.getExecutionSummary(executionId)
            status = json?.status[0]
            if (status.equals("COMPLETED")) {
                result = json?.resultStatusName[0]
                oClient.debug("current status: ${status}; result: ${result}")
            }

            json = oClient.getExecutionLog(executionId, startTime)
            json.each { data ->
                String path = data.stepInfo.path
                String stepName = data.stepInfo.stepName
                String responseType = (data.stepInfo.responseType ? "\t - ${data.stepInfo.responseType}" : "")
                String stepPrimaryResult = (data.stepPrimaryResult ? "\t - Result: ${data.stepPrimaryResult}" : "")
                println("Step [${path}]\t${stepName}${responseType}${stepPrimaryResult}")
            }

            startTime = new Date().getTime()

            if (status.equals("RUNNING") || status.contains("PAUSED")) {
                continue
            } else {
                oClient.debug("current status: ${status}")
                running = false
            }
        }
    } else {
        json = oClient.getExecutionSummary(executionId)
        status = json?.status[0]
        result = json?.resultStatusName[0]
    }

    apTool.setOutputProperty("executionId", executionId)

    if (status != null) {
        println("Execution status is \"${status}\"")
        apTool.setOutputProperty("executionStatus", status)
    }
    if (result != null) {
        println("Execution result is \"${result}\"")
        apTool.setOutputProperty("executionResult", result)
    }

    if (status.equals("COMPLETED")) {
        def stepCount = oClient.getExecutionStepCount(executionId)
        println("Executed ${stepCount} steps")
        json = oClient.getExecutionLog(executionId)
        result =  (json?.rawResult.Result[0] ? json?.rawResult.Result[0] : "none")
        println("Primary result: ${result}")
        apTool.setOutputProperty(outputProp, result)
    }

    apTool.storeOutputProperties()

    if (status.equals("FAILURE")) {
        System.exit 1
    } else {
        System.exit 0
    }

} catch (StepFailedException e) {
    println "ERROR: ${e.message}"
    System.exit 1
}
