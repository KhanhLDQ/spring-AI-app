package com.tommap.springaiapp.knowledge;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Role check lives in SecurityConfiguration (/api/admin/** requires ADMIN). */
@RestController
@RequestMapping("/api/admin/knowledge")
class KnowledgeController {

    private final KnowledgeService knowledge;

    KnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @GetMapping
    List<KnowledgeDocView> list() {
        return knowledge.list();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    IngestResult upload(@RequestParam("file") MultipartFile file) throws IOException {
        return knowledge.ingest(file.getOriginalFilename(), file.getBytes());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAll() {
        knowledge.deleteAll();
    }

    @GetMapping("/chunking")
    ChunkingView chunking() {
        return knowledge.chunking();
    }

    /** Re-chunks the bundled documents at the new size; uploaded documents are not kept, so they are dropped. */
    @PutMapping("/chunking")
    ChunkingView rechunk(@RequestBody @Valid ChunkingRequest request) {
        return knowledge.rechunk(request.chunkTokens());
    }

    @PostMapping("/search")
    List<SearchHit> search(@RequestBody @Valid KnowledgeQuery request) {
        return knowledge.search(request.query());
    }
}
