# Verto v274 — Party Cutover

- Status: IMPLEMENTED; runtime tests NOT_RUN.
- Client writes now dual-write normalized roles/profiles transactionally.
- Lists, search, activity, inventory supplier lookup and logistics supplier scope use normalized data.
- Delete flow archives roles; hard delete rejects any retained role/history.
