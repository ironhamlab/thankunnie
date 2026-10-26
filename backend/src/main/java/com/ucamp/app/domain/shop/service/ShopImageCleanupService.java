package com.ucamp.app.domain.shop.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.shop.entity.ShopImageCleanup;
import com.ucamp.app.domain.shop.repository.ShopImageCleanupRepository;
import com.ucamp.app.domain.shop.repository.ShopRepository;

/** 이미지 변경 트랜잭션과 독립적으로 정리 실패를 기록하고 재시도한다. */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ShopImageCleanupService {

    private static final int BATCH_SIZE = 20;

    private final ShopImageCleanupRepository repository;
    private final ShopRepository shopRepository;
    private final ShopImageObjectStorage objectStorage;
    private final Clock clock;

    public ShopImageCleanupService(ShopImageCleanupRepository repository, ShopRepository shopRepository,
            ShopImageObjectStorage objectStorage, Clock clock) {
        this.repository = repository;
        this.shopRepository = shopRepository;
        this.objectStorage = objectStorage;
        this.clock = clock;
    }

    /** 원래 이미지 변경이 롤백되어도 삭제 실패 기록은 별도 트랜잭션으로 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(String scope, String fileName) {
        if (!repository.existsByStorageScopeAndFileName(scope, fileName)) {
            repository.save(new ShopImageCleanup(scope, fileName, LocalDateTime.now(clock)));
        }
    }

    /** 현재 저장소의 실행 시각이 지난 작업만 제한된 수로 조회한다. */
    public List<Long> findDueIds() {
        return repository.findByStorageScopeAndNextAttemptAtLessThanEqualOrderByCleanupIdAsc(
                objectStorage.scope(), LocalDateTime.now(clock), PageRequest.of(0, BATCH_SIZE))
                .stream().map(ShopImageCleanup::getCleanupId).toList();
    }

    /** 다중 서버에서도 작업 행을 잠그고 성공한 작업만 제거한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(Long cleanupId) {
        ShopImageCleanup task = repository.findByIdForUpdate(cleanupId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (task == null || !task.getStorageScope().equals(objectStorage.scope())
                || task.getNextAttemptAt().isAfter(now)) {
            return;
        }
        // API는 UUID 파일명을 재사용하지 않는다. DB에서 참조하는 파일은 방어적으로 삭제하지 않는다.
        if (shopRepository.existsByImageUrlEndingWith("/images/" + task.getFileName())) {
            repository.delete(task);
            log.warn("현재 대표 이미지이므로 정리 작업 취소: {}", task.getFileName());
            return;
        }
        try {
            objectStorage.delete(task.getFileName());
        } catch (RuntimeException e) {
            task.reschedule(now);
            log.warn("매장 이미지 정리 재시도 실패: {} ({}회)", task.getFileName(), task.getAttemptCount(), e);
            return;
        }
        // 객체 삭제 후 DB 삭제가 실패하더라도 다음 시도에서 같은 키를 안전하게 다시 삭제할 수 있다.
        repository.delete(task);
    }
}
