export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type TicketStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";

export interface Ticket {
  id: number;
  username: string;
  title: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  assignee: string;
  createdAt: string;
  updatedAt: string;
}

export interface TicketPage {
  content: Ticket[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface CreateTicket {
  title: string;
  description: string;
  priority?: TicketPriority;
}

export interface UpdateTicket {
  title?: string;
  description?: string;
  priority?: TicketPriority;
  status?: TicketStatus;
  assignee?: string;
}

export class TicketApiError extends Error {
  readonly status: number;
  readonly body: unknown;
}

export class TicketClient {
  constructor(options: {
    baseUrl: string;
    tokenProvider: () => string | Promise<string>;
    fetchImpl?: typeof fetch;
  });
  listTickets(options?: { page?: number; size?: number; sort?: string }): Promise<TicketPage>;
  getTicket(id: number | string): Promise<Ticket>;
  createTicket(ticket: CreateTicket): Promise<Ticket>;
  updateTicket(id: number | string, updates: UpdateTicket): Promise<Ticket>;
}
