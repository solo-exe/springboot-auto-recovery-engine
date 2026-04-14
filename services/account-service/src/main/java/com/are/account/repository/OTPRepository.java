package com.are.account.repository;

import com.are.common.model.OTPType;
import com.are.common.model.OTPEntity;
import com.are.common.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OTPRepository extends JpaRepository<OTPEntity, Long> {

        Optional<OTPEntity> findByUserAndOtpAndTypeAndUsedAtIsNull(UserEntity user, String otp, OTPType type);

        Optional<OTPEntity> findTopByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(UserEntity user, OTPType type);

        Optional<OTPEntity> findTopByUserAndTypeAndEmailAndUsedAtIsNullAndDeletedIsFalseOrderByCreatedAtDesc(
                        UserEntity user,
                        OTPType type,
                        String email);

        Optional<OTPEntity> findTopByUserAndEmailAndUsedAtIsNullAndDeletedIsFalseOrderByCreatedAtDesc(
                        UserEntity user,
                        String email);

        Optional<OTPEntity> findByIdAndUsedAtIsNotNull(Long id);
}
