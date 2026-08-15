package com.webchat.platformapi.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileKindTest {

    @Test
    void fromMimeTypeClassifiesCommonTypes() {
        assertEquals(FileKind.IMAGE, FileKind.fromMimeType("image/png"));
        assertEquals(FileKind.AUDIO, FileKind.fromMimeType("audio/mpeg"));
        assertEquals(FileKind.VIDEO, FileKind.fromMimeType("video/mp4"));
        assertEquals(FileKind.DOCUMENT, FileKind.fromMimeType("application/pdf"));
        assertEquals(FileKind.DOCUMENT, FileKind.fromMimeType("text/plain"));
        assertEquals(FileKind.DOCUMENT, FileKind.fromMimeType("text/csv; charset=utf-8"));
        assertEquals(FileKind.DOCUMENT, FileKind.fromMimeType("application/json"));
        assertEquals(FileKind.DOCUMENT, FileKind.fromMimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        assertEquals(FileKind.OTHER, FileKind.fromMimeType("application/octet-stream"));
        assertEquals(FileKind.OTHER, FileKind.fromMimeType(null));
    }

    @Test
    void fallsBackToFilenameWhenMimeTypeIsGeneric() {
        assertEquals(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                FileKind.normalizeMimeType("application/octet-stream", "deck.pptx")
        );
        assertEquals(FileKind.DOCUMENT, FileKind.fromMimeTypeOrName("application/octet-stream", "report.csv"));
        assertEquals(FileKind.IMAGE, FileKind.fromMimeTypeOrName("", "diagram.svg"));
    }
}

