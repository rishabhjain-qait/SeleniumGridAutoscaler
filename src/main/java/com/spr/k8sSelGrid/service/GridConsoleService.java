package com.spr.k8sSelGrid.service;

import com.jayway.jsonpath.JsonPath;
import com.spr.k8sSelGrid.domain.Grid4NodesInfo;
import com.spr.k8sSelGrid.domain.GridConsoleStatus;
import net.minidev.json.JSONArray;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

@Service
public class GridConsoleService {
    private static final Logger logger = LoggerFactory.getLogger(GridConsoleService.class);
    @Value("${gridUrl}")
    private String gridUrl;

    final RestTemplate restTemplate = new RestTemplate();

    public GridConsoleStatus getStatusforGrid4() throws ParseException {
        String graphqlEndpoint = gridUrl + "/graphql";
        String graphqlQueryForSessions = "{ sessionsInfo { sessionQueueRequests, sessions { capabilities } } }";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>("{\"query\":\"" + graphqlQueryForSessions + "\"}", headers);
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(graphqlEndpoint, requestEntity, String.class);
        String responseBody = responseEntity.getBody();

        JSONArray sessions = JsonPath.read(responseBody, "$.data.sessionsInfo.sessions");
        JSONArray sessionQueueRequests = JsonPath.read(responseBody, "$.data.sessionsInfo.sessionQueueRequests");

        int chromeSessionCount = 0;
        int ffsessionCount = 0;
        for (int i = 0; i < sessions.size(); i++) {
            String browserName = JsonPath.read(sessions.get(i).toString(), "$.browserName").toString();

            if (browserName.equalsIgnoreCase("chrome"))
                chromeSessionCount++;
            else if (browserName.equalsIgnoreCase("firefox"))
                ffsessionCount++;
        }

        int chromeSessionQueue = 0;
        int firefoxSessionQueue = 0;
        for (int i = 0; i < sessionQueueRequests.size(); i++) {
            String browserName = JsonPath.read(sessionQueueRequests.get(i).toString(), "$.browserName").toString();

            if (browserName.equalsIgnoreCase("chrome"))
                chromeSessionQueue++;
            else if (browserName.equalsIgnoreCase("firefox"))
                firefoxSessionQueue++;
        }

        Grid4NodesInfo grid4NodesInfo = getGrid4NodesInfo();
        int availableChromeNodesCount = grid4NodesInfo.getChromeNodes() * grid4NodesInfo.getChromeConcurrency() - chromeSessionCount;
        int busyChromeNodesCount = chromeSessionCount;
        int waitingChromeRequestsCount = chromeSessionQueue;
        int availableFirefoxNodesCount = grid4NodesInfo.getFirefoxNodes() * grid4NodesInfo.getFirefoxConcurrency() - ffsessionCount;
        int busyFirefoxNodesCount = ffsessionCount;
        int waitingFirefoxRequestsCount = firefoxSessionQueue;

        GridConsoleStatus status = new GridConsoleStatus();
        status.setAvailableFirefoxNodesCount(availableFirefoxNodesCount);
        status.setBusyFirefoxNodesCount(busyFirefoxNodesCount);
        status.setWaitingFirefoxRequestsCount(waitingFirefoxRequestsCount);
        status.setAvailableChromeNodesCount(availableChromeNodesCount);
        status.setBusyChromeNodesCount(busyChromeNodesCount);
        status.setWaitingChromeRequestsCount(waitingChromeRequestsCount);

        logger.info(String.valueOf(status));
        return status;
    }

    public Grid4NodesInfo getGrid4NodesInfo() throws ParseException {
        String graphqlEndpoint = gridUrl + "/graphql";
        String graphqlQuery = "{ nodesInfo { nodes { maxSession, stereotypes } } }";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>("{\"query\":\"" + graphqlQuery + "\"}", headers);
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(graphqlEndpoint, requestEntity, String.class);
        String responseBody = responseEntity.getBody();


        JSONArray nodes = JsonPath.read(responseBody, "$.data.nodesInfo.nodes[*].stereotypes");
        int chromeNodes = 0;
        int firefoxNodes = 0;
        int chromeConcurrency = 0;
        int firefoxConcurrency = 0;
        for (int i = 0; i < nodes.size(); i++) {
            JSONParser parser = new JSONParser();
            JSONArray jsonArray = (JSONArray) parser.parse(nodes.get(i).toString());

            String browser = JsonPath.read(jsonArray.get(0), "$.stereotype.browserName").toString();
            int concurrency = Integer.parseInt(JsonPath.read(jsonArray.get(0), "$.slots").toString());

            if (browser.equalsIgnoreCase("chrome")) {
                chromeNodes++;
                chromeConcurrency = concurrency;
            } else if ((browser.equalsIgnoreCase("firefox"))) {
                firefoxNodes++;
                firefoxConcurrency = concurrency;
            }
        }

        // Create and return your Grid4NodesInfo object
        Grid4NodesInfo grid4NodesInfo = new Grid4NodesInfo();
        grid4NodesInfo.setFirefoxNodes(firefoxNodes);
        grid4NodesInfo.setChromeNodes(chromeNodes);
        grid4NodesInfo.setFirefoxConcurrency(firefoxConcurrency);
        grid4NodesInfo.setChromeConcurrency(chromeConcurrency);

        return grid4NodesInfo;
    }

    public HashMap<String, String> getNodeIdsOnWhichSessionIsNotRunning(String browser) throws MalformedURLException, ParseException {
        String graphqlEndpoint = gridUrl + "/graphql";
        String graphqlQuery = "{ nodesInfo { nodes { id uri sessionCount stereotypes} } }";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>("{\"query\":\"" + graphqlQuery + "\"}", headers);
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(graphqlEndpoint, requestEntity, String.class);
        String responseBody = responseEntity.getBody();

        List<String> nodeIds = JsonPath.read(responseBody, "$.data.nodesInfo.nodes[*].id");
        List<String> nodeUris = JsonPath.read(responseBody, "$.data.nodesInfo.nodes[*].uri");
        List<Integer> nodeSessionCounts = JsonPath.read(responseBody, "$.data.nodesInfo.nodes[*].sessionCount");
        JSONArray stereoTypes = JsonPath.read(responseBody, "$.data.nodesInfo.nodes[*].stereotypes");

        HashMap<String, String> nodeIdsWithZeroSessions = new HashMap<>();

        for (int i = 0; i < nodeSessionCounts.size(); i++) {
            int sessionCount = nodeSessionCounts.get(i);
            if (sessionCount == 0) {
                URL url = new URL(nodeUris.get(i));
                String host = url.getHost();
                JSONParser parser = new JSONParser();
                JSONArray jsonArray = (JSONArray) parser.parse(stereoTypes.get(i).toString());
                String nodeBrowser = JsonPath.read(jsonArray.get(0), "$.stereotype.browserName").toString().toLowerCase();
                if (nodeBrowser.contains(browser))
                    nodeIdsWithZeroSessions.put(nodeIds.get(i), host);
            }
        }
        logger.info("Node Ids and IP map for " + browser + " that are having zero sessions running");
        logger.info(String.valueOf(nodeIdsWithZeroSessions));
        return nodeIdsWithZeroSessions;
    }

    public void removeNode(String browser, String nodeId) {
        try {
            URL url = new URL(gridUrl + "/se/grid/distributor/node/" + nodeId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            connection.setRequestProperty("X-REGISTRATION-SECRET", "");

            int responseCode = connection.getResponseCode();
            logger.info("Response Code for Removing " + browser + " Node " + nodeId + " is" + responseCode);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

