package dev.monthendbuddy.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Minimal HTTP client for integration tests: JSON in, {@link Response} out. */
public class ApiClient {

	public static final String ORIGIN = "http://localhost:8080";

	private final HttpClient http = HttpClient.newHttpClient();

	private final int port;

	private final JsonMapper json;

	private final Map<String, String> headers = new LinkedHashMap<>();

	public ApiClient(int port, JsonMapper json) {
		this.port = port;
		this.json = json;
	}

	public static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.test";
	}

	public ApiClient header(String name, String value) {
		headers.put(name, value);
		return this;
	}

	public ApiClient bearer(String accessToken) {
		return header("Authorization", "Bearer " + accessToken);
	}

	/** Headers the SPA sends on the cookie-authenticated endpoints, plus the refresh cookie. */
	public ApiClient refreshCookie(String refreshToken) {
		return header("Cookie", "meb_refresh=" + refreshToken).header("Origin", ORIGIN).header("X-MEB-Client", "web");
	}

	public Response get(String path) {
		return send(request(path).GET());
	}

	public Response post(String path, Object body) {
		return send(request(path).header("Content-Type", "application/json").POST(publisher(body)));
	}

	public Response put(String path, Object body) {
		return send(request(path).header("Content-Type", "application/json").PUT(publisher(body)));
	}

	public Response patch(String path, Object body) {
		return send(request(path).header("Content-Type", "application/json").method("PATCH", publisher(body)));
	}

	public Response delete(String path) {
		return send(request(path).DELETE());
	}

	/** Multipart upload of one file field named {@code file}. */
	public Response upload(String path, String filename, String contentType, byte[] content) {
		String boundary = "----meb" + UUID.randomUUID();
		byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
				+ "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] body = new byte[head.length + content.length + tail.length];
		System.arraycopy(head, 0, body, 0, head.length);
		System.arraycopy(content, 0, body, head.length, content.length);
		System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
		return send(request(path).header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.POST(HttpRequest.BodyPublishers.ofByteArray(body)));
	}

	public Response uploadCsv(String csv) {
		return upload("/api/v1/imports", "statement.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	/** Any method, for table-driven tests. */
	public Response call(String method, String path, Object body) {
		return switch (method) {
			case "GET" -> get(path);
			case "POST" -> post(path, body);
			case "PUT" -> put(path, body);
			case "PATCH" -> patch(path, body);
			case "DELETE" -> delete(path);
			default -> throw new IllegalArgumentException(method);
		};
	}

	private HttpRequest.BodyPublisher publisher(Object body) {
		return body == null ? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : json.writeValueAsString(body));
	}

	/** Registers a fresh user and returns its tokens. */
	public Session register(String email, String password) {
		Response response = post("/api/v1/auth/register",
				Map.of("email", email, "password", password, "displayName", "Test User"));
		if (response.status() != 201) {
			throw new IllegalStateException("register failed: " + response.status() + " " + response.body());
		}
		return new Session(response.json().get("accessToken").asString(), response.cookie("meb_refresh").orElseThrow());
	}

	public Session register() {
		return register(uniqueEmail(), "correct-horse-battery");
	}

	private HttpRequest.Builder request(String path) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		headers.forEach(builder::header);
		return builder;
	}

	private Response send(HttpRequest.Builder builder) {
		try {
			HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			return new Response(response.statusCode(), response.headers(), response.body(), json);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	public record Session(String accessToken, String refreshToken) {
	}

	public record Response(int status, HttpHeaders headers, String body, JsonMapper mapper) {

		public JsonNode json() {
			return mapper.readTree(body);
		}

		public Optional<String> header(String name) {
			return headers.firstValue(name);
		}

		/** The full {@code Set-Cookie} header for a cookie, e.g. to check its attributes. */
		public Optional<String> setCookie(String name) {
			return headers.allValues("Set-Cookie").stream().filter(c -> c.startsWith(name + "=")).findFirst();
		}

		public Optional<String> cookie(String name) {
			return setCookie(name).map(c -> c.substring(name.length() + 1, c.indexOf(';')));
		}

	}

}
