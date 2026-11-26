package com.akshat.ai.help_desk_bot.constants;

public final class IntegrationConstants {

    public static final String TICKET_API_TAG = "Tickets";
    public static final String TICKET_API_DESCRIPTION =
            "Authenticated ticket operations. Ticket ownership is derived from the verified Google identity; "
                    + "clients cannot select or change the ticket owner.";
    public static final String CREATE_TICKET_DESCRIPTION =
            "Create a ticket for the authenticated user. The owner is derived from the bearer token.";
    public static final String LIST_TICKETS_DESCRIPTION =
            "Return a page of tickets owned by the authenticated user.";
    public static final String GET_TICKET_DESCRIPTION =
            "Return a ticket only when it belongs to the authenticated user.";
    public static final String UPDATE_TICKET_DESCRIPTION =
            "Update fields on a ticket only when it belongs to the authenticated user.";
    public static final String UPDATE_TICKET_EMPTY_REQUEST =
            "At least one ticket field must be provided.";
    public static final String TICKET_NOT_FOUND =
            "Ticket not found.";
    public static final String INVALID_TICKET_EVENT =
            "Ticket event is missing its idempotency identifier.";
    public static final String DEAD_LETTER_SUFFIX = ".DLT";
    public static final int EVENT_SCHEMA_VERSION = 1;
    public static final int MAX_OUTBOX_ERROR_LENGTH = 1000;
    public static final int DEFAULT_TICKET_PAGE_SIZE = 20;
    public static final int MAX_TICKET_PAGE_SIZE = 100;

    private IntegrationConstants() {
    }
}
