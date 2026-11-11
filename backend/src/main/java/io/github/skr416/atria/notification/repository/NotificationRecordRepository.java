package io.github.skr416.atria.notification.repository;

import io.github.skr416.atria.notification.model.NotificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link NotificationRecord}.
 */
public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {

    @Query("select n from NotificationRecord n where n.recipientUserId = :userId order by n.createdAt desc, n.id desc")
    List<NotificationRecord> findByRecipientUserIdOrderByCreatedAtDescIdDesc(@Param("userId") Long userId);

    @Query("select n from NotificationRecord n where n.recipientUserId = :userId and n.read = false order by n.createdAt desc, n.id desc")
    List<NotificationRecord> findByRecipientUserIdAndReadFalseOrderByCreatedAtDescIdDesc(@Param("userId") Long userId);

    @Query("select count(n) from NotificationRecord n where n.recipientUserId = :userId and n.read = false")
    long countUnreadByRecipientUserId(@Param("userId") Long userId);

    @Query("select n from NotificationRecord n where n.recipientUserId = :userId and n.read = false")
    List<NotificationRecord> findByRecipientUserIdAndReadFalse(@Param("userId") Long userId);
}
