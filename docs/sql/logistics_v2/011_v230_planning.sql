-- Verto Logistics V2 v230 planning contract. Remote runtime remains OFF.
-- Planning creates route legs before a carrier is selected; carrier is execution data (v231).
ALTER TABLE public.logistics_shipment_legs
    ALTER COLUMN carrier_partner_id DROP NOT NULL;

COMMENT ON COLUMN public.logistics_shipment_legs.carrier_partner_id IS
    'Nullable during DRAFT/READY planning; must be resolved before operational movement.';
