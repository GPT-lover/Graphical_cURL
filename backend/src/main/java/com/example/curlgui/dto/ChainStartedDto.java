package com.example.curlgui.dto;

/** Response of {@code POST /api/requests/run-chain}: poll status with this id. */
public record ChainStartedDto(String chainId) {
}
