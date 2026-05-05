package com.agent.protocol.http;

import com.agent.core.rag.DocumentIngestionService;
import com.agent.core.rag.RagProperties;
import com.agent.core.rag.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST API for the RAG pipeline.
 * Provides endpoints for document ingestion and knowledge-based Q&A.
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final DocumentIngestionService ingestionService;
    private final RagProperties ragProperties;

    // ---- Info Endpoint ----

    /**
     * Get current RAG mode and configuration info.
     */
    @GetMapping("/mode")
    public ResponseEntity<Map<String, Object>> getMode() {
        String mode = ragProperties.getMode();
        return ResponseEntity.ok(Map.of(
                "mode", mode,
                "description", "mcp".equalsIgnoreCase(mode)
                        ? "Using L.O.C.A.L MCP for retrieval (no local ingestion needed)"
                        : "Using local PGVector for retrieval (requires document ingestion)"
        ));
    }

    // ---- Q&A Endpoints ----

    /**
     * Full RAG pipeline: query rewrite -> multi-path recall -> hybrid search
     * -> rerank -> prompt assembly -> LLM summarization.
     *
     * @param request containing query and optional tenantId
     * @return RAG result with answer and execution trace
     */
    @PostMapping("/query")
    public ResponseEntity<RagService.RagResult> query(@RequestBody QueryRequest request) {
        log.info("RAG query: query='{}', tenant='{}'", request.getQuery(), request.getTenantId());
        RagService.RagResult result = ragService.query(
                request.getQuery(),
                request.getTenantId() != null ? request.getTenantId() : "default"
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieve context only (without LLM summarization).
     * Useful for external systems that want to handle their own prompt assembly.
     *
     * @param request containing query and optional tenantId
     * @return knowledge context string
     */
    @PostMapping("/retrieve")
    public ResponseEntity<Map<String, Object>> retrieveContext(@RequestBody QueryRequest request) {
        String context = ragService.retrieveContext(
                request.getQuery(),
                request.getTenantId() != null ? request.getTenantId() : "default"
        );
        return ResponseEntity.ok(Map.of(
                "query", request.getQuery(),
                "context", context
        ));
    }

    // ---- Document Ingestion Endpoints ----

    /**
     * Upload and ingest a document file (PDF, MD, HTML, Word, etc.).
     *
     * @param file     the document file
     * @param tenantId tenant identifier
     * @param source   human-readable source name
     * @param category document category (e.g. "SOP", "FAQ", "ticket")
     * @return ingestion result with chunk count
     */
    @PostMapping("/ingest/file")
    public ResponseEntity<Map<String, Object>> ingestFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tenantId", defaultValue = "default") String tenantId,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "category", defaultValue = "general") String category) {

        if ("mcp".equalsIgnoreCase(ragProperties.getMode())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Document ingestion is not needed in MCP mode. "
                            + "Knowledge base is managed by L.O.C.A.L platform."
            ));
        }

        String sourceName = source != null ? source : file.getOriginalFilename();
        log.info("Ingesting file: name='{}', tenant='{}', category='{}'",
                sourceName, tenantId, category);

        int chunkCount = ingestionService.ingest(file.getResource(), tenantId, sourceName, category);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "source", sourceName,
                "chunkCount", chunkCount,
                "message", String.format("Successfully ingested %d chunks from '%s'", chunkCount, sourceName)
        ));
    }

    /**
     * Ingest raw text content directly.
     *
     * @param request containing text content and metadata
     * @return ingestion result with chunk count
     */
    @PostMapping("/ingest/text")
    public ResponseEntity<Map<String, Object>> ingestText(@RequestBody IngestTextRequest request) {
        if ("mcp".equalsIgnoreCase(ragProperties.getMode())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Text ingestion is not needed in MCP mode. "
                            + "Knowledge base is managed by L.O.C.A.L platform."
            ));
        }

        log.info("Ingesting text: source='{}', tenant='{}', category='{}'",
                request.getSource(), request.getTenantId(), request.getCategory());

        String tenantId = request.getTenantId() != null ? request.getTenantId() : "default";
        String category = request.getCategory() != null ? request.getCategory() : "general";

        int chunkCount = ingestionService.ingestText(
                request.getContent(), tenantId, request.getSource(), category);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "source", request.getSource(),
                "chunkCount", chunkCount,
                "message", String.format("Successfully ingested %d chunks", chunkCount)
        ));
    }

    /**
     * Delete documents by tenant and source.
     *
     * @param tenantId tenant identifier
     * @param source   source name to delete
     * @return deletion result
     */
    @DeleteMapping("/documents")
    public ResponseEntity<Map<String, Object>> deleteDocuments(
            @RequestParam("tenantId") String tenantId,
            @RequestParam("source") String source) {

        log.info("Deleting documents: tenant='{}', source='{}'", tenantId, source);
        ingestionService.deleteBySource(tenantId, source);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", String.format("Deleted documents for tenant='%s', source='%s'", tenantId, source)
        ));
    }

    // ---- Request DTOs ----

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QueryRequest {
        private String query;
        private String tenantId;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class IngestTextRequest {
        private String content;
        private String source;
        private String tenantId;
        private String category;
    }
}
