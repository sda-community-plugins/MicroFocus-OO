// --------------------------------------------------------------------------------
// Get the execution status of an Operations Orchestration flow
// --------------------------------------------------------------------------------

import com.serena.air.plugin.oo.*

import com.serena.air.StepFailedException
import com.serena.air.StepPropertiesHelper
import com.urbancode.air.AirPluginTool

//
// Create some variables that we can use throughout the plugin step.
// These are mainly for checking what operating system we are running on.
//
final def PLUGIN_HOME = System.getenv()['PLUGIN_HOME']
final String lineSep = System.getProperty('line.separator')
final String osName = System.getProperty('os.name').toLowerCase(Locale.US)
final String pathSep = System.getProperty('path.separator')
final boolean windows = (osName =~ /windows/)
final boolean vms = (osName =~ /vms/)
final boolean os9 = (osName =~ /mac/ && !osName.endsWith('x'))
final boolean unix = (pathSep == ':' && !vms && !os9)

//
// Initialise the plugin tool and retrieve all the properties that were sent to the step.
//
final def  apTool = new AirPluginTool(this.args[0], this.args[1])
final def  props  = new StepPropertiesHelper(apTool.getStepProperties(), true)

//
// Set a variable for each of the plugin steps's inputs.
// We can check whether a required input is supplied (the helper will fire an exception if not) and
// if it is of the required type.
//
File workDir = new File('.').canonicalFile
String oServerUrl = props.notNull('serverUrl')
String oUsername = props.notNull('username')
String oPassword = props.notNull('password')
String executionId = props.notNull('executionId')
boolean waitForCompletion = props.optionalBoolean("waitForCompletion", false)
String outputProp = props.optional("outputProp", "flowPrimaryResult")
long delay = props.optionalInt("delay", 6000)
boolean debugMode = props.optionalBoolean("debugMode", false)

println "----------------------------------------"
println "-- STEP INPUTS"
println "----------------------------------------"

//
// Print out each of the property values.
//
println "Working directory: ${workDir.canonicalPath}"
println "OO Server URL: ${oServerUrl}"
println "OO Username: ${oUsername}"
println "OO Password: ${oPassword.replaceAll(".", "*")}"
println "Executiod Id: ${executionId}"
println "Wait for Completion: ${waitForCompletion}"
println "Flow Result Property: ${outputProp}"
println "Delay Interval: ${delay}"
println "Debug mode value: ${debugMode}"
if (debugMode) { props.setDebugLoggingMode() }

println "----------------------------------------"
println "-- STEP EXECUTION"
println "----------------------------------------"


def status = "UNKNOWN"
def result = "UNKNOWN"

try {
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

    if (status != null) {
        println("Execution status is \"${status}\"")
    }
    if (result != null) {
        println("Execution result is \"${result}\"")
    }

    if (status.equals("COMPLETED")) {
        def stepCount = oClient.getExecutionStepCount(executionId)
        println("Executed ${stepCount} steps")
        json = oClient.getExecutionLog(executionId)
        result =  (json?.rawResult.Result[0] ? json?.rawResult.Result[0] : "none")
        println("Primary result: ${result}")
    }

    if (status.equals("FAILURE")) {
        System.exit 1
    }

} catch (StepFailedException e) {
    println "ERROR: ${e.message}"
    System.exit 1
}

println "----------------------------------------"
println "-- STEP OUTPUTS"
println "----------------------------------------"
println("Setting \"executionId\" output property to \"${executionId}\"")
apTool.setOutputProperty("executionId", executionId)
if (status != null) {
    println("Setting \"executionStatus\" output property to \"${status}\"")
    apTool.setOutputProperty("executionStatus", status)
}
if (result != null) {
    println("Setting \"executionResult\" output property to \"${result}\"")
    apTool.setOutputProperty("executionResult", result)
}
if (status.equals("COMPLETED")) {
    println("Setting \"${outputProp}\" output property to \"${result}\"")
    apTool.setOutputProperty(outputProp, result)

}
apTool.storeOutputProperties()

//
// An exit with a zero value means the plugin step execution will be deemed successful.
//
System.exit(0)
