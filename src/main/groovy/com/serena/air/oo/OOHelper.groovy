package com.serena.air.oo

import com.serena.air.StepFailedException
import com.serena.air.http.HttpBaseClient
import com.serena.air.http.HttpResponse
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.apache.http.HttpEntity
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicCookieStore


class OOHelper extends HttpBaseClient {

    boolean debug = false

    static final String BASE_URL = "/oo/rest/latest"
    static final String VERSION_URL = "${BASE_URL}/version"
    static final String FLOWS_URL = "${BASE_URL}/flows"
    static final String EXECUTIONS_URL = "${BASE_URL}/executions"

    OOHelper(String serverUrl, String username, String password) {
        super(serverUrl, username, password)
    }

    @Override
    protected String getFullServerUrl(String serverUrl) {
         return serverUrl
    }

    /**
     * Validate login to OO
     */
    def validate() {
        BasicCookieStore cookieStore = new BasicCookieStore()
        defaultContext.cookieStore = cookieStore
        defaultContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore)

        HttpResponse response = execGet(VERSION_URL)
        checkStatusCode(response.code)
        debug(response.body)
    }

    /**
     * Get the details of a specific flow
     * @param fId the uuid of the flow
     * @return JSON object with details of the flow
     */
    def getFlowDetails(String flowId) {
        HttpResponse response = execGet("${FLOWS_URL}/${flowId}")
        checkStatusCode(response.code)
        def json = new JsonSlurper().parseText(response.body)
        debug(json.toString())
        return json
    }

    /**
     * Execute a flow with inputs
     * @param fId the uuid of the flow
     * @param rName the name of the execution for display
     * @param logLev the log level
     * @param fInputs key:value input map
     * @return the execution Id of the flow
     */
    def executeFlow(String fId, String rName, String logLev, def fInputs) {

        JsonBuilder fJson = new JsonBuilder()
        fJson {
            flowUuid fId
            logLevel logLev
            if (rName) {
                runName rName
            }
            inputs fInputs
        }

        debug("Executing flow ${fId} using JSON:\n" + fJson.toPrettyString())

        HttpResponse response = execPost("${EXECUTIONS_URL}", fJson.toString())

        checkStatusCode(response.code)

        if (response.code != 201) {
           throw new StepFailedException("Error executing flow ${fId}")
        } else {
            return response.body
        }
    }


    /**
     * Get the summary of an execution
     * @param executionId the execution id
     * @return JSON object with summary of the execution
     */
    def getExecutionSummary(String executionId) {
        HttpResponse response = execGet("${EXECUTIONS_URL}/${executionId}/summary")
        checkStatusCode(response.code)
        def json = new JsonSlurper().parseText(response.body)
        debug(json.toString())
        return json
    }

    /**
     * Get the total step count for the execution
     * @param executionId the execution uuid
     * @return the step count
     */
    def getExecutionStepCount(String executionId) {
        HttpResponse response = execGet("${EXECUTIONS_URL}/${executionId}/steps/count")
        checkStatusCode(response.code)
        return response.body
    }

    /**
     * Get the step log of an execution from the specified startTime
     * @param executionId the execution uuid
     * @param startTimeFrom timestamp to get steps from
     * @return JSON object with log of the execution
     */
    def getExecutionLog(String executionId, long startTimeFrom = 0) {
        HttpResponse response = null
        if (startTimeFrom == 0)
            response = execGet("${EXECUTIONS_URL}/${executionId}/steps")
        else
            response = execGet("${EXECUTIONS_URL}/${executionId}/steps?startTimeFrom=${startTimeFrom}")
        checkStatusCode(response.code)
        def json = new JsonSlurper().parseText(response.body)
        debug(json.toString())
        return json
    }

    /**
     * Get the full step log of an execution as a CSV
     * @param executionId the execution uuid
     * @return JSON object with log of the execution
     */
    def getExecutionLogCSV(String executionId) {
        HttpResponse response = execGet("${EXECUTIONS_URL}/${executionId}/steps?mediaType=csv")
        checkStatusCode(response.code)
        return response.body
    }

    //

    static boolean isNotEmpty(String str) {
        return (str != null) && !(str.trim().isEmpty());
    }

    static boolean isEmpty(String str) {
        return (str == null) || str.trim().isEmpty();
    }

    def debug(String message) {
        if (this.debug) {
            println("[DEBUG] ${message}")
        }
    }

    def info(String message) {
        println("[INFO] ${message}")
    }

    def error(String message) {
        println("[ERROR] ${message}")
    }

    //
    // private methods
    //

    private HttpResponse execMethod(def method) {
        try {
            return exec(method)
        } catch (UnknownHostException e) {
            throw new StepFailedException("Unknown host: ${e.message}")
        } catch (HttpHostConnectException ignore) {
            throw new StepFailedException('Connection refused!')
        }
    }

    private HttpResponse execGet(def url) {
        HttpGet method = new HttpGet(getUriBuilder(url.toString()).build())
        return execMethod(method)
    }

    private HttpResponse execPost(def url, def json) {
        HttpPost httpPost = new HttpPost(getUriBuilder(url.toString()).build())

        // execute a get to retrieve CSRF-TOKEN
        HttpGet httpGet = new HttpGet(getUriBuilder("${VERSION_URL}").build())
        HttpResponse result = execMethod(httpGet)
        for (Header header: result.getHeaders()) {
            if (header.getName().equals( "X-CSRF-TOKEN")) {
                httpPost.addHeader("X-CSRF-TOKEN", header.getValue())
            }
        }

        HttpEntity body = new StringEntity(json.toString(), ContentType.APPLICATION_JSON)
        httpPost.entity = body
        return execMethod(httpPost)
    }

    private HttpResponse execPut(def url, def json) {
        HttpPut httpPut = new HttpPut(getUriBuilder(url.toString()).build())

        // execute a get to retrieve CSRF-TOKEN
        HttpGet httpGet = new HttpGet(getUriBuilder("${VERSION_URL}").build())
        HttpResponse result = execMethod(httpGet)
        for (Header header: result.getHeaders()) {
            if (header.getName().equals( "X-CSRF-TOKEN")) {
                httpPut.addHeader("X-CSRF-TOKEN", header.getValue())
            }
        }

        HttpEntity body = new StringEntity(json.toString(), ContentType.APPLICATION_JSON)
        httpPut.entity = body
        return execMethod(httpPut)
    }

}
