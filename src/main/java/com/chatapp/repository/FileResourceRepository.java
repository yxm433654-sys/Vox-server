package com.chatapp.repository;

import com.chatapp.entity.FileResource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileResourceRepository extends JpaRepository<FileResource, Long> {
}
