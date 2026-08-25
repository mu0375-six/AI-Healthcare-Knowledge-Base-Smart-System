package com.healthkb.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScoredChunk {
    private Long chunkId;
    private Long documentId;
    private String content;
    private String title;
    private String category;
    private String source;
    private float score;
}
