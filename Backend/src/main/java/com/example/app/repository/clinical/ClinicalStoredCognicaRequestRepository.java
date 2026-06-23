package com.example.app.repository.clinical;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.app.model.StoredCognicaRequest;

@Repository
public interface ClinicalStoredCognicaRequestRepository extends JpaRepository<StoredCognicaRequest, Long> {

    List<StoredCognicaRequest> findAllByOrderByUpdatedAtDesc();

    List<StoredCognicaRequest> findByNameContainingIgnoreCase(String name);

    List<StoredCognicaRequest> findByNameIgnoreCase(String name);

    @Query("SELECT sr FROM StoredCognicaRequest sr WHERE " +
           "sr.complementaryOptionsJson LIKE CONCAT('%', :keyword, '%') " +
           "ORDER BY sr.updatedAt DESC")
    List<StoredCognicaRequest> searchByComplementaryOptionKeyword(@Param("keyword") String keyword);
}
