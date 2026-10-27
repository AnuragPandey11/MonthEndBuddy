package dev.monthendbuddy.transactions.api;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.common.CursorPage;
import dev.monthendbuddy.common.web.MoneyDto;
import dev.monthendbuddy.common.web.PositiveMoney;
import dev.monthendbuddy.identity.CurrentUser;
import dev.monthendbuddy.transactions.TransactionFilter;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.TransactionView;
import dev.monthendbuddy.transactions.application.TransactionService;

/** Transaction CRUD with keyset pagination (architecture §13.2). */
@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController {

	private final TransactionService transactions;

	TransactionController(TransactionService transactions) {
		this.transactions = transactions;
	}

	@GetMapping
	CursorPage<TransactionView> list(CurrentUser user,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) UUID categoryId, @RequestParam(required = false) TransactionType type,
			@RequestParam(name = "q", required = false) @Size(max = 100) String text,
			@RequestParam(required = false) BigDecimal minAmount, @RequestParam(required = false) BigDecimal maxAmount,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
		TransactionFilter filter = new TransactionFilter(from, to, categoryId, type, text, minAmount, maxAmount);
		return transactions.list(user.id(), filter, cursor, limit);
	}

	@GetMapping("/{id}")
	TransactionView get(CurrentUser user, @PathVariable UUID id) {
		return transactions.get(user.id(), id);
	}

	@PostMapping
	ResponseEntity<TransactionView> create(CurrentUser user, @Valid @RequestBody TransactionRequest body) {
		TransactionView created = transactions.create(user.id(), body.toInput());
		return ResponseEntity.created(URI.create("/api/v1/transactions/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	TransactionView update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody UpdateTransactionRequest body) {
		return transactions.update(user.id(), id, body.transaction().toInput(), body.version());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(CurrentUser user, @PathVariable UUID id) {
		transactions.delete(user.id(), id);
		return ResponseEntity.noContent().build();
	}

	record TransactionRequest(@NotNull TransactionType type, @NotNull @PositiveMoney MoneyDto amount,
			@NotNull LocalDate transactionDate, @NotBlank @Size(max = 255) String description,
			@Size(max = 120) String merchant, UUID categoryId, @Size(max = 1000) String notes) {

		TransactionService.Input toInput() {
			return new TransactionService.Input(type, amount.toMoney("amount"), transactionDate, description, merchant,
					categoryId, notes);
		}

	}

	/** The same fields as a create, plus the {@code version} the client last saw (optimistic locking). */
	record UpdateTransactionRequest(@NotNull TransactionType type, @NotNull @PositiveMoney MoneyDto amount,
			@NotNull LocalDate transactionDate, @NotBlank @Size(max = 255) String description,
			@Size(max = 120) String merchant, UUID categoryId, @Size(max = 1000) String notes,
			@NotNull Long version) {

		TransactionRequest transaction() {
			return new TransactionRequest(type, amount, transactionDate, description, merchant, categoryId, notes);
		}

	}

}
