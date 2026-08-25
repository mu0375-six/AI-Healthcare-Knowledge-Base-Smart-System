package com.healthkb.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Citation {
    private String title;
    private String source;
    private String snippet;
    private String category;
}
