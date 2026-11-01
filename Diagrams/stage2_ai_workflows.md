# Stage 2 AI workflows — separate retrieval and validation branches

Both views use AgentController. No generated output writes data until the candidate
selects numbered proposals and confirms. These are implemented bounded tasks, not
a generic loop standing in for unimplemented AI features.

## F03 — Resume skill extraction

```mermaid
sequenceDiagram
    actor Candidate
    participant View as GUI / CLI
    participant Controller as AgentController
    participant Facade as CareerFacade
    participant Agent as CareerAgent
    participant Tools as ToolManager
    participant LLM as LLMClient / OpenAIAdapter
    participant Store as Repository
    Candidate->>View: Select saved resume; allow provider request
    View->>Controller: submitRequest("resume.extract-skills", resumeId)
    Controller->>Controller: Clear earlier pendingSkills
    Controller->>Facade: extractSkills(resumeId)
    Facade->>Agent: extractSkills(resumeId)
    Agent->>Tools: readProfile(); readResume(resumeId)
    Tools->>Store: loadProfile(); findResume(resumeId)
    alt Missing profile/resume or source exceeds 60000 characters
        Agent-->>Controller: Validation error, no provider request or save
        Controller-->>View: ERROR; manual profile editing available
    else Saved source is usable
        Agent->>LLM: complete(resume-specific instructions, saved text)
        alt Provider/configuration failure or invalid skills schema
            LLM-->>Controller: Error, no data mutation
            Controller-->>View: ERROR; retry or manual entry
        else Structured skills response
            Agent->>Agent: Validate verbatim evidence, whole skill token, lengths, duplicates
            Agent-->>Controller: Immutable SkillProposal list
            Controller-->>View: Numbered skills and supporting excerpts
            alt No grounded proposals
                View-->>Candidate: No skills found; manual entry available
            else Candidate selects numbered proposals
                Candidate->>View: Accept subset, or cancel
                View->>Controller: skills.approve(confirmed, selected)
                alt Cancel or no confirmation
                    Controller->>Controller: Discard pendingSkills
                else Invalid number
                    Controller-->>View: ERROR; saved profile unchanged
                else Valid selection
                    Controller->>Facade: approveSkills(pendingSkills, selected)
                    Facade->>Tools: readProfile()
                    Facade->>Facade: Merge accepted skills with current approved skills
                    Facade->>Store: save(profile)
                    Controller->>Controller: Clear pendingSkills only after successful save
                    Controller-->>View: Approved skills saved
                end
            end
        end
    end
```

## F05 — Job description analysis

```mermaid
sequenceDiagram
    actor Candidate
    participant View as GUI / CLI
    participant Controller as AgentController
    participant Facade as CareerFacade
    participant Agent as CareerAgent
    participant Tools as ToolManager
    participant LLM as LLMClient / OpenAIAdapter
    participant Store as Repository
    Candidate->>View: Select job; allow provider request
    View->>Controller: submitRequest("job.analyze", jobId)
    Controller->>Controller: Clear earlier pendingAnalysis
    Controller->>Facade: analyzeJob(jobId)
    Facade->>Agent: analyzeJob(jobId)
    Agent->>Tools: readJob(jobId)
    Tools->>Store: findJob(jobId)
    alt Missing job or description exceeds 60000 characters
        Agent-->>Controller: Validation error; no provider request
        Controller-->>View: ERROR; correct input or enter manual requirements
    else Saved description available
        Agent->>LLM: complete(job-specific instructions, description)
        alt Provider failure or invalid requirements schema/category
            Agent-->>Controller: Error; previous reviewed analysis unchanged
            Controller-->>View: ERROR; retry or job.requirements fallback
        else Structured requirements response
            Agent->>Agent: Validate category, source quotation, requirement text, lengths, duplicates
            Agent-->>Controller: JobAnalysis(jobId, sourceDescription, requirements)
            Controller-->>View: Numbered required/preferred skills, qualifications, experience, uncertain items
            alt No grounded requirements
                View-->>Candidate: Manual requirements available; no automatic score
            else Candidate reviews proposals
                Candidate->>View: Accept subset, or cancel
                View->>Controller: job.analysis.approve(confirmed, selected)
                alt Cancel
                    Controller->>Controller: Discard pendingAnalysis
                else Valid selection requested
                    Controller->>Facade: approveJobAnalysis(snapshot, selected)
                    Facade->>Tools: readJob(jobId)
                    alt Current description differs from snapshot or selection is invalid
                        Facade-->>View: ERROR; analyze again or correct selection
                    else Source matches and selection is valid
                        Facade->>Facade: Set reviewed skill sets and categorized evidence
                        Facade->>Store: save(job)
                        Controller->>Controller: Clear pendingAnalysis after successful save
                        Controller-->>View: Reviewed analysis saved
                        Candidate->>View: Show job / skill gaps / matching
                        View->>Controller: job.show / job.gaps / job.match
                        Controller->>Facade: Read reviewed data; deterministic Strategy scoring
                        Facade-->>View: Evidence / gaps / ranked jobs
                    end
                end
            end
        end
    end
```

Changing the stored description clears old analysis. Education, experience and
uncertain requirements remain visible with evidence but are not scored as skills.
F06/F07 AI explanations and F08–F13/F16/F17 have no implemented AI branches yet.
