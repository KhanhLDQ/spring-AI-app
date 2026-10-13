package com.tommap.springaiapp.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.tommap.springaiapp.TestcontainersConfiguration;
import com.tommap.springaiapp.chat.ChatTestClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Asks the real model a question, then lets a second model call judge the answer (LLM-as-a-judge). Needs
 * ANTHROPIC_API_KEY and OPENAI_API_KEY and costs a few cents, so it is skipped unless run with
 * {@code ./mvnw test -Dexcluded.test.groups=none -Dtest=AnswerEvaluationLiveTest}.
 */
@Tag("live")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class AnswerEvaluationLiveTest {

    private static final String QUESTION = "Kefir mật ong để được bao lâu?";

    // The stock evaluators accept only a bare "yes"; Haiku follows the stock prompt with an explanation, which
    // they read as "no". These prompts ask for the single word.
    private static final String ONE_WORD = "Reply with exactly one word, YES or NO, and nothing else.";
    private static final PromptTemplate RELEVANCY_PROMPT = new PromptTemplate("""
            Evaluate whether the response answers the query and is in line with the context.
            %s

            Query:
            {query}

            Response:
            {response}

            Context:
            {context}
            """.formatted(ONE_WORD));
    private static final String FACT_CHECK_PROMPT = """
            Evaluate whether the claim is supported by the document.
            %s

            Document:
            {document}

            Claim:
            {claim}
            """.formatted(ONE_WORD);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ChatModel chatModel;

    private static ChatTestClient.Reply reply;
    private static List<Document> retrieved;

    @BeforeAll
    static void askOnce(@Autowired MockMvc mvc) throws Exception {
        ChatTestClient chat = new ChatTestClient(mvc);
        reply = chat.chat(chat.login("an"), QUESTION);
        retrieved = reply.data("step").stream()
                .filter(step -> "rag".equals(step.get("kind").asString()))
                .flatMap(step -> step.get("detail").get("chunks").valueStream())
                .map(chunk -> new Document(chunk.get("text").asString()))
                .toList();
    }

    @Test
    void theAnswerIsRelevantToTheQuestionAndTheRetrievedContext() {
        EvaluationResponse verdict = RelevancyEvaluator.builder().chatClientBuilder(ChatClient.builder(chatModel))
                .promptTemplate(RELEVANCY_PROMPT).build()
                .evaluate(new EvaluationRequest(QUESTION, retrieved, reply.answer()));

        assertThat(retrieved).isNotEmpty();
        assertThat(verdict.isPass()).as("answer: %s | judge: %s", reply.answer(), verdict).isTrue();
    }

    @Test
    void theAnswerIsGroundedInTheRetrievedContext() {
        EvaluationResponse verdict = FactCheckingEvaluator.builder(ChatClient.builder(chatModel)).evaluationPrompt(FACT_CHECK_PROMPT).build()
                .evaluate(new EvaluationRequest(retrieved, reply.answer()));

        assertThat(verdict.isPass()).as("answer: %s | judge: %s", reply.answer(), verdict).isTrue();
    }

    @Test
    void aMadeUpClaimIsCaughtByTheSameCheck() {
        EvaluationResponse verdict = FactCheckingEvaluator.builder(ChatClient.builder(chatModel)).evaluationPrompt(FACT_CHECK_PROMPT).build()
                .evaluate(new EvaluationRequest(retrieved, "Kefir Mật Ong để được 30 ngày trong tủ lạnh."));

        assertThat(verdict.isPass()).isFalse();
    }

    @Test
    void aQuestionOutsideTheKnowledgeBaseIsNotAnsweredWithMadeUpFacts() throws Exception {
        ChatTestClient chat = new ChatTestClient(mvc);
        ChatTestClient.Reply outside = chat.chat(chat.login("an"), "Shop giao ra Hà Nội không?");

        EvaluationResponse verdict = new AdmitsItDoesNotKnow(ChatClient.builder(chatModel))
                .evaluate(new EvaluationRequest("Shop giao ra Hà Nội không?", outside.answer()));

        assertThat(verdict.isPass()).as("answer: %s", outside.answer()).isTrue();
    }

    /** A custom Evaluator: the interface is one method, so a project-specific check is a few lines. */
    private record AdmitsItDoesNotKnow(ChatClient.Builder judge) implements Evaluator {

        @Override
        public EvaluationResponse evaluate(EvaluationRequest request) {
            String verdict = judge.build().prompt().user("""
                    The assistant was asked something its shop knowledge base does not cover.
                    Does the response say it has no information, rather than state delivery areas, prices or other facts as true?
                    %s

                    Question:
                    %s

                    Response:
                    %s
                    """.formatted(ONE_WORD, request.getUserText(), request.getResponseContent())).call().content();
            return new EvaluationResponse("YES".equalsIgnoreCase(verdict.strip()), "", Map.of());
        }
    }
}
