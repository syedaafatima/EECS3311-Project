# Stage 2 repository commit summary

Repository: https://github.com/syedaafatima/EECS3311-Project

## Commit coverage

The log includes all 43 unique Stage 2 commits reachable from the published repository branches at checkpoint [02db7ed](https://github.com/syedaafatima/EECS3311-Project/commit/02db7ed01644ffc2250e02213f7ef6de4bb209fc): 39 on main and 4 additional report-draft commits. Commit dates use Toronto local time and show the full month name and day only. Code, tests, documentation, configuration and merges are recorded. Documentation/configuration commits and repeated merge copies do not count as separate meaningful code changes. Later publication-only changes can be inspected in the [full main-branch history](https://github.com/syedaafatima/EECS3311-Project/commits/main).

Period 3 is displayed through November 14 for this development log. The original Stage 2 brief defines the minimum five-code-commit window as November 3–9; later follow-up entries are listed without extending that original window.

Within the original reporting windows, Periods 1, 2 and 3 contain 5, 5 and 8 implementation/test commits respectively. Each meets the minimum of five; documentation, configuration and merge entries are excluded from these counts.

**Period 1 (October 20–26)**

| Date | Commit | Change | Type |
|---|---|---|---|
| October 25 | [843af84](https://github.com/syedaafatima/EECS3311-Project/commit/843af846cece205112223f2ced6c5111b082f892) | Create README with the project description. | Docs |
| October 25 | [b80574f](https://github.com/syedaafatima/EECS3311-Project/commit/b80574f42fbc7531cc801f4089f11f916fa18ad6) | Revise README project information. | Docs |
| October 25 | [8542dbe](https://github.com/syedaafatima/EECS3311-Project/commit/8542dbe6ec354758c494af9cb61d6bea3026dfee) | Add the .env.example configuration template; this commit does not contain domain model code. | Config |
| October 25 | [a500b89](https://github.com/syedaafatima/EECS3311-Project/commit/a500b89df5ebb08f6c90022e041699075e0d526b) | Implement candidate/job domain models, JSON support and repository persistence. | Code |
| October 25 | [4ac8717](https://github.com/syedaafatima/EECS3311-Project/commit/4ac8717a5133c02cff9861e446c483d270429d3d) | Connect F01 profile management through MVC, Facade and the CLI. | Code |
| October 25 | [0945542](https://github.com/syedaafatima/EECS3311-Project/commit/0945542134c520d84f7407335a13395066792295) | Implement F04 job import and duplicate-posting updates. | Code |
| October 26 | [1509555](https://github.com/syedaafatima/EECS3311-Project/commit/15095554d2d04cb3b67af374a0eec053370da747) | Implement F14 application tracking and persisted status history. | Code |
| October 26 | [c9cfb2d](https://github.com/syedaafatima/EECS3311-Project/commit/c9cfb2dbba72905e30b81815963435061d74f8bd) | Add Strategy matching policies and Period 1 verification. | Code/tests |

**Period 2 (October 27–November 2)**

| Date | Commit | Change | Type |
|---|---|---|---|
| October 29 | [80fbb8e](https://github.com/syedaafatima/EECS3311-Project/commit/80fbb8e8d90ef99ca6d5c0130fe069b75db10343) | Implement F02 PDF/DOCX import through the document Adapter, Maven build and import tests. | Code/tests |
| October 29 | [92c6cf4](https://github.com/syedaafatima/EECS3311-Project/commit/92c6cf43d041a7ea16a630fbe9f3f7624e80fb7a) | Merge the F02 resume-import branch; incorporates 80fbb8e without a second independent implementation. | Merge |
| October 29 | [d0f9e9f](https://github.com/syedaafatima/EECS3311-Project/commit/d0f9e9f6bc3a94dee20628eea9702c7ea9ea3d57) | Update README after resume-import integration. | Docs |
| November 1 | [5d74f5d](https://github.com/syedaafatima/EECS3311-Project/commit/5d74f5df79703a8e06bb4889304313e3cc6b6b48) | Implement F03 evidence-backed skill extraction, LLM Adapter integration and approvals. | Code/tests |
| November 1 | [0c92787](https://github.com/syedaafatima/EECS3311-Project/commit/0c92787930b9f172cac6fbc6f316ef6ea34d1866) | Implement F05 reviewed requirements, source checks and stale-analysis invalidation. | Code/tests |
| November 1 | [df2882e](https://github.com/syedaafatima/EECS3311-Project/commit/df2882eb431f01d2afb6d1169f46668a5a5a8821) | Implement F15 timezone-aware deadlines and Observer notifications. | Code/tests |
| November 1 | [12fc14a](https://github.com/syedaafatima/EECS3311-Project/commit/12fc14a3e4c5a56db85f9c3e756db2a974083cf5) | Add the shared Swing GUI and refine implemented UML and feature workflows. | Code/tests |

**Period 3 (November 3–14)**

| Date | Commit | Change | Type |
|---|---|---|---|
| November 7 | [a73d1ac](https://github.com/syedaafatima/EECS3311-Project/commit/a73d1acb612ee4f6960ba6d7ad0224cae2565010) | Complete F06-F08 explanations, deterministic assessment and job comparison. | Code/tests |
| November 7 | [bbe8de8](https://github.com/syedaafatima/EECS3311-Project/commit/bbe8de8eaee101f27fc26d4357b56f570f55a25f) | Implement F09-F10 editable evidence-backed resume advice and cover-letter drafts. | Code/tests |
| November 7 | [7bd7015](https://github.com/syedaafatima/EECS3311-Project/commit/7bd701565a6de56f4ce1aa3fe2bc185f26c8a927) | Implement F11-F13 questions, reusable rubrics, persisted interview sessions and answer feedback. | Code/tests |
| November 7 | [579f50a](https://github.com/syedaafatima/EECS3311-Project/commit/579f50ab29664dc0f949ea0aeadf20bbcbec7c5a) | Implement F16-F17 source-cited company summaries and budget-validated career plans. | Code/tests |
| November 7 | [6a3364c](https://github.com/syedaafatima/EECS3311-Project/commit/6a3364ce08125cae07ba5de99500e398b15d82b7) | Draft Stage 2 feature mapping and verification records on the report branch. | Docs/draft |
| November 7 | [2e4adde](https://github.com/syedaafatima/EECS3311-Project/commit/2e4added67471e6bc5aff6724c66cfb933063f66) | Add a feature-by-feature GUI and CLI demonstration checklist on the report branch. | Docs/draft |
| November 7 | [896d723](https://github.com/syedaafatima/EECS3311-Project/commit/896d72308b59372d48f29cfbcd31bc5360aa644a) | Record Toronto weekly commit evidence and upload status on the report branch. | Docs/draft |
| November 7 | [0f2b00a](https://github.com/syedaafatima/EECS3311-Project/commit/0f2b00a3611cc0ad830ab2b7f36b90ebf51366fa) | Harden restart-safe drafts, atomic persistence, source fingerprints and API setup; add desktop checks. | Code/tests |
| November 7 | [6979590](https://github.com/syedaafatima/EECS3311-Project/commit/697959049eb272ffe2cb899bd1edd2b7b2d90ca1) | Display persisted application history in GUI/CLI tracker results and add a regression check. | Code/tests |
| November 7 | [8d52ea2](https://github.com/syedaafatima/EECS3311-Project/commit/8d52ea2e020cbef40f431a92777d1ba6593a57b7) | Add the configurable Groq Adapter with isolated provider credentials and HTTP tests. | Code/tests |
| November 7 | [35c5ae3](https://github.com/syedaafatima/EECS3311-Project/commit/35c5ae3f6c91e49dc04a49cf2dfa5126244d43ed) | Prepare report exports, refined UML, demonstration records and fictional example files. | Docs/UML |
| November 7 | [2921c99](https://github.com/syedaafatima/EECS3311-Project/commit/2921c998ce5bef3fb39cd338933ae9553b5e7644) | Merge current implementation and report drafts into the report review branch. | Merge/draft |
| November 7 | [7bfdf4a](https://github.com/syedaafatima/EECS3311-Project/commit/7bfdf4a1afd76eb59ff1297163572d5e7bb1b25c) | Record successful local Groq verification and remaining feature checks in documentation. | Docs |
| November 7 | [9b59fb2](https://github.com/syedaafatima/EECS3311-Project/commit/9b59fb2d40064fb581b5b0e82205e7daf56d49a4) | Refine Swing with a sidebar, shared theme, responsive controls and native display checks. | Code/tests |
| November 7 | [315dcc4](https://github.com/syedaafatima/EECS3311-Project/commit/315dcc4341ded034ab0e186e38275193bef7da15) | Document desktop changes, live checks, updated UML and local update instructions. | Docs/UML |
| November 12 | [9fa26eb](https://github.com/syedaafatima/EECS3311-Project/commit/9fa26eb2fcfb1ef0e22882af2fac89c2d9935fbd) | Select new records automatically; preserve job/application identity while editing; test collisions and cancellation. | Code/tests |
| November 12 | [fd23537](https://github.com/syedaafatima/EECS3311-Project/commit/fd23537cff80bda3a59acc472317093ad6dbd54f) | Update verified workflow documentation, UML, report exports and the corrected Period 1 commit evidence. | Docs/UML |
| November 14 | [5985534](https://github.com/syedaafatima/EECS3311-Project/commit/5985534aa0111e1d8704d1cec051e7f08ff74364) | Remove Stage 2 report exports and their documentation links during repository cleanup. | Docs |
| November 14 | [61fd001](https://github.com/syedaafatima/EECS3311-Project/commit/61fd001b1e9103ffaa8c423ed4b6af13a30f0b49) | Restore the Stage 2 Markdown source and its links while keeping PDF/Word exports removed. | Docs |
| November 14 | [1ba43f2](https://github.com/syedaafatima/EECS3311-Project/commit/1ba43f2103c00e6cb37a351b4caae3c1f610a0cb) | Remove the repository demonstration guide and its README/progress links. | Docs |
| November 14 | [c161f32](https://github.com/syedaafatima/EECS3311-Project/commit/c161f32f0bcb27ea64570d0ae1722769fb365911) | Add Clear profile with reload/new-candidate persistence checks and native label verification; remove obsolete guides/screenshots. | Code/tests |
| November 14 | [a8d85ea](https://github.com/syedaafatima/EECS3311-Project/commit/a8d85eae892edbfc75990f35ec217440d8612426) | Update weekly commit evidence through the Clear profile checkpoint. | Docs |
| November 14 | [60b2015](https://github.com/syedaafatima/EECS3311-Project/commit/60b20154fc61f8cdae477713df2db53bc38977c4) | Organize README around prerequisites, setup, launch and example usage. | Docs |

**Follow-up revisions (November 15 onward)**

| Date | Commit | Change | Type |
|---|---|---|---|
| November 15 | [4e4c4d4](https://github.com/syedaafatima/EECS3311-Project/commit/4e4c4d40b5d45f607f4d79bad1a00434179ca83e) | Fix AI source references and verify all 17 feature workflows. | Code/tests |
| November 15 | [b379ada](https://github.com/syedaafatima/EECS3311-Project/commit/b379ada81485ada181b050f110299591a6c67fbf) | Link F11-F13 interview demonstrations in the README. | Docs |
| November 15 | [db79949](https://github.com/syedaafatima/EECS3311-Project/commit/db7994935a60e029865c048e6b6bf31f509211ee) | Add F01-F10 demonstration links to complete the F01-F13 video index. | Docs |
| November 15 | [0f604bc](https://github.com/syedaafatima/EECS3311-Project/commit/0f604bcb915ac06512d89a4423cc724d2c602515) | Label demonstration links by feature ID in the README. | Docs |
| November 15 | [02db7ed](https://github.com/syedaafatima/EECS3311-Project/commit/02db7ed01644ffc2250e02213f7ef6de4bb209fc) | Complete the F01-F17 feature demonstration video index. | Docs |
