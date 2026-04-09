package com.are.account.repository;

import com.are.common.model.AccountEntity;
import com.are.common.model.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    Optional<AccountEntity> findByAccountNumber(String accountNumber);

    Page<AccountEntity> findByStatus(AccountStatus status, Pageable pageable);

    boolean existsByAccountNumber(String accountNumber);
}
