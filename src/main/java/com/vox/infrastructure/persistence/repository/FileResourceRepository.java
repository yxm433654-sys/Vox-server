package com.vox.infrastructure.persistence.repository;

import com.vox.infrastructure.persistence.entity.FileResource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileResourceRepository extends JpaRepository<FileResource, Long> {
}

