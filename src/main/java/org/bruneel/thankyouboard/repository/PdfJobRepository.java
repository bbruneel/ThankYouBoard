package org.bruneel.thankyouboard.repository;

import org.bruneel.thankyouboard.domain.PdfJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PdfJobRepository extends JpaRepository<PdfJob, UUID> {

    Optional<PdfJob> findByJobIdAndBoardId(UUID jobId, UUID boardId);
}
