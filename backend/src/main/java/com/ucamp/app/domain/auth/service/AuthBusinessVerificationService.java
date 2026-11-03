package com.ucamp.app.domain.auth.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.auth.client.NtsBusinessClient;
import com.ucamp.app.domain.auth.dto.request.AuthBusinessVerificationRequest;
import com.ucamp.app.domain.auth.repository.ShopAccountRepository;

@Service
@Transactional(readOnly = true)
public class AuthBusinessVerificationService {

    private static final int[] CHECKSUM_WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};
    private final ShopAccountRepository shopAccountRepository;
    private final NtsBusinessClient ntsBusinessClient;
    private final Clock clock;

    public AuthBusinessVerificationService(ShopAccountRepository shopAccountRepository,
            NtsBusinessClient ntsBusinessClient, Clock clock) {
        this.shopAccountRepository = shopAccountRepository;
        this.ntsBusinessClient = ntsBusinessClient;
        this.clock = clock;
    }

    public void verify(AuthBusinessVerificationRequest request) {
        String number = request.businessNumber();
        if (!isValidNumber(number)) {
            throw new BusinessException(ErrorCode.AUTH_BUSINESS_INVALID, "사업자등록번호 10자리를 확인해 주세요.");
        }
        if (request.representativeName() == null || request.representativeName().isBlank()
                || request.openedOn() == null || request.openedOn().isAfter(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.AUTH_BUSINESS_INVALID, "대표자명과 개업일자를 확인해 주세요.");
        }
        if (shopAccountRepository.existsByBusinessNumber(number)) {
            throw new BusinessException(ErrorCode.AUTH_DUPLICATE_BUSINESS_NUMBER);
        }
        ntsBusinessClient.validate(number, request.representativeName(), request.openedOn());
    }

    private static boolean isValidNumber(String number) {
        if (number == null || !number.matches("[0-9]{10}") || number.equals("0000000000")) {
            return false;
        }
        int sum = 0;
        for (int index = 0; index < CHECKSUM_WEIGHTS.length; index++) {
            sum += (number.charAt(index) - '0') * CHECKSUM_WEIGHTS[index];
        }
        sum += ((number.charAt(8) - '0') * 5) / 10;
        return (10 - sum % 10) % 10 == number.charAt(9) - '0';
    }
}
