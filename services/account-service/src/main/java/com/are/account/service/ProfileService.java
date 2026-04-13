package com.are.account.service;

import com.are.account.dto.ProfileResponse;
import com.are.account.repository.AccountRepository;
import com.are.account.repository.TransactionRepository;
import com.are.account.repository.UserRepository;
import com.are.common.exception.ResourceNotFoundException;
import com.are.common.model.AccountEntity;
import com.are.common.model.TransactionEntity;
import com.are.common.model.UserEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Profile operations for authenticated users.
 */
@Service
public class ProfileService {

        private final UserRepository userRepository;
        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;

        public ProfileService(UserRepository userRepository,
                        AccountRepository accountRepository,
                        TransactionRepository transactionRepository) {
                this.userRepository = userRepository;
                this.accountRepository = accountRepository;
                this.transactionRepository = transactionRepository;
        }

        public ProfileResponse getProfile(Long userId) {
                UserEntity user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

                AccountEntity account = accountRepository.findByUser(user)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Account not found for user: " + userId));

                return new ProfileResponse(
                                user.getId(),
                                user.getFirstName(),
                                user.getLastName(),
                                user.getEmail(),
                                user.getPhoneNumber(),
                                user.getStatus(),
                                user.getType().name(),
                                account.getId(),
                                account.getAccountNumber(),
                                account.getBalance(),
                                account.getCurrency(),
                                account.getStatus(),
                                user.getCreatedAt());
        }

        public Page<TransactionEntity> getTransactions(Long userId, Pageable pageable) {
                UserEntity user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

                AccountEntity account = accountRepository.findByUser(user)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Account not found for user: " + userId));

                return transactionRepository.findByAccountId(account.getId(), pageable);
        }
}
