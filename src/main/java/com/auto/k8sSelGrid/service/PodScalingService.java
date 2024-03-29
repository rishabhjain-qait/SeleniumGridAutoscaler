package com.auto.k8sSelGrid.service;

import com.jayway.jsonpath.JsonPath;
import com.auto.k8sSelGrid.domain.Grid4NodesInfo;
import com.auto.k8sSelGrid.domain.GridConsoleStatus;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import net.minidev.json.parser.ParseException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("resource")
@Service
public class PodScalingService {
    private static final TrustManager[] UNQUESTIONING_TRUST_MANAGER = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
    };
    private static final Logger logger = LoggerFactory.getLogger(PodScalingService.class);
    @Value("${namespace}")
    private String namespace;
    @Value("${gridUrl}")
    private String gridUrl;
    @Value("${k8s_api_url_chrome}")
    private String k8sApiUrlChrome;
    @Value("${k8s_api_url_firefox}")
    private String k8sApiUrlFirefox;
    @Value("${ch_max_scale_limit}")
    private int maxScaleLimitChrome;
    @Value("${ch_min_scale_limit}")
    private int minScaleLimitChrome;
    @Value("${ff_max_scale_limit}")
    private int maxScaleLimitFirefox;
    @Value("${ff_min_scale_limit}")
    private int minScaleLimitFirefox;
    @Value("${k8s_host}")
    private String k8s_host;
    @Value("${k8s_token}")
    private String k8sToken;
    @Autowired
    private GridConsoleService gridStatusService;
    private OkHttpClient httpClient;

    @PostConstruct
    private void init() throws NoSuchAlgorithmException, KeyManagementException {
        logger.info("Grid Console URL: {}", gridUrl);
        logger.info("K8s API URL for Chrome: {}", k8sApiUrlChrome);
        logger.info("K8s API URL for Firefox: {}", k8sApiUrlFirefox);
        httpClient = new OkHttpClient();
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, UNQUESTIONING_TRUST_MANAGER, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        httpClient.setSslSocketFactory(sc.getSocketFactory());
    }

    public void adjustScale(String browser, GridConsoleStatus gridStatus, Grid4NodesInfo grid4NodesInfo) throws IOException, InterruptedException, ParseException, NoSuchAlgorithmException, KeyManagementException {
        logger.info("Let's check if auto-scaling is required for {}", browser);
        int availableNodesCount, busyNodesCount, browserConcurrency, queuedRequests;
        if (browser.contains("chrome")) {
            availableNodesCount = gridStatus.getAvailableChromeNodesCount();
            busyNodesCount = gridStatus.getBusyChromeNodesCount();
            browserConcurrency = grid4NodesInfo.getChromeConcurrency();
            queuedRequests = gridStatus.getWaitingChromeRequestsCount();
        } else {
            availableNodesCount = gridStatus.getAvailableFirefoxNodesCount();
            busyNodesCount = gridStatus.getBusyFirefoxNodesCount();
            browserConcurrency = grid4NodesInfo.getFirefoxConcurrency();
            queuedRequests = gridStatus.getWaitingFirefoxRequestsCount();
        }
        HashMap<String, String> nodeIdsWithNoSession = gridStatusService.getNodeIdsOnWhichSessionIsNotRunning(browser);
        int totalRunningNodes = (availableNodesCount + busyNodesCount);

        if (totalRunningNodes > 0)
            totalRunningNodes /= browserConcurrency;

        int currentScale = getScale(browser);
        int requiredScale = 0;
        int scaleForWaitingReq;
        int minScaleLimit = getMinScaleLimit(browser);
        if (queuedRequests > 0) {
            scaleForWaitingReq = queuedRequests / browserConcurrency;
            if (queuedRequests % browserConcurrency != 0)
                scaleForWaitingReq += 1;
            requiredScale = totalRunningNodes + scaleForWaitingReq;
            logger.info("Scale up may be required for {}. Current scale: {} and required scale: {}", browser, currentScale, requiredScale);
        } else if (totalRunningNodes < minScaleLimit) {
            requiredScale = minScaleLimit;
            logger.info("Scale up may be required for {}. Current scale: {} and required scale: {}", browser, currentScale, requiredScale);
        } else if (queuedRequests == 0 && !nodeIdsWithNoSession.isEmpty() && currentScale > minScaleLimit) {
            logger.info("Scaling down all the pods for {} where no session is running, number of such pods is " + nodeIdsWithNoSession.size(), browser);
            scaleDownPodsWhereNoSessionIsRunning(browser, nodeIdsWithNoSession);
            return;
        } else {
            logger.info("No scaling is required for {}", browser);
            return;
        }
        if (requiredScale != currentScale && requiredScale >= minScaleLimit) {
            logger.info("Scale {} for {}. Current scale: {} and required scale: {}", requiredScale > currentScale ? "up" : "down", browser, currentScale, requiredScale);
            updateScale(browser, requiredScale);
        } else {
            logger.info("Skipping scaling for {}. Current scale: {} and required scale: {}", browser, currentScale, requiredScale);
        }
    }

    public void cleanUp() throws IOException, InterruptedException, ParseException {
        logger.info("Cleaning up the Grid by re-starting all the nodes");
        scale("chrome", 0);
        scale("firefox", 0);
        scale("chrome", getMinScaleLimit("chrome"));
        scale("firefox", getMinScaleLimit("firefox"));
    }

    private int getScale(String browser) throws IOException {
        Request r = new Request.Builder()
                .url(browser.contains("chrome") ? k8sApiUrlChrome : k8sApiUrlFirefox)
                .header("Authorization", "Bearer " + k8sToken)
                .get()
                .build();
        Call call = httpClient.newCall(r);
        Response response = call.execute();
        String htmlContent = response.body().string();
        JSONObject jsonObject = new JSONObject(htmlContent);
        return jsonObject.getJSONObject("status").getInt("replicas");
    }

    private void updateScale(String browser, int scaledValue) throws IOException, InterruptedException, ParseException {
        int minScaleLimit = getMinScaleLimit(browser);
        int maxScaleLimit = getMaxScaleLimit(browser);
        if (scaledValue > maxScaleLimit) {
            logger.warn("{} scale required {} which is more than the max scale limit of {}. Hence no auto-scaling is performed.", browser, scaledValue, maxScaleLimit);
            logger.warn("So scaling {} to maximum scale {}.", browser, maxScaleLimit);
            scale(browser, maxScaleLimit);
        } else if (scaledValue < minScaleLimit)
            logger.warn("{} scale required {} which is less than the min scale limit of {}. Hence no auto-scaling is performed.", browser, scaledValue, minScaleLimit);
        else {
            scale(browser, scaledValue);
        }
    }

    private void scale(String browser, int scaledValue) throws IOException, InterruptedException, ParseException {
        MediaType JSON = MediaType.parse("application/strategic-merge-patch+json");
        String payload = String.format("{ \"spec\": { \"replicas\": %s } }", scaledValue);
        Request r = new Request.Builder()
                .url(browser.contains("chrome") ? k8sApiUrlChrome : k8sApiUrlFirefox)
                .header("Authorization", "Bearer " + k8sToken)
                .header("Accept", "application/json")
                .header("Content-Type", "application/strategic-merge-patch+json")
                .patch(RequestBody.create(JSON, payload))
                .build();
        Call call = httpClient.newCall(r);
        Response response = call.execute();
        if (response.code() != 200)
            throw new RuntimeException("Error while scaling the grid for browser " + browser + " - " + response.body().string());
        String responseString = response.body().string();
        JSONObject jsonObject = new JSONObject(responseString);
        int updatedScale;

        JSONObject spec = jsonObject.getJSONObject("spec");
        if (spec.has("replicas"))
            updatedScale = spec.getInt("replicas");
        else
            updatedScale = 0;

        if (updatedScale != scaledValue)
            logger.error("Error in scaling " + browser + " . Here is the json response: " + responseString);
        else
            waitForScaleToHappen(browser, scaledValue);
    }

    private void waitForScaleToHappen(String browser, int scale) throws InterruptedException, ParseException {
        Grid4NodesInfo grid4NodesInfo = gridStatusService.getGrid4NodesInfo();
        int existingScale = 0;

        existingScale = browser.contains("chrome") ? grid4NodesInfo.getChromeNodes() : grid4NodesInfo.getFirefoxNodes();

        while (existingScale != scale) {
            int pollingTime = 5;
            logger.info("Sleeping {} seconds for {} scaling to happen. Current scale: {} and required scale: {}", pollingTime, browser, existingScale, scale);
            TimeUnit.SECONDS.sleep(pollingTime);
            grid4NodesInfo = gridStatusService.getGrid4NodesInfo();
            existingScale = browser.contains("chrome") ? grid4NodesInfo.getChromeNodes() : grid4NodesInfo.getFirefoxNodes();
        }
        logger.info("Selenium Grid for {} is successfully scaled to {}", browser, scale);
    }

    private HashMap<String, String> getAllPodIPs(String browser) throws IOException, NoSuchAlgorithmException, KeyManagementException {
        String podsApiUrl = "https://" + k8s_host + "/api/v1/namespaces/" + namespace + "/pods";

        Request request = new Request.Builder()
                .url(podsApiUrl)
                .header("Authorization", "Bearer " + k8sToken)
                .get()
                .build();
        Response response = httpClient.newCall(request).execute();
        String responseBody = response.body().string();
        List<String> podIPs = JsonPath.read(responseBody, "$.items[*].status.podIP");
        List<String> podNames = JsonPath.read(responseBody, "$.items[*].metadata.name");

        HashMap<String, String> podNamesAndIps = new HashMap<>();
        for (int i = 0; i < podIPs.size(); i++) {
            podNamesAndIps.put(podIPs.get(i), podNames.get(i));
        }
        logger.info("Running for browser " + browser);
        logger.info("Pod Names and IPs Map");
        logger.info(String.valueOf(podNamesAndIps));
        return podNamesAndIps;
    }

    public void updateCostOfPod(String podName, String browser) throws IOException {
        String podNameApiUrl = "https://" + k8s_host + "/api/v1/namespaces/" + namespace + "/pods/" + podName;
        MediaType JSON = MediaType.parse("application/strategic-merge-patch+json");
        String payload = "{ \"metadata\": { \"annotations\": { \"controller.kubernetes.io/pod-deletion-cost\": \"-1\" } } }";
        Request r = new Request.Builder()
                .url(podNameApiUrl)
                .header("Authorization", "Bearer " + k8sToken)
                .header("Accept", "application/json")
                .header("Content-Type", "application/strategic-merge-patch+json")
                .patch(RequestBody.create(JSON, payload))
                .build();
        Call call = httpClient.newCall(r);
        Response response = call.execute();
        if (response.code() != 200)
            throw new RuntimeException("Error while scaling the grid for browser " + browser + " - " + response.body().string());
        String responseString = response.body().string();
        JSONObject jsonObject = new JSONObject(responseString);
        logger.info("Updation of Cost for pod " + podName + " is done");
    }

    private void scaleDownPodsWhereNoSessionIsRunning(String browser, HashMap<String, String> nodeIdsWithNoSession) throws IOException, NoSuchAlgorithmException, KeyManagementException, ParseException, InterruptedException {
        HashMap<String, String> podsIpsWithName = getAllPodIPs(browser);
        Set<String> keySet = nodeIdsWithNoSession.keySet();
        Iterator<String> iterator = keySet.iterator();
        int currentScale = getScale(browser);
        while (iterator.hasNext()) {
            String nodeId = iterator.next();
            String host = nodeIdsWithNoSession.get(nodeId);
            gridStatusService.removeNode(browser, nodeId);
            String podToUpdate = podsIpsWithName.get(host);
            updateCostOfPod(podToUpdate, browser);
            currentScale--;
            logger.info("Current scale for " + browser + "is " + currentScale);
            updateScale(browser, currentScale);
        }
    }

    private int getMaxScaleLimit(String browser) {
        return browser.contains("chrome") ? maxScaleLimitChrome : maxScaleLimitFirefox;
    }

    private int getMinScaleLimit(String browser) {
        return browser.contains("chrome") ? minScaleLimitChrome : minScaleLimitFirefox;
    }
}