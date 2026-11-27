export class TicketApiError extends Error {
  constructor(status, body) {
    super(`Helpdesk API request failed with status ${status}`);
    this.name = "TicketApiError";
    this.status = status;
    this.body = body;
  }
}

export class TicketClient {
  constructor({ baseUrl, tokenProvider, fetchImpl = globalThis.fetch }) {
    if (!baseUrl) throw new TypeError("baseUrl is required");
    if (typeof tokenProvider !== "function") throw new TypeError("tokenProvider must be a function");
    if (typeof fetchImpl !== "function") throw new TypeError("fetch is not available");

    this.baseUrl = baseUrl.replace(/\/+$/, "");
    this.tokenProvider = tokenProvider;
    this.fetchImpl = fetchImpl;
  }

  async listTickets({ page = 0, size = 20, sort = "createdAt,desc" } = {}) {
    const query = new URLSearchParams({ page: String(page), size: String(size), sort });
    return this.request(`/v1/tickets?${query}`);
  }

  async getTicket(id) {
    return this.request(`/v1/tickets/${encodeURIComponent(id)}`);
  }

  async createTicket(ticket) {
    return this.request("/v1/tickets", { method: "POST", body: ticket });
  }

  async updateTicket(id, updates) {
    return this.request(`/v1/tickets/${encodeURIComponent(id)}`, {
      method: "PATCH",
      body: updates
    });
  }

  async request(path, { method = "GET", body } = {}) {
    const token = await this.tokenProvider();
    if (typeof token !== "string" || token.trim().length === 0) {
      throw new TypeError("tokenProvider must return a non-empty bearer token");
    }

    const headers = {
      Accept: "application/json",
      Authorization: `Bearer ${token}`
    };
    const request = { method, headers };
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
      request.body = JSON.stringify(body);
    }

    const response = await this.fetchImpl(`${this.baseUrl}${path}`, request);
    const text = await response.text();
    const responseBody = text.length === 0 ? undefined : JSON.parse(text);
    if (!response.ok) throw new TicketApiError(response.status, responseBody);
    return responseBody;
  }
}
