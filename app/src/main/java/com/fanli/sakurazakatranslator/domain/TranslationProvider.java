package com.fanli.sakurazakatranslator.domain;

/** One explicit text request. Implementations must run off the UI thread. */
public interface TranslationProvider {
    TranslationResult translate(TranslationRequest request) throws java.io.IOException;
    void cancel();
}
