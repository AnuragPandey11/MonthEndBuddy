package dev.monthendbuddy.agent.tools;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import dev.monthendbuddy.common.TextSanitizer;

/**
 * A pseudo-tool: calling it ends the turn with {@code NEEDS_CLARIFICATION}, and the UI shows the options as
 * quick-reply chips (architecture §10.4). It reads no data.
 */
@Component
public class AskClarifyingQuestionTool implements FinanceTool<AskClarifyingQuestionTool.ClarifyInput> {

	public static final String NAME = "askClarifyingQuestion";

	record ClarifyInput(@JsonPropertyDescription("One short question for the user") @NotBlank @Size(max = 300) String question,
			@JsonProperty(required = false) @JsonPropertyDescription("Up to 5 short answers the user can tap") @Size(max = 5) List<@NotBlank @Size(max = 60) String> options) {
	}

	/** The question as the UI shows it. */
	public record Clarification(String question, List<String> options) {
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String description() {
		return "Ask the user one short question when the period, amount or goal is ambiguous and the answer would "
				+ "change. Ends your turn; do not also answer.";
	}

	@Override
	public Class<ClarifyInput> inputType() {
		return ClarifyInput.class;
	}

	@Override
	public String statusLabel() {
		return "Asking a question";
	}

	@Override
	public ToolResult execute(ClarifyInput input, ToolContext context) {
		List<String> options = input.options() == null ? List.of()
				: input.options().stream().map(TextSanitizer::singleLine).toList();
		return ToolResult.ok(new Clarification(TextSanitizer.singleLine(input.question()), options), null, null);
	}

}
