package org.bruneel.thankyouboard.repository;

import org.bruneel.thankyouboard.domain.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BoardRepository extends JpaRepository<Board, UUID> {
    List<Board> findByOwnerIdOrderByCreatedAtAsc(String ownerId);

    long countByOwnerId(String ownerId);
}
