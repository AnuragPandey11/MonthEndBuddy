package dev.monthendbuddy.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;

class CategoryApiIT extends IntegrationTest {

	private String token;

	@BeforeEach
	void newUser() {
		token = api().register().accessToken();
	}

	private ApiClient as() {
		return api().bearer(token);
	}

	private List<JsonNode> list() {
		List<JsonNode> result = new ArrayList<>();
		as().get("/api/v1/categories").json().forEach(result::add);
		return result;
	}

	@Test
	void everyUserSeesTheSystemCategoriesGroupedByKind() {
		List<JsonNode> categories = list();
		assertThat(categories).hasSize(21).allMatch(c -> c.get("system").asBoolean());
		assertThat(categories.get(0).get("kind").asString()).isEqualTo("EXPENSE");
		assertThat(categories.get(categories.size() - 1).get("kind").asString()).isEqualTo("INCOME");
		assertThat(categories).extracting(c -> c.get("name").asString()).contains("Dining", "Salary", "EMI & Loans");
	}

	@Test
	void customCategoriesCanBeCreatedRenamedAndArchived() {
		Response created = as().post("/api/v1/categories", Map.of("name", " Pets ", "kind", "EXPENSE", "icon", "paw"));
		assertThat(created.status()).isEqualTo(201);
		String id = created.json().get("id").asString();
		assertThat(created.json().get("name").asString()).isEqualTo("Pets");
		assertThat(created.json().get("system").asBoolean()).isFalse();
		assertThat(list()).extracting(c -> c.get("name").asString()).contains("Pets");

		Response renamed = as().patch("/api/v1/categories/" + id, Map.of("name", "Pet care"));
		assertThat(renamed.status()).isEqualTo(200);
		assertThat(renamed.json().get("name").asString()).isEqualTo("Pet care");
		assertThat(renamed.json().get("icon").asString()).isEqualTo("paw");

		assertThat(as().delete("/api/v1/categories/" + id).status()).isEqualTo(204);
		assertThat(list()).extracting(c -> c.get("name").asString()).doesNotContain("Pet care");
		// The archived name stays reserved, because transactions still show it.
		assertThat(as().post("/api/v1/categories", Map.of("name", "pet CARE", "kind", "EXPENSE")).status())
			.isEqualTo(409);
	}

	@Test
	void namesAreUniquePerUserIncludingSystemNamesCaseInsensitively() {
		as().post("/api/v1/categories", Map.of("name", "Pets", "kind", "EXPENSE"));
		Response duplicate = as().post("/api/v1/categories", Map.of("name", "PETS", "kind", "INCOME"));
		assertThat(duplicate.status()).isEqualTo(409);
		assertThat(duplicate.json().get("code").asString()).isEqualTo("category_name_taken");
		assertThat(as().post("/api/v1/categories", Map.of("name", "dining", "kind", "EXPENSE")).status())
			.isEqualTo(409);

		String other = as().post("/api/v1/categories", Map.of("name", "Hobbies", "kind", "EXPENSE"))
			.json()
			.get("id")
			.asString();
		assertThat(as().patch("/api/v1/categories/" + other, Map.of("name", "pets")).status()).isEqualTo(409);
		assertThat(as().patch("/api/v1/categories/" + other, Map.of("name", "HOBBIES")).status()).isEqualTo(200);

		// Another user may use the same name.
		assertThat(api().bearer(api().register().accessToken())
			.post("/api/v1/categories", Map.of("name", "Pets", "kind", "EXPENSE"))
			.status()).isEqualTo(201);
	}

	@Test
	void systemCategoriesAreReadOnly() {
		Response rename = as().patch("/api/v1/categories/" + TransactionFixtures.DINING, Map.of("name", "Eating out"));
		assertThat(rename.status()).isEqualTo(403);
		assertThat(rename.json().get("code").asString()).isEqualTo("category_read_only");
		assertThat(as().delete("/api/v1/categories/" + TransactionFixtures.DINING).status()).isEqualTo(403);
	}

	@Test
	void requestValidation() {
		assertThat(as().post("/api/v1/categories", Map.of("name", "", "kind", "EXPENSE")).status()).isEqualTo(422);
		assertThat(as().post("/api/v1/categories", Map.of("name", "x".repeat(61), "kind", "EXPENSE")).status())
			.isEqualTo(422);
		assertThat(as().post("/api/v1/categories", Map.of("name", "Fine")).status()).isEqualTo(422);
	}

}
