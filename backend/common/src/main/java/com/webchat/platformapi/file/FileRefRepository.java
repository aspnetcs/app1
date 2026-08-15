package com.webchat.platformapi.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileRefRepository extends JpaRepository<FileRefEntity, Long> {
    long countByFileId(String fileId);
    List<FileRefEntity> findTop200ByFileIdOrderByIdAsc(String fileId);
    void deleteByFileId(String fileId);
    void deleteByRefTypeAndRefId(String refType, String refId);
}

