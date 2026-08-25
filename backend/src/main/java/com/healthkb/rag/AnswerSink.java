package com.healthkb.rag;

public interface AnswerSink {
    void delta(String token);
}
