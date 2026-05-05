package com.agent.core.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Document Ingestion Service.
 * Handles document loading, chunking, and vector store insertion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    /**
     * Ingest a document resource into the vector store.
     *
     * @param resource  the document resource (PDF, MD, HTML, Word, etc.)
     * @param tenantId  tenant identifier for multi-tenant isolation
     * @param source    human-readable source description
     * @param category  document category (e.g. "SOP", "FAQ", "ticket")
     * @return number of chunks ingested
     */
    public int ingest(Resource resource, String tenantId, String source, String category) {
        log.info("Ingesting document: source='{}', tenant='{}', category='{}'", source, tenantId, category);

        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> rawDocuments = reader.get();

        RagProperties.Ingestion ingestionConfig = ragProperties.getIngestion();
        TokenTextSplitter splitter = new TokenTextSplitter(
                ingestionConfig.getChunkSize(),
                ingestionConfig.getChunkOverlap(),
                5,
                10000,
                true
        );
        List<Document> chunks = splitter.apply(rawDocuments);

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("tenantId", tenantId);
            metadata.put("source", source);
            metadata.put("category", category);
            metadata.put("chunkIndex", i);
            metadata.put("totalChunks", chunks.size());
            metadata.put("ingestTime", System.currentTimeMillis());
            chunk.getMetadata().putAll(metadata);
        }

        vectorStore.add(chunks);
        log.info("Ingested {} chunks from document '{}'", chunks.size(), source);
        return chunks.size();
    }

    /**
     * Ingest raw text content directly into the vector store.
     *
     * @param content   text content
     * @param tenantId  tenant identifier
     * @param source    source description
     * @param category  document category
     * @return number of chunks ingested
     */
    public int ingestText(String content, String tenantId, String source, String category) {
        log.info("Ingesting text: source='{}', tenant='{}', length={}", source, tenantId, content.length());

        Document rawDocument = new Document(content);

        RagProperties.Ingestion ingestionConfig = ragProperties.getIngestion();
        TokenTextSplitter splitter = new TokenTextSplitter(
                ingestionConfig.getChunkSize(),
                ingestionConfig.getChunkOverlap(),
                5,
                10000,
                true
        );
        List<Document> chunks = splitter.apply(List.of(rawDocument));

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put("tenantId", tenantId);
            chunk.getMetadata().put("source", source);
            chunk.getMetadata().put("category", category);
            chunk.getMetadata().put("chunkIndex", i);
            chunk.getMetadata().put("totalChunks", chunks.size());
            chunk.getMetadata().put("ingestTime", System.currentTimeMillis());
        }

        vectorStore.add(chunks);
        log.info("Ingested {} chunks from text '{}'", chunks.size(), source);
        return chunks.size();
    }

    /**
     * Delete documents by metadata filter.
     *
     * @param tenantId tenant identifier
     * @param source   source to delete
     */
    public void deleteBySource(String tenantId, String source) {
        log.info("Deleting documents: tenant='{}', source='{}'", tenantId, source);
        vectorStore.delete(
                List.of("tenantId == '" + tenantId + "' && source == '" + source + "'")
        );
    }
}
