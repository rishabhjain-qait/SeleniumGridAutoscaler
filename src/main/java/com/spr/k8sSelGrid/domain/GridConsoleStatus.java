package com.spr.k8sSelGrid.domain;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(includeFieldNames = true)
public class GridConsoleStatus {
    private int availableFirefoxNodesCount;
    private int busyFirefoxNodesCount;
    private int waitingFirefoxRequestsCount;
    private int availableChromeNodesCount;
    private int busyChromeNodesCount;
    private int waitingChromeRequestsCount;
}
