package org.bruneel.thankyouboard.repository;

import org.bruneel.thankyouboard.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findByBoardIdOrderByCreatedAtAsc(UUID boardId);
    long countByBoardId(UUID boardId);
}
