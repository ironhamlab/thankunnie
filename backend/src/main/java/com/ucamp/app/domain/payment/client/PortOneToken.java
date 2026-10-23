package com.ucamp.app.domain.payment.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PortOneToken(@JsonProperty("access_token") String accessToken) {
}
