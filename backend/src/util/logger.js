/**
 * Simple logger for tracking user behavior events.
 * In a production app, this would write to a file or a service like ELK/Sentry.
 */
class Logger {
    static info(message, meta = {}) {
        const timestamp = new Date().toISOString();
        console.log(`[${timestamp}] INFO: ${message}`, Object.keys(meta).length ? meta : '');
    }

    static warn(message, meta = {}) {
        const timestamp = new Date().toISOString();
        console.warn(`[${timestamp}] WARN: ${message}`, Object.keys(meta).length ? meta : '');
    }

    static error(message, error = null, meta = {}) {
        const timestamp = new Date().toISOString();
        console.error(`[${timestamp}] ERROR: ${message}`, error || '', Object.keys(meta).length ? meta : '');
    }

    /**
     * Specifically for tracking user-triggered business events.
     */
    static userEvent(userId, action, details = {}) {
        const timestamp = new Date().toISOString();
        console.log(`[${timestamp}] USER_EVENT | User: ${userId} | Action: ${action} | Details: ${JSON.stringify(details)}`);
    }
}

module.exports = Logger;
