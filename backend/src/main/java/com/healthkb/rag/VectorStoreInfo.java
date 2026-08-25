package com.healthkb.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorStoreInfo {
    private String backend;
    private boolean connected;
    private int count;
    private int dim;
    private String collection;
    private String detail;
}
