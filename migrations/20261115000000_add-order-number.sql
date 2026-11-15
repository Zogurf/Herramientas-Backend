CREATE SEQUENCE IF NOT EXISTS public.orders_number_seq
    START WITH 1
    INCREMENT BY 1;

ALTER TABLE public.orders
ADD COLUMN IF NOT EXISTS order_number VARCHAR(20);

UPDATE public.orders
SET order_number = 'PED-' || LPAD(
    nextval('public.orders_number_seq')::TEXT,
    6,
    '0'
)
WHERE order_number IS NULL;

ALTER TABLE public.orders
ALTER COLUMN order_number SET NOT NULL;

ALTER TABLE public.orders
ALTER COLUMN order_number
SET DEFAULT 'PED-' || LPAD(
    nextval('public.orders_number_seq')::TEXT,
    6,
    '0'
);

ALTER TABLE public.orders
ADD CONSTRAINT uq_orders_order_number UNIQUE (order_number);