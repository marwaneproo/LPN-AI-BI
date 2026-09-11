package com.lpn.aibi.llmorchestrator;

import java.util.List;

interface SchemaRetrievalClient {

    List<RetrievedTable> retrieve(String question, int topK, String language);
}
