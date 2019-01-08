// --------------------------------------------------------------------------------
// Get a flow's execution log and save as a CSV file format
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
String outputFile = props.notNull("outputFile")
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
println "Execution Id: ${executionId}"
println "Output File Name: ${outputFile}"
println "Debug mode value: ${debugMode}"
if (debugMode) { props.setDebugLoggingMode() }

println "----------------------------------------"
println "-- STEP EXECUTION"
println "----------------------------------------"

try {
    OOHelper oClient = new OOHelper(oServerUrl, oUsername, oPassword)

    oClient.setPreemptiveAuth()
    oClient.setSSL()
    oClient.validate()
    oClient.setDebug(debugMode)

    def csv = oClient.getExecutionLogCSV(executionId)
    println("Writing execution log to file: \"${outputFile}\"")
    new File(outputFile).write(csv)

    System.exit 0

} catch (StepFailedException e) {
    println "ERROR: ${e.message}"
    System.exit 1
}
