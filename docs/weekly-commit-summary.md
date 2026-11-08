# Weekly code-commit summary — working record

Repository: https://github.com/syedaafatima/EECS3311-Project

Course periods use **Toronto local time**. Select five distinct meaningful implementation/test changes per period. Repeated imports/merge copies of the same development change and documentation-only commits are not additional meaningful code changes.

## Period 1: October 20–26, 2026

| Commit | Development change | Upload status |
|---|---|---|
| [8542dbe](https://github.com/syedaafatima/EECS3311-Project/commit/8542dbe) | Core domain models and JSON repository | Present in repository history |
| [4ac8717](https://github.com/syedaafatima/EECS3311-Project/commit/4ac8717) | F01 candidate profile through MVC and Facade | Present in repository history |
| [0945542](https://github.com/syedaafatima/EECS3311-Project/commit/0945542) | F04 job import and duplicate updates | Present in repository history |
| [1509555](https://github.com/syedaafatima/EECS3311-Project/commit/1509555) | F14 application tracking/history | Present in repository history |
| [c9cfb2d](https://github.com/syedaafatima/EECS3311-Project/commit/c9cfb2d) | Strategy matching and Period 1 verification | Present in repository history |

## Period 2: October 27–November 2, 2026

| Commit | Development change | Upload status |
|---|---|---|
| [80fbb8e](https://github.com/syedaafatima/EECS3311-Project/commit/80fbb8e) | F02 PDF/DOCX resume import and verification | Present in repository history |
| [5d74f5d](https://github.com/syedaafatima/EECS3311-Project/commit/5d74f5d) | F03 reviewed resume skill extraction | Present in repository history |
| [0c92787](https://github.com/syedaafatima/EECS3311-Project/commit/0c92787) | F05 reviewed job analysis and invalidation | Present in repository history |
| [df2882e](https://github.com/syedaafatima/EECS3311-Project/commit/df2882e) | F15 deadline Observer | Present in repository history |
| [12fc14a](https://github.com/syedaafatima/EECS3311-Project/commit/12fc14a) | Shared Swing GUI and refined UML/workflows | Present in repository history |

## Period 3: November 3–9, 2026

| Commit | Development change | Upload status |
|---|---|---|
| [a73d1ac](https://github.com/syedaafatima/EECS3311-Project/commit/a73d1acb612ee4f6960ba6d7ad0224cae2565010) | Complete F06-F08 AI explanations and deterministic job comparison | Pushed November 7, 18:00:29 Toronto |
| [bbe8de8](https://github.com/syedaafatima/EECS3311-Project/commit/bbe8de8eaee101f27fc26d4357b56f570f55a25f) | Implement F09-F10 editable evidence-backed resume and cover-letter drafts | Pushed November 7, 18:00:29 Toronto |
| [7bd7015](https://github.com/syedaafatima/EECS3311-Project/commit/7bd701565a6de56f4ce1aa3fe2bc185f26c8a927) | Implement F11-F13 interview questions, persistent sessions and rubric feedback | Pushed November 7, 18:00:29 Toronto |
| [579f50a](https://github.com/syedaafatima/EECS3311-Project/commit/579f50ab29664dc0f949ea0aeadf20bbcbec7c5a) | Implement F16-F17 source-cited company research and budgeted career plans | Pushed November 7, 18:00:29 Toronto |
| [0f2b00a](https://github.com/syedaafatima/EECS3311-Project/commit/0f2b00a3611cc0ad830ab2b7f36b90ebf51366fa) | Harden restart-safe drafts, persistence and API setup with desktop verification | Pushed November 7, 18:00:29 Toronto |

All five selected substantive development changes are on main. [6979590](https://github.com/syedaafatima/EECS3311-Project/commit/6979590) additionally fixes tracker-history display. The user-requested free-provider adapter is additionally recorded in [8d52ea2](https://github.com/syedaafatima/EECS3311-Project/commit/8d52ea2). Documentation changes are excluded from the selected five.

## Evidence and counting notes

- Period 1's Strategy/verification commit was pushed at approximately 2026-10-27 03:31 UTC, which is **October 26 at 11:31 p.m. Toronto**, inside Period 1. Do not classify it using UTC calendar dates.
- F02 appears in the history as an original imported commit plus a merge/import copy. It represents one selected development change here.
- The four November 1 additions were confirmed on main, pushed on **November 1 at 4:02:39 p.m. Toronto**, advancing main from d0f9e9f to 12fc14a. GitHub's repository Activity API recorded the push, independently of profile-calendar indexing.
- All five selected Period 3 code commits were pushed November 7 at 23:00:29 UTC / 18:00:29 Toronto, advancing main from 12fc14a to 0f2b00a. The repository Activity API and matching remote SHA confirm this upload.
- The report/checklist files on branch stage2-report-draft-nov7 are documentation changes and are not counted toward the five-code-commit minimum.
- Before final submission, retain these direct commit links in the reviewed report and verify the final source/report/video links. Dates and history have not been changed.

## Additional desktop usability refinement

[9b59fb2](https://github.com/syedaafatima/EECS3311-Project/commit/9b59fb2d40064fb581b5b0e82205e7daf56d49a4) adds sidebar navigation, a built-in shared Swing theme, responsive action buttons, readable multiline inputs and separate saved-job/posting-entry tools. Native checks exercise both window sizes and a real displayed ranking; the full desktop-enabled suite passed 61 tests. The legacy verification fixture now uses Toronto dates consistently. This is an additional code change beyond the five selected Period 3 commits; report/export-only changes are excluded from the five-code-commit count.
