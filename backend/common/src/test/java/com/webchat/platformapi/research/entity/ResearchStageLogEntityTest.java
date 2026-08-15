package com.webchat.platformapi.research.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResearchStageLogEntityTest {

    @Test
    void jsonColumnsUseHibernateJsonBinding() throws Exception {
        assertJsonBinding("inputJson");
        assertJsonBinding("outputJson");
        assertJsonBinding("artifactsJson");
    }

    private void assertJsonBinding(String fieldName) throws Exception {
        Field field = ResearchStageLogEntity.class.getDeclaredField(fieldName);
        JdbcTypeCode annotation = field.getAnnotation(JdbcTypeCode.class);

        assertNotNull(annotation, fieldName + " should declare JdbcTypeCode");
        assertEquals(SqlTypes.JSON, annotation.value(), fieldName + " should bind as JSON");
    }
}
