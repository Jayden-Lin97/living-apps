package dev.livingapps;

import static org.junit.Assert.*;

import dev.livingapps.core.J;
import dev.livingapps.core.ResponsesProtocol;
import dev.livingapps.core.Templates;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Real HTTP transport against a loopback fixture with no model or external service. */
public class ResponsesClientTest {
  private ServerSocket server;
  private ExecutorService worker;
  private ResponsesClient client;

  @Before
  public void start() throws IOException {
    server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
    server.setSoTimeout(3000);
    worker = Executors.newSingleThreadExecutor();
    client =
        new ResponsesClient("http://127.0.0.1:" + server.getLocalPort() + "/v1", "fixture-token");
  }

  @After
  public void stop() throws IOException {
    if (server != null) server.close();
    if (worker != null) worker.shutdownNow();
  }

  @Test(timeout = 10000)
  public void completedResponseProducesCandidateAndRequestKeepsConfiguredModel() throws Exception {
    JSONObject definition = Templates.all().get(0).json();
    String candidate = J.obj("app", definition, "explanation", "Fixture preview").toString();
    Future<String> request =
        respond(
            200,
            "",
            J.obj(
                    "status",
                    "completed",
                    "error",
                    JSONObject.NULL,
                    "output",
                    J.arr(
                        J.obj(
                            "type",
                            "message",
                            "content",
                            J.arr(J.obj("type", "output_text", "text", candidate)))))
                .toString());

    ResponsesProtocol.Generated result =
        client.generate("fixture-model", "Design", "Fixture input");
    JSONObject body = J.parse(request.get(3, TimeUnit.SECONDS));
    assertEquals(definition.optString("name"), result.spec.name());
    assertEquals("Fixture preview", result.explanation);
    assertEquals("fixture-model", body.optString("model"));
    assertFalse(body.optBoolean("store", true));
    assertFalse(body.optBoolean("stream", true));
  }

  @Test(timeout = 10000)
  public void rateLimitErrorDoesNotExposeResponseBody() {
    respond(429, "", "private-fixture-body");

    IOException error = assertThrows(IOException.class, client::models);
    assertTrue(error.getMessage().contains("429"));
    assertFalse(error.getMessage().contains("private-fixture-body"));
  }

  @Test(timeout = 10000)
  public void modelDiscoveryReportsRedirectWithoutFollowingIt() {
    respond(307, "Location: /redirected\r\n", "");

    IOException error = assertThrows(IOException.class, client::models);
    assertTrue(error.getMessage().contains("307"));
  }

  private Future<String> respond(int status, String headers, String body) {
    return worker.submit(
        () -> {
          try (Socket socket = server.accept()) {
            // Only one request is served. A redirect that is followed cannot succeed silently.
            server.close();
            socket.setSoTimeout(3000);
            DataInputStream input =
                new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            readHeaderLine(input);
            int length = 0;
            for (String line = readHeaderLine(input);
                !line.isEmpty();
                line = readHeaderLine(input)) {
              if (line.toLowerCase(Locale.ROOT).startsWith("content-length:"))
                length = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
            byte[] request = new byte[length];
            input.readFully(request);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            OutputStream output = socket.getOutputStream();
            output.write(
                ("HTTP/1.1 "
                        + status
                        + " Fixture\r\nContent-Type: application/json\r\n"
                        + "Connection: close\r\nContent-Length: "
                        + bytes.length
                        + "\r\n"
                        + headers
                        + "\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.flush();
            return new String(request, StandardCharsets.UTF_8);
          }
        });
  }

  private static String readHeaderLine(DataInputStream input) throws IOException {
    StringBuilder line = new StringBuilder();
    for (int value = input.readUnsignedByte(); value != '\n'; value = input.readUnsignedByte()) {
      if (value != '\r') line.append((char) value);
    }
    return line.toString();
  }
}
