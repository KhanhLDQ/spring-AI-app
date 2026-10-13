package com.tommap.springaiapp.chat;

import java.util.List;

/** The shape the model is asked to fill in; Spring AI turns this record into the JSON schema sent with the prompt. */
public record RevenueBrief(String headline, List<String> highlights, String suggestion) {
}
