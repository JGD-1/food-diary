-- Foods with a barcode (label scans, typed foods, cached Open Food Facts) are known to the whole household:
-- a pack one person scanned is found by barcode on the other phone too. Only the owner can change them
-- ("own foods" stays the only rule for saving). Foods without a barcode stay private.

create policy "household barcode foods" on public.foods for select to authenticated
  using (
    barcode is not null
    and owner_id in (select m.user_id from public.household_members m where m.household_id = public.my_household())
  );

-- Phones have already fetched past the foods saved before this rule. A new server time makes them fetch
-- those foods again; nothing else about the rows changes (the stamp trigger would skip an unchanged row).
alter table public.foods disable trigger sync_stamp;
update public.foods set server_updated_at = clock_timestamp() where barcode is not null and not deleted;
alter table public.foods enable trigger sync_stamp;
