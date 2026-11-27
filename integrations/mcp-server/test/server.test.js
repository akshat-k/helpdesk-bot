import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { fileURLToPath } from "node:url";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

test("exposes the authenticated ticket API as MCP tools", async t => {
  const requests = [];
  const api = createServer(async (request, response) => {
    let body = "";
    for await (const chunk of request) body += chunk;
    requests.push({
      method: request.method,
      url: request.url,
      authorization: request.headers.authorization,
      body: body === "" ? undefined : JSON.parse(body)
    });

    if (request.method === "GET") {
      response.writeHead(200, { "content-type": "application/json" });
      response.end(JSON.stringify({
        content: [{ id: 8, title: "VPN issue" }],
        page: { size: 10, number: 0, totalElements: 1, totalPages: 1 }
      }));
      return;
    }

    response.writeHead(201, { "content-type": "application/json" });
    response.end(JSON.stringify({ id: 9, title: "Network access" }));
  });
  await new Promise(resolve => api.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise(resolve => api.close(resolve)));

  const address = api.address();
  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [fileURLToPath(new URL("../src/server.js", import.meta.url))],
    env: {
      ...process.env,
      HELPDESK_API_URL: `http://127.0.0.1:${address.port}`,
      HELPDESK_BEARER_TOKEN: "test-user-token"
    }
  });
  const client = new Client({ name: "helpdesk-mcp-test", version: "1.0.0" }, { capabilities: {} });
  await client.connect(transport);
  t.after(() => client.close());

  const listedTools = await client.listTools();
  assert.deepEqual(
    listedTools.tools.map(tool => tool.name).sort(),
    ["create_ticket", "get_ticket", "list_tickets", "update_ticket"]
  );

  const listResult = await client.callTool({
    name: "list_tickets",
    arguments: { page: 0, size: 10 }
  });
  assert.equal(listResult.isError, undefined);
  assert.equal(requests[0].authorization, "Bearer test-user-token");
  assert.equal(requests[0].url, "/v1/tickets?page=0&size=10&sort=createdAt%2Cdesc");

  const createResult = await client.callTool({
    name: "create_ticket",
    arguments: { title: "Network access", description: "Cannot access VPN", priority: "HIGH" }
  });
  assert.equal(createResult.isError, undefined);
  assert.deepEqual(requests[1].body, {
    title: "Network access",
    description: "Cannot access VPN",
    priority: "HIGH"
  });

  const invalidUpdate = await client.callTool({
    name: "update_ticket",
    arguments: { id: 9 }
  });
  assert.equal(invalidUpdate.isError, true);
  assert.equal(requests.length, 2);
});
