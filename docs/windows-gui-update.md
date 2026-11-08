# Update the desktop layout on Windows

These steps are for an existing project downloaded using GitHub's **Download ZIP**.
The update copies the two presentation source files into that project. Keep using
your current project directory so its private configuration and saved demo data
remain available.

1. Close the Career Agent window and wait for the normal `PS ...>` PowerShell prompt.
2. Make sure PowerShell is in your existing project folder, containing `pom.xml`.
3. Paste and run this whole block:

```powershell
$uiRoot = ".\src\main\java\ca\yorku\careeragent\ui"
$uiSource = "https://raw.githubusercontent.com/syedaafatima/EECS3311-Project/9b59fb2d40064fb581b5b0e82205e7daf56d49a4/src/main/java/ca/yorku/careeragent/ui"
foreach ($uiFile in @("CareerTheme.java", "JobSearchGUI.java")) {
    Invoke-WebRequest "$uiSource/$uiFile" -OutFile (Join-Path $uiRoot $uiFile) -ErrorAction Stop
}
```

4. Compile and launch the updated app:

```powershell
mvn compile exec:java "-Dexec.mainClass=ca.yorku.careeragent.ui.JobSearchGUI"
```

Use the sidebar to navigate. **Jobs & matching → Saved jobs** contains the analysis,
ranking and comparison controls. Use **Add / update posting** to create the next
demo job. The shared UI theme is included in Java and adds no dependency.

![Actual desktop preview with fictional test data](images/gui-refreshed-workspace.png)

The source URLs are pinned to the tested layout commit, so both files come from
the same revision. To update the whole project later, use a current checkout;
this small update only installs the presentation changes. Full validation of the
repository at this checkpoint passed 61 tests, including native window navigation,
file-picker cancellation, resizing and the visible deterministic ranking.
