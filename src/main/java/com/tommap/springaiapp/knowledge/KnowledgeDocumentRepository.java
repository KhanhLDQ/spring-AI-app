package com.tommap.springaiapp.knowledge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    boolean existsByContentHash(String contentHash);

    boolean existsByFilename(String filename);

    List<KnowledgeDocument> findAllByOrderByUploadedAtDescIdDesc();
}
