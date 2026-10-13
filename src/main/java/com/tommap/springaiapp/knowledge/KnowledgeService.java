package com.tommap.springaiapp.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import com.tommap.springaiapp.cache.SemanticCache;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The RAG ETL (read, split, embed, store) plus listing, retrieval testing and reset of the knowledge base. */
@Service
public class KnowledgeService {

    private static final int SEARCH_TOP_K = 5;

    private final VectorStore store;
    private final KnowledgeReader reader;
    private final TokenTextSplitter splitter;
    private final KnowledgeDocumentRepository docs;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SemanticCache cache;

    KnowledgeService(@Qualifier(KnowledgeConfiguration.STORE) VectorStore store, KnowledgeReader reader,
                     TokenTextSplitter splitter, KnowledgeDocumentRepository docs, JdbcTemplate jdbc, Clock clock,
                     SemanticCache cache) {
        this.store = store;
        this.reader = reader;
        this.splitter = splitter;
        this.docs = docs;
        this.jdbc = jdbc;
        this.clock = clock;
        this.cache = cache;
    }

    /** Chunks and vectors commit together with the document row, so a failed embedding leaves nothing behind. */
    @Transactional
    IngestResult ingest(String clientFilename, byte[] bytes) {
        String filename = KnowledgeReader.displayName(clientFilename);
        String hash = sha256(bytes);
        if (docs.existsByContentHash(hash) || docs.existsByFilename(filename)) {
            throw duplicate(filename);
        }

        long start = System.nanoTime();
        List<Document> sources = reader.read(filename, bytes);
        long readDone = System.nanoTime();
        List<Document> chunks = splitter.apply(sources);
        long splitDone = System.nanoTime();
        if (chunks.isEmpty()) {
            throw new KnowledgeRejectedException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "No text could be extracted. A scanned PDF needs OCR first.");
        }
        long embedMs = TimingEmbeddingModel.embedMillisDuring(() -> store.add(chunks));
        long storeDone = System.nanoTime();

        KnowledgeDocument saved;
        try {
            saved = docs.saveAndFlush(new KnowledgeDocument(filename, hash, clock.instant(), chunks.size()));
        } catch (DataIntegrityViolationException e) {
            throw duplicate(filename);
        }
        cache.clear();
        long storeMs = Math.max(0, millis(splitDone, storeDone) - embedMs);
        return new IngestResult(saved.getId(), filename, chunks.size(),
                new EtlTimings(millis(start, readDone), millis(readDone, splitDone), embedMs, storeMs, chunks.size()));
    }

    @Transactional(readOnly = true)
    List<KnowledgeDocView> list() {
        return docs.findAllByOrderByUploadedAtDescIdDesc().stream()
                .map(d -> new KnowledgeDocView(d.getId(), d.getFilename(), d.getUploadedAt(), d.getChunkCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    boolean isEmpty() {
        return docs.count() == 0;
    }

    /** Cached answers were built on the old knowledge, so they go too. */
    @Transactional
    public void deleteAll() {
        jdbc.execute("TRUNCATE TABLE vector_store");
        cache.clear();
        docs.deleteAllInBatch();
    }

    /** Back to the bundled shop documents only; anything uploaded since is dropped. */
    public void reseed() {
        deleteAll();
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:knowledge/*.md");
            Arrays.sort(files, Comparator.comparing(Resource::getFilename));
            for (Resource file : files) {
                ingest(file.getFilename(), file.getContentAsByteArray());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Bundled knowledge files could not be read", e);
        }
    }

    /** Similarity search only, no model call; the threshold is left open so every score is visible. */
    List<SearchHit> search(String query) {
        return store.similaritySearch(SearchRequest.builder()
                        .query(query).topK(SEARCH_TOP_K).similarityThreshold(0.0).build())
                .stream()
                .map(d -> new SearchHit(
                        String.valueOf(d.getMetadata().get(KnowledgeReader.SOURCE)),
                        d.getMetadata().get(KnowledgeReader.PAGE) instanceof Number page ? page.intValue() : null,
                        d.getScore() == null ? 0 : d.getScore(),
                        d.getText()))
                .toList();
    }

    private static KnowledgeRejectedException duplicate(String filename) {
        return new KnowledgeRejectedException(HttpStatus.CONFLICT,
                "'" + filename + "' is already in the knowledge base (same name or same content).");
    }

    private static long millis(long fromNanos, long toNanos) {
        return (toNanos - fromNanos) / 1_000_000;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
