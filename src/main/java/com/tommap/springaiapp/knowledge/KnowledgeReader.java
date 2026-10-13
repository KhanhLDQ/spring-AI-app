package com.tommap.springaiapp.knowledge;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates an upload and turns it into source documents (one per PDF page or Markdown section) tagged with
 * {@code source} and, for PDFs, {@code page}. The file type comes from the content, never the extension.
 */
@Component
class KnowledgeReader {

    static final String SOURCE = "source";
    static final String PAGE = "page";

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_NAME_LENGTH = 100;

    private final KnowledgeProperties properties;

    KnowledgeReader(KnowledgeProperties properties) {
        this.properties = properties;
    }

    /** Drops any client-supplied path and unusual characters; the result is only a label, never a file path. */
    static String displayName(String clientName) {
        String name = clientName == null ? "" : clientName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1)
                .replaceAll("[^\\p{L}\\p{N} ._-]", "_")
                .strip();
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(name.length() - MAX_NAME_LENGTH);
        }
        return name.isEmpty() || name.chars().allMatch(c -> c == '.') ? "document" : name;
    }

    List<Document> read(String filename, byte[] bytes) {
        if (bytes.length == 0) {
            throw new KnowledgeRejectedException(HttpStatus.UNPROCESSABLE_CONTENT, "The file is empty.");
        }
        if (bytes.length > properties.maxFileSize().toBytes()) {
            throw new KnowledgeRejectedException(HttpStatus.CONTENT_TOO_LARGE,
                    "The file is larger than " + properties.maxFileSize().toMegabytes() + " MB.");
        }
        return isPdf(bytes) ? readPdf(filename, bytes) : readMarkdown(filename, bytes);
    }

    private List<Document> readPdf(String filename, byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            if (pdf.getNumberOfPages() > properties.maxPages()) {
                throw new KnowledgeRejectedException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "The PDF has more than " + properties.maxPages() + " pages.");
            }
        } catch (IOException e) {
            throw new KnowledgeRejectedException(HttpStatus.UNPROCESSABLE_CONTENT, "The PDF cannot be read.");
        }
        List<Document> pages = new ArrayList<>();
        for (Document page : new PagePdfDocumentReader(new ByteArrayResource(bytes)).get()) {
            String text = tidy(page.getText());
            if (!text.isEmpty()) {
                pages.add(new Document(text, Map.of(
                        SOURCE, filename,
                        PAGE, page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER))));
            }
        }
        return pages;
    }

    /** PDF extraction keeps layout padding; collapse it so chunks are compact and read well as citations. */
    private static String tidy(String pageText) {
        return pageText == null ? "" : pageText.lines()
                .map(line -> line.replaceAll("[ \\t]+", " ").strip())
                .collect(Collectors.joining("\n"))
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    private List<Document> readMarkdown(String filename, byte[] bytes) {
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new KnowledgeRejectedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only PDF and Markdown (.md) files are supported.");
        }
        String text = decodeUtf8(bytes).replace("\r\n", "\n");
        List<Document> sections = new ArrayList<>();
        for (String section : text.split("(?m)^(?=## )")) {
            if (!section.isBlank() && !isTitleOnly(section)) {
                sections.add(new Document(section.strip(), Map.of(SOURCE, filename)));
            }
        }
        return sections;
    }

    private static boolean isTitleOnly(String section) {
        List<String> lines = section.lines().filter(line -> !line.isBlank()).toList();
        return lines.size() == 1 && lines.getFirst().startsWith("# ");
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (text.indexOf('\0') >= 0) {
                throw new CharacterCodingException();
            }
            return text;
        } catch (CharacterCodingException e) {
            throw new KnowledgeRejectedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only PDF and Markdown (.md) files are supported.");
        }
    }

    private static boolean isPdf(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
