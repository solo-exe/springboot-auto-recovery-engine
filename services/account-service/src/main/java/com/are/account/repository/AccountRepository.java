package com.are.account.repository;

import com.are.common.model.AccountEntity;
import com.are.common.model.AccountStatus;
import com.are.common.model.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    Optional<AccountEntity> findByAccountNumber(String accountNumber);

    Optional<AccountEntity> findByUser(UserEntity user);

    Optional<AccountEntity> findByUserAndStatus(UserEntity user, AccountStatus status);

    Page<AccountEntity> findByStatus(AccountStatus status, Pageable pageable);

    Optional<AccountEntity> findByUserId(Long userId);

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByUserId(Long userId);
}
