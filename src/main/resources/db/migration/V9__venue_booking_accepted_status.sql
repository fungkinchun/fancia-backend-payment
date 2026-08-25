ALTER TABLE venue_bookings
    DROP CONSTRAINT IF EXISTS venue_bookings_status_check;

UPDATE venue_bookings
SET status = 'REQUESTED'
WHERE status = 'APPROVED';

UPDATE venue_bookings
SET status = 'ACCEPTED'
WHERE status = 'COMPLETED';

UPDATE venue_bookings
SET status = 'DENIED'
WHERE status = 'CANCELLED';

ALTER TABLE venue_bookings
    ADD CONSTRAINT venue_bookings_status_check CHECK (
        status IN (
            'REQUESTED',
            'PAID',
            'ACCEPTED',
            'DENIED',
            'WITHDRAWN',
            'EXPIRED'
        )
    );
