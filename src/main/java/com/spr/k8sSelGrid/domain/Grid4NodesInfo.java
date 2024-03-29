package com.spr.k8sSelGrid.domain;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(includeFieldNames = true)
public class Grid4NodesInfo {

    private int chromeNodes;
    private int firefoxNodes;
    private int chromeConcurrency;
    private int firefoxConcurrency;

}
