import test from "node:test";
import assert from "node:assert/strict";
import { TicketApiError, TicketClient } from "../src/index.js";

function response(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body)
  };
}

test("sends authenticated list request with pagination", async () => {
  let request;
  const client = new TicketClient({
    baseUrl: "https://helpdesk.example/",
    tokenProvider: async () => "jwt-token",
    fetchImpl: async (url, options) => {
      request = { url, options };
      return response({ content: [], page: { size: 10, number: 2, totalElements: 0, totalPages: 0 } });
    }
  });

  await client.listTickets({ page: 2, size: 10 });

  assert.equal(request.url, "https://helpdesk.example/v1/tickets?page=2&size=10&sort=createdAt%2Cdesc");
  assert.equal(request.options.headers.Authorization, "Bearer jwt-token");
});

test("sends create and patch bodies as JSON", async () => {
  const requests = [];
  const client = new TicketClient({
    baseUrl: "https://helpdesk.example",
    tokenProvider: () => "jwt-token",
    fetchImpl: async (url, options) => {
      requests.push({ url, options });
      return response({ id: 7 });
    }
  });

  await client.createTicket({ title: "VPN issue", description: "Cannot connect" });
  await client.updateTicket(7, { status: "IN_PROGRESS" });

  assert.equal(requests[0].options.method, "POST");
  assert.deepEqual(JSON.parse(requests[0].options.body), {
    title: "VPN issue",
    description: "Cannot connect"
  });
  assert.equal(requests[1].options.method, "PATCH");
  assert.deepEqual(JSON.parse(requests[1].options.body), { status: "IN_PROGRESS" });
});

test("surfaces API errors without hiding their HTTP status", async () => {
  const client = new TicketClient({
    baseUrl: "https://helpdesk.example",
    tokenProvider: () => "jwt-token",
    fetchImpl: async () => response({ detail: "Not found" }, 404)
  });

  await assert.rejects(client.getTicket(999), error => {
    assert.ok(error instanceof TicketApiError);
    assert.equal(error.status, 404);
    assert.deepEqual(error.body, { detail: "Not found" });
    return true;
  });
});

test("rejects empty tokens before sending a request", async () => {
  let requestCount = 0;
  const client = new TicketClient({
    baseUrl: "https://helpdesk.example",
    tokenProvider: () => "",
    fetchImpl: async () => {
      requestCount += 1;
      return response({});
    }
  });

  await assert.rejects(client.listTickets(), TypeError);
  assert.equal(requestCount, 0);
});
