package com.aalsikke.controller;

import com.aalsikke.dto.QuickParseRequest;
import com.aalsikke.dto.QuickParseResponse;
import com.aalsikke.exception.QuickParseLimitException;
import com.aalsikke.service.QuickParseLimiter;
import com.aalsikke.service.QuickParseService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KAN-372: AI quick-add.
 * POST /api/schedule-items/quick-parse {"text": "3andi exam math bukra se3a 10"}
 * Returns the values to pre-fill the New task form with. Nothing is saved.
 */
@RestController
@RequestMapping("/api/schedule-items")
public class QuickParseController {

    private final QuickParseService quickParseService;
    private final QuickParseLimiter limiter;

    public QuickParseController(QuickParseService quickParseService, QuickParseLimiter limiter) {
        this.quickParseService = quickParseService;
        this.limiter = limiter;
    }

    @PostMapping("/quick-parse")
    public QuickParseResponse quickParse(@Valid @RequestBody QuickParseRequest request,
                                         Authentication authentication) {
        if (!limiter.tryAcquire(authentication.getName())) {
            throw new QuickParseLimitException();
        }
        return quickParseService.parse(request.text());
    }
}