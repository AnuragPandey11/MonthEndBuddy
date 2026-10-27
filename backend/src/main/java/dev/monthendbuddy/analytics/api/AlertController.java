package dev.monthendbuddy.analytics.api;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.analytics.AlertStatus;
import dev.monthendbuddy.analytics.AlertView;
import dev.monthendbuddy.analytics.AnomalyService;
import dev.monthendbuddy.identity.CurrentUser;

/** Anomaly alerts (architecture §13.2; served from Phase 2, plan C1). */
@RestController
@RequestMapping("/api/v1/alerts")
class AlertController {

	private final AnomalyService anomalies;

	AlertController(AnomalyService anomalies) {
		this.anomalies = anomalies;
	}

	@GetMapping
	List<AlertView> list(CurrentUser user, @RequestParam(defaultValue = "OPEN") AlertStatus status,
			@RequestParam(required = false) YearMonth month) {
		return anomalies.listAlerts(user.id(), status, month);
	}

	/** Dismiss an alert, or mark it expected; neither re-fires for that subject and month. */
	@PatchMapping("/{id}")
	AlertView resolve(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody ResolveRequest body) {
		return anomalies.resolve(user.id(), id, body.status());
	}

	record ResolveRequest(@NotNull AlertStatus status) {
	}

}
