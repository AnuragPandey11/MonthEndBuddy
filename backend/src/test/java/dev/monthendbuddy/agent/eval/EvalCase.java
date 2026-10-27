package dev.monthendbuddy.agent.eval;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/** One case of {@code eval/cases.yaml}; see that file and docs/ai-evaluation.md for the meaning of each field. */
record EvalCase(String id, String question, List<String> status, List<String> tools, AmountSpec amount,
		List<List<String>> mentions, List<String> absent, List<String> forbidTools, List<FakeCall> fakeCalls,
		String fakeAnswer) {

	/** The figure the answer must state: a JSON money field from a REST GET, or from a tool's evidence. */
	record AmountSpec(String rest, String evidence, String field) {
	}

	record FakeCall(String tool, Map<String, Object> args) {
	}

	@Override
	public String toString() {
		return id;
	}

	@SuppressWarnings("unchecked")
	static List<EvalCase> load() {
		try (InputStream in = EvalCase.class.getResourceAsStream("/eval/cases.yaml")) {
			Map<String, Object> root = new Yaml().load(in);
			return ((List<Map<String, Object>>) root.get("cases")).stream().map(EvalCase::of).toList();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static EvalCase of(Map<String, Object> c) {
		Map<String, Object> amount = (Map<String, Object>) c.get("amount");
		Map<String, Object> fake = (Map<String, Object>) c.get("fake");
		List<FakeCall> calls = ((List<Map<String, Object>>) fake.get("calls")).stream()
			.map(call -> new FakeCall((String) call.get("tool"), (Map<String, Object>) call.get("args")))
			.toList();
		return new EvalCase((String) c.get("id"), (String) c.get("question"), list(c, "status"), list(c, "tools"),
				amount == null ? null
						: new AmountSpec((String) amount.get("rest"), (String) amount.get("evidence"),
								(String) amount.get("field")),
				(List<List<String>>) c.getOrDefault("mentions", List.of()), list(c, "absent"), list(c, "forbidTools"),
				calls, (String) fake.get("answer"));
	}

	@SuppressWarnings("unchecked")
	private static List<String> list(Map<String, Object> c, String key) {
		return ((List<Object>) c.getOrDefault(key, List.of())).stream().map(String::valueOf).toList();
	}

}
