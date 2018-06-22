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
    String flowId = props.notNull('flowId')
    String runName = props.optional("runName")
    String logLevel = props.optional("logLevel", "STANDARD")
    String inputs = props.optional("inputs")
    boolean waitForCompletion = props.optionalBoolean("waitForCompletion", false)
    String outputProp = props.optional("outputProp", "flowPrimaryResult")
    long delay = props.optionalInt("delay", 6000)
    boolean debugMode = props.optionalBoolean("debugMode", false)

    OOHelper oClient = new OOHelper(oServerUrl, oUsername, oPassword)

    oClient.setPreemptiveAuth()
    oClient.setSSL()
    oClient.validate()
    oClient.setDebug(debugMode)

    def json = oClient.getFlowDetails(flowId)
    def flowName = json?.name
    def flowPath = json?.path
    println("Executing flow \"${flowName}\" - \"${flowPath}\"; uuid = \"${flowId}\"")

    Map<String,String> inputsMap = new HashMap<String,String>()
    inputs?.eachLine {
        if (it && it.trim().length() > 0 && it.indexOf('=') > -1) {
            def index = it.indexOf('=')
            def name = it.substring(0, index)
            def value = ''
            if (index < it.length() - 1) {
                value = it.substring(index + 1)
            }
            oClient.debug("adding flow input: '${name}=${value}'")
            inputsMap.put(name,value)
        }
    }

    long startTime = new Date().getTime();
    oClient.debug("Current UNIX time is: ${startTime}")
    def executionId = oClient.executeFlow(flowId, runName, logLevel, inputsMap)
    println("Execution id: \"${executionId}\"")

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
        println("Primary result: \"${result}\"")
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
