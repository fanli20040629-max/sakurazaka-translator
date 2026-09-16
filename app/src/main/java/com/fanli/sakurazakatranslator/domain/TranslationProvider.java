package com.fanli.sakurazakatranslator.domain;

/** Draft provider boundary. Future network implementations must run off the UI thread. */
public interface TranslationProvider {
    TranslationResult translate(TranslationRequest request);
}
