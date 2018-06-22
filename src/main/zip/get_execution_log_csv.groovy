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
    String outputFile = props.notNull("outputFile")
    boolean debugMode = props.optionalBoolean("debugMode", false)

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
