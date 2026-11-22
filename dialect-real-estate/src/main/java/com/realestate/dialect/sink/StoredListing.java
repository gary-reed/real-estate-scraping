package com.realestate.dialect.sink;

import java.util.Map;

/** One listing held by the CSV sink: the same fields the framework posts to a consumer. */
record StoredListing(String id, String hash, boolean deleted, Map<String, Object> attributes, String payload) {}
