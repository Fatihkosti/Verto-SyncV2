---
status: supporting
scope: system
owner: "documentation-governance"
last_verified_against: v315
---
# Verto v316 Documentation Content Report

**Verdict:** `PASS_DOCUMENTATION_CONTENT_V316`  
**Input:** v315 SHA `85fef440abeaef275f10c2905fa6a28ef0ff4f4205856370bd457d16db69ab44`  
**Architecture:** 6/6 required docs; 31/31 modules documented.  
**API:** 49 production RPC call sites, 48 unique/documented, 21 repository-defined, 27 server-definition absent, 1 explicitly runtime-verified from retained evidence; 71 named direct PostgREST tables; 2 Realtime surfaces.  
**Features:** 16/16 production-critical feature docs.  
**Unverified server dependencies:** every client-only RPC is labeled `SERVER_DEFINITION_NOT_PRESENT / RUNTIME_NOT_VERIFIED`; no server semantics are invented.  
**Integrity:** non-Markdown drift `0`; historical Markdown mutation violations `0`; broken canonical links/orphans/canonical conflicts/Unknown docs `0`.  
**Build/tests/runtime:** NOT RUN for this documentation-only session; no PASS is claimed.  
**Handoff 317:** `true`.
