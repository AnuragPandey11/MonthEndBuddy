package dev.monthendbuddy.transactions.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.identity.CurrentUser;
import dev.monthendbuddy.transactions.MatchType;
import dev.monthendbuddy.transactions.application.CategoryRuleService;
import dev.monthendbuddy.transactions.application.CategoryRuleService.RuleView;

@RestController
@RequestMapping("/api/v1/category-rules")
class CategoryRuleController {

	private final CategoryRuleService rules;

	CategoryRuleController(CategoryRuleService rules) {
		this.rules = rules;
	}

	@GetMapping
	List<RuleView> list(CurrentUser user) {
		return rules.list(user.id());
	}

	@PostMapping
	ResponseEntity<RuleView> create(CurrentUser user, @Valid @RequestBody RuleRequest body) {
		RuleView created = rules.create(user.id(), body.matchType(), body.pattern(), body.categoryId(),
				body.priorityOrDefault());
		return ResponseEntity.created(URI.create("/api/v1/category-rules/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	RuleView update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody UpdateRuleRequest body) {
		return rules.update(user.id(), id, body.matchType(), body.pattern(), body.categoryId(),
				body.priority() == null ? 100 : body.priority(), body.version());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(CurrentUser user, @PathVariable UUID id) {
		rules.delete(user.id(), id);
		return ResponseEntity.noContent().build();
	}

	record RuleRequest(@NotNull MatchType matchType, @NotBlank @Size(max = 100) String pattern,
			@NotNull UUID categoryId, @Min(0) @Max(10000) Integer priority) {

		int priorityOrDefault() {
			return priority == null ? 100 : priority;
		}

	}

	record UpdateRuleRequest(@NotNull MatchType matchType, @NotBlank @Size(max = 100) String pattern,
			@NotNull UUID categoryId, @Min(0) @Max(10000) Integer priority, @NotNull Long version) {
	}

}
