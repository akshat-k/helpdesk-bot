# Reuse NovaDesk in other projects

The stable integration point is the authenticated, versioned REST API. It is
described by the live OpenAPI document at `/v3/api-docs`; use that document as
the source when generating clients in other languages.

## JavaScript and TypeScript client

The dependency-free Fetch client supports bearer-token providers so callers can
refresh or retrieve credentials without embedding them in the package:

```js
import { TicketClient } from "@novadesk/ticket-client";

const tickets = new TicketClient({
  baseUrl: process.env.HELPDESK_API_URL,
  tokenProvider: () => getCurrentUserGoogleIdToken()
});

const page = await tickets.listTickets({ page: 0, size: 20 });
```

In a consuming repository, install the package from the monorepo or publish the
folder to the organization's npm registry:

```sh
npm install ./path/to/helpdesk-bot/integrations/ticket-client
```

## MCP server

`@novadesk/helpdesk-mcp-server` exposes `list_tickets`, `get_ticket`,
`create_ticket`, and `update_ticket` as MCP tools. It calls the same REST API,
so identity and owner scoping remain enforced by the service:

```json
{
  "mcpServers": {
    "novadesk-helpdesk": {
      "command": "node",
      "args": ["/absolute/path/integrations/mcp-server/src/server.js"],
      "env": {
        "HELPDESK_API_URL": "https://helpdesk.example.com",
        "HELPDESK_BEARER_TOKEN_FILE": "/run/secrets/helpdesk_bearer_token"
      }
    }
  }
}
```

Inject short-lived tokens using the MCP host's secret/token provider; do not
commit tokens to client configuration. Prefer a mounted token file so a
supervisor can rotate the credential between calls without restarting the MCP
server. `HELPDESK_BEARER_TOKEN` is available for simple local development only.
The MCP server uses stdio and never exposes a network listener.

## OpenAPI-generated clients

For languages not covered by the Fetch client, download the OpenAPI document
from the target deployment and generate a client using the consumer project's
standard generator. Keep generated clients pinned to a specific API version
and review generated diffs on upgrades.
