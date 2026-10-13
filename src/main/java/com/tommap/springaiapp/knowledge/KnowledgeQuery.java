package com.tommap.springaiapp.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record KnowledgeQuery(@NotBlank @Size(max = 500) String query) {
}
