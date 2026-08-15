package com.webchat.platformapi.knowledge;

import org.hibernate.annotations.ColumnTransformer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KnowledgeChunkEntityTest {

    @Test
    void embeddingColumnUsesVectorCastForWriteAndTextForRead() throws Exception {
        Field field = KnowledgeChunkEntity.class.getDeclaredField("embedding");
        ColumnTransformer annotation = field.getAnnotation(ColumnTransformer.class);

        assertNotNull(annotation, "embedding should declare ColumnTransformer");
        assertEquals("embedding::text", annotation.read());
        assertEquals("cast(? as vector)", annotation.write());
    }
}
