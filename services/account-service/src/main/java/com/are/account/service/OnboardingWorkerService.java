package com.are.account.service;

import com.are.account.config.RabbitConfig;
import com.are.account.repository.AccountRepository;
import com.are.common.model.AccountEntity;
import com.are.common.model.AccountStatus;
import com.are.common.model.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OnboardingWorkerService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingWorkerService.class);

    private final AccountRepository accountRepository;
    private final RabbitTemplate rabbitTemplate;

    public OnboardingWorkerService(AccountRepository accountRepository,
            RabbitTemplate rabbitTemplate) {
        this.accountRepository = accountRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public void completeOnboarding(UserEntity user) {
        AccountEntity account = accountRepository.findByUser(user)
                .orElseGet(() -> createInactiveAccount(user));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            account.setStatus(AccountStatus.ACTIVE);
            accountRepository.save(account);
            log.info("Activated onboarding account {} for user {}", account.getId(), user.getEmail());
        }

        sendOnboardingCompleteNotification(user, account);
    }

    private AccountEntity createInactiveAccount(UserEntity user) {
        AccountEntity account = new AccountEntity();
        account.setUser(user);
        account.setAccountNumber(generateAccountNumber());
        account.setAccountName(user.getFirstName() + " " + user.getLastName());
        account.setBalance(BigDecimal.ZERO);
        account.setCurrency("NGN");
        account.setStatus(AccountStatus.INACTIVE);
        return accountRepository.save(account);
    }

    private void sendOnboardingCompleteNotification(UserEntity user, AccountEntity account) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "ONBOARDING_COMPLETE");
        event.put("email", user.getEmail());
        event.put("firstName", user.getFirstName());
        event.put("lastName", user.getLastName());
        event.put("accountNumber", account.getAccountNumber());
        event.put("subject", "Your ARE account is ready");
        event.put("body", "<p>Hi " + user.getFirstName() + ",</p>" +
                "<p>Your account " + account.getAccountNumber() + " is now active.</p>" +
                "<p>Thank you for onboarding with ARE.</p>");

        rabbitTemplate.convertAndSend(RabbitConfig.NOTIFICATION_EXCHANGE,
                RabbitConfig.NOTIFICATION_ROUTING_KEY, event);
        log.info("Enqueued onboarding completion notification for {}", user.getEmail());
    }

    private String generateAccountNumber() {
        return "ARE" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 10);
    }
}
