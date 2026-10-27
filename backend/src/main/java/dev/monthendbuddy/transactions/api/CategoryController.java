package dev.monthendbuddy.transactions.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.identity.CurrentUser;
import dev.monthendbuddy.transactions.CategoryKind;
import dev.monthendbuddy.transactions.application.CategoryService;
import dev.monthendbuddy.transactions.application.CategoryService.CategoryView;

/** System and custom categories. Deleting a custom category archives it (architecture §13.2). */
@RestController
@RequestMapping("/api/v1/categories")
class CategoryController {

	private final CategoryService categories;

	CategoryController(CategoryService categories) {
		this.categories = categories;
	}

	@GetMapping
	List<CategoryView> list(CurrentUser user) {
		return categories.listUsable(user.id());
	}

	@PostMapping
	ResponseEntity<CategoryView> create(CurrentUser user, @Valid @RequestBody CreateCategoryRequest body) {
		CategoryView created = categories.create(user.id(), body.name(), body.kind(), body.icon());
		return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	CategoryView update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest body) {
		return categories.update(user.id(), id, body.name(), body.icon());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> archive(CurrentUser user, @PathVariable UUID id) {
		categories.archive(user.id(), id);
		return ResponseEntity.noContent().build();
	}

	record CreateCategoryRequest(@NotBlank @Size(max = 60) String name, @NotNull CategoryKind kind,
			@Size(max = 40) String icon) {
	}

	record UpdateCategoryRequest(@Size(min = 1, max = 60) String name, @Size(max = 40) String icon) {
	}

}
