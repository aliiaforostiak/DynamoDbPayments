package com.sulf.dyndb.persistence.controller;

import jakarta.validation.constraints.NotBlank;

public record AddPaymentEventRequest(@NotBlank String type) {
}
