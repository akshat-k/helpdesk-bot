#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { TicketClient } from "@novadesk/ticket-client";
import { readFile } from "node:fs/promises";

const apiUrl = process.env.HELPDESK_API_URL;
const bearerTokenFile = process.env.HELPDESK_BEARER_TOKEN_FILE;
if (!apiUrl || (!bearerTokenFile && !process.env.HELPDESK_BEARER_TOKEN)) {
  throw new Error("HELPDESK_API_URL and a bearer token or bearer token file must be configured");
}

const client = new TicketClient({
  baseUrl: apiUrl,
  tokenProvider: async () => bearerTokenFile
    ? (await readFile(bearerTokenFile, "utf8")).trim()
    : process.env.HELPDESK_BEARER_TOKEN
});
const server = new McpServer({ name: "novadesk-helpdesk", version: "1.0.0" });

function result(value) {
  return { content: [{ type: "text", text: JSON.stringify(value) }] };
}

function failure(error) {
  return {
    isError: true,
    content: [{ type: "text", text: error instanceof Error ? error.message : "Ticket API request failed" }]
  };
}

server.registerTool(
  "list_tickets",
  {
    description: "List tickets owned by the authenticated user.",
    inputSchema: {
      page: z.number().int().min(0).optional(),
      size: z.number().int().min(1).max(100).optional()
    }
  },
  async input => {
    try {
      return result(await client.listTickets(input));
    } catch (error) {
      return failure(error);
    }
  }
);

server.registerTool(
  "get_ticket",
  {
    description: "Get one ticket owned by the authenticated user.",
    inputSchema: { id: z.number().int().positive() }
  },
  async ({ id }) => {
    try {
      return result(await client.getTicket(id));
    } catch (error) {
      return failure(error);
    }
  }
);

server.registerTool(
  "create_ticket",
  {
    description: "Create a ticket for the authenticated user.",
    inputSchema: {
      title: z.string().trim().min(1).max(200),
      description: z.string().trim().min(1).max(10000),
      priority: z.enum(["LOW", "MEDIUM", "HIGH", "URGENT"]).optional()
    }
  },
  async input => {
    try {
      return result(await client.createTicket(input));
    } catch (error) {
      return failure(error);
    }
  }
);

server.registerTool(
  "update_ticket",
  {
    description: "Update fields on a ticket owned by the authenticated user.",
    inputSchema: {
      id: z.number().int().positive(),
      title: z.string().trim().min(1).max(200).optional(),
      description: z.string().trim().min(1).max(10000).optional(),
      priority: z.enum(["LOW", "MEDIUM", "HIGH", "URGENT"]).optional(),
      status: z.enum(["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"]).optional(),
      assignee: z.string().trim().min(1).max(200).optional()
    }
  },
  async ({ id, ...updates }) => {
    if (Object.keys(updates).length === 0) {
      return failure(new TypeError("At least one update field must be provided"));
    }
    try {
      return result(await client.updateTicket(id, updates));
    } catch (error) {
      return failure(error);
    }
  }
);

const transport = new StdioServerTransport();
await server.connect(transport);
