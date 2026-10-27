package dev.monthendbuddy.agent.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.monthendbuddy.agent.application.AgentOrchestrator;
import dev.monthendbuddy.agent.application.AgentResponse;
import dev.monthendbuddy.agent.application.AgentTurnLock;
import dev.monthendbuddy.agent.application.CollectingAgentEventSink;
import dev.monthendbuddy.agent.application.ConversationService;
import dev.monthendbuddy.agent.application.ConversationService.ConversationSummary;
import dev.monthendbuddy.agent.application.ConversationService.ConversationView;
import dev.monthendbuddy.common.error.ConflictException;
import dev.monthendbuddy.common.error.ProviderUnavailableException;
import dev.monthendbuddy.common.web.CorrelationIdFilter;
import dev.monthendbuddy.common.ratelimit.RateLimitBucket;
import dev.monthendbuddy.common.ratelimit.RateLimiter;
import dev.monthendbuddy.identity.CurrentUser;

/**
 * The copilot (architecture §10). A provider failure is not an HTTP error: it comes back as 200 with
 * {@code status = PROVIDER_UNAVAILABLE}, so the UI can show its fallback.
 * <p>
 * {@code POST /chat} answers with JSON, or streams Server-Sent Events when the request accepts
 * {@code text/event-stream} (architecture §10.7). Both run the same orchestrator; only the event sink differs.
 */
@RestController
@RequestMapping("/api/v1/agent")
class AgentController implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(AgentController.class);

	/** Longer than a turn can run, so the lock only outlives a request that crashed. */
	private static final Duration LOCK_TTL = Duration.ofSeconds(90);

	/**
	 * Streamed turns beyond this many at once are refused rather than queued. The per-user and provider rate limits
	 * already bound the load; this only stops a burst from piling up.
	 */
	private static final int MAX_STREAMS = 64;

	private final AgentOrchestrator orchestrator;

	private final ConversationService conversations;

	private final AgentTurnLock lock;

	private final RateLimiter rateLimiter;

	/**
	 * Runs streamed turns off the request thread, on virtual threads, since a turn spends nearly all its time waiting
	 * on the model. Owned here rather than declared as a bean, which would replace Boot's application executor.
	 */
	private final SimpleAsyncTaskExecutor streams = new SimpleAsyncTaskExecutor("agent-sse-");

	AgentController(AgentOrchestrator orchestrator, ConversationService conversations, AgentTurnLock lock,
			RateLimiter rateLimiter) {
		this.orchestrator = orchestrator;
		this.conversations = conversations;
		this.lock = lock;
		this.rateLimiter = rateLimiter;
		streams.setVirtualThreads(true);
		streams.setConcurrencyLimit(MAX_STREAMS);
		streams.setRejectTasksWhenLimitReached(true);
		// On shutdown, give running turns time to finish and persist.
		streams.setTaskTerminationTimeout(LOCK_TTL.toMillis());
	}

	record ChatRequest(UUID conversationId, @NotBlank @Size(max = 2000) String message) {
	}

	@PostMapping("/chat")
	AgentResponse chat(CurrentUser user, @Valid @RequestBody ChatRequest body) {
		UUID conversationId = admit(user, body);
		try {
			return orchestrator.run(user.id(), conversationId, body.message(), new CollectingAgentEventSink());
		}
		finally {
			release(conversationId);
		}
	}

	/**
	 * The streaming transport. Admission (rate limit, ownership, the turn lock) runs here on the request thread, so
	 * its failures are ordinary problem responses; once the stream opens, a failure is an {@code error} event.
	 */
	@PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter chatStream(CurrentUser user, @Valid @RequestBody ChatRequest body, HttpServletResponse response) {
		UUID conversationId = admit(user, body);
		SseEmitter emitter = new SseEmitter(LOCK_TTL.toMillis());
		Map<String, String> mdc = MDC.getCopyOfContextMap();
		SseAgentEventSink sink = new SseAgentEventSink(emitter, MDC.get(CorrelationIdFilter.MDC_KEY));
		try {
			streams.execute(() -> {
				if (mdc != null) {
					MDC.setContextMap(mdc);
				}
				try {
					orchestrator.run(user.id(), conversationId, body.message(), sink);
				}
				catch (RuntimeException e) {
					log.error("Streamed agent turn failed", e);
					sink.failed("internal_error", "Something went wrong while answering. Try again.");
				}
				finally {
					release(conversationId);
					sink.finish();
					MDC.clear();
				}
			});
		}
		catch (TaskRejectedException e) {
			release(conversationId);
			throw new ProviderUnavailableException("The copilot is busy. Try again in a minute.");
		}
		// Proxies such as nginx must pass each event on as it is written.
		response.setHeader("X-Accel-Buffering", "no");
		return emitter;
	}

	/** Returns the conversation to continue, locked for this turn, or null for a new one. */
	private UUID admit(CurrentUser user, ChatRequest body) {
		rateLimiter.consumeOrThrow(RateLimitBucket.AGENT_CHAT, user.id().value().toString());
		UUID conversationId = body.conversationId();
		if (conversationId == null) {
			return null;
		}
		// Ownership first, so another user's conversation is a 404 and never a 409 that would reveal it exists.
		conversations.require(user.id(), conversationId);
		if (!lock.tryAcquire(conversationId, LOCK_TTL)) {
			throw new ConflictException("turn_in_progress", "Still answering your previous question");
		}
		return conversationId;
	}

	private void release(UUID conversationId) {
		if (conversationId != null) {
			lock.release(conversationId);
		}
	}

	@Override
	public void destroy() {
		streams.close();
	}

	@GetMapping("/conversations")
	List<ConversationSummary> list(CurrentUser user) {
		return conversations.list(user.id());
	}

	@GetMapping("/conversations/{id}")
	ConversationView get(CurrentUser user, @PathVariable UUID id) {
		return conversations.get(user.id(), id);
	}

	@DeleteMapping("/conversations/{id}")
	ResponseEntity<Void> delete(CurrentUser user, @PathVariable UUID id) {
		conversations.delete(user.id(), id);
		return ResponseEntity.noContent().build();
	}

}
