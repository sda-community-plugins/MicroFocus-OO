package com.serena.air.plugin.oo

import com.serena.air.plugin.oo.OOHelper
import org.json.JSONObject

def oServerUrl = "https://oo.microfocus.com:8443"
def oUsername = ""
def oPassword = "!"

def flowId = "06fe8531-868b-4e79-aa7a-13a5e30a66ec"
//def flowId = "0127e497-ed82-4114-9124-6614ebf18647"
def inputs = """
min=1
max=10
"""
Map<String,String> inputsMap = new HashMap<String,String>()
inputs.put("min", "1")
inputs.put("max", "10")

inputs?.eachLine {
    if (it && it.trim().length() > 0 && it.indexOf('=') > -1) {
        def index = it.indexOf('=')
        def name = it.substring(0, index)
        def value = ''
        if (index < it.length() - 1) {
            value = it.substring(index + 1)
        }
        println("adding flow inputs: '${name}=${value}'")
        inputsMap.put(name,value)
    }
}

OOHelper oClient = new OOHelper(oServerUrl, oUsername, oPassword)

oClient.setPreemptiveAuth()
oClient.setSSL()
oClient.validate()
oClient.setDebug(false)

def json = oClient.getFlowDetails(flowId)
def flowName = json?.name
def flowPath = json?.path
oClient.info("Executing flow \"${flowName}\" - \"${flowPath}\"; uuid: \"${flowId}\"")

long startTime = new Date().getTime();
oClient.debug("Current UNIX time is: ${startTime}")
def execId = oClient.executeFlow(flowId, "run1", "STANDARD", inputsMap)
oClient.info("Execution id: \"${execId}\"")

def running = true
while (running) {
    sleep(6000)
    json = oClient.getExecutionSummary(execId)
    def status = json?.status[0]
    if (status.equals("COMPLETED")) {
        def result = json?.resultStatusName[0]
        oClient.info("current status: ${status}; result: ${result}")
        status = result
    }
    json = oClient.getExecutionLog(execId, startTime)
    json.each { data ->
        String path = data.stepInfo.path
        String stepName = data.stepInfo.stepName
        String responseType = (data.stepInfo.responseType ? "\t - ${data.stepInfo.responseType}" : "")
        String stepPrimaryResult = (data.stepPrimaryResult ? "\t - Result: ${data.stepPrimaryResult}" : "")
        oClient.info "Step [${path}]\t${stepName}${responseType}${stepPrimaryResult}"
    }
    startTime = new Date().getTime();
    if (status.equals("RUNNING") || status.contains("PAUSED")) {
        continue
    }
    else {
        oClient.info("final status: ${status}")
        running = false
    }
}

def stepCount = oClient.getExecutionStepCount(execId)
oClient.info("Executed ${stepCount} steps")
json = oClient.getExecutionLog(execId)
String result =  (json?.rawResult.Result[0] ? json?.rawResult.Result[0] : "none")
oClient.info("Primary result: ${result}")

println oClient.getExecutionLogCSV(execId)
